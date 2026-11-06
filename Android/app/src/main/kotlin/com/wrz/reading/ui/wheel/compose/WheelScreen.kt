package com.wrz.reading.ui.wheel.compose

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wrz.reading.R
import com.wrz.reading.ui.compose.ReadingToolbar
import com.wrz.reading.ui.compose.pressFeedback
import com.wrz.reading.ui.wheel.model.Wheel
import com.wrz.reading.ui.wheel.view.wheelview.WheelView

/**
 * 转盘页 Compose 实现。
 *
 * 竖屏：顶部工具栏 + 白色主卡片（标题/副标题/转盘/结果/提示）+ 底部三按钮；
 * 横屏/平板横屏：整页卡片，左侧信息列、右侧转盘、底部三按钮，菜单浮在卡片右上角。
 * WheelView 的绘制、旋转与震动反馈保留为 AndroidView 互操作。
 */
@Composable
internal fun WheelScreen(
    wheel: Wheel?,
    currentIndex: Int,
    wheelRequestTick: Int,
    uiState: WheelUiState,
    onWheelViewCreated: (WheelView) -> Unit,
    onWheelViewReleased: (WheelView) -> Unit,
    onTempSelected: (String) -> Unit,
    onItemSelected: (String) -> Unit,
    onAllSelected: () -> Unit,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onStartClick: () -> Unit,
    onNextClick: () -> Unit,
    onEditClick: () -> Unit,
    onCardClick: () -> Unit,
    onPointerClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTabletLandscape = isLandscape && configuration.smallestScreenWidthDp >= 600

    val title = wheel?.let { w ->
        val text = w.title.orEmpty()
        if (!w.emoji.isNullOrBlank()) "${w.emoji} $text" else text
    } ?: stringResource(R.string.title_wheel)

    val subtitle = wheel?.let {
        "${currentIndex + 1}/${it.list?.size ?: 0}"
    } ?: ""

    val resultText = when (uiState.resultKind) {
        WheelResultKind.NONE -> ""
        WheelResultKind.SPINNING -> stringResource(R.string.hint_spinning)
        WheelResultKind.TEMP -> uiState.resultValue.orEmpty()
        WheelResultKind.RESULT -> stringResource(
            R.string.message_result,
            uiState.resultValue.orEmpty()
        )

        WheelResultKind.ALL_SELECTED -> stringResource(R.string.notification_all_selected)
    }
    val resultColor = if (uiState.resultKind == WheelResultKind.NONE) {
        colorResource(R.color.ios_text_primary)
    } else {
        colorResource(R.color.apple_red)
    }

    val hostParams = WheelHostParams(
        wheel = wheel,
        wheelRequestTick = wheelRequestTick,
        onWheelViewCreated = onWheelViewCreated,
        onWheelViewReleased = onWheelViewReleased,
        onTempSelected = onTempSelected,
        onItemSelected = onItemSelected,
        onAllSelected = onAllSelected
    )

    if (isLandscape) {
        LandscapeWheelScreen(
            title = title,
            subtitle = subtitle,
            resultText = resultText,
            resultColor = resultColor,
            uiState = uiState,
            bottomMargin = if (isTabletLandscape) 76.dp else 8.dp,
            menuTopPadding = if (isTabletLandscape) 4.dp else 0.dp,
            hostParams = hostParams,
            onTitleClick = onTitleClick,
            onMenuClick = onMenuClick,
            onStartClick = onStartClick,
            onNextClick = onNextClick,
            onEditClick = onEditClick,
            onCardClick = onCardClick,
            onPointerClick = onPointerClick
        )
    } else {
        PortraitWheelScreen(
            title = title,
            subtitle = subtitle,
            resultText = resultText,
            resultColor = resultColor,
            uiState = uiState,
            hostParams = hostParams,
            onTitleClick = onTitleClick,
            onMenuClick = onMenuClick,
            onStartClick = onStartClick,
            onNextClick = onNextClick,
            onEditClick = onEditClick,
            onCardClick = onCardClick,
            onPointerClick = onPointerClick
        )
    }
}

