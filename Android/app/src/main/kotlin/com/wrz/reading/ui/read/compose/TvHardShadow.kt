package com.wrz.reading.ui.read.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 电视端硬阴影面板：绘制向下/向右偏移的实色阴影 + 实色面板 + 内描边。
 * 对应 drawable 中 layer-list 的「shadow 偏移、front 反向内缩」写法。
 *
 * @param shadowRadius 阴影本身的圆角（部分 drawable 的阴影是直角，部分带圆角）
 */
internal fun Modifier.tvHardShadow(
    offset: Dp,
    shadowColor: Color,
    fillColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    cornerRadius: Dp,
    shadowRadius: Dp = 0.dp
): Modifier = drawBehind {
    val shadowOffset = offset.toPx()
    val frontWidth = size.width - shadowOffset
    val frontHeight = size.height - shadowOffset
    if (frontWidth <= 0f || frontHeight <= 0f) {
        return@drawBehind
    }

    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(shadowOffset, shadowOffset),
        size = Size(frontWidth, frontHeight),
        cornerRadius = CornerRadius(shadowRadius.toPx())
    )
    drawRoundRect(
        color = fillColor,
        size = Size(frontWidth, frontHeight),
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )

    val stroke = borderWidth.toPx()
    if (stroke > 0f) {
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(frontWidth - stroke, frontHeight - stroke),
            cornerRadius = CornerRadius(max(0f, cornerRadius.toPx() - stroke / 2f)),
            style = Stroke(width = stroke)
        )
    }
}
