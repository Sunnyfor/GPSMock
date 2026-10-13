package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import java.util.UUID

/**
 * 用户主动启动的模拟写入前台宿主；不读取真实位置、不自动重启、不提供外部启动入口。
 * 业务权威仍为唯一协调器；通知停止携带原会话身份，不能停止后来的会话。
 */
class MockLocationForegroundService : Service() {
    /** 仅用于通知状态观察和停止命令的主线程任务范围。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 本宿主曾承载的会话身份；销毁回调不能误用新会话的当前身份。 */
    private var hostedSessionId: String? = null

    /** 唯一通知观察任务，重复启动服务不能增加重复收集器。 */
    private var notificationObserver: Job? = null

    /** 服务创建后立即建立通知渠道；API 23–25 使用旧通知 API。 */
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "模拟定位运行状态", NotificationManager.IMPORTANCE_LOW))
        }
    }

    /** 及时进入前台后才完成准备确认；不通过 START_STICKY 自动恢复模拟运行。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val id = intent.getStringExtra(EXTRA_SESSION_ID)
            if (id != null) serviceScope.launch { SessionServiceHost.session.stop(UUID.randomUUID().toString(), id) }
            return START_NOT_STICKY
        }
        try {
            SessionServiceHost.expectedStop = false
            hostedSessionId = SessionServiceHost.session.state.value.sessionId
            val initial = notification("正在启动", hostedSessionId)
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID, initial)
            SessionServiceHost.ready?.complete(Unit)
            notificationObserver?.cancel()
            notificationObserver = serviceScope.launch {
                SessionServiceHost.session.state.distinctUntilChangedBy { it.phase to it.sessionId }.collect { view ->
                    val text = when (view.phase) {
                        SessionPhase.Starting -> "正在启动"
                        SessionPhase.Running -> "固定位置模拟中 · 每 400ms 尝试写入"
                        SessionPhase.Stopping -> "正在停止与清理"
                        SessionPhase.CleanupRequired -> "清理未完成，请返回应用重试"
                        SessionPhase.Idle -> "已停止"
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, view.sessionId))
                }
            }
        } catch (error: Exception) {
            SessionServiceHost.ready?.completeExceptionally(error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** 仅本应用内部调用，服务不提供 Binder 业务接口。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 非清理销毁也请求停止；进程被直接杀死时由下次日志恢复路径处理。 */
    override fun onDestroy() {
        if (!SessionServiceHost.expectedStop) {
            hostedSessionId?.let { id ->
                // 此任务属于 Application 范围，不能在随服务销毁的 scope 中发出后再立即取消。
                interruptionCallback?.invoke(id)
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 通知不写坐标；回应用与停止均为显式、不可变 PendingIntent。 */
    private fun notification(text: String, sessionId: String?): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("定位助手")
            .setContentText(text).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
        if (sessionId != null) {
            val stop = Intent(this, MockLocationForegroundService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_SESSION_ID, sessionId)
            // 每个会话独立 PendingIntent，旧通知不能被新会话的 extras 覆盖。
            stop.data = android.net.Uri.parse("gpsmock://stop/$sessionId")
            val action = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "停止模拟", action).build())
        }
        return builder.build()
    }

    /** 框架动作与渠道身份，均不携带用户位置。 */
    companion object {

        /** 仅供内部通知使用的停止动作。 */
        const val ACTION_STOP = "com.sunnyfor.gpsmock.STOP_SESSION"

        /** 明确关联通知发出时的会话。 */
        const val EXTRA_SESSION_ID = "session_id"

        /** 系统通知渠道身份。 */
        private const val CHANNEL = "mock-session"

        /** 唯一前台通知身份。 */
        private const val NOTIFICATION_ID = 101

        /** wiring 注入的 Application 生命周期停止入口。 */
        internal var interruptionCallback: ((String) -> Unit)? = null
    }
}
