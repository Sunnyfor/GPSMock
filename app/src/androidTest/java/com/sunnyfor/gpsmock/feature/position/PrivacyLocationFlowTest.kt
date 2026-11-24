package com.sunnyfor.gpsmock.feature.position

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.sunnyfor.gpsmock.platform.ui.mapui.*
import com.sunnyfor.gpsmock.platform.ui.privacy.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** 授权恢复与定位隔离的设备回归；使用独立偏好文件，不授予系统权限或启动真实模拟。 */
class PrivacyLocationFlowTest {
    /** 同意落盘后新 ViewModel 无需重弹；撤回后恢复为关闭，不删除本机业务数据。 */
    @Test fun persistentConsentRestoresAndRevocationSurvivesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "privacy-flow-test-${UUID.randomUUID()}"
        val store = AndroidPrivacyConsentStore(context, name)
        try {
            val first = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store) }
            assertTrue(first.state.value.privacyRequired)
            onMain { first.acceptPrivacy() }
            waitFor { !first.state.value.privacySaving }
            assertEquals(OnlineMapGate.Ready, first.state.value.gate)
            val restored = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store) }
            assertFalse(restored.state.value.privacyRequired)
            assertEquals(OnlineMapGate.Ready, restored.state.value.gate)
            onMain { restored.revokePrivacy() }
            waitFor { !restored.state.value.privacySaving }
            val revoked = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store) }
            assertFalse(revoked.state.value.privacyRequired)
            assertFalse(revoked.state.value.mapConsentAccepted)
            assertEquals(OnlineMapGate.Disabled, revoked.state.value.gate)
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    /** 旧政策不恢复在线状态；清理当前独立测试偏好后等同于无记录。 */
    @Test fun expiredVersionAndMissingRecordRequireNewDisclosure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "privacy-version-test-${UUID.randomUUID()}"
        val store = AndroidPrivacyConsentStore(context, name)
        try {
            runBlocking { store.write(PrivacyConsentRecord(amapVersion = "old", mapAccepted = true)) }
            val model = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store) }
            assertTrue(model.state.value.privacyRequired)
            assertFalse(model.state.value.mapConsentAccepted)
            assertEquals(OnlineMapGate.Disabled, model.state.value.gate)
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    /** 设备位置仅形成相机请求，不自动产生 selected；选点取消未完成定位，迟到结果不能覆盖。 */
    @Test fun deviceCenteringNeverSelectsTargetAndLateResultIsIgnored() {
        val request = CompletableDeferred<DeviceLocationResult>()
        val store = MemoryPrivacyStore(PrivacyConsentRecord(mapAccepted = true, deviceAccepted = true))
        val model = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store,
            deviceLocation = DeviceLocationClient { request.await() }) }
        onMain { model.locateDevice() }
        request.complete(DeviceLocationResult.Located(MapCoordinate(31.0, 121.0, MapCoordinateSystem.WGS84), 800f))
        waitFor { !model.state.value.locating }
        assertNull(model.state.value.selected)
        assertEquals(12f, model.state.value.cameraRequest!!.zoom)
        onMain { model.changeLatitude("0"); model.changeLongitude("0"); model.disableOnline(); model.submitManual() }
        assertNull(model.state.value.cameraRequest)
        assertEquals(0.0, model.state.value.selected!!.coordinate.latitude, 0.0)
        val late = CompletableDeferred<DeviceLocationResult>()
        val pending = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = store,
            deviceLocation = DeviceLocationClient { late.await() }) }
        onMain { pending.locateDevice(); pending.disableOnline() }
        late.complete(DeviceLocationResult.Located(MapCoordinate(32.0, 122.0, MapCoordinateSystem.WGS84), 20f))
        onMain { assertNull(pending.state.value.cameraRequest); assertFalse(pending.state.value.locating) }
    }

    /** 无用途同意不调用定位端口；保存同意失败时不打开 SDK 门禁。 */
    @Test fun deniedScopeAndFailedPersistenceFailClosed() {
        val accepted = MemoryPrivacyStore(PrivacyConsentRecord(mapAccepted = true))
        val model = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = accepted,
            deviceLocation = DeviceLocationClient { error("Unexpected device location") }) }
        onMain { model.locateDevice() }
        assertNull(model.state.value.cameraRequest)
        val failed = onMain { PositionPickerViewModel(NoPrivacyQueries(), true, privacyStore = MemoryPrivacyStore(null, false)) }
        onMain { failed.acceptPrivacy() }
        waitFor { !failed.state.value.privacySaving }
        assertTrue(failed.state.value.privacyRequired)
        assertNotNull(failed.state.value.privacyError)
        assertNotEquals(OnlineMapGate.Ready, failed.state.value.gate)
    }

    /** 系统位置过期或带 mock 标记均拒绝；只使用构造的测试 Location，不读设备位置。 */
    @Test fun rejectsStaleAndMockLocations() {
        val location = Location("test").apply {
            latitude = 0.0; longitude = 0.0; accuracy = 25f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        assertNotNull(location.toDeviceResult())
        location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - 31_000_000_000L
        assertNull(location.toDeviceResult())
        location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        androidx.core.location.LocationCompat.setMock(location, true)
        assertNull(location.toDeviceResult())
    }

    /** 将页面事件串行送到真实主线程，读取结果经同步交付，不在后台直接调用 ViewModel。 */
    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return result!!
    }

    /** 有界等待授权/定位的明确完成状态，超时直接失败，不静默跳过回归。 */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertTrue("Timed out waiting for UI state", condition())
    }
}

/** 只为门禁回归提供内存记录，不冒充持久化；实际偏好写入另有设备测试覆盖。 */
private class MemoryPrivacyStore(
    /** 测试当前可恢复记录；null 为首次使用。 */
    private var record: PrivacyConsentRecord?,

    /** 模拟存储成功或失败的明确开关。 */
    private val writable: Boolean = true
) : PrivacyConsentStore {
    /** 返回测试种子，不初始化 SDK。 */
    override fun read(): PrivacyConsentRecord? = record

    /** 失败不替换记录，便于验证门禁没有被提前释放。 */
    override suspend fun write(record: PrivacyConsentRecord): Boolean {
        if (writable) this.record = record
        return writable
    }
}

/** 这些测试不搜索也不为设备位置查询地址；任何误调用直接失败。 */
private class NoPrivacyQueries : MapQueryClient {
    /** 设备居中不得隐式发起搜索。 */
    override fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit) = error("Unexpected search")

    /** 设备居中不得隐式选择目标并查询其地址。 */
    override fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit) = error("Unexpected reverse geocode")
}
