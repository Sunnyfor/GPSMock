package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract

/** 收藏坐标的公开参考系，不暴露领域值对象或地图 SDK 类型。 */
enum class CatalogCoordinateSystem {
    /** 全球 WGS-84 坐标。 */
    WGS84,

    /** 高德地图使用的 GCJ-02 坐标。 */
    GCJ02
}

/** 收藏读写边界上的完整坐标；应用层仍须校验有限性和范围。 */
data class CatalogCoordinate(
    /** 纬度，单位为十进制度。 */
    val latitude: Double,

    /** 经度，单位为十进制度。 */
    val longitude: Double,

    /** 明确的原始参考系，不随展示转换。 */
    val system: CatalogCoordinateSystem
)

/** 已提交的收藏只读投影；运行目标须另外冻结，不能持有可变存储对象。 */
data class SavedPosition(
    /** 稳定的收藏身份。 */
    val id: String,

    /** 当前正数修订版本，用于乐观并发检查。 */
    val revision: Long,

    /** 规范化后的 1–30 码点名称。 */
    val name: String,

    /** 保存时的原始坐标。 */
    val coordinate: CatalogCoordinate
)

/** 一次收藏命令；相同命令身份只能对应同一参数。 */
sealed interface CatalogMutation {
    /** 保存新收藏；允许同名和同坐标，身份由应用层生成。 */
    data class Create(

        /** 用户名称草稿。 */
        val name: String,

        /** 原始目标坐标。 */
        val coordinate: CatalogCoordinate
    ) : CatalogMutation

    /** 原子替换指定版本的名称与坐标。 */
    data class Edit(

        /** 待修改身份。 */
        val id: String,

        /** 用户读取时的版本。 */
        val expectedRevision: Long,

        /** 新名称草稿。 */
        val name: String,

        /** 新坐标。 */
        val coordinate: CatalogCoordinate
    ) : CatalogMutation

    /** 删除明确身份和版本的收藏；确认 UI 是调用方责任。 */
    data class Delete(

        /** 待删除身份。 */
        val id: String,

        /** 用户确认时的版本。 */
        val expectedRevision: Long
    ) : CatalogMutation

    /** 删除全部收藏；运行期间的批量操作限制由跨 BC 旅程检查。 */
    data object Clear : CatalogMutation
}

/** 收藏操作的类型化结果，不把存储异常或内部聚合传给 UI。 */
enum class CatalogCode {

    /** 已成功提交事务。 */
    Success,

    /** 名称、坐标或命令身份非法。 */
    InvalidInput,

    /** 指定收藏不存在。 */
    NotFound,

    /** 收藏已被其他操作修改。 */
    Conflict,

    /** 同一个命令身份被用于不同参数。 */
    CommandConflict,

    /** 存储无法读取或提交。 */
    StorageFailure
}

/** 收藏命令或解析结果；成功保存/编辑/解析时携带投影。 */
data class CatalogResult(

    /** 本次结果类别。 */
    val code: CatalogCode,

    /** 已提交或已解析的记录；删除/清空和失败时为 null。 */
    val position: SavedPosition? = null
)

/** 收藏列表读取结果；失败不以空列表伪装成读取成功。 */
data class CatalogListing(

    /** 读取是否成功。 */
    val code: CatalogCode,

    /** 成功时的完整只读列表。 */
    val positions: List<SavedPosition> = emptyList()
)

/** 本地收藏公开能力；写入及持久化去重处于同一存储事务。 */
interface PositionCatalog {
    /** 读取当前列表；调用方不得把失败列表当作权威空库。 */
    suspend fun list(): CatalogListing

    /** 重新解析精确版本，启动收藏时拒绝已删除或已修改的条目。 */
    suspend fun resolve(id: String, revision: Long): CatalogResult

    /** 执行命令；相同身份与参数重放返回原结果，至少保留 24 小时。 */
    suspend fun execute(commandId: String, mutation: CatalogMutation): CatalogResult
}
