package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.MockSession
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCommandResult
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionTargetSnapshot
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionView
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.StartTarget
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.MockLocationSessionAggregateRootEntity
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.SessionFactResult
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationSessionState
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationTarget
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionCallbackIdentity
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionFailureReason
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCode
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.PositionCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 唯一活动会话协调器：串行保护命令与引擎事实，平台 I/O 在可取消任务中执行。
 *
 * 停止换代后先取消并等待旧任务，再清理；旧启动事实不能恢复 Running。
 * Starting 日志落盘先于 provider 操作；进程恢复只安排清理，绝不自动启动。
 */
class MockLocationSessionCoordinator(

    /** 同进程的公开收藏能力，解析收藏不导入对方领域对象。 */
    private val catalog: PositionCatalog,

    /** 前台服务与 provider 适配端口。 */
    private val engine: MockEnginePort,

    /** 私有持久化日志。 */
    private val journal: SessionJournalPort,

    /** Application 所有的任务范围，不能绑定 Activity 生命周期。 */
    private val scope: CoroutineScope,

    /** 写入目标间隔毫秒；实际调度会受系统影响。 */
    private val intervalMillis: Long = 400L
) : MockSession {

    /** 同时保护活动根、命令收据和事实转换。 */
    private val mutex = Mutex()

    /** 当前根；清理与日志删除完成后才释放。 */
    private var active: MockLocationSessionAggregateRootEntity? = null

    /** 当前引擎任务，清理等待它完全结束。 */
    private var writer: Job? = null

    /** 唯一清理任务；重复 stop/retry 复用。 */
    private var cleaner: Job? = null

    /** 首次运行纳秒，进程恢复不沿用旧值。 */
    private var startedAt: Long? = null

    /** 完成历史读取之前关闭启动入口。 */
    private var initialized = false

    /** 同进程命令参数与结果收据，不跨进程重放运行命令。 */
    private val receipts = mutableMapOf<String, Pair<Any, SessionCommandResult>>()

    /** 可写投影，初态表示初始化/清理尚未确认。 */
    private val mutableState = MutableStateFlow(SessionView())

    /** 向 UI 和通知共享权威状态。 */
    override val state = mutableState.asStateFlow()

    init {
        require(intervalMillis > 0)
        scope.launch { recover() }
    }

    /** 解析、校验并记录启动意图；不同命令不能抢占活动槽位。 */
    override suspend fun start(commandId: String, target: StartTarget): SessionCommandResult = mutex.withLock {
        duplicate(commandId, target)?.let { return@withLock it }
        if (commandId.isBlank()) return@withLock SessionCommandResult(error = SessionError.InvalidInput)
        if (!initialized || cleaner?.isActive == true || state.value.phase == SessionPhase.CleanupRequired) {
            return@withLock remember(commandId, target, SessionCommandResult(error = SessionError.CleanupRequired))
        }
        if (active != null) return@withLock remember(commandId, target, SessionCommandResult(error = SessionError.Busy))
        val snapshot = when (target) {
            is StartTarget.Inline -> SessionTargetSnapshot(target.coordinate, target.displayName.trim())
            is StartTarget.Saved -> {
                val result = catalog.resolve(target.id, target.revision)
                val position = result.position
                if (result.code != CatalogCode.Success || position == null) {
                    val error = when (result.code) {
                        CatalogCode.NotFound -> SessionError.NotFound
                        CatalogCode.Conflict -> SessionError.Conflict
                        else -> SessionError.StorageFailure
                    }
                    return@withLock remember(commandId, target, SessionCommandResult(error = error))
                }
                SessionTargetSnapshot(SessionCoordinate(position.coordinate.latitude, position.coordinate.longitude,
                    SessionCoordinateSystem.valueOf(position.coordinate.system.name)), position.name, position.id, position.revision)
            }
        }
        val root = try { MockLocationSessionAggregateRootEntity.start(UUID.randomUUID().toString(), 1, domainTarget(snapshot)) }
        catch (_: IllegalArgumentException) { return@withLock remember(commandId, target, SessionCommandResult(error = SessionError.InvalidInput)) }
        try { journal.save(record(root)) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { return@withLock remember(commandId, target, SessionCommandResult(error = SessionError.StorageFailure)) }
        active = root
        startedAt = null
        publish()
        val identity = root.callbackIdentity()
        writer = scope.launch { runWriter(identity, snapshot) }
        remember(commandId, target, SessionCommandResult(root.sessionId))
    }

    /** 明确停止目标会话，过期通知或重复停止不伤及新会话。 */
    override suspend fun stop(commandId: String, sessionId: String): SessionCommandResult = mutex.withLock {
        val parameters = "stop:$sessionId"
        duplicate(commandId, parameters)?.let { return@withLock it }
        if (commandId.isBlank()) return@withLock SessionCommandResult(error = SessionError.InvalidInput)
        val root = active
        if (root != null && root.sessionId == sessionId && root.state != MockLocationSessionState.CleanupRequired) {
            root.requestStop()
            try { saveBestEffort(root) }
            finally {
                // 停止已换代后，即使页面任务在日志 I/O 期间取消，也必须由应用范围继续清理。
                publish()
                scheduleCleanup()
            }
        }
        remember(commandId, parameters, SessionCommandResult(sessionId))
    }

    /** Framework 宿主异常销毁时提交中断事实；过期服务不得影响后来的会话。 */
    suspend fun interrupt(sessionId: String) = mutex.withLock {
        val root = active ?: return@withLock
        if (root.sessionId == sessionId) fail(root, root.callbackIdentity(), SessionError.Interrupted)
    }

    /** 换代重试清理；损坏日志不能自动恢复运行，显式重试只在平台清理后删除旧日志。 */
    override suspend fun retryCleanup(commandId: String): SessionCommandResult {
        return mutex.withLock {
            duplicate(commandId, "retry")?.let { return@withLock it }
            if (commandId.isBlank()) return@withLock SessionCommandResult(error = SessionError.InvalidInput)
            val root = active
            if (root != null && root.state == MockLocationSessionState.CleanupRequired) root.retryCleanup()
            if (!initialized) { initialized = true; scheduleCleanup() }
            else if (root != null || state.value.phase == SessionPhase.CleanupRequired) scheduleCleanup()
            publish()
            remember(commandId, "retry", SessionCommandResult(root?.sessionId))
        }
    }

    /** 读取旧日志并安排补偿清理；即使无会话日志仍检查引擎的持久化资源账本。 */
    private suspend fun recover() = mutex.withLock {
        try {
            journal.load()?.let { old ->
                active = MockLocationSessionAggregateRootEntity.recoverForCleanup(
                    MockLocationSessionAggregateRootEntity.Snapshot(old.sessionId, old.generation, domainTarget(old.target),
                        MockLocationSessionState.valueOf(old.phase.name), old.lastWriteNanos, domainFailure(old.error)))
            }
            initialized = true
            publish()
            scheduleCleanup()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.value = SessionView(phase = SessionPhase.CleanupRequired, error = SessionError.StorageFailure) }
    }

    /** 全部 provider 首次写入成功才确认 Running；后续失败停止循环并补偿清理。 */
    private suspend fun runWriter(identity: SessionCallbackIdentity, target: SessionTargetSnapshot) {
        try {
            var result = engine.prepareAndWrite(target)
            var first = true
            while (currentCoroutineContext().isActive) {
                val keepGoing = mutex.withLock {
                    val root = active ?: return@withLock false
                    if (root.callbackIdentity() != identity) return@withLock false
                    val at = result.confirmedAtNanos
                    if (result.error != null || at == null) {
                        fail(root, identity, result.error ?: SessionError.EngineFailure)
                        false
                    } else {
                        val accepted = if (first) root.confirmStarted(identity, at) else root.confirmWrite(identity, at)
                        if (accepted != SessionFactResult.Applied && accepted != SessionFactResult.IgnoredDuplicate) return@withLock false
                        if (first) {
                            startedAt = at
                            if (!saveBestEffort(root)) { fail(root, identity, SessionError.StorageFailure); return@withLock false }
                        }
                        publish()
                        true
                    }
                }
                if (!keepGoing) return
                first = false
                delay(intervalMillis)
                result = engine.write()
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutex.withLock { active?.let { fail(it, identity, SessionError.EngineFailure) } } }
    }

    /** 匹配当前引擎任务的失败才换代并启动清理，保留类型化失败反馈。 */
    private suspend fun fail(root: MockLocationSessionAggregateRootEntity, identity: SessionCallbackIdentity, error: SessionError) {
        if (root.reportEngineFailure(identity, domainFailure(error) ?: SessionFailureReason.EngineFailure) != SessionFactResult.Applied) return
        saveBestEffort(root)
        publish(error)
        scheduleCleanup()
    }

    /** 只创建一个清理任务；先等待旧写入完全结束，杜绝清理后重新注册的竞态。 */
    private fun scheduleCleanup() {
        if (cleaner?.isActive == true) return
        val previous = writer
        cleaner = scope.launch {
            previous?.cancelAndJoin()
            val cleaned = try { engine.cleanup() } catch (error: CancellationException) { throw error } catch (_: Exception) { false }
            mutex.withLock {
                val root = active
                val journalCleared = if (cleaned) try { journal.clear(); true }
                catch (error: CancellationException) { throw error } catch (_: Exception) { false } else false
                if (cleaned && journalCleared) {
                    root?.confirmStopped(root.callbackIdentity())
                    val reason = state.value.error ?: root?.failure?.let { publicFailure(it) }
                    active = null
                    writer = null
                    startedAt = null
                    mutableState.value = SessionView(SessionPhase.Idle, error = reason)
                } else {
                    root?.markCleanupRequired(root.callbackIdentity())
                    root?.let { saveBestEffort(it) }
                    publish(if (cleaned) SessionError.StorageFailure else state.value.error ?: SessionError.CleanupRequired)
                    if (root == null) mutableState.value = SessionView(SessionPhase.CleanupRequired,
                        error = if (cleaned) SessionError.StorageFailure else state.value.error ?: SessionError.CleanupRequired)
                }
                cleaner = null
            }
        }
    }

    /** 检查进程内幂等命令；同 ID 不同参数明确拒绝。 */
    private fun duplicate(id: String, parameters: Any): SessionCommandResult? = receipts[id]?.let {
        if (it.first == parameters) it.second else SessionCommandResult(error = SessionError.CommandConflict)
    }

    /** 在串行保护下记录命令结果，不自动重放实际平台副作用。 */
    private fun remember(id: String, parameters: Any, result: SessionCommandResult): SessionCommandResult {
        receipts[id] = parameters to result
        return result
    }

    /** 保存恢复线索；失败返回 false，调用方仍须执行真实平台清理。 */
    private suspend fun saveBestEffort(root: MockLocationSessionAggregateRootEntity): Boolean = try { journal.save(record(root)); true }
    catch (error: CancellationException) { throw error } catch (_: Exception) { false }

    /** 将内部根映射为 UI 投影，旧错误不会覆盖新会话的 Running。 */
    private fun publish(error: SessionError? = null) {
        val root = active ?: return
        mutableState.value = SessionView(SessionPhase.valueOf(root.state.name), root.sessionId, publicTarget(root.target),
            startedAt, root.lastConfirmedWriteNanos, error ?: root.failure?.let { publicFailure(it) })
    }

    /** 领域根到可持久化日志；只在根处于活动阶段调用。 */
    private fun record(root: MockLocationSessionAggregateRootEntity): SessionJournalRecord = SessionJournalRecord(
        root.sessionId, root.generation, publicTarget(root.target), SessionPhase.valueOf(root.state.name),
        root.lastConfirmedWriteNanos, root.failure?.let { publicFailure(it) })

    /** 校验本方目标 VO，不引用位置库的内部类型。 */
    private fun domainTarget(target: SessionTargetSnapshot): MockLocationTarget = MockLocationTarget(
        MockLocationCoordinate(target.coordinate.latitude, target.coordinate.longitude, CoordinateSystem.valueOf(target.coordinate.system.name)),
        target.displayName, target.sourceId, target.sourceRevision)

    /** 导出冻结坐标及来源版本；始终保留原参考系。 */
    private fun publicTarget(target: MockLocationTarget): SessionTargetSnapshot = SessionTargetSnapshot(
        SessionCoordinate(target.coordinate.latitude, target.coordinate.longitude, SessionCoordinateSystem.valueOf(target.coordinate.coordinateSystem.name)),
        target.displayName, target.sourceLocationId, target.sourceRevision)

    /** 平台错误到领域原因；存储/服务等其他故障属于引擎运行失败。 */
    private fun domainFailure(error: SessionError?): SessionFailureReason? = when (error) {
        null -> null
        SessionError.PermissionDenied -> SessionFailureReason.PermissionDenied
        SessionError.ProviderUnsupported -> SessionFailureReason.ProviderUnsupported
        SessionError.Interrupted -> SessionFailureReason.Interrupted
        SessionError.CleanupRequired -> SessionFailureReason.CleanupFailure
        else -> SessionFailureReason.EngineFailure
    }

    /** 领域原因到公开分类，UI 不导入领域枚举。 */
    private fun publicFailure(reason: SessionFailureReason): SessionError = when (reason) {
        SessionFailureReason.PermissionDenied -> SessionError.PermissionDenied
        SessionFailureReason.ProviderUnsupported -> SessionError.ProviderUnsupported
        SessionFailureReason.EngineFailure -> SessionError.EngineFailure
        SessionFailureReason.CleanupFailure -> SessionError.CleanupRequired
        SessionFailureReason.Interrupted -> SessionError.Interrupted
    }
}
