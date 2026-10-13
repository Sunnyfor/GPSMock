package com.sunnyfor.gpsmock.feature.position

import androidx.lifecycle.ViewModelStore
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryClient
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryResult
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import org.junit.Assert.*
import org.junit.Test

/** 选点协调器测试，手动控制真实端口边界的返回顺序，不加载 SDK 或伪造运行状态。 */
class PositionPickerViewModelTest {
    /** 空 Key 下任何开启、同意或搜索事件都不能发出在线请求，手输仍可用。 */
    @Test
    fun missingKeyBlocksSdkButAllowsManualZero() {
        val client = ControlledQueries()
        val model = PositionPickerViewModel(client, false)
        model.enableOnline()
        model.acceptPrivacy()
        model.changeQuery("学校")
        model.search()
        model.changeLatitude("0")
        model.changeLongitude("0")
        model.submitManual()
        assertEquals(OnlineMapGate.MissingKey, model.state.value.gate)
        assertNotNull(model.state.value.selected)
        assertTrue(client.searchCallbacks.isEmpty())
        assertTrue(client.addressCallbacks.isEmpty())
    }

    /** 已配置 Key 仍需明确同意；关闭同意框继续保持离线且不查询。 */
    @Test
    fun configuredKeyDoesNotBypassExplicitConsent() {
        val client = ControlledQueries()
        val model = PositionPickerViewModel(client, true)
        model.changeQuery("公园")
        model.search()
        model.enableOnline()
        assertEquals(OnlineMapGate.ConsentRequired, model.state.value.gate)
        model.search()
        model.disableOnline()
        assertEquals(OnlineMapGate.Disabled, model.state.value.gate)
        assertTrue(client.searchCallbacks.isEmpty())
        model.enableOnline()
        model.acceptPrivacy()
        model.search()
        assertEquals(1, client.searchCallbacks.size)
    }

    /** 手输错误保留原目标，更改坐标系选项也不能重贴原目标标签。 */
    @Test
    fun invalidInputAndSystemChoiceDoNotCorruptSelectedTarget() {
        val model = PositionPickerViewModel(ControlledQueries(), false)
        model.changeLatitude("31")
        model.changeLongitude("121")
        model.submitManual()
        val original = model.state.value.selected
        model.changeInputSystem(MapCoordinateSystem.GCJ02)
        assertEquals(original, model.state.value.selected)
        model.changeLatitude("NaN")
        model.submitManual()
        assertEquals(original, model.state.value.selected)
        assertNotNull(model.state.value.latitudeError)
    }

    /** 旧搜索响应在新响应之后到达时不能覆盖当前候选；城市变化同样使请求失效。 */
    @Test
    fun outOfOrderSearchAndChangedCityAreIgnored() {
        val client = ControlledQueries()
        val model = readyModel(client)
        model.changeQuery("旧")
        model.search()
        model.changeQuery("新")
        model.search()
        val current = place("新地点")
        client.searchCallbacks[1](MapQueryResult.Success(listOf(current)))
        client.searchCallbacks[0](MapQueryResult.Success(listOf(place("旧地点"))))
        assertEquals(listOf(current), model.state.value.places)
        model.search()
        model.changeCity("杭州")
        client.searchCallbacks[2](MapQueryResult.Success(listOf(place("旧城市"))))
        assertTrue(model.state.value.places.isEmpty())
        assertFalse(model.state.value.searching)
    }

    /** 重选相同坐标也有新请求身份，旧地址不能覆盖新的意图。 */
    @Test
    fun lateAddressCannotOverwriteEvenIdenticalReselection() {
        val client = ControlledQueries()
        val model = readyModel(client)
        val coordinate = MapCoordinate(30.0, 120.0, MapCoordinateSystem.GCJ02)
        model.pickMap(coordinate)
        model.pickMap(coordinate)
        client.addressCallbacks[1](MapQueryResult.Success("新地址"))
        client.addressCallbacks[0](MapQueryResult.Success("旧地址"))
        assertEquals("新地址", model.state.value.selected?.address)
        assertEquals(MapCoordinateSystem.GCJ02, model.state.value.selected?.coordinate?.system)
    }

