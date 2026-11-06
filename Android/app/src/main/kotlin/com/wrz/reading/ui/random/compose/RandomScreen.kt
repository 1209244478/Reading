package com.wrz.reading.ui.random.compose

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.pressFeedback
import com.wrz.reading.ui.compose.rememberActionBarSize
import com.wrz.reading.ui.compose.themeColor
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme

/**
 * 随机数页面 Compose 实现。
 *
 * 同时覆盖原项目的三套布局：
 * - layout/fragment_random.xml：竖屏，顶部工具栏 + 白色主卡片 + 底部双按钮；
 * - layout-land/fragment_random.xml：手机横屏，整页为一张卡片，顶部仅范围胶囊 + 设置入口；
 * - layout-sw600dp-land/fragment_random.xml：平板/TV 横屏，同上但底部留出 76dp 导航空间。
 *
 * 数据与设置值由 Fragment 持有，这里只负责渲染与事件回传。
 */
@Composable
fun RandomScreen(
    items: List<String>,
    start: Int,
    end: Int,
    scrollToEndRequest: Int,
    onGenerateClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeRandomScreen(
            items = items,
            start = start,
            end = end,
            scrollToEndRequest = scrollToEndRequest,
            bottomMargin = if (configuration.smallestScreenWidthDp >= 600) 76.dp else 8.dp,
            onGenerateClick = onGenerateClick,
            onClearClick = onClearClick,
            onSettingsClick = onSettingsClick
        )
    } else {
        PortraitRandomScreen(
            items = items,
            start = start,
            end = end,
            scrollToEndRequest = scrollToEndRequest,
            onGenerateClick = onGenerateClick,
            onClearClick = onClearClick,
            onSettingsClick = onSettingsClick
        )
    }
}

// ==================== 竖屏 ====================

@Composable
private fun PortraitRandomScreen(
    items: List<String>,
    start: Int,
    end: Int,
    scrollToEndRequest: Int,
    onGenerateClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToEndRequest) {
        if (scrollToEndRequest <= 0 || items.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(items.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ios_background))
            // 对应原布局根节点的 layout_marginBottom=62dp
            .padding(bottom = 62.dp)
    ) {
        PortraitTopBar(
            start = start,
            end = end,
            onSettingsClick = onSettingsClick
        )
        PortraitListCard(
            items = items,
            listState = listState,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionButton(
                text = stringResource(R.string.generate_random_number),
                onClick = onGenerateClick,
                modifier = Modifier.weight(3f),
                filled = true,
                textSize = 15.sp,
                fillWidth = true
            )
            ActionButton(
                text = stringResource(R.string.clear),
                onClick = onClearClick,
                modifier = Modifier.weight(2f),
                filled = false,
                textSize = 15.sp,
                fillWidth = true
            )
        }
    }
}

/** 竖屏顶部工具栏：标题、范围胶囊（点击打开设置）、右端设置按钮。 */
@Composable
private fun PortraitTopBar(
    start: Int,
    end: Int,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rememberActionBarSize())
            .background(colorResource(R.color.ios_card_background))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.random_number),
            style = MaterialTheme.typography.h6,
            color = colorResource(R.color.ios_text_primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Toolbar 的 titleMarginEnd 为 4dp
        Spacer(Modifier.width(4.dp))
        RangeRow(start = start, end = end, onSettingsClick = onSettingsClick)
        Spacer(Modifier.weight(1f))
        SettingsButton(onClick = onSettingsClick)
    }
}

/** 竖屏主卡片：20dp 圆角、1dp 描边，列表竖排（对应 LinearLayoutManager）。 */
@Composable
private fun PortraitListCard(
    items: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(shape)
            .background(colorResource(R.color.ios_card_background))
            .border(1.dp, colorResource(R.color.ios_border), shape)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            listItems(items) { item ->
                RandomNumberItem(item)
            }
        }
    }
}

// ==================== 横屏（手机 / 平板与 TV） ====================