private class WheelHostParams(
    val wheel: Wheel?,
    val wheelRequestTick: Int,
    val onWheelViewCreated: (WheelView) -> Unit,
    val onWheelViewReleased: (WheelView) -> Unit,
    val onTempSelected: (String) -> Unit,
    val onItemSelected: (String) -> Unit,
    val onAllSelected: () -> Unit
)

@Composable
private fun PortraitWheelScreen(
    title: String,
    subtitle: String,
    resultText: String,
    resultColor: Color,
    uiState: WheelUiState,
    hostParams: WheelHostParams,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onStartClick: () -> Unit,
    onNextClick: () -> Unit,
    onEditClick: () -> Unit,
    onCardClick: () -> Unit,
    onPointerClick: () -> Unit
) {
    val pageBackground = colorResource(R.color.ios_background)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .padding(bottom = 62.dp)
    ) {
        ReadingToolbar(
            title = stringResource(R.string.wheel),
            background = colorResource(R.color.ios_card_background)
        ) {
            WheelMenuButton(
                enabled = uiState.menuEnabled,
                onClick = onMenuClick,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        val cardShape = RoundedCornerShape(20.dp)
        val cardInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .hoverable(cardInteractionSource)
                .pressFeedback(cardInteractionSource)
                .clip(cardShape)
                .background(colorResource(R.color.ios_card_background))
                .border(1.dp, colorResource(R.color.ios_border), cardShape)
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onCardClick
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WheelTitle(
                    text = title,
                    letterSpacing = (-0.02f).em,
                    verticalPadding = 20.dp,
                    onClick = onTitleClick
                )
                WheelSubtitle(subtitle)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    WheelHost(
                        params = hostParams,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(550.dp)
                    )
                    WheelPointer(size = 60.dp, onClick = onPointerClick)
                }
                WheelResult(resultText, resultColor)
                WheelHint(topPadding = 8.dp, bottomPadding = 8.dp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WheelActionButton(
                label = stringResource(R.string.start_spinning),
                enabled = uiState.startEnabled,
                filled = true,
                textColor = colorResource(R.color.white),
                textSize = 15.sp,
                fillWidth = true,
                modifier = Modifier.weight(1f),
                onClick = onStartClick
            )
            WheelActionButton(
                label = stringResource(R.string.next_wheel),
                enabled = true,
                filled = false,
                textColor = colorResource(R.color.ios_text_primary),
                textSize = 15.sp,
                fillWidth = true,
                modifier = Modifier.weight(1f),
                onClick = onNextClick
            )
            WheelActionButton(
                label = stringResource(R.string.edit_wheel),
                enabled = uiState.editEnabled,
                filled = false,
                textColor = colorResource(R.color.apple_red),
                textSize = 15.sp,
                fillWidth = true,
                modifier = Modifier.weight(1f),
                onClick = onEditClick
            )
        }
    }
}

