package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.coordinate.CoordinateConversionAdapter
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.coordinate.EngineCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application.EngineWriteResult
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.application.MockEnginePort
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionError
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionTargetSnapshot
import com.sunnyfor.gpsmock.platform.storage.GpsMockDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 真正的 Android 固定位置注入适配器，依赖系统模拟应用授权，不隐藏 mock 标记。
 *
 * gps/network 均准备并写入成功才确认；注册意图先持久化，进程恢复按账本清理。
 * 调用由协调器串行任务安排，停止须先等待旧调用结束；本类不创建写入循环。
 */
internal class AndroidMockLocationAdapter(

    /** 只持有 Application 上下文。 */
    private val context: Context,

    /** 持久化本方 provider 所有权的私有数据库。 */
    private val database: GpsMockDatabase
) : MockEnginePort {

    /** 不访问真实定位数据，只用于测试 provider 写入。 */
    private val manager = context.getSystemService(LocationManager::class.java)

    /** prepare 时转换一次的 WGS-84 固定目标；清理后置空。 */
    private var coordinate: EngineCoordinate? = null
    /** 用户显式运行期间的限时 CPU 唤醒锁；写入检查每 30 秒续期，清理必须释放。 */
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSMock:fixed-position").apply { setReferenceCounted(false) }

    /** 下一次唤醒锁续期的单调毫秒阈值，避免每 400ms 重复操作电源服务。 */
    private var nextWakeRenewMillis = 0L

    /** 服务进入前台后再注册全部必要 provider，首次写入完成才返回运行事实。 */
    override suspend fun prepareAndWrite(target: SessionTargetSnapshot): EngineWriteResult {
        return try {
            rejectKnownAuthorizationDenial()
            coordinate = CoordinateConversionAdapter.toWgs84(target.coordinate)
            withContext(Dispatchers.Main.immediate) {
                val ready = CompletableDeferred<Unit>()
                SessionServiceHost.ready = ready
                ContextCompat.startForegroundService(context, Intent(context, MockLocationForegroundService::class.java))
                withTimeout(8_000) { ready.await() }
            }
            withContext(Dispatchers.IO) {
                for (provider in PROVIDERS) {
                    currentCoroutineContext().ensureActive()
                    database.transaction { db ->
                        check(db.insertWithOnConflict("owned_providers", null, ContentValues().apply { put("name", provider) },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                    }
                    registerProvider(provider)
                    manager.setTestProviderEnabled(provider, true)
                }
                writeNow()
            }
        } catch (_: TimeoutCancellationException) { EngineWriteResult(error = SessionError.EngineFailure) }
        catch (error: CancellationException) { throw error }
        catch (_: SecurityException) { EngineWriteResult(error = SessionError.PermissionDenied) }
        catch (_: IllegalArgumentException) { EngineWriteResult(error = SessionError.ProviderUnsupported) }
        catch (_: Exception) { EngineWriteResult(error = SessionError.EngineFailure) }
    }

    /**
     * Android 12 起使用明确属性对象；旧签名沿用相同公开能力常量。
     * 常量的整数值会内联，不在旧系统读取 API 31 字段；属性对象只在版本门禁内创建。
     */
    @SuppressLint("InlinedApi")
    private fun registerProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            manager.addTestProvider(provider, ProviderProperties.Builder()
                .setHasSatelliteRequirement(provider == LocationManager.GPS_PROVIDER)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE).build())
        } else {
            // 使用 SDK IntDef 要求的命名常量；编译后都是整数，不执行新 API 字段访问。
            manager.addTestProvider(provider, false, provider == LocationManager.GPS_PROVIDER, false, false,
                false, false, false, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE)
        }
    }

    /** 在后台写入，每一轮都生成新墙钟时间和单调时间，不虚构卫星、速度或海拔。 */
    override suspend fun write(): EngineWriteResult = withContext(Dispatchers.IO) {
        try { writeNow() }
        catch (_: SecurityException) { EngineWriteResult(error = SessionError.PermissionDenied) }
        catch (_: IllegalArgumentException) { EngineWriteResult(error = SessionError.ProviderUnsupported) }
        catch (_: Exception) { EngineWriteResult(error = SessionError.EngineFailure) }
    }

    /** 同步完成一整轮写入；5m 是明确注入参数，不声称为真实测量精度。 */
    private fun writeNow(): EngineWriteResult {
        rejectKnownAuthorizationDenial()
        val value = checkNotNull(coordinate)
        val nowMillis = SystemClock.elapsedRealtime()
        if (!wakeLock.isHeld || nowMillis >= nextWakeRenewMillis) {
            wakeLock.acquire(60_000L)
            nextWakeRenewMillis = nowMillis + 30_000L
        }
        for (provider in PROVIDERS) {
            manager.setTestProviderLocation(provider, Location(provider).apply {
                latitude = value.latitude; longitude = value.longitude; accuracy = 5f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
        }
        rejectKnownAuthorizationDenial()
        return EngineWriteResult(SystemClock.elapsedRealtimeNanos())
    }

    /**
     * 明确拒绝必须中止；部分旧系统会静默忽略未授权的 void 写入，不能因此确认 Running。
     *
     * 此检查不授予权限、不替代 provider 结果。无法检查时保留 Unknown，交给真实平台调用验证；
     * 首轮准备和每轮写入前后检查，授权撤销后不继续发布成功确认。
     */
    private fun rejectKnownAuthorizationDenial() {
        val mode = try {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            if (Build.VERSION.SDK_INT >= 29) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            else appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
        } catch (_: Exception) { return }
        if (mode != AppOpsManager.MODE_ALLOWED) throw SecurityException("Mock location app not authorized")
    }

    /** 只移除账本中的本方 provider；任一失败保留其账本供重试，绝不仅清空内存。 */
    override suspend fun cleanup(): Boolean = withContext(Dispatchers.IO) {
        coordinate = null
        nextWakeRenewMillis = 0L
        if (wakeLock.isHeld) wakeLock.release()
        val owned = database.transaction { db ->
            db.query("owned_providers", arrayOf("name"), null, null, null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
        var success = true
        for (provider in owned) {
            try {
                check(provider in PROVIDERS)
                manager.removeTestProvider(provider)
                database.transaction { it.delete("owned_providers", "name=?", arrayOf(provider)) }
            } catch (_: Exception) { success = false }
        }
        if (success) withContext(Dispatchers.Main.immediate) {
            SessionServiceHost.expectedStop = true
            context.stopService(Intent(context, MockLocationForegroundService::class.java))
            SessionServiceHost.ready = null
        }
        success
    }

    /** 固定必要 provider 集合；不伪造 fused 等其他实现。 */
    companion object {
        /** 首次与周期写入必须全部成功的测试提供者。 */
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