    /** 关闭在线后，搜索与地址晚到结果都无效，原手输坐标保持不变。 */
    @Test
    fun disablingOnlineDiscardsInflightResults() {
        val client = ControlledQueries()
        val model = readyModel(client)
        model.changeLatitude("10")
        model.changeLongitude("20")
        model.submitManual()
        model.changeQuery("公园")
        model.search()
        model.disableOnline()
        client.addressCallbacks.single()(MapQueryResult.Success("晚到地址"))
        client.searchCallbacks.single()(MapQueryResult.Success(listOf(place("晚到地点"))))
        assertNull(model.state.value.selected?.address)
        assertTrue(model.state.value.places.isEmpty())
        assertEquals(MapCoordinateSystem.WGS84, client.coordinates.single().system)
    }

    /** 查无结果和失败均真实显示，不构造候选或修改有效目标。 */
    @Test
    fun emptyAndFailureResultsRemainHonest() {
        val client = ControlledQueries()
        val model = readyModel(client)
        model.changeQuery("没有的地点")
        model.search()
        client.searchCallbacks[0](MapQueryResult.Success(emptyList()))
        assertTrue(model.state.value.searchMessage!!.contains("未找到"))
        model.search()
        client.searchCallbacks[1](MapQueryResult.Failure(1001, "鉴权失败"))
        assertEquals("鉴权失败", model.state.value.searchMessage)
        assertFalse(model.state.value.searching)
        assertNull(model.state.value.selected)
    }

    /** 只接受当前候选，选择后保留真正的地点名称、地址与 GCJ-02 坐标。 */
    @Test
    fun selectingSearchResultRequiresCurrentCandidate() {
        val client = ControlledQueries()
        val model = readyModel(client)
        val result = place("公园")
        model.pickPlace(result)
        assertNull(model.state.value.selected)
        model.changeQuery("公园")
        model.search()
        client.searchCallbacks.single()(MapQueryResult.Success(listOf(result)))
        model.pickPlace(result)
        assertEquals(PositionSource.Search, model.state.value.selected?.source)
        assertEquals("真实地址", model.state.value.selected?.address)
        assertTrue(client.addressCallbacks.isEmpty())
    }

    /** SDK 失败保留合法手输能力，失败地图的点击不会制造目标。 */
    @Test
    fun mapFailureDoesNotPretendSuccessOrDisableManualInput() {
        val model = readyModel(ControlledQueries())
        model.mapFailed("初始化失败")
        model.pickMap(MapCoordinate(30.0, 120.0, MapCoordinateSystem.GCJ02))
        assertNull(model.state.value.selected)
        model.changeLatitude("-10")
        model.changeLongitude("-20")
        model.submitManual()
        assertNotNull(model.state.value.selected)
        assertEquals("初始化失败", model.state.value.mapError)
    }

    /** 清除 Activity 的 ViewModelStore 后，不再应用仍在完成的 SDK 查询回调。 */
    @Test
    fun clearedViewModelRejectsCallbacks() {
        val client = ControlledQueries()
        val model = readyModel(client)
        val store = ViewModelStore()
        store.put("picker", model)
        model.changeQuery("公园")
        model.search()
        val before = model.state.value
        store.clear()
        client.searchCallbacks.single()(MapQueryResult.Success(listOf(place("晚到地点"))))
        assertEquals(before, model.state.value)
    }

    /** 创建已明确同意的测试协调器，不绕过生产门禁规则。 */
    private fun readyModel(client: ControlledQueries): PositionPickerViewModel =
        PositionPickerViewModel(client, true).apply { enableOnline(); acceptPrivacy() }

    /** 创建符合端口约定的不可变候选，仅用于控制查询响应。 */
    private fun place(title: String): MapPlace = MapPlace(title, "真实地址",
        MapCoordinate(30.0, 120.0, MapCoordinateSystem.GCJ02))
}

/** 可控查询端口替身，延迟回调以验证乱序和关闭语义，不替代应用中的实际适配器。 */
private class ControlledQueries : MapQueryClient {
    /** 按调用顺序保存搜索回调。 */
    val searchCallbacks = mutableListOf<(MapQueryResult<List<MapPlace>>) -> Unit>()

    /** 按调用顺序保存地址回调。 */
    val addressCallbacks = mutableListOf<(MapQueryResult<String?>) -> Unit>()

    /** 记录真实传给地址查询端口的原始坐标，验证坐标系不被隐式转换。 */
    val coordinates = mutableListOf<MapCoordinate>()

    /** 只登记请求，由测试主动交付搜索结果。 */
    override fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit) {
        searchCallbacks += callback
    }

    /** 记录坐标并登记回调，由测试主动交付地址结果。 */
    override fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit) {
        coordinates += coordinate
        addressCallbacks += callback
    }
}
