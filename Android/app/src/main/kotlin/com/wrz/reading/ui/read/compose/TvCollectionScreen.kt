package com.wrz.reading.ui.read.compose

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.theme.tvActionChipTextStyle
import com.wrz.reading.ui.compose.theme.tvBadgeTextStyle
import com.wrz.reading.ui.compose.theme.tvCardMetaTextStyle
import com.wrz.reading.ui.compose.theme.tvCardTitleTextStyle
import com.wrz.reading.ui.compose.theme.tvCtaLabelTextStyle
import com.wrz.reading.ui.compose.theme.tvDisplayTextStyle
import com.wrz.reading.ui.compose.theme.tvKickerTextStyle
import com.wrz.reading.ui.read.utils.FileUtils

/** 对应 animator/tv_focus_lift.xml 的 decelerate_quint */
private val TvFocusInEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private class TvColors(
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
private fun tvColors(): TvColors = TvColors(
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

/**
 * 电视端书库首页：超大标题 + 块状操作区 + 5 列硬阴影卡片。
 * 焦点/选中/按压背景与 layout-land-television 下的 selector drawable 保持一致。
 */
@Composable
internal fun TvCollectionScreen(
    items: List<CollectionUiItem>,
    selection: CollectionSelectionState,
    resumeTick: Int,
    dataVersion: Int,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onCreateOrDelete: () -> Unit,
    onItemClick: (CollectionUiItem) -> Unit,
    onItemLongClick: (CollectionUiItem) -> Unit,
    onSettingClick: () -> Unit
) {
    val colors = tvColors()
    val gridState = rememberLazyGridState()
    val firstFocusRequester = remember { FocusRequester() }
    var gridHasFocus by remember { mutableStateOf(false) }
    var anyFocus by remember { mutableStateOf(false) }

    // 数据刷新后：仅在焦点不在列表内时把焦点交回首项（对应 TvFocusHelper）
    LaunchedEffect(dataVersion) {
        if (dataVersion <= 0 || items.isEmpty() || gridHasFocus) return@LaunchedEffect
        focusFirstItem(gridState, firstFocusRequester)
    }
    // 从阅读器/其它页面返回：仅在整页焦点丢失时回到首项（对应 onResume）
    LaunchedEffect(resumeTick) {
        if (items.isEmpty() || anyFocus) return@LaunchedEffect
        focusFirstItem(gridState, firstFocusRequester)
    }

    val pagePaddingHorizontal = dimensionResource(R.dimen.tv_page_padding_horizontal)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A22), Color(0xFF08080B))
                )
            )
            .onFocusChanged { anyFocus = it.hasFocus }
    ) {
        TvHeader(
            selection = selection,
            colors = colors,
            onRefresh = onRefresh,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected,
            onCreateOrDelete = onCreateOrDelete,
            onSettingClick = onSettingClick
        )

        // 黄色分隔条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(colors.yellow)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            contentPadding = PaddingValues(
                start = pagePaddingHorizontal,
                top = 18.dp,
                end = pagePaddingHorizontal,
                bottom = 20.dp
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onFocusChanged { gridHasFocus = it.hasFocus }
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TvCollectionCard(
                    item = item,
                    selection = selection,
                    colors = colors,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    focusRequester = if (index == 0) firstFocusRequester else null
                )
            }
        }
    }
}

private suspend fun focusFirstItem(gridState: LazyGridState, focusRequester: FocusRequester) {
    runCatching { gridState.scrollToItem(0) }
    withFrameNanos { }
    runCatching { focusRequester.requestFocus() }
}

@Composable
private fun TvHeader(
    selection: CollectionSelectionState,
    colors: TvColors,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onCreateOrDelete: () -> Unit,
    onSettingClick: () -> Unit
) {
    val pagePaddingHorizontal = dimensionResource(R.dimen.tv_page_padding_horizontal)
    val pagePaddingVertical = dimensionResource(R.dimen.tv_page_padding_vertical)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = pagePaddingHorizontal,
                top = pagePaddingVertical,
                end = pagePaddingHorizontal,
                bottom = 18.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tv_library_title),
                style = tvDisplayTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.tv_library_kicker),
                modifier = Modifier.padding(top = 6.dp),
                style = tvKickerTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TvActionChip(
            text = stringResource(R.string.tv_action_refresh),
            onClick = onRefresh,
            colors = colors,
            modifier = Modifier.padding(end = 12.dp)
        )
        if (selection.selectMode) {
            TvActionChip(
                text = stringResource(R.string.tv_action_rename),
                onClick = onRenameSelected,
                colors = colors,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        TvActionChip(
            text = stringResource(
                if (selection.selectMode) {
                    R.string.tv_action_exit_select
                } else {
                    R.string.tv_action_select
                }
            ),
            onClick = onToggleSelectMode,
            colors = colors,
            modifier = Modifier.padding(end = 12.dp)
        )
        TvCtaBlock(
            selectMode = selection.selectMode,
            onClick = onCreateOrDelete,
            colors = colors
        )
        TvActionChip(
            text = stringResource(R.string.setting),
            onClick = onSettingClick,
            colors = colors
        )
    }
}

