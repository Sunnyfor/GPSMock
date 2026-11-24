package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 地图画布的静态浅色令牌：克制靛蓝作为行动色，中性白灰承载内容。
 *
 * 辅助容器不再带绿色，避免大片低饱和色混用；运行成功色仅由状态令牌用于真实反馈。
 */
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3D5CC6), onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F3FC), onPrimaryContainer = Color(0xFF2E4492),
    secondary = Color(0xFF566176), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F2F6), onSecondaryContainer = Color(0xFF19243C),
    tertiary = LightStatusColors.success, onTertiary = Color.White,
    tertiaryContainer = LightStatusColors.successContainer, onTertiaryContainer = LightStatusColors.onSuccessContainer,
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF19243C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF19243C),
    surfaceVariant = Color(0xFFF0F2F6), onSurfaceVariant = Color(0xFF566176),
    surfaceDim = Color(0xFFE5E8EF), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF2F4F8), surfaceContainerHigh = Color(0xFFECEFF5),
    surfaceContainerHighest = Color(0xFFE5E8EF), surfaceTint = Color(0xFF3D5CC6),
    inverseSurface = Color(0xFF19243C), inverseOnSurface = Color.White, inversePrimary = Color(0xFFB8C8FF),
    outline = Color(0xFF768196), outlineVariant = Color(0xFFE4E8F0),
    error = Color(0xFFB64848), onError = Color.White,
    errorContainer = Color(0xFFFFEBEB), onErrorContainer = Color(0xFF7A2828), scrim = Color.Black
)

/** 与靛蓝浅色配对的中性深色外观；仍是同一地图画布，不继承已撤掉的绿色辅色。 */
internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9BBFF), onPrimary = Color(0xFF172354),
    primaryContainer = Color(0xFF28355A), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB7C1D4), onSecondary = Color(0xFF18202E),
    secondaryContainer = Color(0xFF252D3B), onSecondaryContainer = Color(0xFFEAF0FC),
    tertiary = DarkStatusColors.success, onTertiary = DarkStatusColors.successContainer,
    tertiaryContainer = DarkStatusColors.successContainer, onTertiaryContainer = DarkStatusColors.onSuccessContainer,
    background = Color(0xFF131720), onBackground = Color(0xFFEAF0FC),
    surface = Color(0xFF131720), onSurface = Color(0xFFEAF0FC),
    surfaceVariant = Color(0xFF252D3B), onSurfaceVariant = Color(0xFFB7C1D4),
    surfaceDim = Color(0xFF10131A), surfaceBright = Color(0xFF343D4C),
    surfaceContainerLowest = Color(0xFF10131A), surfaceContainerLow = Color(0xFF1A202C),
    surfaceContainer = Color(0xFF202735), surfaceContainerHigh = Color(0xFF252D3B),
    surfaceContainerHighest = Color(0xFF343D4C), surfaceTint = Color(0xFFA9BBFF),
    inverseSurface = Color(0xFFEAF0FC), inverseOnSurface = Color(0xFF19243C), inversePrimary = Color(0xFF3D5CC6),
    outline = Color(0xFFB7C1D4), outlineVariant = Color(0xFF404B60),
    error = Color(0xFFFFB7B7), onError = Color(0xFF611C1C),
    errorContainer = Color(0xFF5B272D), onErrorContainer = Color(0xFFFFDADA), scrim = Color.Black
)
