package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionTargetSnapshot

/** 一轮全部必要 provider 写入的结果；部分成功不能返回确认时间。 */
data class EngineWriteResult(

    /** 成功后采样的单调纳秒；失败为 null。 */
    val confirmedAtNanos: Long? = null,

    /** 平台归一失败；成功为 null。 */
    val error: SessionError? = null
)

/** 模拟平台端口；每次 prepare 只转换一次，cleanup 必须追踪本方历代资源。 */
interface MockEnginePort {
    /** 准备 provider 并完成首次写入；服务准备须包含在此确认内。 */
    suspend fun prepareAndWrite(target: SessionTargetSnapshot): EngineWriteResult
    /** 向全部必要 provider 写入已准备的固定坐标。 */
    suspend fun write(): EngineWriteResult
    /** 移除本方资源；有任何残留或所有权日志提交失败时返回 false。 */
    suspend fun cleanup(): Boolean
}

/** 可持久化的恢复线索，不直接保存内部领域快照类型。 */
data class SessionJournalRecord(

    /** 原会话身份。 */
    val sessionId: String,

    /** 原正数任务代次。 */
    val generation: Long,

    /** 原始冻结输入。 */
    val target: SessionTargetSnapshot,

    /** 原阶段；非终态只能恢复为清理。 */
    val phase: SessionPhase,

    /** 上次真实写入的纳秒确认；恢复时丢弃。 */
    val lastWriteNanos: Long?,

    /** 原始失败原因。 */
    val error: SessionError?
)

/** 会话日志持久化端口；平台所有权账本另由引擎维护。 */
interface SessionJournalPort {

    /** 读取旧线索；损坏或 I/O 失败须抛出，不伪装成没有历史。 */
    suspend fun load(): SessionJournalRecord?

    /** 在平台启动之前持久化 Starting，停止请求和失败时更新。 */
    suspend fun save(record: SessionJournalRecord)

    /** 仅实际清理完成后删除日志，提交失败必须阻止释放活动槽位。 */
    suspend fun clear()
}
