package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr

/**
 * 聚合处理一条引擎事实的结果，不代表外部引擎任务或持久化已经成功。
 *
 * 除 [Applied] 外，各结果均不改变聚合状态；具体校验顺序由对应行为定义。
 */
internal enum class SessionFactResult {
    /** 事实已被接受，并应用到当前聚合状态。 */
    Applied,

    /** 会话身份或代次不匹配，忽略过期或其他任务的事实。 */
    IgnoredStale,

    /** 当前状态或时间表明事实重复，保留原状态。 */
    IgnoredDuplicate,

    /** 当前生命周期阶段不允许该事实，拒绝处理。 */
    RejectedState,

    /** 确认时间为负数或早于已确认时间，拒绝更新。 */
    RejectedTimestamp
}
