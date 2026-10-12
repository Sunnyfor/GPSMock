package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationSessionState
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationTarget
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionCallbackIdentity
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** 模拟会话聚合的行为测试，覆盖生命周期、任务身份、事实顺序、清理与历史恢复；不运行平台引擎。 */
class MockLocationSessionAggregateRootEntityTest {
    /** 带收藏来源的固定会话目标，用于验证冻结输入与版本记录。 */
    private val target = MockLocationTarget(
        MockLocationCoordinate(39.9, 116.4, CoordinateSystem.GCJ02), "家", "location-1", 1L
    )

    /** 创建会话只有启动意图：目标被记录，但未产生运行状态、写入时间或失败原因。 */
    @Test
    fun startIsOnlyIntentUntilTheFirstWriteIsConfirmed() {
        val root = start()
        assertEquals(MockLocationSessionState.Starting, root.state)
        assertNull(root.lastConfirmedWriteNanos)
        assertNull(root.failure)
        assertEquals(target, root.target)
    }

    /** 正常生命周期必须接受匹配的启动、写入和清理事实，停止换代且保留末次写入历史。 */
    @Test
    fun normalLifecycleRequiresMatchingStartupAndCleanupFacts() {
        val root = start()
        val startup = root.callbackIdentity()
        assertEquals(SessionFactResult.Applied, root.confirmStarted(startup, 0))
        assertEquals(MockLocationSessionState.Running, root.state)
        assertEquals(SessionFactResult.Applied, root.confirmWrite(startup, 400_000_000))
        val cleanup = root.requestStop()
        assertEquals(MockLocationSessionState.Stopping, root.state)
        assertEquals(startup.generation + 1, cleanup.generation)
        assertEquals(SessionFactResult.Applied, root.confirmStopped(cleanup))
        assertEquals(MockLocationSessionState.Stopped, root.state)
        assertEquals(400_000_000L, root.lastConfirmedWriteNanos)
    }

    /** 启动中停止后，旧任务的启动、停止与失败事实均被忽略且不改变快照。 */
    @Test
    fun stoppingDuringStartupInvalidatesTheLateStartupConfirmation() {
        val root = start()
        val old = root.callbackIdentity()
        val cleanup = root.requestStop()
        val before = root.snapshot()
        assertEquals(SessionFactResult.IgnoredStale, root.confirmStarted(old, 1))
        assertEquals(SessionFactResult.IgnoredStale, root.confirmStopped(old))
        assertEquals(SessionFactResult.IgnoredStale, root.reportEngineFailure(old, SessionFailureReason.PermissionDenied))
        assertEquals(before, root.snapshot())
        assertEquals(SessionFactResult.Applied, root.confirmStopped(cleanup))
    }

    /** 其他会话或不同代次的所有事实均被忽略，当前聚合保持原值。 */
    @Test
    fun foreignSessionAndForeignGenerationFactsCannotChangeState() {
        val root = start()
        val before = root.snapshot()
        for (identity in listOf(SessionCallbackIdentity("other-session", 1), SessionCallbackIdentity("session-1", 2))) {
            assertEquals(SessionFactResult.IgnoredStale, root.confirmStarted(identity, 10))
            assertEquals(SessionFactResult.IgnoredStale, root.confirmWrite(identity, 10))
            assertEquals(SessionFactResult.IgnoredStale, root.confirmStopped(identity))
            assertEquals(SessionFactResult.IgnoredStale, root.markCleanupRequired(identity))
            assertEquals(SessionFactResult.IgnoredStale, root.reportEngineFailure(identity, SessionFailureReason.EngineFailure))
        }
        assertEquals(before, root.snapshot())
    }

    /** 启动阶段拒绝写入/清理事实及清理重试，不留下部分状态修改。 */
    @Test
    fun invalidStateFactsAreRejectedWithoutMutatingTheAggregate() {
        val root = start()
        val before = root.snapshot()
        val identity = root.callbackIdentity()
        assertEquals(SessionFactResult.RejectedState, root.confirmWrite(identity, 1))
        assertEquals(SessionFactResult.RejectedState, root.confirmStopped(identity))
        assertEquals(SessionFactResult.RejectedState, root.markCleanupRequired(identity))
        assertThrows(IllegalStateException::class.java) { root.retryCleanup() }
        assertEquals(before, root.snapshot())
    }

    /** 负启动时间和早于上次写入的时间被拒绝，相同写入时间视为重复且不更新。 */
    @Test
    fun invalidOrOlderTimestampsCannotInventAFreshConfirmation() {
        val root = start()
        val identity = root.callbackIdentity()
        val before = root.snapshot()
        assertEquals(SessionFactResult.RejectedTimestamp, root.confirmStarted(identity, -1))
        assertEquals(before, root.snapshot())
        root.confirmStarted(identity, 10)
        val running = root.snapshot()
        assertEquals(SessionFactResult.RejectedTimestamp, root.confirmWrite(identity, 9))
        assertEquals(SessionFactResult.IgnoredDuplicate, root.confirmWrite(identity, 10))
        assertEquals(running, root.snapshot())
    }

