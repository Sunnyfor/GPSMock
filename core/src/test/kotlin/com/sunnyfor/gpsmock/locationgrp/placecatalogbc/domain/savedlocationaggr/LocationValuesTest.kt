package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.GeographicCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.LocationName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 收藏目录值对象测试，覆盖名称规范化、码点长度、坐标边界及复制校验。 */
class LocationValuesTest {
    /** 名称去除首尾空白后按规范化文本判等，包括制表符和换行输入。 */
    @Test
    fun namesAreTrimmedAndCompareByNormalizedValue() {
        assertEquals(LocationName.of("  家  "), LocationName.of("家"))
        assertEquals("家", LocationName.of("\t 家 \n").value)
    }

    /** 名称上限按 Unicode 码点计数：30 个基本 emoji 有效，31 个被拒绝。 */
    @Test
    fun nameLimitCountsUnicodeCodePointsInsteadOfUtf16Length() {
        val thirtyEmoji = "📍".repeat(30)
        assertEquals(thirtyEmoji, LocationName.of(thirtyEmoji).value)
        assertThrows(IllegalArgumentException::class.java) { LocationName.of("📍".repeat(31)) }
    }

    /** 空名称、纯空白及超长名称均被拒绝。 */
    @Test
    fun blankAndOversizedNamesAreRejected() {
        for (name in listOf("", " \t\n ", "a".repeat(31))) {
            assertThrows(IllegalArgumentException::class.java) { LocationName.of(name) }
        }
    }

    /** 零点及经纬度上下限端点均是有效坐标。 */
    @Test
    fun zeroAndBoundaryCoordinatesAreValid() {
        for ((latitude, longitude) in listOf(0.0 to 0.0, -90.0 to -180.0, 90.0 to 180.0)) {
            val coordinate = GeographicCoordinate(latitude, longitude, CoordinateSystem.WGS84)
            assertEquals(latitude, coordinate.latitude, 0.0)
            assertEquals(longitude, coordinate.longitude, 0.0)
        }
    }

    /** 经纬度相同但参考系不同的坐标不是同一个值。 */
    @Test
    fun coordinateSystemIsPartOfTheValue() {
        assertNotEquals(
            GeographicCoordinate(1.0, 2.0, CoordinateSystem.WGS84),
            GeographicCoordinate(1.0, 2.0, CoordinateSystem.GCJ02)
        )
    }

    /** 经纬度构造拒绝 NaN、正负无穷及超出范围的输入。 */
    @Test
    fun latitudeAndLongitudeRejectNonFiniteAndOutOfRangeValues() {
        for (latitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -90.1, 90.1)) {
            assertThrows(IllegalArgumentException::class.java) {
                GeographicCoordinate(latitude, 0.0, CoordinateSystem.WGS84)
            }
        }
        for (longitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -180.1, 180.1)) {
            assertThrows(IllegalArgumentException::class.java) {
                GeographicCoordinate(0.0, longitude, CoordinateSystem.WGS84)
            }
        }
    }

    /** 数据类复制仍校验坐标，不能用 copy 绕过有限性与范围限制。 */
    @Test
    fun dataClassCopyCannotBypassCoordinateValidation() {
        val valid = GeographicCoordinate(0.0, 0.0, CoordinateSystem.GCJ02)
        assertThrows(IllegalArgumentException::class.java) { valid.copy(latitude = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(longitude = 181.0) }
    }
}
