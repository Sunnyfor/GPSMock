package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.*
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.MemoryCatalogStorage
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application.PositionCatalogCoordinator
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** 会话协调器端口测试；不把这些替身断言当作真实 provider 或前台服务验收。 */
class MockLocationSessionCoordinatorTest {
    /** Starting 没有运行事实；全部首次写入确认后才 Running，同命令只启动一次。 */
    @Test fun requiresConfirmedFirstWriteAndDeduplicatesStart() = withFixture {
        val accepted = session.start("start", inline())
        assertEquals(SessionPhase.Starting, session.state.value.phase)
        assertEquals(accepted, session.start("start", inline()))
        assertEquals(1, engine.preparations)
        assertEquals(SessionError.CommandConflict, session.start("start", inline(1.0)).error)
        engine.prepared.complete(EngineWriteResult(100))
        await(SessionPhase.Running)
        assertEquals(100L, session.state.value.startedAtNanos)
        session.stop("stop", accepted.sessionId!!)
        await(SessionPhase.Idle)
        assertNull(journal.record)
    }

    /** 不同启动不能抢占活动槽位，停止旧身份也不能停止新会话。 */
    @Test fun protectsActiveSlotAndIgnoresOldStop() = withFixture {
        val first = session.start("first", inline())
        assertEquals(SessionError.Busy, session.start("other", inline(1.0)).error)
        session.stop("stop-first", first.sessionId!!)
        await(SessionPhase.Idle)
        engine.prepared = CompletableDeferred()
        val second = session.start("second", inline(2.0))
        session.stop("old-notification", first.sessionId)
        assertEquals(second.sessionId, session.state.value.sessionId)
        assertEquals(SessionPhase.Starting, session.state.value.phase)
    }

    /** 启动中停止取消并等待旧准备；迟到确认不能让已经停止的会话 Running。 */
    @Test fun stopDuringStartingInvalidatesLatePreparation() = withFixture {
        val accepted = session.start("start", inline())
        session.stop("stop", accepted.sessionId!!)
        await(SessionPhase.Idle)
        engine.prepared.complete(EngineWriteResult(100))
        yield()
        assertEquals(SessionPhase.Idle, session.state.value.phase)
        assertEquals(0, engine.writes)
    }

    /** 页面调用者在停止日志 I/O 中取消后，应用范围仍须完成旧任务等待和平台清理。 */
    @Test fun cancelledStopCallerStillSchedulesApplicationCleanup() = withFixture {
        val accepted = session.start("start", inline())
        journal.pauseStoppingSave = true
        val caller = scope.launch { session.stop("stop", accepted.sessionId!!) }
        withTimeout(2_000) { journal.stoppingSaveEntered.await() }
        caller.cancelAndJoin()
        await(SessionPhase.Idle)
        assertEquals(2, engine.cleanups)
        assertNull(journal.record)
    }

    /** 授权拒绝先清理，再回 Idle 并保留权限错误；不得出现 Running。 */
    @Test fun deniedStartCompensatesAndKeepsFailure() = withFixture {
        session.start("start", inline())
        engine.prepared.complete(EngineWriteResult(error = SessionError.PermissionDenied))
        await(SessionPhase.Idle)
        assertEquals(SessionError.PermissionDenied, session.state.value.error)
        assertEquals(2, engine.cleanups)
    }

    /** 清理失败保留活动根并阻止启动，重试真实清理成功后才释放。 */
    @Test fun failedCleanupBlocksStartUntilRetrySucceeds() = withFixture {
        val accepted = session.start("start", inline())
        engine.cleanupSucceeds = false
        session.stop("stop", accepted.sessionId!!)
        await(SessionPhase.CleanupRequired)
        assertEquals(SessionError.CleanupRequired, session.start("blocked", inline()).error)
        engine.cleanupSucceeds = true
        session.retryCleanup("retry")
        await(SessionPhase.Idle)
        assertNull(journal.record)
    }

    /** Starting 记录无法落盘时拒绝启动，不操作任何 provider。 */
    @Test fun journalFailurePreventsEngineStart() = withFixture {
        journal.failSave = true
        assertEquals(SessionError.StorageFailure, session.start("start", inline()).error)
        assertEquals(0, engine.preparations)
        assertEquals(SessionPhase.Idle, session.state.value.phase)
    }

