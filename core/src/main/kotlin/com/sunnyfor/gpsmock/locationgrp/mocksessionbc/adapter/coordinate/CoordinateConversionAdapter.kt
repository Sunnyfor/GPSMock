package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.coordinate

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 引擎已经转换好的 WGS-84 坐标，不含业务来源或地图类型。 */
data class EngineCoordinate(

    /** WGS-84 纬度，十进制度。 */
    val latitude: Double,

    /** WGS-84 经度，十进制度。 */
    val longitude: Double
)

/**
 * 纯 Kotlin 坐标适配：WGS-84 原样输出，GCJ-02 用正向模型迭代反算一次。
 *
 * 正向偏移公式改写自 MIT 的 wandergis/coordtransform，许可证随 APK assets 分发。
 * 反算要求正向残差不超过 1e-7 度；这是算法自洽指标，不是实测地理精度承诺。
 * 适用范围采用参考算法的矩形近似，不代表行政国界；范围外不偏移。
 */
object CoordinateConversionAdapter {
    /** 转换一次完整原坐标；无效值或反算不收敛时拒绝，不能以错误坐标继续启动。 */
    fun toWgs84(input: SessionCoordinate): EngineCoordinate {
        require(input.latitude.isFinite() && input.latitude in -90.0..90.0)
        require(input.longitude.isFinite() && input.longitude in -180.0..180.0)
        if (input.system == SessionCoordinateSystem.WGS84 || !inside(input.latitude, input.longitude)) {
            return EngineCoordinate(input.latitude, input.longitude)
        }
        var latitude = input.latitude
        var longitude = input.longitude
        repeat(20) {
            val forward = toGcj02(EngineCoordinate(latitude, longitude))
            val latitudeError = forward.latitude - input.latitude
            val longitudeError = forward.longitude - input.longitude
            if (max(abs(latitudeError), abs(longitudeError)) <= 1e-7) return EngineCoordinate(latitude, longitude)
            latitude -= latitudeError
            longitude -= longitudeError
        }
        error("Coordinate inverse did not converge")
    }

    /** 计算正向偏移，用于迭代和回归基准；不参与领域状态变更。 */
    fun toGcj02(input: EngineCoordinate): EngineCoordinate {
        if (!inside(input.latitude, input.longitude)) return input
        val x = input.longitude - 105.0
        val y = input.latitude - 35.0
        val common = (20 * sin(6 * x * PI) + 20 * sin(2 * x * PI)) * 2 / 3
        val latOffset = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x)) + common +
            (20 * sin(y * PI) + 40 * sin(y * PI / 3)) * 2 / 3 + (160 * sin(y * PI / 12) + 320 * sin(y * PI / 30)) * 2 / 3
        val lonOffset = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x)) + common +
            (20 * sin(x * PI) + 40 * sin(x * PI / 3)) * 2 / 3 + (150 * sin(x * PI / 12) + 300 * sin(x * PI / 30)) * 2 / 3
        val radians = input.latitude * PI / 180
        val eccentricity = 0.00669342162296594323
        val semiMajorAxis = 6378245.0
        val magic = 1 - eccentricity * sin(radians).pow(2)
        return EngineCoordinate(input.latitude + latOffset * 180 / (semiMajorAxis * (1 - eccentricity) / magic.pow(1.5) * PI),
            input.longitude + lonOffset * 180 / (semiMajorAxis / sqrt(magic) * cos(radians) * PI))
    }

    /** 参考模型的矩形适用范围；零坐标和境外坐标原样输出。 */
    private fun inside(latitude: Double, longitude: Double): Boolean =
        longitude > 73.66 && longitude < 135.05 && latitude > 3.86 && latitude < 53.55
}
