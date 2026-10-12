package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** 静态主题色对的本地对比度测试，只覆盖列出的不透明色对，不证明所有实际页面均可读。 */
class ThemeContrastTest {
    /** 浅深色主题的主要文字/背景配对均达到测试约定的 4.5:1 对比度。 */
    @Test
    fun normalTextPairsMeetFourPointFiveToOneInBothThemes() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            val pairs = listOf(
                "primary" to (scheme.onPrimary to scheme.primary),
                "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "background" to (scheme.onBackground to scheme.background),
                "surface" to (scheme.onSurface to scheme.surface),
                "surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                "inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                "error" to (scheme.onError to scheme.error),
                "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer)
            )
            for ((role, pair) in pairs) {
                val contrast = contrast(pair.first, pair.second)
                assertTrue("$role contrast was $contrast", contrast >= 4.5)
            }
        }
    }

    /** 成功/提醒强调色在主题表面及各自容器前景配对中达到 4.5:1。 */
    @Test
    fun statusTextRemainsReadableOnSurfacesAndItsOwnContainers() {
        for ((status, surface) in listOf(
            LightStatusColors to LightColorScheme.surface,
            DarkStatusColors to DarkColorScheme.surface
        )) {
            for ((foreground, background) in listOf(
                status.success to surface,
                status.warning to surface,
                status.onSuccessContainer to status.successContainer,
                status.onWarningContainer to status.warningContainer
            )) {
                assertTrue(contrast(foreground, background) >= 4.5)
            }
        }
    }

    /** 以两色的相对亮度计算对比度，范围为 1:1 至 21:1；测试输入均为不透明 sRGB 色。 */
    private fun contrast(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** 将 sRGB 通道线性化后加权计算相对亮度，忽略 alpha；调用方使用不透明测试色。 */
    private fun luminance(color: Color): Double {
        /** 按 sRGB 分段函数将归一化通道转换为线性亮度分量。 */
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
