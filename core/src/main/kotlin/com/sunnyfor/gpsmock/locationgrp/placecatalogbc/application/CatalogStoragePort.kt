package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogMutation
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogResult
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.SavedPosition

/** 一次事务内的去重收据；原命令和提交结果一起持久化。 */
data class CatalogReceipt(

    /** 原始命令参数，比较时不通过字符串拼接猜测相等。 */
    val mutation: CatalogMutation,

    /** 原事务结果，包括当时的修订投影。 */
    val result: CatalogResult,

    /** 收据过期墙钟毫秒值；不是会话运行时钟。 */
    val expiresAtMillis: Long
)

/** 存储事务内的能力；对象不得逃逸到事务外使用。 */
interface CatalogTransaction {

    /** 获取指定身份，缺失返回 null。 */
    fun get(id: String): SavedPosition?

    /** 读取同一事务中的所有收藏。 */
    fun all(): List<SavedPosition>

    /** 写入已经由应用/领域校验的完整记录。 */
    fun put(position: SavedPosition)

    /** 删除指定身份。 */
    fun remove(id: String)

    /** 清空收藏，不清空命令收据。 */
    fun clearPositions()

    /** 读取未过期命令结果，并回收过期收据。 */
    fun receipt(commandId: String, nowMillis: Long): CatalogReceipt?

    /** 写入本次命令收据，与内容更新一起提交。 */
    fun putReceipt(commandId: String, receipt: CatalogReceipt)
}

/** 提供原子事务和线程安全的持久化端口，不包含 Android 数据库类型。 */
interface CatalogStoragePort {
    /** 在后台执行事务；异常必须回滚，成功返回前必须完成提交。 */
    suspend fun <T> transaction(block: (CatalogTransaction) -> T): T
}
