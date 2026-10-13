package com.sunnyfor.gpsmock.platform.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 应用私有 SQLite 宿主，只提供事务基础设施，不持有 BC 业务模型。
 * 第一版 schema 同时容纳收藏、去重收据、会话线索和 provider 所有权账本；禁止破坏性升级。
 *
 * @param context Application 上下文，避免持有页面或服务宿主。
 * @param name 私有数据库文件名；生产使用默认值，测试使用独立名称而不污染用户数据。
 */
internal class GpsMockDatabase(context: Context, name: String = "gpsmock.db") : SQLiteOpenHelper(context, name, null, 1) {
    /** 创建第一版表；主键和事务保护身份、版本及命令唯一性。 */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE positions (id TEXT PRIMARY KEY, revision INTEGER NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, system TEXT NOT NULL)")
        db.execSQL("CREATE TABLE catalog_receipts (command_id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE session_journal (slot INTEGER PRIMARY KEY CHECK(slot=1), payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE owned_providers (name TEXT PRIMARY KEY)")
    }

    /** 尚无旧版迁移路径，遇不支持版本明确失败，绝不删表重建用户收藏。 */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported database migration: $oldVersion to $newVersion")
    }

    /** 执行同步短事务；调用方须在 IO 调度器调用，异常和取消后回滚。 */
    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally { db.endTransaction() }
    }
}
