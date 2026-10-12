package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** 冻结目标及会话坐标的值对象测试，覆盖临时输入、来源配对、复制与坐标边界。 */
class MockLocationTargetTest {
    /** 有效的零点 WGS-84 坐标，供目标构造和复制场景复用。 */
    private val coordinate = MockLocationCoordinate(0.0, 0.0, CoordinateSystem.WGS84)

    /** 临时坐标允许没有名称和收藏来源，默认来源身份与版本同时为空。 */
    @Test
    fun inlineCoordinateNeedsNeitherNameNorSavedLocation() {
        val target = MockLocationTarget(coordinate)
        assertEquals("", target.displayName)
        assertNull(target.sourceLocationId)
        assertNull(target.sourceRevision)
    }

    /** 收藏来源记录身份与版本；复制修改产生新目标，原记录保持不变。 */
    @Test
    fun savedSourceIsAnImmutableIdentityAndRevisionPair() {
        val target = MockLocationTarget(coordinate, "家", "location-1", 3L)
        assertEquals("location-1", target.sourceLocationId)
        assertEquals(3L, target.sourceRevision)
        val edited = target.copy(displayName = "办公室", sourceRevision = 4L)
        assertEquals("家", target.displayName)
        assertEquals(3L, target.sourceRevision)
        assertEquals("办公室", edited.displayName)
    }

    /** 复制同样执行构造校验，拒绝单边来源、空白身份及非正版本。 */
    @Test
    fun partialOrInvalidSavedSourceIsRejectedIncludingThroughCopy() {
        val inline = MockLocationTarget(coordinate)
        val saved = MockLocationTarget(coordinate, "家", "location-1", 1L)
        assertThrows(IllegalArgumentException::class.java) { inline.copy(sourceLocationId = "location-1") }
        assertThrows(IllegalArgumentException::class.java) { inline.copy(sourceRevision = 1L) }
        assertThrows(IllegalArgumentException::class.java) { saved.copy(sourceLocationId = " ") }
        assertThrows(IllegalArgumentException::class.java) { saved.copy(sourceRevision = 0L) }
    }

    /** 显示名称未预先去除首尾空白时拒绝构造，不自动规范化。 */
    @Test
    fun unnormalizedDisplayNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { MockLocationTarget(coordinate, " 家 ") }
    }

    /** 会话坐标的构造与复制拒绝非有限数或越界经纬度。 */
    @Test
    fun ownCoordinateTypeRejectsInvalidInputAndCopy() {
        for (latitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -90.1, 90.1)) {
            assertThrows(IllegalArgumentException::class.java) {
                MockLocationCoordinate(latitude, 0.0, CoordinateSystem.WGS84)
            }
        }
        for (longitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -180.1, 180.1)) {
            assertThrows(IllegalArgumentException::class.java) {
                MockLocationCoordinate(0.0, longitude, CoordinateSystem.WGS84)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { coordinate.copy(latitude = 91.0) }
    }

    /** 经纬度端点有效，坐标系明确记录且可通过新值复制变更。 */
    @Test
    fun geographicLimitsAndReferenceSystemsRemainExplicit() {
        assertEquals(-90.0, MockLocationCoordinate(-90.0, -180.0, CoordinateSystem.WGS84).latitude, 0.0)
        assertEquals(180.0, MockLocationCoordinate(90.0, 180.0, CoordinateSystem.GCJ02).longitude, 0.0)
        assertEquals(CoordinateSystem.GCJ02, coordinate.copy(coordinateSystem = CoordinateSystem.GCJ02).coordinateSystem)
    }
}
