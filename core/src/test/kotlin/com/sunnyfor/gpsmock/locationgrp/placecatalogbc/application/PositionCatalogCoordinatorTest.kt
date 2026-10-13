package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 收藏应用边界测试：校验、版本、失败回滚与持久化命令语义。 */
class PositionCatalogCoordinatorTest {
    /** 原坐标为零仍可保存，同名允许；去重只按命令而不是名称。 */
    @Test fun acceptsZeroAndDuplicateNames() = runBlocking {
        val storage = MemoryCatalogStorage()
        val catalog = PositionCatalogCoordinator(storage)
        val input = CatalogMutation.Create(" 常用位置 ", zero())
        val first = catalog.execute("first", input)
        val second = catalog.execute("second", input)
        assertEquals(CatalogCode.Success, first.code)
        assertEquals("常用位置", first.position!!.name)
        assertNotEquals(first.position.id, second.position!!.id)
        assertEquals(2, catalog.list().positions.size)
    }

    /** 重建协调器仍能读取同事务收据；重放不新增收藏且返回原投影。 */
    @Test fun replaySurvivesCoordinatorRecreation() = runBlocking {
        val storage = MemoryCatalogStorage()
        val input = CatalogMutation.Create("位置", zero())
        val first = PositionCatalogCoordinator(storage).execute("same", input)
        val replay = PositionCatalogCoordinator(storage).execute("same", input)
        assertEquals(first, replay)
        assertEquals(1, storage.positions.size)
        assertEquals(CatalogCode.CommandConflict, PositionCatalogCoordinator(storage).execute("same", input.copy(name = "其他")).code)
    }

    /** 编辑推进版本，旧 expectedRevision 不覆盖新内容；删除缺失与版本冲突分别返回。 */
    @Test fun editsAndDeletesUseExpectedRevision() = runBlocking {
        val catalog = PositionCatalogCoordinator(MemoryCatalogStorage())
        val saved = catalog.execute("create", CatalogMutation.Create("旧", zero())).position!!
        val changed = catalog.execute("edit", CatalogMutation.Edit(saved.id, 1, "新", zero())).position!!
        assertEquals(2L, changed.revision)
        assertEquals(CatalogCode.Conflict, catalog.execute("stale", CatalogMutation.Edit(saved.id, 1, "错误覆盖", zero())).code)
        assertEquals("新", catalog.resolve(saved.id, 2).position!!.name)
        assertEquals(CatalogCode.Conflict, catalog.execute("wrong-delete", CatalogMutation.Delete(saved.id, 1)).code)
        assertEquals(CatalogCode.Success, catalog.execute("delete", CatalogMutation.Delete(saved.id, 2)).code)
        assertEquals(CatalogCode.NotFound, catalog.resolve(saved.id, 2).code)
        assertEquals(CatalogCode.NotFound, catalog.execute("again", CatalogMutation.Delete(saved.id, 2)).code)
    }

    /** NaN、超范围、空名称拒绝且不写记录；30 个补充平面码点名称有效。 */
    @Test fun rejectsInvalidValuesAndCountsUnicodeCodePoints() = runBlocking {
        val catalog = PositionCatalogCoordinator(MemoryCatalogStorage())
        assertEquals(CatalogCode.InvalidInput, catalog.execute("nan", CatalogMutation.Create("有效", zero().copy(latitude = Double.NaN))).code)
        assertEquals(CatalogCode.InvalidInput, catalog.execute("range", CatalogMutation.Create("有效", zero().copy(latitude = 91.0))).code)
        assertEquals(CatalogCode.InvalidInput, catalog.execute("empty", CatalogMutation.Create(" ", zero())).code)
        assertEquals(CatalogCode.Success, catalog.execute("emoji", CatalogMutation.Create("😀".repeat(30), zero())).code)
        assertEquals(1, catalog.list().positions.size)
    }

    /** 模拟提交失败时内容和收据同时回滚；同命令重试才能真正成功。 */
    @Test fun storageFailureRollsBackContentAndReceipt() = runBlocking {
        val storage = MemoryCatalogStorage()
        storage.failCommit = true
        val catalog = PositionCatalogCoordinator(storage)
        val command = CatalogMutation.Create("保留草稿", zero())
        assertEquals(CatalogCode.StorageFailure, catalog.execute("retry", command).code)
        assertTrue(storage.positions.isEmpty())
        assertTrue(storage.receipts.isEmpty())
        storage.failCommit = false
        assertEquals(CatalogCode.Success, catalog.execute("retry", command).code)
    }

