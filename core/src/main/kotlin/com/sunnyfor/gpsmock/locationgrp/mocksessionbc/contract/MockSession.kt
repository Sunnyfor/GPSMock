package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract

import kotlinx.coroutines.flow.StateFlow

/** 模拟会话输入的明确参考系。 */
enum class SessionCoordinateSystem {

    /** 直接交给系统 provider 的全球坐标。 */
    WGS84,

    /** 仅在引擎准备时反算一次为 WGS-84。 */
    GCJ02
}

/** 公开输入坐标；应用层再次执行领域校验。 */
data class SessionCoordinate(

    /** 十进制度纬度。 */
    val latitude: Double,

    /** 十进制度经度。 */
    val longitude: Double,

    /** 数值所属的原始参考系。 */
    val system: SessionCoordinateSystem
)

/** 启动意图：临时坐标或重新解析指定版本的收藏。 */
sealed interface StartTarget {
    /** 可不收藏直接启动的坐标输入。 */
    data class Inline(

        /** 原始坐标。 */
        val coordinate: SessionCoordinate,

        /** 可选展示名称，不作为引擎参数。 */
        val displayName: String = ""
    ) : StartTarget
    /** 启动前必须重新检查存在性与版本的收藏引用。 */
    data class Saved(

        /** 收藏身份。 */
        val id: String,

        /** 用户选中时的版本。 */
        val revision: Long
    ) : StartTarget
}

/** 已解析、冻结的会话目标；收藏的后续编辑/删除不会改变此值。 */
data class SessionTargetSnapshot(

    /** 启动时原坐标与参考系。 */
    val coordinate: SessionCoordinate,

    /** 启动时规范化展示名称。 */
    val displayName: String = "",

    /** 收藏来源身份；临时目标为 null。 */
    val sourceId: String? = null,

    /** 收藏来源版本；与来源身份成对。 */
    val sourceRevision: Long? = null
)

/** UI 可观察阶段；Idle 表示协调器没有占用活动槽位，不是领域根状态。 */
enum class SessionPhase {

    /** 无活动会话，可启动。 */
    Idle,

    /** 已受理，尚未收到必要首次写入成功事实。 */
    Starting,

    /** 必要 provider 曾确认写入，仍持续监测写入结果。 */
    Running,

    /** 正在取消写入并清理资源。 */
    Stopping,

    /** 清理或日志提交失败，禁止新启动。 */
    CleanupRequired
}

/** 模拟命令与运行事实的公开失败分类。 */
enum class SessionError {

    /** 原坐标或命令身份非法。 */
    InvalidInput,

    /** 已有活动会话。 */
    Busy,

    /** 存在未完成清理。 */
    CleanupRequired,

    /** 同命令身份对应不同参数。 */
    CommandConflict,

    /** 收藏已不存在。 */
    NotFound,

    /** 收藏版本已经变更。 */
    Conflict,

    /** 数据库无法完成必要操作。 */
    StorageFailure,

    /** 尚未选择本应用为模拟位置应用，或授权被撤销。 */
    PermissionDenied,

    /** 系统不支持所需 provider。 */
    ProviderUnsupported,

    /** 平台写入或前台服务启动失败。 */
    EngineFailure,

    /** 上次会话随服务异常销毁或进程结束而中断。 */
    Interrupted
}

/** 已受理命令的结果；成功仅说明受理，Running 必须观察状态流。 */
data class SessionCommandResult(

    /** 本次关联的会话身份；没有受理时可为 null。 */
    val sessionId: String? = null,

    /** 拒绝原因；null 表示已受理或重复操作成功。 */
    val error: SessionError? = null
)

/** 运行投影；时间来自同一单调时钟，不使用墙钟计算运行时长。 */
data class SessionView(

    /** 当前阶段，首次读取日志前不允许启动。 */
    val phase: SessionPhase = SessionPhase.Stopping,

    /** 当前会话身份；无活动根为 null。 */
    val sessionId: String? = null,

    /** 冻结的目标输入；尚无目标为 null。 */
    val target: SessionTargetSnapshot? = null,

    /** 首次写入确认纳秒；未实际运行或进程恢复为 null。 */
    val startedAtNanos: Long? = null,

    /** 最近实际写入确认纳秒；没有确认时为 null。 */
    val lastWriteNanos: Long? = null,

    /** 当前或上次停止原因，不包含平台异常原文。 */
    val error: SessionError? = null
)

/** 跨 UI/服务共享的唯一会话公开能力。 */
interface MockSession {

    /** 只读当前会话流，Activity 退出不关闭它。 */
    val state: StateFlow<SessionView>

    /** 受理启动；命令在进程内幂等，不跨进程自动重放。 */
    suspend fun start(commandId: String, target: StartTarget): SessionCommandResult

    /** 停止明确会话身份；旧通知不能停止新的会话。 */
    suspend fun stop(commandId: String, sessionId: String): SessionCommandResult

    /** 显式重试未完成清理；重复正在清理的请求不创建新任务。 */
    suspend fun retryCleanup(commandId: String): SessionCommandResult
}
