package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.adapter.storage

import androidx.test.platform.app.InstrumentationRegistry
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.CatalogStoragePort
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.CatalogTransaction
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.PositionCatalogCoordinator
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.*
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

/** 真正 SQLite 设备测试；使用独立临时库，不清空用户的生产收藏。 */
class SQLiteCatalogAdapterTest {
    /** 关闭并重新打开数据库后，同命令仍返回原记录且不会新增重复收藏。 */
    @Test fun receiptSurvivesDatabaseReopen() = withDatabase { name, database ->
        val mutation = CatalogMutation.Create(" 持久化位置 ", zero())
        val original = PositionCatalogCoordinator(SQLiteCatalogAdapter(database)).execute("stable", mutation)
        database.close()
        val reopened = GpsMockDatabase(InstrumentationRegistry.getInstrumentation().targetContext, name)
        try {
            val catalog = PositionCatalogCoordinator(SQLiteCatalogAdapter(reopened))
            assertEquals(original, catalog.execute("stable", mutation))
            assertEquals("持久化位置", catalog.list().positions.single().name)
        } finally { reopened.close() }
    }

    /** 同事务最后一步失败时，收藏和去重收据一起回滚。 */
    @Test fun transactionRollsBackContentAndReceiptTogether() = withDatabase { _, database ->
        val adapter = SQLiteCatalogAdapter(database)
        val failing = object : CatalogStoragePort {
            /** 在原子事务内、应用逻辑完成之后注入故障。 */
            override suspend fun <T> transaction(block: (CatalogTransaction) -> T): T = adapter.transaction { tx ->
                block(tx)
                throw IOException("Test rollback")
            }
        }
        val mutation = CatalogMutation.Create("草稿保留", zero())
        assertEquals(CatalogCode.StorageFailure, PositionCatalogCoordinator(failing).execute("retry", mutation).code)
        val healthy = PositionCatalogCoordinator(adapter)
        assertTrue(healthy.list().positions.isEmpty())
        assertEquals(CatalogCode.Success, healthy.execute("retry", mutation).code)
        assertEquals(1, healthy.list().positions.size)
    }

    /** 实际提交成功但调用方未收到结果时，重试通过持久化收据找回成功，不重复写入。 */
    @Test fun lostCommitReplyIsRecoveredBySameCommand() = withDatabase { _, database ->
        val adapter = SQLiteCatalogAdapter(database)
        val uncertainReply = object : CatalogStoragePort {
            /** 提交之后模拟通信边界丢失返回值，不回滚已经完成的事务。 */
            override suspend fun <T> transaction(block: (CatalogTransaction) -> T): T {
                adapter.transaction(block)
                throw IOException("Test lost reply")
            }
        }
        val mutation = CatalogMutation.Create("只保存一次", zero())
        assertEquals(CatalogCode.StorageFailure, PositionCatalogCoordinator(uncertainReply).execute("uncertain", mutation).code)
        val catalog = PositionCatalogCoordinator(adapter)
        assertEquals(CatalogCode.Success, catalog.execute("uncertain", mutation).code)
        assertEquals(1, catalog.list().positions.size)
    }

    /** 真实数据库拒绝过期版本；修改/删除及 clear 后的重复命令保持原结果。 */
    @Test fun optimisticVersionAndClearKeepReceipts() = withDatabase { _, database ->
        val catalog = PositionCatalogCoordinator(SQLiteCatalogAdapter(database))
        val mutation = CatalogMutation.Create("同名", zero())
        val saved = catalog.execute("create", mutation).position!!
        val edited = catalog.execute("edit", CatalogMutation.Edit(saved.id, saved.revision, "新名", zero())).position!!
        assertEquals(2L, edited.revision)
        assertEquals(CatalogCode.Conflict, catalog.execute("stale", CatalogMutation.Delete(saved.id, saved.revision)).code)
        assertEquals(CatalogCode.Success, catalog.execute("clear", CatalogMutation.Clear).code)
        assertEquals(saved, catalog.execute("create", mutation).position)
        assertTrue(catalog.list().positions.isEmpty())
    }

    /** 非有限输入能够保存为失败收据并准确重放，不因 JSON 数值规则变成存储失败。 */
    @Test fun invalidNonFiniteInputHasStableRejectedReceipt() = withDatabase { _, database ->
        val catalog = PositionCatalogCoordinator(SQLiteCatalogAdapter(database))
        val mutation = CatalogMutation.Create("有效名", zero().copy(latitude = Double.POSITIVE_INFINITY))
        val rejected = catalog.execute("invalid", mutation)
        assertEquals(CatalogCode.InvalidInput, rejected.code)
        assertEquals(rejected, catalog.execute("invalid", mutation))
        assertTrue(catalog.list().positions.isEmpty())
    }

    /** 各场景独立数据库，结束后仅删除本场景创建的已知临时库。 */
    private fun withDatabase(block: suspend (String, GpsMockDatabase) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "catalog-test-${UUID.randomUUID()}.db"
        val database = GpsMockDatabase(context, name)
        try { block(name, database) } finally { database.close(); context.deleteDatabase(name) }
    }

    /** 保留合法零坐标和显式 WGS-84，避免测试掩盖零值边界。 */
    private fun zero() = CatalogCoordinate(0.0, 0.0, CatalogCoordinateSystem.WGS84)
}
