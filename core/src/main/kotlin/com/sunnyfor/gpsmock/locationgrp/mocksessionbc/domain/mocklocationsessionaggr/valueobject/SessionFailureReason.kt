package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 会话失败的领域原因，用于记录事实而非直接展示平台异常或 UI 文案。
 *
 * 平台错误由调用方映射；聚合不自行检查权限、提供者或引擎状态。
 */
internal enum class SessionFailureReason {
    /** 缺少执行模拟定位所需的权限或授权。 */
    PermissionDenied,

    /** 当前定位提供者不支持所需模拟能力。 */
    ProviderUnsupported,

    /** 引擎准备或写入过程失败，具体技术细节留在适配层。 */
    EngineFailure,

    /** 清理未完成且没有更早的失败原因需要保留。 */
    CleanupFailure,

    /** 从历史记录恢复非终止会话时标记的中断原因，需要重新清理。 */
    Interrupted
}
