package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.content.ContentValues
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application.SessionJournalPort
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application.SessionJournalRecord
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionTargetSnapshot
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 独立会话线索适配器；每条操作在 IO 线程提交，不把旧 Running 当作当前运行事实。 */
internal class SQLiteSessionJournalAdapter(

    /** 不进入云备份的应用私有数据库。 */
    private val database: GpsMockDatabase
) : SessionJournalPort {
    /** 严格解析原始目标、状态和代次；损坏日志须阻止启动。 */
    override suspend fun load(): SessionJournalRecord? = withContext(Dispatchers.IO) {
        database.transaction { db ->
            db.query("session_journal", arrayOf("payload"), "slot=1", null, null, null, null).use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val json = JSONObject(cursor.getString(0))
                    SessionJournalRecord(json.getString("id"), json.getLong("generation"), SessionTargetSnapshot(
                        SessionCoordinate(json.getDouble("latitude"), json.getDouble("longitude"), SessionCoordinateSystem.valueOf(json.getString("system"))),
                        json.getString("name"), if (json.isNull("sourceId")) null else json.getString("sourceId"),
                        if (json.isNull("sourceRevision")) null else json.getLong("sourceRevision")),
                        SessionPhase.valueOf(json.getString("phase")), if (json.isNull("lastWrite")) null else json.getLong("lastWrite"),
                        if (json.isNull("error")) null else SessionError.valueOf(json.getString("error")))
                }
            }
        }
    }

    /** 将冻结快照完整原子替换到唯一日志槽；不记录到 logcat。 */
    override suspend fun save(record: SessionJournalRecord) = withContext(Dispatchers.IO) {
        val target = record.target
        val json = JSONObject().put("id", record.sessionId).put("generation", record.generation)
            .put("latitude", target.coordinate.latitude).put("longitude", target.coordinate.longitude).put("system", target.coordinate.system.name)
            .put("name", target.displayName).put("sourceId", target.sourceId ?: JSONObject.NULL).put("sourceRevision", target.sourceRevision ?: JSONObject.NULL)
            .put("phase", record.phase.name).put("lastWrite", record.lastWriteNanos ?: JSONObject.NULL).put("error", record.error?.name ?: JSONObject.NULL)
        database.transaction { db ->
            check(db.insertWithOnConflict("session_journal", null, ContentValues().apply {
                put("slot", 1); put("payload", json.toString())
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        }
    }

    /** 真实清理之后删除线索；失败向上传播，不能假称已释放活动槽位。 */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        database.transaction { it.delete("session_journal", "slot=1", null); Unit }
    }
}
