package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 模拟定位会话状态：表达聚合当前的生命周期阶段，而非 UI 的操作意图。
 *
 * ## 使用边界
 * 转换由所属聚合控制；跨会话的活动槽位与启动排他不由本枚举保证。
 * 不定义 `Idle`：UI 空闲表示没有活动会话，不是一个已有会话的生命周期阶段。
 */
internal enum class MockLocationSessionState {
    /** 已创建会话并等待准备及必要首次写入确认，尚不能视为实际运行。 */
    Starting,

    /** 已接受匹配任务身份的准备完成及必要首次写入确认，不能由点击启动直接进入。 */
    Running,

    /** 已进入停止阶段并使此前任务回调失效，等待当前清理任务的完成确认。 */
    Stopping,

    /** 清理未能完成，仍可能存在遗留资源；需要显式重试清理。 */
    CleanupRequired,

    /** 已接受当前停止任务的完成确认；本聚合终止，再次启动须创建新会话。 */
    Stopped
}
