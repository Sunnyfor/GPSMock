package com.sunnyfor.gpsmock.feature.position

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCode

/** 将权威阶段转换为文字，不凭颜色或选择目标推断运行。 */
internal fun SessionPhase.displayName(): String = when (this) {
    SessionPhase.Idle -> "尚未模拟"
    SessionPhase.Starting -> "正在启动"
    SessionPhase.Running -> "模拟运行中"
    SessionPhase.Stopping -> "正在停止/检查"
    SessionPhase.CleanupRequired -> "清理未完成"
}

/** 会话类型化错误到恢复引导；null 表示没有错误。 */
internal fun sessionErrorText(error: SessionError?): String? = when (error) {
    null -> null
    SessionError.InvalidInput -> "坐标或命令无效，请检查输入。"
    SessionError.Busy -> "已有活动会话，请先停止，再启动新目标。"
    SessionError.CleanupRequired -> "本方资源尚未清理完成，请重试清理；暂不能启动。"
    SessionError.CommandConflict -> "命令身份冲突，请重新发起操作。"
    SessionError.NotFound -> "该收藏已删除，请刷新列表重新选择。"
    SessionError.Conflict -> "收藏已修改，请刷新列表重新选择最新版本。"
    SessionError.StorageFailure -> "私有存储读写失败；输入已保留，检查空间后重试。"
    SessionError.PermissionDenied -> "模拟位置授权被拒绝。请在开发者选项选择“定位助手”，返回后重试清理/启动。"
    SessionError.ProviderUnsupported -> "系统不支持必要模拟 provider；启动未完成，请查看当前清理状态。"
    SessionError.EngineFailure -> "服务准备或位置写入失败，已请求停止，请查看当前清理状态。"
    SessionError.Interrupted -> "上次运行已中断，不会自动重启；已安排残留清理，以当前阶段为准。"
}

/** 收藏结果到明确文字；保留版本冲突、删除缺失和存储失败的差异。 */
internal fun catalogErrorText(code: CatalogCode): String = when (code) {
    CatalogCode.Success -> "操作已完成。"
    CatalogCode.InvalidInput -> "名称须为 1–30 字，坐标须有效；请修改后重试。"
    CatalogCode.NotFound -> "该收藏已不存在，请刷新并重新选择。"
    CatalogCode.Conflict -> "收藏版本冲突，未覆盖新记录；请刷新后重新编辑。"
    CatalogCode.CommandConflict -> "命令参数冲突，请重新打开编辑。"
    CatalogCode.StorageFailure -> "收藏存储失败，未宣称保存成功；请检查存储空间后重试。"
}
