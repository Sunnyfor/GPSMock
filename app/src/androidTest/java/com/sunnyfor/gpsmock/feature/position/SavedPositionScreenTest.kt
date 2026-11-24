package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.*
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.adapter.storage.SQLiteCatalogAdapter
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.PositionCatalogCoordinator
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.*
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryClient
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryResult
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** 收藏交互使用真实 SQLite 的设备测试；会话替身只测试 UI 禁止批量清空，不代表引擎运行。 */
class SavedPositionScreenTest {

    /** 管理真实 Compose 控件和 UI 线程断言。 */
    @get:Rule val compose = createComposeRule()

    /** 每场景独立库名，不操作生产数据库。 */
    private lateinit var databaseName: String

    /** 独立数据库宿主。 */
    private lateinit var database: GpsMockDatabase

    /** 真实收藏公开用例。 */
    private lateinit var catalog: PositionCatalogCoordinator

    /** 初始化独立数据库和真实事务适配器。 */
    @Before fun prepare() {
        databaseName = "saved-ui-test-${UUID.randomUUID()}.db"
        database = GpsMockDatabase(InstrumentationRegistry.getInstrumentation().targetContext, databaseName)
        catalog = PositionCatalogCoordinator(SQLiteCatalogAdapter(database))
    }

    /** 仅删除本测试创建的临时数据库，生产收藏保持不变。 */
    @After fun close() {
        database.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }

    /** 手输零点可收藏、编辑名称并推进版本；删除必须确认，成功后真实存储为空。 */
    @Test fun savesEditsAndConfirmsDeletion() {
        val model = PositionPickerViewModel(NoQueriesForSavedTest(), false, catalog)
        content(model)
        compose.onNodeWithText("输入坐标").performClick()
        compose.onNodeWithText("纬度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("经度").performScrollTo().performTextInput("0")
        compose.onNodeWithText("使用此坐标").performScrollTo().performClick()
        compose.onNodeWithText("常用位置").performClick()
        compose.onNodeWithText("收藏当前目标").performScrollTo().performClick()
        compose.onNodeWithText("名称（1–30 字）").performTextInput("测试收藏")
        compose.onNodeWithText("保存收藏").performClick()
        compose.waitUntil(5_000) { model.state.value.savedPositions.size == 1 }
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithText("名称（1–30 字）").performTextReplacement("修改后的收藏")
        compose.onNodeWithText("保存收藏").performClick()
        compose.waitUntil(5_000) { model.state.value.savedPositions.singleOrNull()?.revision == 2L }
        assertEquals("修改后的收藏", model.state.value.savedPositions.single().name)
        compose.onNodeWithText("删除").performScrollTo().performClick()
        assertEquals(1, runBlocking { catalog.list().positions.size })
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5_000) { model.state.value.savedPositions.isEmpty() }
        assertEquals(0, runBlocking { catalog.list().positions.size })
    }

    /** 有活动会话时批量清空按钮禁用；返回 Idle 后取消确认仍保留收藏。 */
    @Test fun activeSessionDisablesBulkClearAndCancelKeepsData() {
        runBlocking { catalog.execute("seed", CatalogMutation.Create("不会误删", CatalogCoordinate(0.0, 0.0, CatalogCoordinateSystem.WGS84))) }
        val sessions = SessionUiTestPort()
        val model = PositionPickerViewModel(NoQueriesForSavedTest(), false, catalog, sessions)
        content(model)
        compose.onNodeWithText("常用位置").performClick()
        compose.waitUntil(5_000) { model.state.value.savedPositions.size == 1 }
        compose.onNodeWithText("删除全部收藏").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { sessions.state.value = SessionView(SessionPhase.Idle) }
        compose.waitUntil(5_000) { model.state.value.session.phase == SessionPhase.Idle }
        compose.onNodeWithText("删除全部收藏").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, runBlocking { catalog.list().positions.size })
    }

    /** 注入无 SDK 地图槽位，避免测试访问网络或绕过真实隐私门禁。 */
    private fun content(model: PositionPickerViewModel) {
        compose.setContent {
            val state by model.state.collectAsState()
            GPSMockTheme { PositionPickerScreen(state, model, Modifier.fillMaxSize()) { error("Unexpected map") } }
        }
    }
}

/** 只用于会话状态展示的替身，不执行或假称平台模拟。 */
private class SessionUiTestPort : MockSession {

    /** 测试注入的活动阶段，不是引擎事实。 */
    override val state = MutableStateFlow(SessionView(SessionPhase.Running, "ui-test"))

    /** 本测试不启动模拟，误调用直接失败。 */
    override suspend fun start(commandId: String, target: StartTarget): SessionCommandResult = error("Unexpected start")

    /** 本测试不停止模拟，误调用直接失败。 */
    override suspend fun stop(commandId: String, sessionId: String): SessionCommandResult = error("Unexpected stop")

    /** 本测试不清理模拟，误调用直接失败。 */
    override suspend fun retryCleanup(commandId: String): SessionCommandResult = error("Unexpected retry")
}

/** 收藏离线场景的查询端口，任何访问都视为违规。 */
private class NoQueriesForSavedTest : MapQueryClient {

    /** 不允许离线收藏发出搜索请求。 */
    override fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit) = error("Unexpected search")

    /** 不允许离线收藏访问地址服务。 */
    override fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit) = error("Unexpected address")
}
