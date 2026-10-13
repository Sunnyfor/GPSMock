package com.sunnyfor.gpsmock.wiring

import android.app.Application
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.AndroidMockLocationAdapter
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.MockLocationForegroundService
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.SQLiteSessionJournalAdapter
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.SessionServiceHost
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application.MockLocationSessionCoordinator
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.MockSession
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.adapter.storage.SQLiteCatalogAdapter
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.PositionCatalogCoordinator
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.PositionCatalog
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application 装配根，保证整个进程只有一个数据库宿主与活动会话协调器。 */
class GpsMockApplication : Application() {
    /** 应用生命周期任务范围；Activity/Service 退出不取消清理补偿。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** UI 只通过此公开契约操作收藏。 */
    lateinit var catalog: PositionCatalog
        private set

    /** 页面与前台服务共享同一会话权威。 */
    lateinit var session: MockSession
        private set

    /** 在任何页面/服务启动前装配业务端口，并开始只清理不重启的恢复检查。 */
    override fun onCreate() {
        super.onCreate()
        val database = GpsMockDatabase(this)
        catalog = PositionCatalogCoordinator(SQLiteCatalogAdapter(database))
        val coordinator = MockLocationSessionCoordinator(catalog, AndroidMockLocationAdapter(this, database),
            SQLiteSessionJournalAdapter(database), applicationScope)
        session = coordinator
        SessionServiceHost.session = session
        MockLocationForegroundService.interruptionCallback = { id ->
            applicationScope.launch { coordinator.interrupt(id) }
        }
    }
}
