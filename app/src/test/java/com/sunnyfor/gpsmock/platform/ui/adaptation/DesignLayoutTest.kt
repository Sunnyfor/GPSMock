package com.sunnyfor.gpsmock.platform.ui.adaptation

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设计适配的本地计算测试，覆盖比例、窗口边界、分屏、高度及字体换算。 */
class DesignLayoutTest {
    /** 不同手机像素宽度均使用 360 逻辑基准，100 设计单位严格按宽度比例换算。 */
    @Test
    fun phoneWidthsKeepTheDesignProportion() {
        for (widthPx in listOf(720f, 1080f, 1440f)) {
            val layout = resolveDesignLayout(widthPx, 2400f, systemDensity = widthPx / 360f)
            assertEquals(WindowWidthClass.Compact, layout.widthClass)
            assertEquals(360f, layout.referenceWidth, 0f)
            assertEquals(widthPx * 100f / 360f, 100f * layout.density, 0.001f)
        }
    }

    /** 1080 像素三倍稿中的 300 px 与 360 逻辑稿中的 100 单位得到相同输出像素。 */
    @Test
    fun threeTimesPixelDraftAndLogicalDraftProduceTheSamePixels() {
        val layout = resolveDesignLayout(
            1440f, 2400f, systemDensity = 4f
        )
        assertEquals(300f * 1440f / 1080f, 100f * layout.density, 0.001f)
    }

    /** 按原始系统 dp 在各宽度阈值的两侧分类，不使用替换后的设计密度。 */
    @Test
    fun classifiesPhysicalWindowBeforeReplacingDensity() {
        val cases = listOf(
            599f to WindowWidthClass.Compact,
            600f to WindowWidthClass.Medium,
            839f to WindowWidthClass.Medium,
            840f to WindowWidthClass.Expanded,
            1199f to WindowWidthClass.Expanded,
            1200f to WindowWidthClass.Large,
            1599f to WindowWidthClass.Large,
            1600f to WindowWidthClass.ExtraLarge
        )
        for ((widthDp, expected) in cases) {
            val layout = resolveDesignLayout(widthDp * 2f, 1600f, systemDensity = 2f)
            assertEquals(expected, layout.widthClass)
        }
    }

    /** 大屏分配到窄分屏区域时选择窄窗口布局，密度按该区域宽度计算。 */
    @Test
    fun splitWindowSelectsPhoneLayoutEvenOnAnExpandedDisplay() {
        val full = resolveDesignLayout(1800f, 1600f, systemDensity = 2f)
        val split = resolveDesignLayout(900f, 1600f, systemDensity = 2f)
        assertEquals(WindowWidthClass.Expanded, full.widthClass)
        assertEquals(WindowWidthClass.Compact, split.widthClass)
        assertEquals(900f / 360f, split.density, 0.001f)
    }

    /** 紧凑高度只取决于原始系统 dp，480 dp 边界不受宽度缩放影响。 */
    @Test
    fun compactHeightIsIndependentOfWidthScaling() {
        assertTrue(resolveDesignLayout(2100f, 1200f, 3f).isCompactHeight)
        assertFalse(resolveDesignLayout(2100f, 1440f, 3f).isCompactHeight)
    }

    /** 模拟非线性字体映射，验证沿用系统 sp/dp 换算并按新的密度生成像素。 */
    @Test
    fun preservesNonlinearFontConversionAndUsesTheNewPixelDensity() {
        val platformDensity = object : Density {

            /** 测试系统密度固定为 2 px/dp。 */
            override val density = 2f

            /** 模拟用户设置的两倍字体缩放。 */
            override val fontScale = 2f

            /** 特殊映射 14 sp 为 23 dp，以识别错误的线性字体换算。 */
            override fun TextUnit.toDp(): Dp = if (value == 14f) 23.dp else (value * 2).dp
            /** 提供上述特殊字体映射的逆向换算。 */
            override fun Dp.toSp(): TextUnit = if (value == 23f) 14.sp else (value / 2).sp
        }
        val scaled = DesignDensity(density = 3f, systemDensity = platformDensity)
        with(scaled) {
            assertEquals(2f, fontScale, 0f)
            assertEquals(69f, 14.sp.toPx(), 0.001f)
            assertEquals(14f, 69f.toSp().value, 0.001f)
            assertEquals(300f, 100.dp.toPx(), 0.001f)
            assertEquals(100f, 300f.toDp().value, 0.001f)
        }
    }

    /** 零宽度不能生成设计密度，计算入口必须拒绝。 */
    @Test(expected = IllegalArgumentException::class)
    fun zeroWidthCannotCreateADensity() {
        resolveDesignLayout(0f, 1000f, 3f)
    }

    /** 非有限的设计基准宽度在策略构造时被拒绝。 */
    @Test(expected = IllegalArgumentException::class)
    fun invalidDesignReferenceIsRejected() {
        DesignWidthPolicy(compact = Float.NaN)
    }
}