    /** 运行中的收藏目标已冻结；删除来源不会修改目标或妨碍停止。 */
    @Test fun savedTargetRemainsFrozenAfterDeletion() = withFixture {
        val saved = catalog.execute("save", CatalogMutation.Create("冻结名称", CatalogCoordinate(0.0, 0.0, CatalogCoordinateSystem.WGS84))).position!!
        val accepted = session.start("start", StartTarget.Saved(saved.id, saved.revision))
        engine.prepared.complete(EngineWriteResult(100))
        await(SessionPhase.Running)
        catalog.execute("delete", CatalogMutation.Delete(saved.id, saved.revision))
        assertEquals("冻结名称", session.state.value.target!!.displayName)
        assertEquals(saved.id, session.state.value.target!!.sourceId)
        session.stop("stop", accepted.sessionId!!)
        await(SessionPhase.Idle)
    }

    /** 两个真实并发调用只占用一个活动槽位，另一个得到 Busy。 */
    @Test fun concurrentStartsAcceptExactlyOne() = withFixture {
        val results = coroutineScope {
            listOf(async(Dispatchers.Default) { session.start("parallel-a", inline(1.0)) },
                async(Dispatchers.Default) { session.start("parallel-b", inline(2.0)) }).awaitAll()
        }
        assertEquals(1, results.count { it.error == null })
        assertEquals(1, results.count { it.error == SessionError.Busy })
        assertEquals(1, engine.preparations)
    }

    /** 不可取消平台准备晚返回时，停止等待该资源任务退出才清理，绝不重新 Running。 */
    @Test fun waitsForLateUncancellablePreparationBeforeCleanup() = withFixture {
        engine.ignorePrepareCancellation = true
        val accepted = session.start("start", inline())
        session.stop("stop", accepted.sessionId!!)
        assertEquals(SessionPhase.Stopping, session.state.value.phase)
        assertEquals(1, engine.cleanups)
        engine.prepared.complete(EngineWriteResult(100))
        await(SessionPhase.Idle)
        assertEquals(2, engine.cleanups)
        assertNull(session.state.value.startedAtNanos)
    }

    /** provider 已清理但日志删除失败仍阻止启动，重试同时完成日志清理后才 Idle。 */
    @Test fun journalClearFailureRetainsCleanupBarrier() = withFixture {
        val accepted = session.start("start", inline())
        journal.failClear = true
        session.stop("stop", accepted.sessionId!!)
        await(SessionPhase.CleanupRequired)
        assertEquals(SessionError.StorageFailure, session.state.value.error)
        journal.failClear = false
        session.retryCleanup("retry")
        await(SessionPhase.Idle)
    }

    /** 运行中平台授权撤销通过下一轮失败事实触发停止，错误保留，不能继续 Running。 */
    @Test fun periodicWriteFailureStopsAndCompensates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TestEngine().apply { writeError = SessionError.PermissionDenied }
        try {
            val session = MockLocationSessionCoordinator(PositionCatalogCoordinator(MemoryCatalogStorage()), engine, MemoryJournal(), scope, 1)
            withTimeout(2_000) { session.state.first { it.phase == SessionPhase.Idle } }
            session.start("start", inline())
            engine.prepared.complete(EngineWriteResult(100))
            withTimeout(2_000) { session.state.first { it.phase == SessionPhase.Idle } }
            assertEquals(SessionError.PermissionDenied, session.state.value.error)
            assertTrue(engine.writes >= 1)
            assertEquals(2, engine.cleanups)
        } finally { scope.cancel() }
    }

    /** 没有活动根但资源账本清理失败时，也必须支持显式重试，不永久锁住启动。 */
    @Test fun startupCleanupWithoutJournalCanBeRetried() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TestEngine().apply { cleanupSucceeds = false }
        try {
            val session = MockLocationSessionCoordinator(PositionCatalogCoordinator(MemoryCatalogStorage()), engine, MemoryJournal(), scope)
            withTimeout(2_000) { session.state.first { it.phase == SessionPhase.CleanupRequired } }
            assertEquals(SessionError.CleanupRequired, session.start("blocked", inline()).error)
            engine.cleanupSucceeds = true
            session.retryCleanup("retry")
            withTimeout(2_000) { session.state.first { it.phase == SessionPhase.Idle } }
            assertEquals(SessionPhase.Idle, session.state.value.phase)
        } finally { scope.cancel() }
    }

    /** 历史 Running 只恢复清理，不调用引擎准备，也不沿用旧单调时间。 */
    @Test fun recoveryNeverRestartsHistoricalRunning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = TestEngine()
        val journal = MemoryJournal().apply {
            record = SessionJournalRecord("old", 1, SessionTargetSnapshot(inline().coordinate), SessionPhase.Running, 100, null)
        }
        try {
            val session = MockLocationSessionCoordinator(PositionCatalogCoordinator(MemoryCatalogStorage()), engine, journal, scope)
            withTimeout(2_000) { session.state.first { it.phase == SessionPhase.Idle } }
            assertEquals(0, engine.preparations)
            assertEquals(SessionError.Interrupted, session.state.value.error)
            assertNull(session.state.value.startedAtNanos)
            assertNull(journal.record)
        } finally { scope.cancel() }
    }

    /** 临时输入生成器，纬度可变化以检查命令参数冲突。 */
    private fun inline(latitude: Double = 0.0) = StartTarget.Inline(SessionCoordinate(latitude, 0.0, SessionCoordinateSystem.WGS84))

    /** 每个场景独立 Application 范围，结束时取消任务而不跨测试保留状态。 */
    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture()
        try { fixture.await(SessionPhase.Idle); fixture.block() } finally { fixture.scope.cancel() }
    }
}

