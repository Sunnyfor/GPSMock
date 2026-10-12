package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationSessionState
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationTarget
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionCallbackIdentity
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionFailureReason

/**
 * 模拟定位会话聚合根：维护一次会话的冻结目标、生命周期及已确认的引擎事实。
 *
 * ## 领域职责
 * 请求意图与执行事实分离；只有匹配当前任务身份的事实才能改变状态。
 * 创建见 [start]，历史恢复见 [recoverForCleanup]，回调身份见 [callbackIdentity]。
 *
 * ## 使用边界
 * 本聚合不执行 I/O、不启动引擎，也不证明平台资源已经清理。
 * 调用方须串行访问、依据真实引擎结果提交事实，并协调跨会话的唯一活动槽位和持久化。
 * 运行状态只代表曾接受启动确认，不等同于持续健康检查。
 */
internal class MockLocationSessionAggregateRootEntity private constructor(
    /** 本次会话的稳定非空白身份；由调用方分配，聚合不验证全局唯一性。 */
    val sessionId: String,
    generation: Long,

    /** 启动时冻结的目标输入；整个会话期间保持不变。 */
    val target: MockLocationTarget,
    state: MockLocationSessionState,
    lastConfirmedWriteNanos: Long?,
    failure: SessionFailureReason?
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID must not be blank" }
        require(generation > 0) { "Generation must be positive" }
        require(lastConfirmedWriteNanos == null || lastConfirmedWriteNanos >= 0) {
            "Confirmation time must be non-negative"
        }
        when (state) {
            MockLocationSessionState.Starting -> require(lastConfirmedWriteNanos == null && failure == null)
            MockLocationSessionState.Running -> require(lastConfirmedWriteNanos != null && failure == null)
            MockLocationSessionState.CleanupRequired -> require(failure != null)
            MockLocationSessionState.Stopping, MockLocationSessionState.Stopped -> Unit
        }
    }

    /**
     * 当前任务的正数代次，用于拒绝旧任务回调；不是收藏修订版本。
     *
     * 停止或清理任务换代见 [moveToStopping]，仅在同一会话身份内比较。
     */
    var generation: Long = generation
        private set

    /** 当前生命周期阶段，只能通过聚合行为推进；语义见 [MockLocationSessionState]。 */
    var state: MockLocationSessionState = state
        private set

    /**
     * 最近一次接受的真实写入确认时间，单位为纳秒，来自调用方一致的单调时钟。
     *
     * null 表示当前没有可用的写入确认；非空时须为非负数。
     * 停止阶段可保留其作为历史，不代表引擎仍在运行；跨进程恢复见 [recoverForCleanup]。
     */
    var lastConfirmedWriteNanos: Long? = lastConfirmedWriteNanos
        private set

    /** 最近记录的失败原因；null 表示尚未记录失败，清理成功不会自动抹去历史原因。 */
    var failure: SessionFailureReason? = failure
        private set

    /** 返回当前任务身份，供调用方在发起引擎操作时捕获并原样回传。 */
    fun callbackIdentity(): SessionCallbackIdentity = SessionCallbackIdentity(sessionId, generation)

    /**
     * 接受启动准备及必要首次写入已经完成的事实，进入运行阶段。
     *
     * 先检查身份，再检查状态和时间；已运行时视为重复，不改写首次确认时间。
     * 其他阶段或负时间被拒绝。调用方须确认服务准备与必要首次写入均已完成，
     * 不能仅凭用户点击启动或任务已提交就调用本方法。
     *
     * @param confirmedAtNanos 同一单调时钟下的非负纳秒时间，不是墙上时钟时间。
     * @return 本条事实的处理结果；未应用时聚合保持原值。
     */
    fun confirmStarted(identity: SessionCallbackIdentity, confirmedAtNanos: Long): SessionFactResult {
        if (!matches(identity)) return SessionFactResult.IgnoredStale
        if (state == MockLocationSessionState.Running) return SessionFactResult.IgnoredDuplicate
        if (state != MockLocationSessionState.Starting) return SessionFactResult.RejectedState
        if (confirmedAtNanos < 0) return SessionFactResult.RejectedTimestamp
        lastConfirmedWriteNanos = confirmedAtNanos
        state = MockLocationSessionState.Running
        return SessionFactResult.Applied
    }

    /**
     * 在运行阶段更新最近一次真实写入的确认时间。
     *
     * 身份不匹配时忽略；非运行阶段拒绝。时间早于上次确认时拒绝，
     * 相等时视为重复，只有更晚的确认时间才更新 [lastConfirmedWriteNanos]。
     *
     * @param confirmedAtNanos 与启动确认使用同一单调时钟的纳秒时间。
     * @return 本条事实的处理结果，不代表本方法执行了位置写入。
     */
    fun confirmWrite(identity: SessionCallbackIdentity, confirmedAtNanos: Long): SessionFactResult {
        if (!matches(identity)) return SessionFactResult.IgnoredStale
        if (state != MockLocationSessionState.Running) return SessionFactResult.RejectedState
        val previous = requireNotNull(lastConfirmedWriteNanos)
        if (confirmedAtNanos < previous) return SessionFactResult.RejectedTimestamp
        if (confirmedAtNanos == previous) return SessionFactResult.IgnoredDuplicate
        lastConfirmedWriteNanos = confirmedAtNanos
        return SessionFactResult.Applied
    }

    /**
     * 请求停止：启动或运行阶段进入停止阶段，使此前的启动/写入回调失效。
     *
     * 停止中、待清理或已停止时复用当前身份，不增加代次，也不将待清理直接变成已停止。
     * 本方法不执行资源清理；完成事实须由调用方通过 [confirmStopped] 提交。
     *
     * @return 当前停止/清理任务身份，供调用方安排或复用外部任务。
     * @throws IllegalStateException 需要换代但代次已耗尽；失败时聚合保持原值。
     */
    fun requestStop(): SessionCallbackIdentity {
        if (state == MockLocationSessionState.Starting || state == MockLocationSessionState.Running) {
            moveToStopping()
        }
        return callbackIdentity()
    }

    /**
     * 接受当前启动或运行任务的失败事实，进入停止阶段并记录失败原因。
     *
     * 过期身份被忽略，其他状态拒绝；成功时换代以阻止旧写入回调继续影响状态。
     * 已确认写入时间保留为历史。原因由调用方映射，本方法不识别平台异常。
     *
     * @return 本条失败事实的处理结果。
     * @throws IllegalStateException 代次已耗尽；状态、时间和失败原因均保持原值。
     */
    fun reportEngineFailure(identity: SessionCallbackIdentity, reason: SessionFailureReason): SessionFactResult {
        if (!matches(identity)) return SessionFactResult.IgnoredStale
        if (state != MockLocationSessionState.Starting && state != MockLocationSessionState.Running) {
            return SessionFactResult.RejectedState
        }
        moveToStopping()
        failure = reason
        return SessionFactResult.Applied
    }

    /**
     * 接受当前停止任务的资源清理完成事实，进入已停止终态。
     *
     * 过期身份被忽略；已停止时视为重复，其他非停止中阶段拒绝。
     * 保留历史时间与失败原因；调用方必须依据真实清理结果确认，不能仅凭停止意图。
     */
    fun confirmStopped(identity: SessionCallbackIdentity): SessionFactResult {
        if (!matches(identity)) return SessionFactResult.IgnoredStale
        if (state == MockLocationSessionState.Stopped) return SessionFactResult.IgnoredDuplicate
        if (state != MockLocationSessionState.Stopping) return SessionFactResult.RejectedState
        state = MockLocationSessionState.Stopped
        return SessionFactResult.Applied
    }

    /**
     * 接受当前停止任务清理未完成的事实，进入待清理阶段。
     *
     * 过期身份被忽略，已待清理时视为重复，其他非停止中阶段拒绝。
     * 已有失败原因保留；没有原始原因时记录 [SessionFailureReason.CleanupFailure]。
     */
    fun markCleanupRequired(identity: SessionCallbackIdentity): SessionFactResult {
        if (!matches(identity)) return SessionFactResult.IgnoredStale
        if (state == MockLocationSessionState.CleanupRequired) return SessionFactResult.IgnoredDuplicate
        if (state != MockLocationSessionState.Stopping) return SessionFactResult.RejectedState
        state = MockLocationSessionState.CleanupRequired
        // 补偿启动或写入失败时，保留最初原因，避免被后续清理失败覆盖。
        if (failure == null) failure = SessionFailureReason.CleanupFailure
        return SessionFactResult.Applied
    }

    /**
     * 显式重试清理：待清理阶段换代后进入停止中，使上次清理回调失效。
     *
     * 已停止中或已停止时返回当前身份，不换代或改变状态；历史原因和写入时间保留。
     * 实际清理及任务复用由调用方安排。
     *
     * @throws IllegalStateException 仍处于启动/运行阶段，或重试所需代次已耗尽；
     * 失败时聚合保持原值。
     */
    fun retryCleanup(): SessionCallbackIdentity {
        check(state == MockLocationSessionState.CleanupRequired ||
            state == MockLocationSessionState.Stopping || state == MockLocationSessionState.Stopped) {
            "Cleanup may only be retried after stopping"
        }
        if (state == MockLocationSessionState.CleanupRequired) moveToStopping()
        return callbackIdentity()
    }

    /** 生成不可变内部快照；后续状态转换不会改变已经生成的记录。 */
    fun snapshot(): Snapshot = Snapshot(sessionId, generation, target, state, lastConfirmedWriteNanos, failure)

    /** 同时匹配会话身份和任务代次，阻止其他会话或旧任务的事实污染当前状态。 */
    private fun matches(identity: SessionCallbackIdentity): Boolean =
        identity.sessionId == sessionId && identity.generation == generation

    /**
     * 推进任务代次并进入停止阶段；先检查溢出，避免留下半完成状态。
     *
     * @throws IllegalStateException 当前代次已达到 [Long.MAX_VALUE]。
     */
    private fun moveToStopping() {
        check(generation < Long.MAX_VALUE) { "Generation exhausted" }
        generation += 1
        state = MockLocationSessionState.Stopping
    }

    /**
     * 会话的内部恢复记录，不作为 UI 的公开运行状态契约。
     *
     * 记录本身不验证状态组合；恢复时重新校验，不能凭历史状态宣称引擎仍在运行。
     * 成员保存记录时的值，其领域含义见所属聚合的对应成员。
     */
    data class Snapshot(
        /** 记录所属的会话身份。 */
        val sessionId: String,

        /** 记录时的任务代次，恢复换代规则见 [recoverForCleanup]。 */
        val generation: Long,

        /** 本次会话冻结的目标输入。 */
        val target: MockLocationTarget,

        /** 记录时的生命周期阶段，不保证当前平台仍处于相同状态。 */
        val state: MockLocationSessionState,

        /** 历史写入确认的单调时钟纳秒值；null 表示没有可用确认。 */
        val lastConfirmedWriteNanos: Long?,

        /** 记录时的失败原因；null 表示尚未记录失败。 */
        val failure: SessionFailureReason?
    )

    /** 聚合创建与历史恢复的受控入口。 */
    companion object {
        /**
         * 创建启动阶段的会话，尚未有写入确认或失败原因。
         *
         * 身份与初始代次由调用方分配，本方法不检查跨会话排他、不启动服务或引擎。
         * 冻结目标由不可变值对象提供，无需额外深复制。
         *
         * @throws IllegalArgumentException 会话身份为空白，或初始代次不是正数。
         */
        fun start(
            sessionId: String,
            generation: Long,
            target: MockLocationTarget
        ): MockLocationSessionAggregateRootEntity = MockLocationSessionAggregateRootEntity(
            sessionId, generation, target, MockLocationSessionState.Starting, null, null
        )

        /**
         * 从历史记录重建待清理会话，绝不将历史运行状态恢复成当前运行事实。
         *
         * ## 恢复规则
         * - 先校验身份、代次、时间和状态组合，非法记录不能绕过构造约束。
         * - 非终止记录换代并进入停止中，原因改为中断，历史单调时间清空。
         * - 已停止记录完整保留，不另建清理代次。
         *
         * 非终止记录返回后仍须由调用方执行真实清理；本方法不恢复服务或处理平台资源。
         *
         * @throws IllegalArgumentException 记录身份为空白、代次非正、时间为负；
         * 或启动阶段带时间/失败、运行阶段缺时间或带失败、待清理阶段缺失败原因。
         * @throws IllegalStateException 非终止记录的代次已耗尽，无法创建新清理任务。
         */
        fun recoverForCleanup(snapshot: Snapshot): MockLocationSessionAggregateRootEntity {
            val recovered = MockLocationSessionAggregateRootEntity(
                snapshot.sessionId, snapshot.generation, snapshot.target, snapshot.state,
                snapshot.lastConfirmedWriteNanos, snapshot.failure
            )
            if (recovered.state != MockLocationSessionState.Stopped) {
                recovered.moveToStopping()
                recovered.failure = SessionFailureReason.Interrupted
                // 旧进程/启动周期中的单调时间不能作为本次运行的新鲜事实。
                recovered.lastConfirmedWriteNanos = null
            }
            return recovered
        }
    }
}
