package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sunnyfor.gpsmock.MainActivity
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.StartTarget
import com.sunnyfor.gpsmock.platform.ui.permissions.MockAuthorization
import com.sunnyfor.gpsmock.platform.ui.permissions.readMockAuthorization
import com.sunnyfor.gpsmock.wiring.GpsMockApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/**
 * Android 适配器设备验收；成功写入/后台/通知场景需要用户先在开发者选项选择本应用。
 * 未授权时这些成功场景跳过，负向拒绝场景继续测试；不静默修改 AppOps 或系统安全设置。
 */
class MockSessionDeviceTest {
    /**
     * 通过公开接口启动零点会话后，读取同一轮的 network 与 gps 结果，
     * 核对来源、时钟和参考 APK 的模拟字段，以及 Android 12 起可公开检查的能力声明。
     */
    @Test fun actualReferenceChannelsShareMockSample() = exercise { application, _ ->
        val manager = application.getSystemService(LocationManager::class.java)
        val port = application.session
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        val accepted = port.start(UUID.randomUUID().toString(),
            StartTarget.Inline(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84)))
        assertNull(accepted.error)
        val running = withTimeout(15_000) { port.state.first { it.phase == SessionPhase.Running || it.error != null } }
        assertEquals(SessionPhase.Running, running.phase)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        try {
            val (network, gps) = withTimeout(5_000) {
                var network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                var gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                // 两次跨进程读取可能跨过一轮写入，仅比较同轮且晚于启动请求的样本。
                while (network == null || gps == null || gps.elapsedRealtimeNanos < requestedAt ||
                    network.elapsedRealtimeNanos != gps.elapsedRealtimeNanos) {
                    delay(20)
                    network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
                network to gps
            }
            assertEquals(gps.time, network.time)
            for (sample in listOf(network, gps)) {
                assertEquals(LocationManager.GPS_PROVIDER, sample.provider)
                assertTrue(androidx.core.location.LocationCompat.isMock(sample))
                assertEquals(0.0, sample.latitude, 0.00001)
                assertEquals(0.0, sample.longitude, 0.00001)
                assertEquals(1f, sample.accuracy, 0f)
                assertTrue(sample.hasAltitude())
                assertEquals(30.0, sample.altitude, 0.0)
                assertTrue(sample.hasBearing())
                assertEquals(1f, sample.bearing, 0f)
                assertEquals(7, sample.extras?.getInt("satellites", -1))
                assertFalse(sample.hasSpeed())
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val gpsProperties = requireNotNull(manager.getProviderProperties(LocationManager.GPS_PROVIDER))
                val networkProperties = requireNotNull(manager.getProviderProperties(LocationManager.NETWORK_PROVIDER))
                assertTrue(gpsProperties.hasSatelliteRequirement())
                assertTrue(gpsProperties.hasCellRequirement())
                assertTrue(gpsProperties.hasAltitudeSupport())
                assertTrue(gpsProperties.hasSpeedSupport())
                assertTrue(gpsProperties.hasBearingSupport())
                assertEquals(ProviderProperties.POWER_USAGE_HIGH, gpsProperties.powerUsage)
                assertEquals(ProviderProperties.ACCURACY_COARSE, gpsProperties.accuracy)
                assertEquals(ProviderProperties.POWER_USAGE_LOW, networkProperties.powerUsage)
                assertEquals(ProviderProperties.ACCURACY_FINE, networkProperties.accuracy)
            }
        } finally { automation.dropShellPermissionIdentity() }
    }

