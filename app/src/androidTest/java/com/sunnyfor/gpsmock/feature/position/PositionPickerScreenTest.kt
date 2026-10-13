package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryClient
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryResult
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 选点页设备交互测试；地图槽位替身只验证门禁，不宣称通过高德在线验收。 */
class PositionPickerScreenTest {
    /** 管理 UI 线程、组合生命周期与控件交互的测试规则。 */
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithText("纬度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("经度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("使用此坐标").performScrollTo().performClick()
        compose.onNodeWithText("已选目标").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("开启在线地图").performClick()
        compose.onNodeWithText("启用高德在线服务").assertExists()
        compose.onNodeWithText("暂不开启").performClick()
        compose.runOnIdle { assertEquals(0, mapCompositions) }
        compose.onNodeWithText("开启在线地图").performClick()
        compose.onNodeWithText("同意并开启").performClick()
        compose.onNodeWithText("测试地图槽位").assertExists()
        compose.runOnIdle { assertTrue(mapCompositions > 0) }
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
