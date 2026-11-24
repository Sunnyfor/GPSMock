package com.sunnyfor.gpsmock.locationprobe

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 独立进程的实时定位接收端，验证模拟是否能通过公开系统定位接口被外部调用者读取。
 *
 * 用户须先主动开启模拟。本测试不启停模拟、不改系统设置、不输出真实或模拟坐标；
 * GPS 的当前模拟位置作为跨通道一致性参照，不能代替坐标转换的已知值测试。
 */
class SystemLocationConsumerTest {
    /**
     * 正在模拟时，外部进程应收到指定系统通道的新模拟回调，且与 GPS 模拟目标一致。
     * 只接受订阅开始后的定位事实，不把旧缓存或引擎自己的 Running 状态当作接收成功。
     * provider 可选择 gps/network/fused/passive/googleflp，默认 gps。订阅仅用于观察选定通道。
     * expectedPassiveSource 可额外检查被动缓存的来源身份，避免只验证坐标而遗漏来源被覆盖。
     */
    @SuppressLint("MissingPermission") // 读取前明确校验用户授予的精准权限；中途撤回仍由系统 API 拒绝。
    @Test(timeout = 60_000L) fun runningSessionIsVisibleToSystemConsumer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val provider = InstrumentationRegistry.getArguments().getString("provider") ?: "gps"
        require(provider in setOf("gps", "network", "fused", "passive", "googleflp")) { "Unsupported observation provider" }
        val monitor = instrumentation.addMonitor(ReceiverObservationActivity::class.java.name, null, false)
        var observation: ReceiverObservationActivity? = null
        try {
            // OEM 拒绝测试进程的后台页面启动；仅用 adb 等价入口打开本方普通页面。
            // 不采用 shell 定位身份、不授予权限、不改变系统设置，定位请求仍来自接收端 UID。
            val launchOutput = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "am start -W -n com.sunnyfor.gpsmock.locationprobe/.ReceiverObservationActivity"
                )
            ).bufferedReader().use { it.readText() }
            assertTrue("Receiver launch failed; location result is inconclusive: $launchOutput", "Status: ok" in launchOutput)
            observation = monitor.waitForActivityWithTimeout(5_000L) as? ReceiverObservationActivity
            assertNotNull("Foreground receiver activity did not start; location result is inconclusive", observation)
            val permissionDeadline = SystemClock.elapsedRealtime() + 40_000L
            var decided = false
            while (!decided && SystemClock.elapsedRealtime() < permissionDeadline) {
                // 创建回调早于窗口获得焦点；仅此一次/前台权限须等真正可见，避免读到 null 缓存而误报停写。
                instrumentation.runOnMainSync { decided = observation!!.permissionDecisionFinished && observation.hasWindowFocus() }
                if (!decided) Thread.sleep(200L)
            }
            assertTrue("Receiver foreground window/permission decision is not ready; location verdict is inconclusive", decided)
            assertTrue("Receiver needs user-approved precise location; denial/timeout is not an engine failure",
                ContextCompat.checkSelfPermission(instrumentation.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            val manager = instrumentation.context.getSystemService(LocationManager::class.java)
            val reference = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            assertTrue("Start a mock session before running receiver tests", reference != null && LocationCompat.isMock(reference))
            val referenceAgeMillis = (SystemClock.elapsedRealtimeNanos() - reference!!.elapsedRealtimeNanos) / 1_000_000L
            assertTrue("Mock writer is not currently fresh: GPS age=${referenceAgeMillis}ms; callback verdict is inconclusive",
                referenceAgeMillis in 0L..5_000L)
            if (provider == "googleflp") {
                observeGoogleFused(reference)
                return
            }
            if (InstrumentationRegistry.getArguments().getString("expectedStationarySpeed") == "true") {
                assertTrue("Stationary GPS speed must be explicit, not the default of an absent field",
                    reference.hasSpeed() && reference.speed == 0f)
            }
            val expectedPassiveSource = InstrumentationRegistry.getArguments().getString("expectedPassiveSource")
            if (!expectedPassiveSource.isNullOrBlank()) {
                val sourceDeadline = SystemClock.elapsedRealtime() + 2_000L
                var consecutive = 0
                var observedSource: String? = null
                while (consecutive < 3 && SystemClock.elapsedRealtime() < sourceDeadline) {
                    val passive = manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    observedSource = passive?.provider
                    consecutive = if (passive != null && LocationCompat.isMock(passive) && passive.provider == expectedPassiveSource) consecutive + 1 else 0
                    // 连续读取跨越短暂的逐通道写入窗口，不把偶然撞上首个 GPS 写入当成一轮最终来源。
                    Thread.sleep(40L)
                }
                assertTrue("Passive cache source mismatch: expected=$expectedPassiveSource, actual=$observedSource",
                    consecutive == 3)
            }
            assertTrue("Requested system provider must exist for this acceptance case: $provider", provider in manager.allProviders)
            val requestedAt = SystemClock.elapsedRealtimeNanos()
            val received = LinkedBlockingQueue<Location>()
            val listener = object : LocationListenerCompat {
                /** 只传递定位事实给测试线程，不输出坐标或改动提供者。 */
                override fun onLocationChanged(location: Location) { received.offer(location) }
            }
            LocationManagerCompat.requestLocationUpdates(
                manager, provider, LocationRequestCompat.Builder(400L).setMinUpdateIntervalMillis(0L).build(),
                { command -> command.run() }, listener
            )
            try {
                val deadline = SystemClock.elapsedRealtime() + 8_000L
                var accepted = false
                var sawFresh = false
                var sawMock = false
                var callbackCount = 0
                while (!accepted && SystemClock.elapsedRealtime() < deadline) {
                    val location = received.poll(400L, TimeUnit.MILLISECONDS) ?: continue
                    callbackCount += 1
                    sawFresh = sawFresh || location.elapsedRealtimeNanos >= requestedAt
                    sawMock = sawMock || LocationCompat.isMock(location)
                    accepted = location.elapsedRealtimeNanos >= requestedAt && LocationCompat.isMock(location) &&
                        abs(location.latitude - reference.latitude) < 0.00001 &&
                        abs(location.longitude - reference.longitude) < 0.00001
                }
                assertTrue("No matching fresh $provider mock callback: callbacks=$callbackCount, fresh=$sawFresh, mock=$sawMock", accepted)
            } finally { manager.removeUpdates(listener) }
        } finally {
            instrumentation.runOnMainSync { observation?.finish() }
            instrumentation.removeMonitor(monitor)
        }
    }

    /**
     * 单独核对 Google Play 服务的实时 FLP 回调，不调用 setMockMode/setMockLocation。
     *
     * 服务不可用属于前置条件不满足；服务可用但收不到新鲜且与 GPS 相同的模拟位置才是通道断言失败。
     * 定位事实仅在内存比较，输出计数和标志，不保存真实坐标；退出时注销本方订阅。
     */
    @SuppressLint("MissingPermission") // 入口已经等待前台焦点并检查用户授予的精准位置权限。
    private fun observeGoogleFused(reference: Location) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        assertTrue("Google Play services unavailable: status=$availability; FLP verdict is inconclusive",
            availability == ConnectionResult.SUCCESS)
        val client = LocationServices.getFusedLocationProviderClient(context)
        val received = LinkedBlockingQueue<Location>()
        val callback = object : LocationCallback() {
            /** 接收本方订阅的所有事实，避免仅看批次末尾掩盖其他来源。 */
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { received.offer(it) }
            }
        }
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        try {
            Tasks.await(client.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 400L)
                    .setMinUpdateIntervalMillis(0L).setMaxUpdateAgeMillis(0L)
                    .setDurationMillis(10_000L).build(), callback, Looper.getMainLooper()
            ), 5L, TimeUnit.SECONDS)
            val deadline = SystemClock.elapsedRealtime() + 8_000L
            var accepted = false
            var sawFresh = false
            var sawMock = false
            var callbackCount = 0
            while (!accepted && SystemClock.elapsedRealtime() < deadline) {
                val location = received.poll(400L, TimeUnit.MILLISECONDS) ?: continue
                callbackCount += 1
                sawFresh = sawFresh || location.elapsedRealtimeNanos >= requestedAt
                sawMock = sawMock || LocationCompat.isMock(location)
                accepted = location.elapsedRealtimeNanos >= requestedAt && LocationCompat.isMock(location) &&
                    abs(location.latitude - reference.latitude) < 0.00001 &&
                    abs(location.longitude - reference.longitude) < 0.00001
            }
            assertTrue("No matching fresh Google FLP mock callback: callbacks=$callbackCount, fresh=$sawFresh, mock=$sawMock", accepted)
        } finally {
            Tasks.await(client.removeLocationUpdates(callback), 3L, TimeUnit.SECONDS)
        }
    }
}
