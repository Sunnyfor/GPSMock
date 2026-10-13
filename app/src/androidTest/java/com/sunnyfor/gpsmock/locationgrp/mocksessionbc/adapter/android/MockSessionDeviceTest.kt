package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.location.LocationManager
import android.os.Build
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
        // 测试观察真实写入需要临时读取权限；生产 Manifest 不申请真实定位权限。
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
