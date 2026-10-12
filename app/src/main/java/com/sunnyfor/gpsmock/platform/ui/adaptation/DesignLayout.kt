package com.sunnyfor.gpsmock.platform.ui.adaptation

import androidx.compose.runtime.Immutable

/**
 * 按原始系统密度下的可用容器宽度划分的布局档位，单位为系统 dp。
 *
 * 档位用于调用方选择布局，不会自动重新排列组件；边界判断见 [resolveDesignLayout]。
 */
enum class WindowWidthClass {
    /** 窄窗口：宽度小于 600 系统 dp。 */
    Compact,

    /** 中等窗口：宽度为 [600, 840) 系统 dp。 */
    Medium,

    /** 宽窗口：宽度为 [840, 1200) 系统 dp。 */
    Expanded,

    /** 大窗口：宽度为 [1200, 1600) 系统 dp。 */
    Large,

    /** 超大窗口：宽度不小于 1600 系统 dp。 */
    ExtraLarge
}

/**
 * 各窗口档位对应的设计基准宽度策略，使用逻辑设计单位，而非导出位图的物理像素。
 *
 * 基准只控制该档位的缩放比例，不改变档位阈值，也不提供不同档位的组件布局。
 * 所有宽度必须为有限正数；不要求按大小递增，调用方应匹配实际设计稿。
 *
 * @throws IllegalArgumentException 任一基准宽度非有限数或不是正数。
 */
@Immutable
data class DesignWidthPolicy(
    /** 窄窗口的逻辑设计宽度，默认 360。 */
    val compact: Float = 360f,

    /** 中等窗口的逻辑设计宽度，默认 600。 */
    val medium: Float = 600f,

    /** 宽窗口的逻辑设计宽度，默认 840。 */
    val expanded: Float = 840f,

    /** 大窗口的逻辑设计宽度，默认 1200。 */
    val large: Float = 1200f,

    /** 超大窗口的逻辑设计宽度，默认 1600。 */
    val extraLarge: Float = 1600f
) {
    init {
        require(listOf(compact, medium, expanded, large, extraLarge).all {
            it.isFinite() && it > 0f
        }) { "Design widths must be finite and positive" }
    }

    /** 返回指定窗口档位的设计基准宽度，不按实际窗口宽度进行插值。 */
    fun referenceWidth(widthClass: WindowWidthClass): Float = when (widthClass) {
        WindowWidthClass.Compact -> compact
        WindowWidthClass.Medium -> medium
        WindowWidthClass.Expanded -> expanded
        WindowWidthClass.Large -> large
        WindowWidthClass.ExtraLarge -> extraLarge
    }
}

/**
 * 一次容器测量产生的布局选择与缩放结果，不负责实际组件排列。
 *
 * 推荐由 [resolveDesignLayout] 创建；此数据记录自身不校验参数。
 */
@Immutable
data class DesignLayout(
    /** 使用原始系统密度判定的宽度档位，不受设计缩放影响。 */
    val widthClass: WindowWidthClass,

    /** 原始可用高度小于 480 系统 dp 时为 true，与宽度档位独立。 */
    val isCompactHeight: Boolean,

    /** 本档位选用的逻辑设计宽度，不是物理屏幕宽度。 */
    val referenceWidth: Float,

    /** 每个设计 dp 对应的像素数，等于可用容器像素宽度 / 设计基准宽度。 */
    val density: Float
)

/**
 * 先用原始系统密度分类可用容器，再按选定基准宽度计算设计密度。
 *
 * 宽度阈值为 600、840、1200、1600 系统 dp，高度阈值为 480 系统 dp。
 * 使用实际分配给内容的区域，不能用设备全屏像素替代分屏或扣除边距后的宽度。
 *
 * @param availableWidthPx 可用容器宽度，单位为 px，须为有限正数。
 * @param availableHeightPx 可用容器高度，单位为 px，须为有限非负数；允许零高度。
 * @param systemDensity 原始系统的 px/dp 比例，须为有限正数，不能传入已经替换的设计密度。
 * @param policy 各档位的逻辑设计宽度，与窗口分类阈值独立。
 * @return 供调用方选择布局并提供局部密度的测量结果。
 * @throws IllegalArgumentException 容器尺寸或系统密度不满足上述输入条件。
 */
fun resolveDesignLayout(
    availableWidthPx: Float,
    availableHeightPx: Float,
    systemDensity: Float,
    policy: DesignWidthPolicy = DesignWidthPolicy()
): DesignLayout {
    require(availableWidthPx.isFinite() && availableWidthPx > 0f)
    require(availableHeightPx.isFinite() && availableHeightPx >= 0f)
    require(systemDensity.isFinite() && systemDensity > 0f)
    val widthDp = availableWidthPx / systemDensity
    val widthClass = when {
        widthDp < 600f -> WindowWidthClass.Compact
        widthDp < 840f -> WindowWidthClass.Medium
        widthDp < 1200f -> WindowWidthClass.Expanded
        widthDp < 1600f -> WindowWidthClass.Large
        else -> WindowWidthClass.ExtraLarge
    }
    val referenceWidth = policy.referenceWidth(widthClass)
    return DesignLayout(
        widthClass = widthClass,
        isCompactHeight = availableHeightPx / systemDensity < 480f,
        referenceWidth = referenceWidth,
        density = availableWidthPx / referenceWidth
    )
}