/** 有明确异步准备门闩的测试夹具，避免依赖任意 sleep。 */
private class Fixture {

    /** 不需要 Android Main 的可控测试范围。 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** 真实应用用例加事务替身。 */
    val catalog = PositionCatalogCoordinator(MemoryCatalogStorage())

    /** 平台端口替身。 */
    val engine = TestEngine()

    /** 会话日志替身。 */
    val journal = MemoryJournal()

    /** 待测唯一协调器，长周期避免未请求的写入影响断言。 */
    val session = MockLocationSessionCoordinator(catalog, engine, journal, scope, 60_000)

    /** 等待精确状态，超时明确失败。 */
    suspend fun await(phase: SessionPhase) { withTimeout(2_000) { session.state.first { it.phase == phase } } }
}

/** 异步引擎替身；第一次准备必须由测试明确完成。 */
private class TestEngine : MockEnginePort {

    /** 待完成准备事实。 */
    var prepared = CompletableDeferred<EngineWriteResult>()

    /** 实际准备调用次数。 */
    var preparations = 0

    /** 周期写入调用次数。 */
    var writes = 0

    /** 包括进程初始化清理的调用次数。 */
    var cleanups = 0

    /** 清理返回是否成功。 */
    var cleanupSucceeds = true

    /** 模拟不能立即取消的系统调用，验证清理必须等待旧资源任务。 */
    var ignorePrepareCancellation = false

    /** 周期写入的注入故障；null 表示成功。 */
    var writeError: SessionError? = null

    /** 等待测试提交首写入事实，可模拟不可取消的平台准备。 */
    override suspend fun prepareAndWrite(target: SessionTargetSnapshot): EngineWriteResult {
        preparations++
        return if (ignorePrepareCancellation) withContext(NonCancellable) { prepared.await() } else prepared.await()
    }

    /** 测试周期事实，成功时间不递减；故障不提供确认时间。 */
    override suspend fun write(): EngineWriteResult {
        writes++
        return writeError?.let { EngineWriteResult(error = it) } ?: EngineWriteResult(100L + writes)
    }

    /** 返回被测试注入的清理结果。 */
    override suspend fun cleanup(): Boolean { cleanups++; return cleanupSucceeds }
}

/** 无 Android 类型的日志端口替身。 */
private class MemoryJournal : SessionJournalPort {

    /** 已提交历史线索。 */
    var record: SessionJournalRecord? = null

    /** 是否注入保存故障。 */
    var failSave = false

    /** 是否注入清理日志提交失败。 */
    var failClear = false

    /** 停止日志进入后暂停，供测试取消页面调用者；应用清理范围必须不受影响。 */
    var pauseStoppingSave = false

    /** 停止日志 I/O 已进入的明确门闩。 */
    val stoppingSaveEntered = CompletableDeferred<Unit>()

    /** 读取历史。 */
    override suspend fun load() = record

    /** 故障或暂停发生在替换之前，不伪造成功提交。 */
    override suspend fun save(record: SessionJournalRecord) {
        check(!failSave)
        if (pauseStoppingSave && record.phase == SessionPhase.Stopping) {
            stoppingSaveEntered.complete(Unit)
            awaitCancellation()
        }
        this.record = record
    }

    /** 删除前注入故障时保留原日志。 */
    override suspend fun clear() { check(!failClear); record = null }
}
