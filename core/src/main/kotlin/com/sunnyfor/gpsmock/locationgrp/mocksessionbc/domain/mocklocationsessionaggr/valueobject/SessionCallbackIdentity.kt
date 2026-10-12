package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 引擎事实回调携带的任务身份，由会话身份和任务代次共同组成。
 *
 * 调用方必须回传产生事实时持有的身份，不能在回调到达后重新取当前代次。
 * 聚合切换到新的停止/清理任务时，旧代次回调即失效。
 *
 * @throws IllegalArgumentException 会话身份为空白，或任务代次不是正数。
 */
internal data class SessionCallbackIdentity(
    /** 产生本次事实的非空白会话身份。 */
    val sessionId: String,

    /** 产生本次事实的正数任务代次，仅在所属会话内比较。 */
    val generation: Long
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID must not be blank" }
        require(generation > 0) { "Generation must be positive" }
    }
}
