package com.sunnyfor.gpsmock.platform.ui.mapui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 一次设备定位的安全结果；不会将默认地图中心或模拟点当作当前位置。 */
internal sealed interface DeviceLocationResult {
    /** 已取得新鲜、有效且未带 mock 标记的系统 WGS-84 坐标。 */
    data class Located(
        /** 仅用于相机居中，不自动转换成业务目标。 */
        val coordinate: MapCoordinate,

        /** 该次系统结果的水平误差估计，单位米，不宣称为精确测量值。 */
        val accuracyMeters: Float
    ) : DeviceLocationResult

    /** 无可用位置；只提供安全恢复提示，不输出设备坐标或异常原文。 */
    data class Unavailable(
        /** 权限、系统开关、超时或模拟来源的中文说明。 */
        val message: String
    ) : DeviceLocationResult
}

/** 一次性前台定位端口；取消必须释放系统请求，不能长期监听或自行申请权限。 */
internal fun interface DeviceLocationClient {
    /** 调用方须先获得用途同意；实现再次检查系统权限，失败不抛业务成功。 */
    suspend fun locate(): DeviceLocationResult
}

/** 只调整相机的有代次请求；同一坐标再次主动居中仍具有新的身份。 */
internal data class MapCameraRequest(
    /** 本页面单调递增身份，不持久保存；用于阻止重组反复抢占视野。 */
    val sequence: Long,

    /** 原系统 WGS-84 坐标，高德绘制时单向转换。 */
    val coordinate: MapCoordinate,

    /** 地图缩放级别；大致位置采用较宽视野。 */
    val zoom: Float
)

/**
 * 用现有 AndroidX 包装系统定位，不初始化高德定位客户端、不请求后台定位。
 *
 * 单次总等待上限 12 秒；多个已启用 provider 同时竞争首个有效结果，结束或取消即释放。
 * 权限检查和 OS 调用仍有竞态，SecurityException 按拒绝处理；协程取消继续传播。
 */
internal class AndroidDeviceLocationAdapter(
    /** 只持有应用上下文，定位回调不延长 Activity 生命周期。 */
    private val context: Context
) : DeviceLocationClient {
    /** 校验新鲜度、mock 标记、精度和经纬度，不读取陈旧缓存来假称定位成功。 */
    @SuppressLint("MissingPermission")
    override suspend fun locate(): DeviceLocationResult {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !fine) return DeviceLocationResult.Unavailable("未授权定位，仍可搜索、选点或手输。")
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            if (!LocationManagerCompat.isLocationEnabled(manager)) return DeviceLocationResult.Unavailable("系统定位未开启，请开启后重试。")
            val providers = manager.getProviders(true).filter {
                it == LocationManager.NETWORK_PROVIDER || (fine && it == LocationManager.GPS_PROVIDER) || it == "fused"
            }
            if (providers.isEmpty()) return DeviceLocationResult.Unavailable("没有可用的定位服务，仍可直接选点。")
            withTimeoutOrNull<DeviceLocationResult>(12_000L) {
                suspendCancellableCoroutine<DeviceLocationResult> { continuation ->
                    val signals = providers.map { CancellationSignal() }
                    var pending = providers.size
                    var mockSeen = false
                    continuation.invokeOnCancellation { signals.forEach { it.cancel() } }
                    try { providers.forEachIndexed { index, provider ->
                        if (continuation.isActive) LocationManagerCompat.getCurrentLocation(manager, provider,
                            signals[index], ContextCompat.getMainExecutor(context)) { location ->
                            if (!continuation.isActive) return@getCurrentLocation
                            mockSeen = mockSeen || (location != null && LocationCompat.isMock(location))
                            val result = location?.toDeviceResult()
                            if (result != null) {
                                continuation.resume(result)
                                signals.forEach { it.cancel() }
                            } else if (--pending == 0) {
                                continuation.resume(DeviceLocationResult.Unavailable(if (mockSeen)
                                    "设备返回模拟位置，不能将它标为真实当前位置。" else "暂未取得有效位置，请稍后重试。"))
                                signals.forEach { it.cancel() }
                            }
                        }
                    } } catch (failure: RuntimeException) {
                        signals.forEach { it.cancel() }
                        throw failure
                    }
                }
            } ?: DeviceLocationResult.Unavailable("定位超时，仍可搜索、选点或手输。")
        } catch (_: SecurityException) {
            DeviceLocationResult.Unavailable("定位权限已被收回，请重新授权后重试。")
        } catch (_: IllegalArgumentException) {
            DeviceLocationResult.Unavailable("定位服务暂不可用，请稍后重试。")
        }
    }
}

/** 使用单调时钟核对系统结果年龄（最多 30 秒）；mock 或无效精度不允许作为设备位置。 */
internal fun Location.toDeviceResult(): DeviceLocationResult.Located? {
    val age = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
    if (LocationCompat.isMock(this) || age !in 0..30_000_000_000L || !hasAccuracy() || !accuracy.isFinite() || accuracy < 0 ||
        !latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return DeviceLocationResult.Located(MapCoordinate(latitude, longitude, MapCoordinateSystem.WGS84), accuracy)
}