@Composable
private fun LandscapeRandomScreen(
    items: List<String>,
    start: Int,
    end: Int,
    scrollToEndRequest: Int,
    bottomMargin: Dp,
    onGenerateClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(scrollToEndRequest) {
        if (scrollToEndRequest <= 0 || items.isEmpty()) return@LaunchedEffect
        gridState.scrollToItem(items.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = bottomMargin)
    ) {
        val cardShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(themeColor(com.google.android.material.R.attr.colorSurface))
                .border(1.dp, colorResource(R.color.ios_border), cardShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RangeRow(start = start, end = end, onSettingsClick = onSettingsClick)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 对应原 RecyclerView 的 marginVertical=8dp + 20dp 圆角浅灰背景
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colorResource(R.color.ios_rv_background))
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(items) { item ->
                                RandomNumberItem(item)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionButton(
                            text = stringResource(R.string.generate_random_number),
                            onClick = onGenerateClick,
                            filled = true,
                            textSize = 18.sp,
                            fillWidth = false
                        )
                        ActionButton(
                            text = stringResource(R.string.clear),
                            onClick = onClearClick,
                            filled = false,
                            textSize = 18.sp,
                            fillWidth = false
                        )
                    }
                }

                // 原布局中设置按钮浮在列表之上（top-end，44dp），在 32dp 顶部行下露出 4dp
                SettingsButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

// ==================== 共用组件 ====================

/** 范围胶囊 + 分隔符，对应原 ll_top / tv_start / tv_end。 */
@Composable
private fun RangeRow(
    start: Int,
    end: Int,
    onSettingsClick: () -> Unit
) {
    RangeChip(value = start, onClick = onSettingsClick)
    Text(
        text = "—",
        modifier = Modifier.padding(horizontal = 8.dp),
        color = colorResource(R.color.ios_text_secondary),
        fontSize = 16.sp,
        letterSpacing = 0.sp
    )
    RangeChip(value = end, onClick = onSettingsClick)
}

/** 范围胶囊：等价于原 tv_start / tv_end，背景 bg_range_chip + 按压缩放。 */
@Composable
private fun RangeChip(value: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ios_click_ripple))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = colorResource(R.color.ios_text_primary),
            fontSize = 15.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            maxLines = 1
        )
    }
}

/** 设置入口：等价于原 44dp ImageButton（24dp 图标 + 无界 ripple）。 */
@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(R.drawable.menu_24px),
            contentDescription = stringResource(R.string.setting),
            tint = colorResource(R.color.ios_text_primary),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** 列表项：12dp 圆角、1dp 描边、14dp 内边距、最小高度 44dp，对应 item_random.xml。 */
@Composable
private fun RandomNumberItem(value: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colorResource(R.color.ios_background))
            .border(1.dp, colorResource(R.color.ios_border), shape)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.ifEmpty { "?" },
                color = colorResource(R.color.ios_text_primary),
                fontSize = 20.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * MaterialButton 的 Compose 等价实现：
 * 原控件高度 48dp、背景因 insetTop/Bottom=4dp 实际绘制为 40dp，点击区域仍是 48dp。
 * 这里用 48dp 外层承接点击与按压缩放，内部 40dp 按钮绘制背景与 ripple。
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    textSize: TextUnit,
    fillWidth: Boolean,
    modifier: Modifier = Modifier
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
            .pressFeedback(interactionSource)
            // 视觉背景上下各内缩 4dp，这两条 4dp 区域仍属于点击范围
            .pointerInput(onClick) {
                val inset = 4.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val outsideVisual = down.position.y < inset ||
                        down.position.y > size.height - inset
                    if (outsideVisual) {
                        val up = waitForUpOrCancellation()
                        if (up != null) onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = innerModifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (filled) {
                    colorResource(R.color.ios_blue)
                } else {
                    Color.Transparent
                },
                contentColor = if (filled) {
                    colorResource(R.color.white)
                } else {
                    colorResource(R.color.ios_text_primary)
                }
            ),
            border = if (filled) {
                null
            } else {
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
                text = text,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1
            )
        }
    }
}

@Preview(name = "Portrait", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Landscape", showBackground = true, widthDp = 780, heightDp = 360)
@Composable
private fun RandomScreenPreview() {
    ReadingComposeTheme {
        RandomScreen(
            items = listOf("1", "52", "100"),
            start = 1,
            end = 100,
            scrollToEndRequest = 0,
            onGenerateClick = {},
            onClearClick = {},
            onSettingsClick = {}
        )
    }
}