    /**
     * 显式设备验收时短暂持有零点模拟，供另一 APK 的实时接收端检查跨进程定位效果。
     * 默认测试集跳过；仅带 externalReceiver=true 时执行，30 秒后无论结果如何均清理本次会话。
     */
    @Test fun holdZeroSessionForExternalReceiver() {
        assumeTrue("Explicit cross-process acceptance only",
            InstrumentationRegistry.getArguments().getString("externalReceiver") == "true")
        exercise { application, scenario ->
            val port = application.session
            val result = port.start(UUID.randomUUID().toString(),
                StartTarget.Inline(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84)))
            assertNull(result.error)
            val running = withTimeout(15_000) { port.state.first { it.phase == SessionPhase.Running || it.error != null } }
            assertEquals(SessionPhase.Running, running.phase)
            scenario.moveToState(Lifecycle.State.CREATED)
            kotlinx.coroutines.delay(30_000L)
            assertEquals(SessionPhase.Running, port.state.value.phase)
        }
    }

    /** 明确未授权时实际 Android 适配器拒绝启动并清理，不以 void API 的正常返回伪装 Running。 */
    @Test fun knownDeniedAuthorizationNeverReportsRunning() = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GpsMockApplication
        assumeTrue("This negative case requires unselected mock app", readMockAuthorization(application) == MockAuthorization.Denied)
        val port = application.session
        withTimeout(10_000) { port.state.first { it.phase == SessionPhase.Idle } }
        val accepted = port.start(UUID.randomUUID().toString(), StartTarget.Inline(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84)))
        try {
            assertNull(accepted.error)
            val stopped = withTimeout(10_000) { port.state.first { it.phase == SessionPhase.Idle } }
            assertEquals(SessionError.PermissionDenied, stopped.error)
            assertNull(stopped.startedAtNanos)
            assertNull(stopped.lastWriteNanos)
        } finally {
            port.state.value.sessionId?.let { port.stop(UUID.randomUUID().toString(), it) }
            withTimeout(10_000) { port.state.first { it.phase == SessionPhase.Idle } }
        }
    }

    /** 合法零点首写入成功后运行；Activity 退后台仍有新确认，停止后释放服务与活动槽位。 */
    @Test fun actualZeroInjectionContinuesInBackgroundAndStops() = exercise { application, scenario ->
        val port = application.session
        val accepted = port.start(UUID.randomUUID().toString(), StartTarget.Inline(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84)))
        assertNull(accepted.error)
        val running = withTimeout(15_000) { port.state.first { it.phase == SessionPhase.Running || it.error != null } }
        assertEquals(SessionPhase.Running, running.phase)
        val locationManager = application.getSystemService(LocationManager::class.java)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // 为本段测试的主动读取临时采用 shell 权限，独立接收端另行验证普通应用身份。
        automation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                val location = locationManager.getLastKnownLocation(provider)
                assertNotNull(location)
                assertEquals(0.0, location!!.latitude, 0.0)
                assertEquals(0.0, location.longitude, 0.0)
                assertTrue(if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider)
            }
        } finally { automation.dropShellPermissionIdentity() }
        scenario.moveToState(Lifecycle.State.CREATED)
        val newer = withTimeout(5_000) { port.state.first { (it.lastWriteNanos ?: 0L) > running.lastWriteNanos!! } }
        assertEquals(SessionPhase.Running, newer.phase)
        assertTrue(application.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
            it.service.className == MockLocationForegroundService::class.java.name && it.foreground
        })
        port.stop(UUID.randomUUID().toString(), accepted.sessionId!!)
        withTimeout(10_000) { port.state.first { it.phase == SessionPhase.Idle } }
        assertFalse(application.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
            it.service.className == MockLocationForegroundService::class.java.name
        })
    }

    /** 通知动作使用同一公开停止入口并关联会话身份；通知权限未授予时跳过此场景。 */
    @Test fun notificationStopCleansTheSameSession() = exercise { application, _ ->
        val notificationManager = application.getSystemService(NotificationManager::class.java)
        assumeTrue("Notification permission/channel must be enabled", notificationManager.areNotificationsEnabled())
        val port = application.session
        val accepted = port.start(UUID.randomUUID().toString(), StartTarget.Inline(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84)))
        assertNull(accepted.error)
        withTimeout(15_000) { port.state.first { it.phase == SessionPhase.Running || it.error != null } }
        assertEquals(SessionPhase.Running, port.state.value.phase)
        val action = withTimeout(5_000) {
            while (true) {
                val notification = notificationManager.activeNotifications.firstOrNull { it.id == 101 }
                val stop = notification?.notification?.actions?.firstOrNull { it.title.toString() == "停止模拟" }
                if (stop != null) return@withTimeout stop
                kotlinx.coroutines.delay(50)
            }
            @Suppress("UNREACHABLE_CODE") error("No notification action")
        }
        action.actionIntent.send()
        withTimeout(10_000) { port.state.first { it.phase == SessionPhase.Idle } }
    }

    /** 只在无活动会话且已由用户授权时测试；失败也在 finally 请求同一会话清理。 */
    private fun exercise(block: suspend (GpsMockApplication, ActivityScenario<MainActivity>) -> Unit) = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GpsMockApplication
        assumeTrue("Select GPSMock as mock location app first", readMockAuthorization(application) == MockAuthorization.Allowed)
        withTimeout(10_000) { application.session.state.first { it.phase == SessionPhase.Idle } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try { block(application, scenario) }
        finally {
            application.session.state.value.sessionId?.let { application.session.stop(UUID.randomUUID().toString(), it) }
            withTimeout(10_000) { application.session.state.first { it.phase == SessionPhase.Idle } }
            scenario.close()
        }
    }
}
