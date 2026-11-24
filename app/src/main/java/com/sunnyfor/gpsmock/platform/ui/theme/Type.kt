package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用排版令牌，明确常用标题、正文与标签的字号、行高和字重。
 *
 * 字号及行高使用 sp，遵循当前密度的字体换算；未覆盖的角色沿用 Material 默认值。
 * 本方案不固定用户 fontScale，适配容器内仍保留系统字体转换规则。
 */
val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)