@Composable
private fun LandscapeWheelScreen(
    title: String,
    subtitle: String,
    resultText: String,
    resultColor: Color,
    uiState: WheelUiState,
    bottomMargin: Dp,
    menuTopPadding: Dp,
    hostParams: WheelHostParams,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onStartClick: () -> Unit,
    onNextClick: () -> Unit,
    onEditClick: () -> Unit,
    onCardClick: () -> Unit,
    onPointerClick: () -> Unit
) {
    val pageBackground = colorResource(R.color.ios_background)
    val cardShape = RoundedCornerShape(20.dp)
    val cardInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = bottomMargin)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hoverable(cardInteractionSource)
                .pressFeedback(cardInteractionSource)
                .clip(cardShape)
                .background(colorResource(R.color.ios_card_background))
                .border(1.dp, colorResource(R.color.ios_border), cardShape)
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onCardClick
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(200.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            WheelTitle(
                                text = title,
                                letterSpacing = 0.sp,
                                verticalPadding = 0.dp,
                                onClick = onTitleClick
                            )
                            WheelSubtitle(subtitle)
                            WheelResult(resultText, resultColor)
                            WheelHint(topPadding = 24.dp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            WheelHost(
                                params = hostParams,
                                modifier = Modifier.fillMaxSize()
                            )
                            WheelPointer(size = 80.dp, onClick = onPointerClick)
                        }
                    }

                    WheelMenuButton(
                        enabled = uiState.menuEnabled,
                        onClick = onMenuClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = menuTopPadding)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelActionButton(
                            label = stringResource(R.string.start_spinning),
                            enabled = uiState.startEnabled,
                            filled = true,
                            textColor = colorResource(R.color.white),
                            textSize = 18.sp,
                            fillWidth = false,
                            onClick = onStartClick
                        )
                        Spacer(Modifier.width(8.dp))
                        WheelActionButton(
                            label = stringResource(R.string.next_wheel),
                            enabled = true,
                            filled = false,
                            textColor = colorResource(R.color.ios_text_primary),
                            textSize = 18.sp,
                            fillWidth = false,
                            onClick = onNextClick
                        )
                        Spacer(Modifier.weight(1f))
                        WheelActionButton(
                            label = stringResource(R.string.edit_wheel),
                            enabled = uiState.editEnabled,
                            filled = false,
                            textColor = colorResource(R.color.ios_text_primary),
                            textSize = 18.sp,
                            fillWidth = false,
                            onClick = onEditClick
                        )
                    }
                }
            }
        }
    }

@Composable
private fun WheelHost(
    params: WheelHostParams,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WheelView(context).also { view ->
                view.setOnItemSelectedListener(object : WheelView.OnItemListener {
                    override fun onAllSelected() {
                        params.onAllSelected()
                    }

                    override fun onItemSelected(item: String) {
                        params.onItemSelected(item)
                    }

                    override fun onTempSelected(item: String) {
                        params.onTempSelected(item)
                    }
                })
                params.onWheelViewCreated(view)
            }
        },
        update = { view ->
            val wheel = params.wheel
            if (wheel != null && view.getTag(R.id.wheel) != params.wheelRequestTick) {
                view.setItems(wheel)
                view.setTag(R.id.wheel, params.wheelRequestTick)
            }
        },
        modifier = modifier,
        onRelease = { view ->
            view.setOnItemSelectedListener(null)
            params.onWheelViewReleased(view)
        }
    )
}

@Composable
private fun WheelPointer(size: Dp, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Image(
        painter = painterResource(R.drawable.ic_pointer),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun WheelMenuButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(44.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(R.drawable.menu_24px),
            contentDescription = stringResource(R.string.my_wheel),
            modifier = Modifier.size(24.dp),
            tint = colorResource(R.color.ios_text_primary).copy(
                alpha = if (enabled) 1f else 0.38f
            )
        )
    }
}

@Composable
private fun WheelTitle(
    text: String,
    letterSpacing: TextUnit,
    verticalPadding: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .padding(vertical = verticalPadding),
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = letterSpacing,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),
        color = colorResource(R.color.ios_text_primary),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WheelSubtitle(subtitle: String) {
    Text(
        text = subtitle,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),
        color = colorResource(R.color.gray_600),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WheelResult(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WheelHint(topPadding: Dp = 0.dp, bottomPadding: Dp = 0.dp) {
    Text(
        text = stringResource(R.string.hint_tips),
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),
        color = colorResource(R.color.ios_text_secondary),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WheelActionButton(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    textColor: Color,
    textSize: TextUnit,
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val innerModifier = if (fillWidth) {
        Modifier
            .fillMaxWidth()
            .height(40.dp)
    } else {
        Modifier.height(40.dp)
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = innerModifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (filled) {
                    colorResource(R.color.ios_blue)
                } else {
                    Color.Transparent
                },
                contentColor = textColor
            ),
            border = if (filled) null else {
                BorderStroke(1.dp, colorResource(R.color.ios_border))
            },
            elevation = ButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            interactionSource = interactionSource
        ) {
            Text(
                text = label,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1
            )
        }
    }
}
