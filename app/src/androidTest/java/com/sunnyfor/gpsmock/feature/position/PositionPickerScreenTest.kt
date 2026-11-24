package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryClient
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryResult
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionView
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionTargetSnapshot
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 选点页设备交互测试；地图槽位替身只验证门禁，不宣称通过高德在线验收。 */
class PositionPickerScreenTest {
    /** 管理 UI 线程、组合生命周期与控件交互的测试规则。 */
    @get:Rule val compose = createComposeRule()

    /** 280dp 低窗口摘要可滚动，固定手输入口可达且主操作保持在窗口内。 */
    @Test
    fun shortWindowScrollsContentWithoutHidingPrimaryAction() {
        val model = PositionPickerViewModel(NoOnlineQueries(), false)
        compose.setContent {
            val state by model.state.collectAsState()
            GPSMockTheme(darkTheme = false) {
                PositionPickerScreen(state, model, Modifier.fillMaxWidth().height(280.dp)) { error("Unexpected map") }
            }
        }
        compose.onNodeWithText("开始模拟").assertIsDisplayed()
        compose.onNodeWithText("输入坐标").performClick()
        compose.onNodeWithText("纬度").assertExists()
        compose.onNodeWithText("返回地图").performClick()
        compose.onNodeWithText("开始模拟").assertIsDisplayed()
    }

    /** 两倍系统字体下仍能打开手输并提交零点，固定主操作不藏进滚动区。 */
    @Test
    fun largeFontKeepsManualInputAndPrimaryActionReachable() {
        val model = PositionPickerViewModel(NoOnlineQueries(), false)
        compose.setContent {
            val state by model.state.collectAsState()
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, 2f)) {
                GPSMockTheme(darkTheme = false) { PositionPickerScreen(state, model, Modifier.fillMaxSize()) { error("Unexpected map") } }
            }
        }
        compose.onNodeWithText("开始模拟").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("输入坐标").performClick()
        compose.onNodeWithText("纬度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("经度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("使用此坐标").performScrollTo().performClick()
        compose.onNodeWithText("开始模拟").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0.0, model.state.value.selected!!.coordinate.latitude, 0.0) }
    }

    /** 模拟处于运行状态时跨目的地均可见停止；后选目标不会替代冻结目标，清理失败不出现启动。 */
    @Test
    fun persistentActionTracksAuthoritativePhaseAcrossPages() {
        val model = PositionPickerViewModel(NoOnlineQueries(), false)
        var state by mutableStateOf(PositionPickerState(OnlineMapGate.MissingKey,
            selected = SelectedPosition(MapCoordinate(1.0, 2.0, MapCoordinateSystem.WGS84), PositionSource.Manual),
            session = SessionView(SessionPhase.Running, "ui-only", SessionTargetSnapshot(
                SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.WGS84), "冻结零点"))))
        compose.setContent { GPSMockTheme(darkTheme = false) {
            PositionPickerScreen(state, model, Modifier.fillMaxSize()) { error("Unexpected map") }
        } }
        compose.onNodeWithText("冻结零点").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看目标详情").performClick()
        compose.onNodeWithText("纬度 0.000000").assertExists()
        compose.onNodeWithText("返回地图").performClick()
        compose.onNodeWithText("停止模拟").assertIsDisplayed()
        compose.onNodeWithText("常用位置").performClick()
        compose.onNodeWithText("停止模拟").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onAllNodesWithText("停止模拟")[1].assertIsDisplayed()
        compose.runOnIdle { state = state.copy(session = SessionView(SessionPhase.CleanupRequired)) }
        compose.onAllNodesWithText("重试清理")[1].assertIsDisplayed()
        compose.onNodeWithText("开始模拟").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(selected = null, session = SessionView(SessionPhase.Idle)) }
        compose.onNodeWithText("开始模拟").assertIsNotEnabled()
    }

    /** 空 Key 显示明确提示且不组合 SDK 槽位，仍能输入并选择零点目标。 */
    @Test
    fun emptyKeyAllowsManualTargetWithoutComposingMap() {
        val model = PositionPickerViewModel(NoOnlineQueries(), false)
        var mapCompositions = 0
        compose.setContent {
            val state by model.state.collectAsState()
            GPSMockTheme {
                PositionPickerScreen(state, model, Modifier.fillMaxSize()) { mapCompositions++ }
            }
        }
        compose.onNodeWithText("地图 Key 未配置").assertExists()
        compose.onNodeWithText("输入坐标").performClick()
        compose.onNodeWithText("纬度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("经度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("使用此坐标").performScrollTo().performClick()
        compose.onNodeWithText("已选目标").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看目标详情").performClick()
        compose.onNodeWithText("纬度 0.000000").assertExists()
        compose.runOnIdle { assertEquals(0, mapCompositions) }
    }

    /** 拒绝同意不组合地图，明确同意后才允许组合；关闭在线再次释放地图槽位。 */
    @Test
    fun explicitConsentIsRequiredBeforeMapComposition() {
        val model = PositionPickerViewModel(NoOnlineQueries(), true)
        var mapCompositions = 0
        compose.setContent {
            val state by model.state.collectAsState()
            GPSMockTheme {
                PositionPickerScreen(state, model, Modifier.fillMaxSize()) {
                    mapCompositions++
                    Text("测试地图槽位")
                }
            }
        }
        compose.onNodeWithText("开启在线地图").performScrollTo().performClick()
        compose.onNodeWithText("欢迎使用定位助手").assertExists()
        compose.onNodeWithText("暂不开启").performClick()
        compose.runOnIdle { assertEquals(0, mapCompositions) }
        compose.onNodeWithText("开启在线地图").performScrollTo().performClick()
        compose.onNodeWithText("同意并开启").performClick()
        compose.onNodeWithText("测试地图槽位").assertExists()
        compose.runOnIdle { assertTrue(mapCompositions > 0) }
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNodeWithText("关闭在线").performClick()
        compose.onNodeWithText("开启在线地图").assertExists()
    }
}

/** 没有选点或搜索时不应访问的查询端口，任何误调用都会令设备测试失败。 */
private class NoOnlineQueries : MapQueryClient {
    /** 这些 UI 测试没有搜索意图，因此访问端口属于失败。 */
    override fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit) {
        error("Unexpected online search")
    }

    /** 这些 UI 测试没有在线选点意图，因此访问端口属于失败。 */
    override fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit) {
        error("Unexpected online reverse geocode")
    }
}
