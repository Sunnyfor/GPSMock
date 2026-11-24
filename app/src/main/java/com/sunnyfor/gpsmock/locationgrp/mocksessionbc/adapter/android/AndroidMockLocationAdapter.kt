package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.Looper
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
 * 按参考 APK 的 network → gps 顺序注册和写入，两路首次写入完成才返回成功。
 * 注册意图先持久化，进程恢复按账本清理，包含历史版本登记的系统 fused 通道。
 * 调用由协调器串行任务安排，停止须先等待旧调用结束；本类不创建写入循环。
 */
internal class AndroidMockLocationAdapter(

    /** 只持有 Application 上下文。 */
    private val context: Context,

    /** 持久化本方 provider 所有权的私有数据库。 */
    private val database: GpsMockDatabase
) : MockEnginePort {

    /** 用于测试 provider 的注册、写入及本方 GPS 请求。 */
    private val manager = context.getSystemService(LocationManager::class.java)

    /** prepare 时转换一次的 WGS-84 固定目标；清理后置空。 */
    private var coordinate: EngineCoordinate? = null

    /**
     * 只用于维持本方测试 GPS 请求的监听器；不解析、保存或输出任何位置事实。
     * 注册前必须已覆盖 GPS 测试 provider，停止时先移除此订阅再恢复真实 provider。
     */
    private val gpsKeepAliveListener = object : LocationListener {
        /** 丢弃位置回调，不把接收结果当成写入确认或真实设备位置。 */
        override fun onLocationChanged(location: Location) = Unit

        /** provider 状态不替代协调器的实际写入结果。 */
        @Deprecated("Legacy framework callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        /** 不根据启用通知更改会话状态。 */
        override fun onProviderEnabled(provider: String) = Unit

        /** 不根据禁用通知自行重启 provider 或模拟。 */
        override fun onProviderDisabled(provider: String) = Unit
    }

    /** 本监听器的清理责任；平台部分注册失败后也保留移除操作。 */
    @SuppressLint("MissingPermission") // subscribeToOwnedGps 检查已有位置权限。
    private val gpsSubscription = MockGpsSubscriptionAdapter(
        register = {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 100f,
                gpsKeepAliveListener, Looper.getMainLooper())
        },
        unregister = { manager.removeUpdates(gpsKeepAliveListener) }
    )

    /** 用户显式运行期间的限时 CPU 唤醒锁；写入检查每 30 秒续期，清理必须释放。 */
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSMock:fixed-position").apply { setReferenceCounted(false) }

    /** 下一次唤醒锁续期的单调毫秒阈值，避免每 400ms 重复操作电源服务。 */
    private var nextWakeRenewMillis = 0L

    /** 确认设备有 GPS 后准备前台服务，再按参考顺序注册两个通道、持有订阅并完成首次写入。 */
    override suspend fun prepareAndWrite(target: SessionTargetSnapshot): EngineWriteResult {
        return try {
            rejectKnownAuthorizationDenial()
            require(LocationManager.GPS_PROVIDER in manager.allProviders) { "GPS provider unavailable" }
            coordinate = CoordinateConversionAdapter.toWgs84(target.coordinate)
            withContext(Dispatchers.Main.immediate) {
                val ready = CompletableDeferred<Unit>()
                SessionServiceHost.ready = ready
                ContextCompat.startForegroundService(context, Intent(context, MockLocationForegroundService::class.java))
                withTimeout(8_000) { ready.await() }
            }
            withContext(Dispatchers.IO) {
                for (provider in WRITE_PROVIDERS) {
                    currentCoroutineContext().ensureActive()
                    database.transaction { db ->
                        check(db.insertWithOnConflict("owned_providers", null, ContentValues().apply { put("name", provider) },
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                    }
                    registerProvider(provider)
                    if (!manager.isProviderEnabled(provider)) manager.setTestProviderEnabled(provider, true)
                }
                subscribeToOwnedGps()
                writeNow()
            }
        } catch (_: TimeoutCancellationException) { EngineWriteResult(error = SessionError.EngineFailure) }
        catch (error: CancellationException) { throw error }
        catch (_: SecurityException) { EngineWriteResult(error = SessionError.PermissionDenied) }
        catch (_: IllegalArgumentException) { EngineWriteResult(error = SessionError.ProviderUnsupported) }
        catch (_: Exception) { EngineWriteResult(error = SessionError.EngineFailure) }
    }

    /**
     * 对照参考 APK 持有本方 GPS 请求，使用用户已有的大致或精准位置权限。
     *
     * 调用前本次全部测试 provider 必须已准备；不读取真实 GPS、不请求后台位置权限。
     * 未授权时仍允许既有纯模拟流程，不为兼容性订阅阻断离线模拟；平台撤权竞态由启动补偿清理。
     * 后台活跃程度仍受系统权限/服务类型约束，不能仅凭注册成功承诺微信兼容。
     */
    private fun subscribeToOwnedGps() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            gpsSubscription.acquire()
        }
    }

    /**
     * 在新旧 Android 注册签名下使用同一组参考能力。
     * GPS 声明卫星和小区依赖，支持海拔、速度及方位，采用高功耗与 COARSE 常量。
     * Network 不声明这些依赖和扩展能力，采用低功耗与 FINE 常量。
     * 能力声明和每轮 [writeNow] 的模拟样本字段各自独立。
     */
    @SuppressLint("InlinedApi")
    private fun registerProvider(provider: String) {
        val isGps = provider == LocationManager.GPS_PROVIDER
        val power = if (isGps) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW
        val accuracy = if (isGps) ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE
        if (Build.VERSION.SDK_INT >= 31) {
            manager.addTestProvider(provider, ProviderProperties.Builder()
                .setHasSatelliteRequirement(isGps)
                .setHasCellRequirement(isGps)
                .setHasAltitudeSupport(isGps)
                .setHasSpeedSupport(isGps)
                .setHasBearingSupport(isGps)
                .setPowerUsage(power)
                .setAccuracy(accuracy)
                .build())
        } else {
            manager.addTestProvider(provider, false, isGps, isGps, false, isGps, isGps, isGps, power, accuracy)
        }
    }

    /** 每轮按 [writeNow] 生成一份带新时间的固定模拟样本。 */
    override suspend fun write(): EngineWriteResult = withContext(Dispatchers.IO) {
        try { writeNow() }
        catch (_: SecurityException) { EngineWriteResult(error = SessionError.PermissionDenied) }
        catch (_: IllegalArgumentException) { EngineWriteResult(error = SessionError.ProviderUnsupported) }
        catch (_: Exception) { EngineWriteResult(error = SessionError.EngineFailure) }
    }

    /**
     * 生成参考 APK 的固定模拟样本，再向两个测试通道依序发布同一对象。
     * 精度、海拔、方位和卫星数均为模拟样本参数，速度保持未设置。
     * 系统测试 API 负责给接收端标记 mock。Android 15 AOSP 会记录 network 通道与 gps 样本来源不同的事件。
     */
    private fun writeNow(): EngineWriteResult {
        rejectKnownAuthorizationDenial()
        val value = checkNotNull(coordinate)
        val nowMillis = SystemClock.elapsedRealtime()
        if (!wakeLock.isHeld || nowMillis >= nextWakeRenewMillis) {
            wakeLock.acquire(60_000L)
            nextWakeRenewMillis = nowMillis + 30_000L
        }
        val sample = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = value.latitude
            longitude = value.longitude
            accuracy = 1f
            altitude = 30.0
            bearing = 1f
            extras = Bundle().apply { putInt("satellites", 7) }
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        for (provider in WRITE_PROVIDERS) {
            manager.setTestProviderLocation(provider, sample)
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

    /**
     * 先移除本方 GPS 订阅，再移除账本中的测试 provider；任一失败保留对应责任供重试。
     * 订阅移除失败时不恢复真实 provider，防止残留订阅继续激活真实 GPS。
     */
    override suspend fun cleanup(): Boolean = withContext(Dispatchers.IO) {
        coordinate = null
        nextWakeRenewMillis = 0L
        if (wakeLock.isHeld) wakeLock.release()
        if (!gpsSubscription.release()) return@withContext false
        val owned = database.transaction { db ->
            db.query("owned_providers", arrayOf("name"), null, null, null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
        var success = true
        for (provider in owned) {
            try {
                // 恢复时设备能力可能已经变化；按固定安全白名单清理账本，而非重新发现的本次集合。
                check(provider in SUPPORTED_SYSTEM_PROVIDERS)
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

    /** 参考包写入顺序及升级恢复所需的历史通道名称。 */
    companion object {
        /** 参考 APK 的注册和写入顺序，两个通道均完成写入才确认。 */
        private val WRITE_PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        /** 仅清理本方账本中的通道，含历史版本可能遗留的 fused。 */
        private val SUPPORTED_SYSTEM_PROVIDERS = WRITE_PROVIDERS.toSet() + "fused"
    }
}
