package com.wrz.reading.ui.compose

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 与 View 体系 PressFeedback 一致的按下缩放比例 */
private const val PRESSED_SCALE = 0.94f

/** 与 View 体系 PressFeedback 一致的悬停放大比例 */
private const val HOVER_SCALE = 1.03f

private val PressEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/**
 * Compose 版按压反馈：按下缩放、释放回弹、鼠标/触控笔悬停微放大。
 *
 * 对应 View 体系的 [com.wrz.reading.common.PressFeedback]；
 * 行为参数保持一致（按下 0.94、悬停 1.03、按下 120ms 减速、回弹过冲）。
 * 不消费点击事件，ripple 与 click 逻辑照常工作。
 */
@Composable
fun Modifier.pressFeedback(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> PRESSED_SCALE
            hovered -> HOVER_SCALE
            else -> 1f
        },
        animationSpec = if (pressed) {
            tween(durationMillis = 120, easing = PressEasing)
        } else {
            // 轻度过冲，对应 View 层的 OvershootInterpolator(1.6)
            spring(dampingRatio = 0.5f, stiffness = 600f)
        },
        label = "pressFeedbackScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
