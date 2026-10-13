package com.sunnyfor.gpsmock.platform.ui.mapui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 地图门禁、坐标不变量与生命周期的纯 JVM 测试，不加载原生 SDK。 */
class MapSupportTest {
    /** Key、开启意愿与隐私同意的八种组合都遵循同一门禁规则。 */
    @Test
    fun gateRequiresAllPrerequisites() {
        listOf(false, true).forEach { key ->
            listOf(false, true).forEach { enabled ->
                listOf(false, true).forEach { consent ->
                    val expected = when {
                        !key -> OnlineMapGate.MissingKey
                        !enabled -> OnlineMapGate.Disabled
                        !consent -> OnlineMapGate.ConsentRequired
                        else -> OnlineMapGate.Ready
                    }
                    assertEquals(expected, resolveOnlineMapGate(key, enabled, consent))
                }
            }
        }
    }

    /** 地图快照接受两端边界和零点，拒绝非有限数与越界经纬度。 */
    @Test
    fun coordinateRejectsInvalidValuesWithoutRejectingZero() {
        MapCoordinate(0.0, 0.0, MapCoordinateSystem.WGS84)
        MapCoordinate(-90.0, 180.0, MapCoordinateSystem.GCJ02)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -90.1, 90.1).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { MapCoordinate(value, 0.0, MapCoordinateSystem.WGS84) }
        }
        assertThrows(IllegalArgumentException::class.java) { MapCoordinate(0.0, -180.1, MapCoordinateSystem.GCJ02) }
    }

    /** 重复生命周期事件及 Compose/Activity 双重释放不会重复调用 SDK。 */
    @Test
    fun lifecycleIsIdempotentAndCannotResumeAfterClose() {
        val events = mutableListOf<String>()
        val lifecycle = MapLifecycle({ events += "resume" }, { events += "pause" }, { events += "destroy" })
        lifecycle.pause()
        lifecycle.resume()
        lifecycle.resume()
        lifecycle.pause()
        lifecycle.pause()
        lifecycle.resume()
        lifecycle.close()
        lifecycle.close()
        lifecycle.resume()
        assertEquals(listOf("resume", "pause", "resume", "pause", "destroy"), events)
    }

    /** 即使暂停回调失败，最终关闭也执行销毁，之后不再次销毁。 */
    @Test
    fun closeDestroysEvenWhenPauseThrows() {
        var destroys = 0
        val lifecycle = MapLifecycle({}, { error("pause failed") }, { destroys++ })
        lifecycle.resume()
        assertThrows(IllegalStateException::class.java) { lifecycle.close() }
        lifecycle.close()
        assertEquals(1, destroys)
    }

    /** 已创建但未恢复的地图可直接释放，不发送多余暂停事件。 */
    @Test
    fun closeBeforeResumeOnlyDestroys() {
        val events = mutableListOf<String>()
        val lifecycle = MapLifecycle({ events += "resume" }, { events += "pause" }, { events += "destroy" })
        lifecycle.close()
        assertEquals(listOf("destroy"), events)
    }
}
