package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.coordinate

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 转换回归只验证数学残差与输入语义，不宣称实地测量精度。 */
class CoordinateConversionAdapterTest {
    /** WGS-84 原值直用，不能被地图坐标转换重复偏移。 */
    @Test fun wgs84IsUnchanged() {
        val actual = CoordinateConversionAdapter.toWgs84(SessionCoordinate(39.9, 116.4, SessionCoordinateSystem.WGS84))
        assertEquals(39.9, actual.latitude, 0.0)
        assertEquals(116.4, actual.longitude, 0.0)
    }
    /** 多个境内基准的正反算残差低于 1e-7 度；输入仍是原始 GCJ-02。 */
    @Test fun inverseMatchesForwardModelAcrossReferencePoints() {
        for (point in listOf(EngineCoordinate(39.9, 116.4), EngineCoordinate(31.2, 121.4), EngineCoordinate(22.5, 114.0), EngineCoordinate(30.6, 104.0))) {
            val gcj = CoordinateConversionAdapter.toGcj02(point)
            val recovered = CoordinateConversionAdapter.toWgs84(SessionCoordinate(gcj.latitude, gcj.longitude, SessionCoordinateSystem.GCJ02))
            assertEquals(point.latitude, recovered.latitude, 1e-7)
            assertEquals(point.longitude, recovered.longitude, 1e-7)
        }
    }
    /** 零点与境外坐标不偏移，范围端点可作为合法 WGS-84 输入。 */
    @Test fun zeroAndOutsideBoundsStayUnchanged() {
        val zero = CoordinateConversionAdapter.toWgs84(SessionCoordinate(0.0, 0.0, SessionCoordinateSystem.GCJ02))
        assertEquals(EngineCoordinate(0.0, 0.0), zero)
        assertEquals(EngineCoordinate(90.0, 180.0), CoordinateConversionAdapter.toWgs84(SessionCoordinate(90.0, 180.0, SessionCoordinateSystem.WGS84)))
    }
    /** 非有限坐标必须拒绝，不能落入境外快速通道而绕过校验。 */
    @Test(expected = IllegalArgumentException::class) fun rejectsNaN() {
        CoordinateConversionAdapter.toWgs84(SessionCoordinate(Double.NaN, 0.0, SessionCoordinateSystem.GCJ02))
    }
}
