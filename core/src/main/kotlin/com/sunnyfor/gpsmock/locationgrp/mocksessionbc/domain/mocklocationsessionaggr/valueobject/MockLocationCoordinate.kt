package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 模拟会话冻结的原始坐标，按经纬度与参考系共同判定值相等。
 *
 * 构造及数据类复制均校验经纬度，允许零值和范围端点。
 * 本对象不改变参考系；引擎需要 WGS-84 时，由适配器承担转换责任。
 *
 * @throws IllegalArgumentException 经纬度非有限数，或超出对应成员规定的范围。
 */
internal data class MockLocationCoordinate(
    /** 纬度，单位为度；须为有限数，范围为 [-90, 90]。 */
    val latitude: Double,

    /** 经度，单位为度；须为有限数，范围为 [-180, 180]。 */
    val longitude: Double,

    /** 输入经纬度所属的原始参考系，不隐含引擎输出已转换。 */
    val coordinateSystem: CoordinateSystem
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}