@Composable
private fun TvActionChip(
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

@Composable
private fun TvCtaBlock(
    selectMode: Boolean,
    onClick: () -> Unit,
    colors: TvColors
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
        modifier = Modifier
            .height(actionHeight)
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
                painter = painterResource(
                    if (selectMode) R.drawable.delete_24px else R.drawable.add_24px
                ),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.ink
            )
            Text(
                text = stringResource(
                    if (selectMode) {
                        R.string.tv_action_delete_selected
                    } else {
                        R.string.tv_action_new_collection
                    }
                ),
                modifier = Modifier.padding(start = 10.dp),
                style = tvCtaLabelTextStyle(),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvCollectionCard(
    item: CollectionUiItem,
    selection: CollectionSelectionState,
    colors: TvColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?
) {
    val collection = item.collection
    val selected = selection.selectMode && item.id in selection.selectedIds
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
        label = "tvCardFocusScale"
    )

    val shadowColor = when {
        focused -> colors.yellow
        selected -> colors.lime
        pressed -> colors.orange
        else -> colors.ink
    }
    val fillColor = when {
        focused -> colors.surfaceAlt
        selected -> colors.selectedFill
        pressed -> colors.surfaceAlt
        else -> colors.surface
    }
    val borderColor = when {
        focused -> colors.yellow
        selected -> colors.lime
        pressed -> colors.orange
        else -> colors.paper
    }
    val borderWidth = if (focused || selected || pressed) 4.dp else 2.dp

    val cardGap = dimensionResource(R.dimen.tv_card_gap)
    val shadowOffset = dimensionResource(R.dimen.tv_card_shadow)
    val iconBoxSize = dimensionResource(R.dimen.tv_collection_icon_box)
    val selectSize = dimensionResource(R.dimen.tv_select_size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardGap)
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
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .tvHardShadow(
                offset = shadowOffset,
                shadowColor = shadowColor,
                fillColor = fillColor,
                borderColor = borderColor,
                borderWidth = borderWidth,
                cornerRadius = 14.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 22.dp, bottom = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconBox(
                    size = iconBoxSize,
                    alpha = if (selected) 0.5f else 1f,
                    colors = colors
                )
                Spacer(Modifier.weight(1f))
                TvCountBadge(count = item.comicCount, colors = colors)
            }
            Text(
                text = collection.name.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                style = tvCardTitleTextStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = cacheText(collection),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                style = tvCardMetaTextStyle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (selection.selectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp)
                    .size(selectSize)
                    .background(colors.ink, RoundedCornerShape(8.dp))
                    .border(3.dp, colors.paper, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_select_indicator),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        tint = colors.lime
                    )
                }
            }
        }
    }
}

@Composable
private fun TvIconBox(
    size: Dp,
    alpha: Float,
    colors: TvColors
) {
    Box(
        modifier = Modifier
            .size(size)
            .tvHardShadow(
                offset = 4.dp,
                shadowColor = colors.ink,
                fillColor = colors.yellow,
                borderColor = colors.ink,
                borderWidth = 3.dp,
                cornerRadius = 10.dp,
                shadowRadius = 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .alpha(alpha),
            tint = colors.ink
        )
    }
}

@Composable
private fun TvCountBadge(count: Int, colors: TvColors) {
    Box(
        modifier = Modifier
            .widthIn(min = 54.dp)
            .tvHardShadow(
                offset = 3.dp,
                shadowColor = colors.ink,
                fillColor = colors.pink,
                borderColor = colors.ink,
                borderWidth = 2.dp,
                cornerRadius = 6.dp,
                shadowRadius = 6.dp
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count 本",
            style = tvBadgeTextStyle(),
            maxLines = 1
        )
    }
}

private fun cacheText(collection: com.wrz.reading.ui.read.model.Collection): String =
    "缓存大小 " + FileUtils.formatFileSizeToString(collection.cacheSize) +
        "\n文件大小 " + FileUtils.formatFileSizeToString(collection.fileSize)