    /** 运行后重复启动确认即使时间更晚，也不改写原确认时间。 */
    @Test
    fun duplicateStartupDoesNotRewriteTheConfirmationTime() {
        val root = start()
        val identity = root.callbackIdentity()
        root.confirmStarted(identity, 10)
        val before = root.snapshot()
        assertEquals(SessionFactResult.IgnoredDuplicate, root.confirmStarted(identity, 999))
        assertEquals(before, root.snapshot())
    }

    /** 重复停止、重复完成和终态清理重试复用身份，不创建新的清理代次。 */
    @Test
    fun duplicateStopAndConfirmedStopDoNotCreateAnotherCleanupTask() {
        val root = start()
        val cleanup = root.requestStop()
        assertEquals(cleanup, root.requestStop())
        root.confirmStopped(cleanup)
        val terminal = root.snapshot()
        assertEquals(SessionFactResult.IgnoredDuplicate, root.confirmStopped(cleanup))
        assertEquals(cleanup, root.requestStop())
        assertEquals(cleanup, root.retryCleanup())
        assertEquals(terminal, root.snapshot())
    }

    /** 清理失败必须留在待清理阶段，普通停止或未经重试的完成事实不能将其清除。 */
    @Test
    fun cleanupFailureCannotBeClearedByOrdinaryStopOrUnconfirmedSuccess() {
        val root = start()
        val cleanup = root.requestStop()
        assertEquals(SessionFactResult.Applied, root.markCleanupRequired(cleanup))
        val pending = root.snapshot()
        assertEquals(SessionFailureReason.CleanupFailure, root.failure)
        assertEquals(cleanup, root.requestStop())
        assertEquals(SessionFactResult.IgnoredDuplicate, root.markCleanupRequired(cleanup))
        assertEquals(SessionFactResult.RejectedState, root.confirmStopped(cleanup))
        assertEquals(pending, root.snapshot())
    }

    /** 显式清理重试换代，拒绝上次尝试的结果，重复重试复用当前任务。 */
    @Test
    fun cleanupRetryGetsANewGenerationAndRejectsResultsFromThePreviousAttempt() {
        val root = start()
        val oldCleanup = root.requestStop()
        root.markCleanupRequired(oldCleanup)
        val retry = root.retryCleanup()
        assertEquals(oldCleanup.generation + 1, retry.generation)
        assertEquals(MockLocationSessionState.Stopping, root.state)
        assertEquals(retry, root.retryCleanup())
        val before = root.snapshot()
        assertEquals(SessionFactResult.IgnoredStale, root.confirmStopped(oldCleanup))
        assertEquals(SessionFactResult.IgnoredStale, root.markCleanupRequired(oldCleanup))
        assertEquals(before, root.snapshot())
        assertEquals(SessionFactResult.Applied, root.confirmStopped(retry))
    }

    /** 部分启动失败先进入清理；后续清理失败与成功均保留最初权限失败原因。 */
    @Test
    fun partialStartupFailureRequiresCleanupAndRetainsItsOriginalReason() {
        val root = start()
        val startup = root.callbackIdentity()
        assertEquals(SessionFactResult.Applied, root.reportEngineFailure(startup, SessionFailureReason.PermissionDenied))
        assertEquals(MockLocationSessionState.Stopping, root.state)
        assertNull(root.lastConfirmedWriteNanos)
        val cleanup = root.callbackIdentity()
        root.markCleanupRequired(cleanup)
        assertEquals(SessionFailureReason.PermissionDenied, root.failure)
        root.confirmStopped(root.retryCleanup())
        assertEquals(MockLocationSessionState.Stopped, root.state)
        assertEquals(SessionFailureReason.PermissionDenied, root.failure)
    }

    /** 运行失败进入停止阶段，保留真实写入历史并拒绝旧写入任务的新事实。 */
    @Test
    fun runtimeFailureStopsTheSessionAndKeepsTheLastRealWriteAsHistory() {
        val root = start()
        val writer = root.callbackIdentity()
        root.confirmStarted(writer, 10)
        assertEquals(SessionFactResult.Applied, root.reportEngineFailure(writer, SessionFailureReason.EngineFailure))
        assertEquals(MockLocationSessionState.Stopping, root.state)
        assertEquals(10L, root.lastConfirmedWriteNanos)
        assertEquals(SessionFactResult.IgnoredStale, root.confirmWrite(writer, 20))
    }

    /** 已停止会话是终态，拒绝再次启动、写入、待清理或引擎失败事实。 */
    @Test
    fun stoppedIsTerminalAndCannotAcceptAnotherStartOrFailure() {
        val root = start()
        val cleanup = root.requestStop()
        root.confirmStopped(cleanup)
        val terminal = root.snapshot()
        assertEquals(SessionFactResult.RejectedState, root.confirmStarted(cleanup, 10))
        assertEquals(SessionFactResult.RejectedState, root.confirmWrite(cleanup, 10))
        assertEquals(SessionFactResult.RejectedState, root.markCleanupRequired(cleanup))
        assertEquals(SessionFactResult.RejectedState, root.reportEngineFailure(cleanup, SessionFailureReason.EngineFailure))
        assertEquals(terminal, root.snapshot())
    }

