package com.wrz.reading.ui.compose

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.theme.tvActionChipTextStyle
import com.wrz.reading.ui.compose.theme.tvCtaLabelTextStyle
import com.wrz.reading.ui.read.compose.tvHardShadow

/** 对应 animator/tv_focus_lift.xml 的 decelerate_quint */
internal val TvFocusInEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** 电视端新粗野主义色板（来自 values/colors_tv.xml） */
internal data class TvColors(
    val ink: Color,
    val paper: Color,
    val muted: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val yellow: Color,
    val pink: Color,
    val cyan: Color,
    val lime: Color,
    val orange: Color,
    val selectedFill: Color
)

@Composable
internal fun tvColors(): TvColors = TvColors(
    ink = colorResource(R.color.tv_ink),
    paper = colorResource(R.color.tv_paper),
    muted = colorResource(R.color.tv_muted),
    surface = colorResource(R.color.tv_surface),
    surfaceAlt = colorResource(R.color.tv_surface_alt),
    yellow = colorResource(R.color.tv_yellow),
    pink = colorResource(R.color.tv_pink),
    cyan = colorResource(R.color.tv_cyan),
    lime = colorResource(R.color.tv_lime),
    orange = colorResource(R.color.tv_orange),
    selectedFill = colorResource(R.color.tv_selected_fill)
)

/** 电视端顶栏操作块：焦点亮黄、按压青、默认深色块。 */
@Composable
internal fun TvActionChip(
    text: String,
    onClick: () -> Unit,
    colors: TvColors,
    modifier: Modifier = Modifier
) {
    val actionHeight = dimensionResource(R.dimen.tv_action_height)
    val minWidth = dimensionResource(R.dimen.tv_action_min_width)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = if (focused) {
            tween(durationMillis = 120, easing = TvFocusInEasing)
        } else {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        },
        label = "tvActionFocusScale"
    )

    val fillColor = when {
        focused -> colors.yellow
        pressed -> colors.cyan
        else -> colors.surfaceAlt
    }
    val shadowColor = if (focused || pressed) colors.pink else colors.ink
    val borderColor = if (focused || pressed) colors.ink else colors.paper
    val borderWidth = if (focused || pressed) 3.dp else 2.dp

    Box(
        modifier = modifier
            .height(actionHeight)
            .widthIn(min = minWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .tvHardShadow(
                offset = 4.dp,
                shadowColor = shadowColor,
                fillColor = fillColor,
                borderColor = borderColor,
                borderWidth = borderWidth,
                cornerRadius = 10.dp
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = tvActionChipTextStyle(if (focused || pressed) colors.ink else colors.paper),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 电视端主行动块（导入 / 新建 / 删除选中）。 */
@Composable
internal fun TvCtaBlock(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    colors: TvColors,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val actionHeight = dimensionResource(R.dimen.tv_action_height)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = if (focused) {
            tween(durationMillis = 120, easing = TvFocusInEasing)
        } else {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        },
        label = "tvCtaFocusScale"
    )

    val fillColor = when {
        focused -> colors.pink
        pressed -> colors.cyan
        else -> colors.yellow
    }

    Box(
        modifier = modifier
            .height(actionHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .tvHardShadow(
                offset = 6.dp,
                shadowColor = colors.ink,
                fillColor = fillColor,
                borderColor = colors.ink,
                borderWidth = 3.dp,
                cornerRadius = 12.dp
            )
            .padding(start = 20.dp, end = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.ink
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp),
                style = tvCtaLabelTextStyle(),
                maxLines = 1
            )
        }
    }
}
