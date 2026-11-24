package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 应用线性图标语义；不包含业务状态或文字，统一使用 24 单位矢量坐标。 */
internal enum class AppGlyph {
    /** 地理目标标记。 */
    Pin,

    /** 地图选点入口。 */
    Map,

    /** 收藏与常用位置入口。 */
    Star,

    /** 设置与参数入口。 */
    Settings,

    /** 地点搜索入口。 */
    Search,

    /** 手动坐标输入入口。 */
    Input,

    /** 启动动作。 */
    Play,

    /** 停止动作。 */
    Stop,

    /** 设备位置居中，不表示模拟目标。 */
    Locate,

    /** 目标详情及更多操作。 */
    More,

    /** 返回主地图画布。 */
    Back,

    /** 搜索入口的前进指示。 */
    Arrow
}

/** 绘制统一线宽的代码原生图标；可访问名称由外层按钮承担，独立使用时传入 description。 */
@Composable
internal fun AppIcon(glyph: AppGlyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary,
    description: String? = null) {
    Canvas(modifier.size(22.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val stroke = Stroke(1.65f, cap = StrokeCap.Round)
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val path = Path()
            when (glyph) {
                AppGlyph.Pin -> {
                    path.moveTo(12f, 22f); path.cubicTo(9f, 18f, 4f, 14f, 4f, 9f)
                    path.cubicTo(4f, -1f, 20f, -1f, 20f, 9f); path.cubicTo(20f, 14f, 15f, 18f, 12f, 22f)
                    drawPath(path, tint, style = stroke); drawCircle(tint, 2.6f, Offset(12f, 9f), style = stroke)
                }
                AppGlyph.Map -> {
                    path.moveTo(2f, 5f); path.lineTo(8f, 2f); path.lineTo(16f, 5f); path.lineTo(22f, 2f)
                    path.lineTo(22f, 19f); path.lineTo(16f, 22f); path.lineTo(8f, 19f); path.lineTo(2f, 22f); path.close()
                    drawPath(path, tint, style = stroke)
                    drawLine(tint, Offset(8f, 2f), Offset(8f, 19f), 1.65f)
                    drawLine(tint, Offset(16f, 5f), Offset(16f, 22f), 1.65f)
                }
                AppGlyph.Star -> {
                    path.moveTo(12f, 2f); path.lineTo(15f, 8f); path.lineTo(22f, 9f); path.lineTo(17f, 14f)
                    path.lineTo(18f, 21f); path.lineTo(12f, 18f); path.lineTo(6f, 21f); path.lineTo(7f, 14f)
                    path.lineTo(2f, 9f); path.lineTo(9f, 8f); path.close(); drawPath(path, tint, style = stroke)
                }
                AppGlyph.Settings -> {
                    listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (x, y) ->
                        drawLine(tint, Offset(x, 3f), Offset(x, 21f), 1.65f, StrokeCap.Round)
                        drawCircle(tint, 2.5f, Offset(x, y))
                    }
                }
                AppGlyph.Search -> {
                    drawCircle(tint, 7f, Offset(10f, 10f), style = stroke)
                    drawLine(tint, Offset(15f, 15f), Offset(22f, 22f), 1.65f, StrokeCap.Round)
                }
                AppGlyph.Input -> {
                    drawRoundRect(tint, Offset(2f, 5f), Size(20f, 14f), androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
                    listOf(6f, 10f, 14f, 18f).forEach { drawLine(tint, Offset(it, 9f), Offset(it, 11f), 1.65f) }
                    drawLine(tint, Offset(7f, 15f), Offset(17f, 15f), 1.65f, StrokeCap.Round)
                }
                AppGlyph.Play -> {
                    path.moveTo(7f, 3f); path.lineTo(21f, 12f); path.lineTo(7f, 21f); path.close(); drawPath(path, tint)
                }
                AppGlyph.Stop -> drawRoundRect(tint, Offset(5f, 5f), Size(14f, 14f), androidx.compose.ui.geometry.CornerRadius(2f))
                AppGlyph.Locate -> {
                    drawCircle(tint, 7f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 2.5f, Offset(12f, 12f), style = stroke)
                    listOf(Offset(12f, 1f) to Offset(12f, 5f), Offset(12f, 19f) to Offset(12f, 23f),
                        Offset(1f, 12f) to Offset(5f, 12f), Offset(19f, 12f) to Offset(23f, 12f)).forEach { (a, b) -> drawLine(tint, a, b, 1.65f) }
                }
                AppGlyph.More -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.4f, Offset(it, 12f)) }
                AppGlyph.Back -> { path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f); drawPath(path, tint, style = stroke) }
                AppGlyph.Arrow -> {
                    path.moveTo(13f, 6f); path.lineTo(19f, 12f); path.lineTo(13f, 18f)
                    drawPath(path, tint, style = stroke); drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), 1.65f)
                }
            }
        }
    }
}
