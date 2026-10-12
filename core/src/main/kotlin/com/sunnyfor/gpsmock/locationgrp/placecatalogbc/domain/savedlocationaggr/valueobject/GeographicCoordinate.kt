package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject

/**
 * 收藏位置的不可变地理坐标，以经纬度和显式坐标系共同判定值相等。
 *
 * 构造及数据类复制均校验经纬度；零值与范围端点有效，不推断地址或自动转换坐标系。
 *
 * @throws IllegalArgumentException 经纬度非有限数，或超出对应成员规定的范围。
 */
internal data class GeographicCoordinate(
    /** 纬度，单位为度；须为有限数，范围为 [-90, 90]。 */
    val latitude: Double,

    /** 经度，单位为度；须为有限数，范围为 [-180, 180]。 */
    val longitude: Double,

    /** 当前经纬度数值所属的参考系，不表示已经完成转换。 */
    val coordinateSystem: CoordinateSystem
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}