    /** 复制修改目标或推进聚合，不会改变会话原始目标及已生成的快照。 */
    @Test
    fun targetAndSnapshotsDoNotFollowLaterEditsToTheSelectedInput() {
        val root = start()
        val originalSnapshot = root.snapshot()
        val editedTarget = target.copy(displayName = "办公室", sourceRevision = 2L)
        root.confirmStarted(root.callbackIdentity(), 10)
        assertEquals("办公室", editedTarget.displayName)
        assertEquals("家", root.target.displayName)
        assertEquals(1L, root.target.sourceRevision)
        assertEquals(MockLocationSessionState.Starting, originalSnapshot.state)
        assertNull(originalSnapshot.lastConfirmedWriteNanos)
        assertEquals(target, originalSnapshot.target)
    }

    /** 所有非终止历史状态恢复后均换代清理、标记中断并清空旧时间，而不恢复运行。 */
    @Test
    fun recoveryOfEveryActiveStateRequiresCleanupInsteadOfResumingRunning() {
        val roots = listOf(
            start(),
            start().apply { confirmStarted(callbackIdentity(), 100) },
            start().apply { requestStop() },
            start().apply { markCleanupRequired(requestStop()) }
        )
        for (root in roots) {
            val snapshot = root.snapshot()
            val recovered = MockLocationSessionAggregateRootEntity.recoverForCleanup(snapshot)
            assertEquals(MockLocationSessionState.Stopping, recovered.state)
            assertEquals(SessionFailureReason.Interrupted, recovered.failure)
            assertNull(recovered.lastConfirmedWriteNanos)
            assertEquals(snapshot.target, recovered.target)
            assertEquals(snapshot.generation + 1, recovered.generation)
            assertEquals(SessionFactResult.IgnoredStale, recovered.confirmStarted(root.callbackIdentity(), 200))
            assertEquals(SessionFactResult.Applied, recovered.confirmStopped(recovered.callbackIdentity()))
        }
    }

    /** 恢复已停止快照完整保留终态，不产生额外清理尝试。 */
    @Test
    fun recoveredTerminalSessionDoesNotCreateAnotherCleanupAttempt() {
        val root = start()
        root.confirmStopped(root.requestStop())
        val terminal = root.snapshot()
        assertEquals(terminal, MockLocationSessionAggregateRootEntity.recoverForCleanup(terminal).snapshot())
    }

    /** 非法身份、代次、时间或状态组合的恢复记录不能绕过构造校验。 */
    @Test
    fun invalidRecoveryRecordCannotBypassConstructionRules() {
        val valid = start().snapshot()
        val invalid = listOf(
            valid.copy(sessionId = " "),
            valid.copy(generation = 0),
            valid.copy(lastConfirmedWriteNanos = -1),
            valid.copy(lastConfirmedWriteNanos = 10),
            valid.copy(state = MockLocationSessionState.Running),
            valid.copy(state = MockLocationSessionState.Running, lastConfirmedWriteNanos = 10, failure = SessionFailureReason.EngineFailure),
            valid.copy(state = MockLocationSessionState.CleanupRequired)
        )
        for (snapshot in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                MockLocationSessionAggregateRootEntity.recoverForCleanup(snapshot)
            }
        }
    }

    /** 聚合启动及回调身份构造均拒绝空白身份和非正代次。 */
    @Test
    fun invalidStartupIdentityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MockLocationSessionAggregateRootEntity.start(" ", 1, target)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MockLocationSessionAggregateRootEntity.start("session-1", 0, target)
        }
        assertThrows(IllegalArgumentException::class.java) { SessionCallbackIdentity("", 1) }
        assertThrows(IllegalArgumentException::class.java) { SessionCallbackIdentity("session-1", -1) }
    }

    /** 代次耗尽时，停止与失败处理均抛出异常且保持完整原快照。 */
    @Test
    fun generationOverflowDoesNotPartiallyApplyStopOrFailure() {
        val root = MockLocationSessionAggregateRootEntity.start("session-1", Long.MAX_VALUE, target)
        val before = root.snapshot()
        assertThrows(IllegalStateException::class.java) { root.requestStop() }
        assertEquals(before, root.snapshot())
        assertThrows(IllegalStateException::class.java) {
            root.reportEngineFailure(root.callbackIdentity(), SessionFailureReason.EngineFailure)
        }
        assertEquals(before, root.snapshot())
    }

    /** 创建固定身份、初始代次为 1 的测试会话，每次返回独立聚合。 */
    private fun start() = MockLocationSessionAggregateRootEntity.start("session-1", 1, target)
}