    /** 存储写入的 IllegalStateException 属于存储故障，不是用户的版本冲突。 */
    @Test fun storageWriteFailureIsNotMisreportedAsConflict() = runBlocking {
        val storage = MemoryCatalogStorage()
        storage.failWrite = true
        val catalog = PositionCatalogCoordinator(storage)
        assertEquals(CatalogCode.StorageFailure, catalog.execute("write-failure", CatalogMutation.Create("位置", zero())).code)
        assertTrue(storage.positions.isEmpty())
        assertTrue(storage.receipts.isEmpty())
    }

    /** 清空收藏不删除去重收据，重放旧创建命令不能偷偷恢复被清空的记录。 */
    @Test fun clearKeepsCommandReceipts() = runBlocking {
        val catalog = PositionCatalogCoordinator(MemoryCatalogStorage())
        val command = CatalogMutation.Create("已删除", zero())
        val created = catalog.execute("create", command)
        catalog.execute("clear", CatalogMutation.Clear)
        assertEquals(created, catalog.execute("create", command))
        assertTrue(catalog.list().positions.isEmpty())
    }

    /** 去重收据到 24 小时边界过期，过期命令可以作为新操作受理。 */
    @Test fun receiptsExpireAtTwentyFourHours() = runBlocking {
        val storage = MemoryCatalogStorage()
        var wallTime = 100L
        val catalog = PositionCatalogCoordinator(storage, { wallTime })
        val input = CatalogMutation.Create("目标", zero())
        catalog.execute("command", input)
        wallTime += 86_400_000L
        catalog.execute("command", input)
        assertEquals(2, catalog.list().positions.size)
    }

    /** 测试坐标固定为合法 WGS-84 零点。 */
    private fun zero(): CatalogCoordinate = CatalogCoordinate(0.0, 0.0, CatalogCoordinateSystem.WGS84)
}

/** 测试事务替身：在影子副本执行，提交失败时不污染原状态。 */
internal class MemoryCatalogStorage : CatalogStoragePort {

    /** 已提交记录。 */
    val positions = mutableMapOf<String, SavedPosition>()

    /** 已提交命令收据。 */
    val receipts = mutableMapOf<String, CatalogReceipt>()

    /** 是否在提交点注入故障。 */
    var failCommit = false

    /** 是否在写入接口抛出状态异常，用于防止被误报为业务版本冲突。 */
    var failWrite = false

    /** 同步事务保护版本与去重；正常完成才原子替换两个容器。 */
    override suspend fun <T> transaction(block: (CatalogTransaction) -> T): T = synchronized(this) {
        val copyPositions = positions.toMutableMap()
        val copyReceipts = receipts.toMutableMap()
        val result = block(object : CatalogTransaction {

            /** 从事务副本读取身份。 */
            override fun get(id: String) = copyPositions[id]

            /** 读取副本全部记录。 */
            override fun all() = copyPositions.values.toList()

            /** 写入副本，不提前改变已提交状态。 */
            override fun put(position: SavedPosition) {
                check(!failWrite) { "Injected storage write failure" }
                copyPositions[position.id] = position
            }

            /** 删除副本身份。 */
            override fun remove(id: String) { copyPositions.remove(id) }

            /** 清空副本收藏。 */
            override fun clearPositions() { copyPositions.clear() }

            /** 按墙钟过期副本收据。 */
            override fun receipt(commandId: String, nowMillis: Long): CatalogReceipt? {
                copyReceipts.entries.removeAll { it.value.expiresAtMillis <= nowMillis }
                return copyReceipts[commandId]
            }

            /** 保存副本收据。 */
            override fun putReceipt(commandId: String, receipt: CatalogReceipt) { copyReceipts[commandId] = receipt }
        })
        check(!failCommit)
        positions.clear(); positions.putAll(copyPositions)
        receipts.clear(); receipts.putAll(copyReceipts)
        result
    }
}
