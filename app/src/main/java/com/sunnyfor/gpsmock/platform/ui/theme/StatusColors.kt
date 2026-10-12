package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 色表之外的成功/提醒展示色，显式提供背景与成对前景。
 *
 * 仅表达视觉语义，不决定会话领域状态；错误色使用 Material 的 error 角色。
 * 页面从 [LocalStatusColors] 读取，不能据颜色推断实际引擎是否运行。
 */
@Immutable
data class StatusColors(
    /** 成功语义的强调色，可用作适当背景上的文字或图标。 */
    val success: Color,

    /** 成功提示容器的背景色，应与 [onSuccessContainer] 配对。 */
    val successContainer: Color,

    /** 成功提示容器上的文字/图标前景色。 */
    val onSuccessContainer: Color,

    /** 提醒语义的强调色，不等同于错误或会话失败。 */
    val warning: Color,

    /** 提醒容器的背景色，应与 [onWarningContainer] 配对。 */
    val warningContainer: Color,

    /** 提醒容器上的文字/图标前景色。 */
    val onWarningContainer: Color
)

/** 浅色主题的成功/提醒色组，与静态浅色表面及对应容器配对验证。 */
internal val LightStatusColors = StatusColors(
    success = Color(0xFF146C2E),
    successContainer = Color(0xFFE8FFEA),
    onSuccessContainer = Color(0xFF145323),
    warning = Color(0xFF8A4B00),
    warningContainer = Color(0xFFFFF7E8),
    onWarningContainer = Color(0xFF643500)
)

/** 深色主题的成功/提醒色组，与静态深色表面及对应容器配对验证。 */
internal val DarkStatusColors = StatusColors(
    success = Color(0xFF85D993),
    successContainer = Color(0xFF123D20),
    onSuccessContainer = Color(0xFFB8F3C0),
    warning = Color(0xFFFFC078),
    warningContainer = Color(0xFF493015),
    onWarningContainer = Color(0xFFFFDDB5)
)

/** 当前组合子树的状态色组，由 [GPSMockTheme] 注入；未提供时默认浅色。 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
