package com.sunnyfor.gpsmock.feature.position

import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 手输解析测试，覆盖明确的单位、坐标系及逐字段校验。 */
class CoordinateInputTest {
    /** 空白、文字、非有限数和越界纬度不能被替换成零值。 */
    @Test
    fun rejectsInvalidLatitudeIndependently() {
        listOf("", " ", "abc", "NaN", "Infinity", "90.01", "-90.01").forEach { text ->
            val result = validateCoordinateInput(text, "0", MapCoordinateSystem.WGS84)
            assertNull(result.coordinate)
            assertNotNull(result.latitudeError)
            assertNull(result.longitudeError)
        }
    }

    /** 经度独立校验，合法纬度不会显示错误。 */
    @Test
    fun rejectsInvalidLongitudeIndependently() {
        listOf("", "NaN", "-Infinity", "180.01", "-180.01").forEach { text ->
            val result = validateCoordinateInput("0", text, MapCoordinateSystem.GCJ02)
            assertNull(result.coordinate)
            assertNotNull(result.longitudeError)
            assertNull(result.latitudeError)
        }
    }

    /** 负数、首尾空白及零值合法，提交保留原坐标系而不执行转换。 */
    @Test
    fun acceptsZeroNegativeValuesAndPreservesSystem() {
        val zero = validateCoordinateInput(" 0 ", "-0", MapCoordinateSystem.GCJ02).coordinate!!
        assertEquals(0.0, zero.latitude, 0.0)
        assertEquals(MapCoordinateSystem.GCJ02, zero.system)
        val boundary = validateCoordinateInput("-90", "180", MapCoordinateSystem.WGS84).coordinate!!
        assertEquals(-90.0, boundary.latitude, 0.0)
        assertEquals(180.0, boundary.longitude, 0.0)
        assertEquals(MapCoordinateSystem.WGS84, boundary.system)
    }
}
