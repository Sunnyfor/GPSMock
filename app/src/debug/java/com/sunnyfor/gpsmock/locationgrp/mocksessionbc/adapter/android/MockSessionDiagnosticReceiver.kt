package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.StartTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

/**
 * 仅 Debug 包提供的显式设备验收入口，通过公开契约恢复用户已选定的测试目标。
 *
 * Manifest 的系统 DUMP 权限限定调用方；Release 不包含此类或组件。
 * 不自动重放旧日志、不抢占已有会话、不修改收藏或系统授权；不输出坐标与异常原文。
 * 调用前须把本应用打开到前台，满足正常前台服务启动条件，不能借此绕过后台限制。
 */
class MockSessionDiagnosticReceiver : BroadcastReceiver() {
    /** 接收明确的验收命令，等待真实 Running 后返回状态；总等待限于 8.5 秒。 */
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_START) return
        val target = try { readApprovedTarget(intent.getStringExtra("approvedTarget")) }
        catch (_: Exception) { resultData = "INVALID_TARGET"; return }
        val pending = goAsync()
        diagnosticScope.launch {
            try {
                withTimeout(8_500L) {
                    val session = SessionServiceHost.session
                    // 只等待启动时的日志清理完成；不替用户重试清理，也不停止已有会话。
                    session.state.first {
                        it.phase == SessionPhase.Idle || it.phase == SessionPhase.Running || it.phase == SessionPhase.Starting
                    }
                    val accepted = session.start(UUID.randomUUID().toString(), target)
                    val rejection = accepted.error
                    if (rejection != null) {
                        pending.resultData = "REJECTED_${rejection.name}"
                    } else {
                        val outcome = session.state.first {
                            (it.sessionId == accepted.sessionId && it.phase == SessionPhase.Running) ||
                                it.phase == SessionPhase.CleanupRequired || it.phase == SessionPhase.Idle
                        }
                        pending.resultData = if (outcome.phase == SessionPhase.Running) "RUNNING" else "NOT_RUNNING"
                    }
                }
            } catch (_: TimeoutCancellationException) { pending.resultData = "TIMEOUT_INCONCLUSIVE" }
            catch (error: CancellationException) { pending.resultData = "CANCELLED"; throw error }
            catch (_: Exception) { pending.resultData = "FAILED" }
            finally { pending.finish() }
        }
    }

    /** 解析显式传入的原始坐标与参考系；输入大小受限，业务校验仍由公开启动契约执行。 */
    private fun readApprovedTarget(encoded: String?): StartTarget.Inline {
        require(encoded != null && encoded.length in 1..2_048)
        val json = JSONObject(String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8))
        return StartTarget.Inline(SessionCoordinate(json.getDouble("latitude"), json.getDouble("longitude"),
            SessionCoordinateSystem.valueOf(json.getString("system"))), json.getString("name"))
    }

    /** 本机调试协议与短时命令提交范围，不承担周期写入或业务状态权威。 */
    private companion object {
        /** 必须显式指定组件与本动作；没有 IntentFilter，不接收隐式广播。 */
        const val ACTION_START = "com.sunnyfor.gpsmock.debug.START_APPROVED_TARGET"

        /** 只运行短时验收提交；实际会话继续由 Application 的协调器管理。 */
        val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
