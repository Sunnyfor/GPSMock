package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 通用间距令牌，以 dp 声明，避免页面散落无语义的尺寸常量。
 *
 * 在设计适配容器内按局部设计密度换算，容器外按系统密度换算；不负责窗口布局选择。
 */
object AppSpacing {
    /** 极小间距：4 dp，用于紧密关联元素。 */
    val extraSmall = 4.dp

    /** 小间距：8 dp，用于相邻细节。 */
    val small = 8.dp

    /** 中间距：12 dp，用于常规内容内部。 */
    val medium = 12.dp

    /** 大间距：16 dp，可用于常规内容边距。 */
    val large = 16.dp

    /** 更大间距：24 dp，可用于相邻内容区块。 */
    val extraLarge = 24.dp

    /** 分区间距：32 dp，用于较独立的内容区域。 */
    val section = 32.dp
}

/**
 * Material 组件的统一圆角方案，五个等级依次为 4、8、12、16、24 dp。
 *
 * 由 [GPSMockTheme] 注入；具体组件选用哪一级由 Material 组件实现决定。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
