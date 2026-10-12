package com.sunnyfor.gpsmock.platform.ui.adaptation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** 保存缩放前的系统密度，使嵌套适配与原生内容恢复不会重复缩放。 */
private val LocalSystemDensity = staticCompositionLocalOf<Density?> { null }

/**
 * 设计密度：替换几何尺寸的 px/dp 比例，保留系统的 sp/dp 转换及字体缩放设置。
 *
 * 系统字体转换可能非线性，不能简单以 fontScale 相乘替代。
 * 文字换算为 dp 后仍按设计密度绘制为像素，因此文字也参与整体几何缩放。
 * 输入由适配容器计算，此类型自身不再次校验。
 */
internal class DesignDensity(
    /** 局部设计密度，单位为 px/设计 dp。 */
    override val density: Float,

    /** 缩放前的系统密度，作为字体换算与 fontScale 的唯一来源。 */
    private val systemDensity: Density
) : Density {
    /** 原样暴露系统字体缩放设置，不在本类固定或关闭用户的大字体选项。 */
    override val fontScale: Float get() = systemDensity.fontScale

    /** 委托原始系统的 sp→dp 换算，保留其非线性字体放大规则。 */
    override fun TextUnit.toDp(): Dp = with(systemDensity) { this@toDp.toDp() }

    /** 委托原始系统的 dp→sp 逆向换算，与 [toDp] 使用同一转换来源。 */
    override fun Dp.toSp(): TextUnit = with(systemDensity) { this@toSp.toSp() }
}

/**
 * 在内容子树内提供按设计宽度计算的 Compose 密度，不修改应用或系统全局配置。
 *
 * ## 布局约定
 * 先应用系统安全区/Scaffold 边距，再将实际可用空间交给本容器。
 * 以原始系统密度分类窗口，设计 dp 按「容器像素宽度 / 基准宽度」转为 px。
 * 调用方依据 [DesignLayout] 显式选择单列、多列等布局，密度本身不会重新排列 UI。
 * 嵌套实例使用自身分配到的区域，而不是物理屏幕宽度；零宽区域不执行内容。
 *
 * ## 组件边界
 * 为遵循最小交互尺寸约定的 Material 组件保留至少 48 系统 dp 对应的物理空间；
 * 自定义手势组件仍需调用方自行保证触控区域。原生 View/地图包装层可使用 [SystemDensityContent]。
 *
 * @param modifier 容器的尺寸、边距等修饰；必须最终得到有限宽高约束。
 * @param policy 各窗口档位的设计基准宽度，默认窄窗口使用 360 逻辑单位。
 * @param content 接收测量结果的内容，在本容器提供的设计密度下组合。
 * @throws IllegalArgumentException 宽高约束无界；应放在滚动内容外的有界容器中。
 */
@Composable
fun DesignScaledLayout(
    modifier: Modifier = Modifier,
    policy: DesignWidthPolicy = DesignWidthPolicy(),
    content: @Composable (DesignLayout) -> Unit
) {
    val systemDensity = LocalSystemDensity.current ?: LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "DesignScaledLayout needs finite width and height; place it outside scrolling content"
        }
        if (constraints.maxWidth == 0) return@BoxWithConstraints
        val layout = resolveDesignLayout(
            availableWidthPx = constraints.maxWidth.toFloat(),
            availableHeightPx = constraints.maxHeight.toFloat(),
            systemDensity = systemDensity.density,
            policy = policy
        )
        val designDensity = remember(layout.density, systemDensity) {
            DesignDensity(layout.density, systemDensity)
        }
        // 换算为设计 dp 后仍保留足够的系统尺寸触控空间，避免随设计缩小。
        val minimumTouchSize = maxOf(48f, 48f * systemDensity.density / layout.density).dp
        CompositionLocalProvider(
            LocalSystemDensity provides systemDensity,
            LocalDensity provides designDensity,
            LocalMinimumInteractiveComponentSize provides minimumTouchSize
        ) {
            content(layout)
        }
    }
}

/**
 * 为当前子树恢复缩放前的 Compose 系统密度，并恢复 48 系统 dp 的最小交互尺寸。
 *
 * 可包裹原生 View/地图的 Compose 布局层；不会修改 View 的 Resources 或 SDK 内部尺寸。
 * SDK 使用的物理 px 仍须调用方显式换算；在适配容器外调用时沿用当前密度。
 */
@Composable
fun SystemDensityContent(content: @Composable () -> Unit) {
    val systemDensity = LocalSystemDensity.current ?: LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides systemDensity,
        LocalMinimumInteractiveComponentSize provides 48.dp,
        content = content
    )
}
