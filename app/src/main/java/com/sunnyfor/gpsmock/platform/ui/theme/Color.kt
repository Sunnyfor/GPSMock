package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 静态浅色 Material 配色，沿用 Bidder 的品牌蓝与中性底色并补全成对前景色。
 *
 * 组件按 primary/surface 等语义取色，不将固定色值分散到业务页面。
 * 主要文字色对由主题对比度测试约束；状态色引用 [LightStatusColors]。
 */
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3461FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEFFF),
    onPrimaryContainer = Color(0xFF173A9E),
    secondary = Color(0xFF4E5969),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDF6),
    onSecondaryContainer = Color(0xFF1D2129),
    tertiary = LightStatusColors.success,
    onTertiary = Color.White,
    tertiaryContainer = LightStatusColors.successContainer,
    onTertiaryContainer = LightStatusColors.onSuccessContainer,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF1D2129),
    surface = Color.White,
    onSurface = Color(0xFF1D2129),
    surfaceVariant = Color(0xFFE7EDF6),
    onSurfaceVariant = Color(0xFF4E5969),
    surfaceDim = Color(0xFFE7EDF6),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFF2F4F9),
    surfaceContainerHigh = Color(0xFFE7EDF6),
    surfaceContainerHighest = Color(0xFFE5E6EB),
    surfaceTint = Color(0xFF3461FF),
    inverseSurface = Color(0xFF1D2129),
    inverseOnSurface = Color(0xFFF6F8FC),
    inversePrimary = Color(0xFFB6C4FF),
    outline = Color(0xFF737D8C),
    outlineVariant = Color(0xFFE5E6EB),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black
)

/**
 * 静态深色 Material 配色，独立匹配深色背景与可读前景，不对浅色方案简单反色。
 *
 * 状态色引用 [DarkStatusColors]；动态配色的启用与回退由 [GPSMockTheme] 决定。
 */
internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF002A86),
    primaryContainer = Color(0xFF2045B7),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFC2C7D2),
    onSecondary = Color(0xFF111318),
    secondaryContainer = Color(0xFF282C34),
    onSecondaryContainer = Color(0xFFE3E7EF),
    tertiary = DarkStatusColors.success,
    onTertiary = DarkStatusColors.successContainer,
    tertiaryContainer = DarkStatusColors.successContainer,
    onTertiaryContainer = DarkStatusColors.onSuccessContainer,
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E7EF),
    surface = Color(0xFF191C22),
    onSurface = Color(0xFFE3E7EF),
    surfaceVariant = Color(0xFF282C34),
    onSurfaceVariant = Color(0xFFC2C7D2),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF343943),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF1F232B),
    surfaceContainerHigh = Color(0xFF282C34),
    surfaceContainerHighest = Color(0xFF343943),
    surfaceTint = Color(0xFFB6C4FF),
    inverseSurface = Color(0xFFE3E7EF),
    inverseOnSurface = Color(0xFF1D2129),
    inversePrimary = Color(0xFF3461FF),
    outline = Color(0xFF8D94A2),
    outlineVariant = Color(0xFF424854),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)
