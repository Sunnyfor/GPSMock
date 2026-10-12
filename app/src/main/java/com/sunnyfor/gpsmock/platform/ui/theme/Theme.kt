package com.sunnyfor.gpsmock.platform.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * 注入应用外观：Material 配色、排版、圆角与补充状态色。
 *
 * 本主题不修改密度或重排布局；几何适配由独立的设计适配容器负责。
 * 默认使用静态品牌色，动态配色开启时只替换 Material 色表，状态色仍按深浅模式选取。
 *
 * @param darkTheme 是否使用深色外观，默认跟随系统。
 * @param dynamicColor 是否启用 Android 12 及以上的动态配色；低版本回退到静态方案。
 * @param content 在本主题下组合的内容。
 */
@Composable
fun GPSMockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
