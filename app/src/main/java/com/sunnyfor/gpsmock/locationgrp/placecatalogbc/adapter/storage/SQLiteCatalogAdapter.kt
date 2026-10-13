package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.adapter.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.CatalogReceipt
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.CatalogStoragePort
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.CatalogTransaction
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCode
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogMutation
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogResult
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.SavedPosition
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** SQLite 收藏端口：版本检查、记录和去重收据在一次真实数据库事务中提交。 */
internal class SQLiteCatalogAdapter(

    /** Application 共享的数据库宿主，不绑定 Activity。 */
    private val database: GpsMockDatabase
) : CatalogStoragePort {
    /** 切换后台执行；block 抛出异常时 SQLite 宿主自动回滚。 */
    override suspend fun <T> transaction(block: (CatalogTransaction) -> T): T = withContext(Dispatchers.IO) {
        database.transaction { block(SQLiteCatalogTransaction(it)) }
    }
}

/** 仅在已打开事务中使用的收藏映射器，不向 UI 暴露 Cursor 或 JSON。 */
private class SQLiteCatalogTransaction(

    /** 本次事务连接，生命周期不超出调用闭包。 */
    private val db: SQLiteDatabase
) : CatalogTransaction {
    /** 按绑定参数查询唯一身份。 */
    override fun get(id: String): SavedPosition? = query("id=?", arrayOf(id)).firstOrNull()

    /** 按稳定 rowid 顺序读取全部收藏。 */
    override fun all(): List<SavedPosition> = query(null, null)

    /** 更新整条投影，失败抛出从而回滚内容与收据。 */
    override fun put(position: SavedPosition) {
        val values = ContentValues().apply {
            put("id", position.id); put("revision", position.revision); put("name", position.name)
            put("latitude", position.coordinate.latitude); put("longitude", position.coordinate.longitude)
            put("system", position.coordinate.system.name)
        }
        check(db.insertWithOnConflict("positions", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }

    /** 只删除指定身份。 */
    override fun remove(id: String) { db.delete("positions", "id=?", arrayOf(id)) }

    /** 删除全部收藏，但保留已经完成的命令结果。 */
    override fun clearPositions() { db.delete("positions", null, null) }

    /** 事务内回收过期收据，再返回当前命令的原结果。 */
    override fun receipt(commandId: String, nowMillis: Long): CatalogReceipt? {
        db.delete("catalog_receipts", "expires_at<=?", arrayOf(nowMillis.toString()))
        return db.query("catalog_receipts", arrayOf("payload", "expires_at"), "command_id=?", arrayOf(commandId), null, null, null).use {
            if (!it.moveToFirst()) null else {
                val json = JSONObject(it.getString(0))
                CatalogReceipt(decodeMutation(json.getJSONObject("mutation")),
                    CatalogResult(CatalogCode.valueOf(json.getString("code")), json.optJSONObject("position")?.let(::decodePosition)), it.getLong(1))
            }
        }
    }

    /** 原参数与原结果一起保存；JSON 坐标用字符串表示，以便保留被拒绝的 NaN 等输入。 */
    override fun putReceipt(commandId: String, receipt: CatalogReceipt) {
        val json = JSONObject().put("mutation", encodeMutation(receipt.mutation)).put("code", receipt.result.code.name)
        receipt.result.position?.let { json.put("position", encodePosition(it)) }
        check(db.insertOrThrow("catalog_receipts", null, ContentValues().apply {
            put("command_id", commandId); put("payload", json.toString()); put("expires_at", receipt.expiresAtMillis)
        }) != -1L)
    }

    /** Cursor 转公开快照；未知参考系或损坏字段抛出而不是悄悄丢弃记录。 */
    private fun query(selection: String?, args: Array<String>?): List<SavedPosition> =
        db.query("positions", arrayOf("id", "revision", "name", "latitude", "longitude", "system"), selection, args, null, null, "rowid DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(SavedPosition(cursor.getString(0), cursor.getLong(1), cursor.getString(2),
                CatalogCoordinate(cursor.getDouble(3), cursor.getDouble(4), CatalogCoordinateSystem.valueOf(cursor.getString(5))))) }
        }

    /** 公开坐标序列化，不对用户坐标做隐含转换。 */
    private fun encodeCoordinate(value: CatalogCoordinate): JSONObject = JSONObject()
        .put("latitude", value.latitude.toString()).put("longitude", value.longitude.toString()).put("system", value.system.name)

    /** 恢复显式参考系，非法序列化内容由存储错误路径处理。 */
    private fun decodeCoordinate(value: JSONObject): CatalogCoordinate = CatalogCoordinate(
        value.getString("latitude").toDouble(), value.getString("longitude").toDouble(), CatalogCoordinateSystem.valueOf(value.getString("system")))

    /** 将当时的提交投影保存到收据。 */
    private fun encodePosition(value: SavedPosition): JSONObject = JSONObject().put("id", value.id).put("revision", value.revision)
        .put("name", value.name).put("coordinate", encodeCoordinate(value.coordinate))

    /** 收据恢复原投影，不查询可能已经被修改/删除的当前收藏。 */
    private fun decodePosition(value: JSONObject): SavedPosition = SavedPosition(value.getString("id"), value.getLong("revision"),
        value.getString("name"), decodeCoordinate(value.getJSONObject("coordinate")))

    /** 按显式命令类型保存参数，避免字符串拼接造成命令相等碰撞。 */
    private fun encodeMutation(value: CatalogMutation): JSONObject = JSONObject().apply {
        when (value) {
            is CatalogMutation.Create -> { put("type", "create"); put("name", value.name); put("coordinate", encodeCoordinate(value.coordinate)) }
            is CatalogMutation.Edit -> { put("type", "edit"); put("id", value.id); put("revision", value.expectedRevision); put("name", value.name); put("coordinate", encodeCoordinate(value.coordinate)) }
            is CatalogMutation.Delete -> { put("type", "delete"); put("id", value.id); put("revision", value.expectedRevision) }
            CatalogMutation.Clear -> put("type", "clear")
        }
    }

    /** 还原命令语义；不支持的格式明确失败，不生成新的写入命令。 */
    private fun decodeMutation(value: JSONObject): CatalogMutation = when (value.getString("type")) {
        "create" -> CatalogMutation.Create(value.getString("name"), decodeCoordinate(value.getJSONObject("coordinate")))
        "edit" -> CatalogMutation.Edit(value.getString("id"), value.getLong("revision"), value.getString("name"), decodeCoordinate(value.getJSONObject("coordinate")))
        "delete" -> CatalogMutation.Delete(value.getString("id"), value.getLong("revision"))
        "clear" -> CatalogMutation.Clear
        else -> error("Unsupported catalog receipt")
    }
}
