package com.wrz.reading.ui.read.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.SelectionState
import com.wrz.reading.ui.compose.TvActionChip
import com.wrz.reading.ui.compose.TvColors
import com.wrz.reading.ui.compose.TvCtaBlock
import com.wrz.reading.ui.compose.TvFocusInEasing
import com.wrz.reading.ui.compose.tvColors
import com.wrz.reading.ui.compose.theme.tvCardMetaTextStyle
import com.wrz.reading.ui.compose.theme.tvCardTitleTextStyle
import com.wrz.reading.ui.compose.theme.tvComicProgressTextStyle
import com.wrz.reading.ui.compose.theme.tvDetailTitleTextStyle
import com.wrz.reading.ui.compose.theme.tvEmptyHintTextStyle
import com.wrz.reading.ui.compose.theme.tvEmptyTitleTextStyle
import com.wrz.reading.ui.compose.theme.tvSearchTextStyle
import com.wrz.reading.ui.compose.theme.tvSummaryTextStyle
import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.Comic

/**
 * 电视端集合详情：标题信息块 + 两行操作区 + 可选搜索条 + 6 列漫画网格。
 * 焦点、选中、按压背景与 layout-land-television 下的 selector drawable 对齐。
 */
@Composable
internal fun TvCollectionDetailScreen(
    collectionName: String,
    comics: List<Comic>,
    allComicsEmpty: Boolean,
    selection: SelectionState,
    currentSort: Int,
    searchVisible: Boolean,
    searchText: String,
    dataVersion: Int,
    resumeTick: Int,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onCycleSort: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onImportOrDelete: () -> Unit,
    onItemClick: (Comic) -> Unit,
    onItemLongClick: (Comic) -> Unit
) {
    val colors = tvColors()
    val gridState = rememberLazyGridState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val ctaFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var gridHasFocus by remember { mutableStateOf(false) }
    var anyFocus by remember { mutableStateOf(false) }

    val sortLabel = tvSortLabel(currentSort)
    val summary = stringResource(R.string.tv_summary_format, comics.size, sortLabel)
    val sortChipText = stringResource(R.string.tv_sort_format, sortLabel)

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            withFrameNanos { }
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    // 数据刷新：空列表聚焦导入 CTA；非空且焦点不在网格内时回列表首项
    LaunchedEffect(dataVersion) {
        if (dataVersion <= 0) return@LaunchedEffect
        if (allComicsEmpty) {
            if (!anyFocus) runCatching { ctaFocusRequester.requestFocus() }
        } else if (comics.isNotEmpty() && !gridHasFocus) {
            focusFirstItem(gridState, firstItemFocusRequester)
        }
    }
    // 返回页面：整页焦点丢失时回列表首项（空列表保持原行为，不主动聚焦）
    LaunchedEffect(resumeTick) {
        if (comics.isNotEmpty() && !anyFocus) {
            focusFirstItem(gridState, firstItemFocusRequester)
        }
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
        TvDetailHeader(
            collectionName = collectionName,
            summary = summary,
            sortChipText = sortChipText,
            selection = selection,
            colors = colors,
            ctaFocusRequester = ctaFocusRequester,
            onBack = onBack,
            onRefresh = onRefresh,
            onCycleSort = onCycleSort,
            onToggleSearch = onToggleSearch,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected,
            onMoveSelected = onMoveSelected,
            onShareSelected = onShareSelected,
            onImportOrDelete = onImportOrDelete
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.yellow)
        )

        if (searchVisible) {
            TvSearchRow(
                text = searchText,
                onTextChange = onSearchTextChange,
                focusRequester = searchFocusRequester,
                colors = colors,
                onSearchAction = { keyboardController?.hide() }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (allComicsEmpty) {
                TvEmptyState(colors = colors)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = pagePaddingHorizontal,
                        top = 12.dp,
                        end = pagePaddingHorizontal,
                        bottom = 12.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { gridHasFocus = it.hasFocus }
                ) {
                    itemsIndexed(comics, key = { _, comic -> comic.id }) { index, comic ->
                        TvComicCard(
                            comic = comic,
                            selection = selection,
                            colors = colors,
                            onClick = { onItemClick(comic) },
                            onLongClick = { onItemLongClick(comic) },
                            focusRequester = if (index == 0) firstItemFocusRequester else null
                        )
                    }
                }
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
private fun TvDetailHeader(
    collectionName: String,
    summary: String,
    sortChipText: String,
    selection: SelectionState,
    colors: TvColors,
    ctaFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCycleSort: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onImportOrDelete: () -> Unit
) {
    val pagePaddingHorizontal = dimensionResource(R.dimen.tv_page_padding_horizontal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = pagePaddingHorizontal, top = 20.dp, end = pagePaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvActionChip(
            text = stringResource(R.string.tv_action_back_library),
            onClick = onBack,
            colors = colors,
            modifier = Modifier.padding(end = 22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collectionName,
                style = tvDetailTitleTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 6.dp),
                style = tvSummaryTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TvCtaBlock(
            iconRes = if (selection.selectMode) R.drawable.delete_24px else R.drawable.add_24px,
            label = stringResource(
                if (selection.selectMode) {
                    R.string.tv_action_delete_selected
                } else {
                    R.string.tv_action_import
                }
            ),
            onClick = onImportOrDelete,
            colors = colors,
            modifier = Modifier.padding(start = 20.dp),
            focusRequester = ctaFocusRequester
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = pagePaddingHorizontal,
                top = 12.dp,
                end = pagePaddingHorizontal,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvActionChip(
            text = stringResource(R.string.tv_action_refresh),
            onClick = onRefresh,
            colors = colors,
            modifier = Modifier.padding(end = 10.dp)
        )
        TvActionChip(
            text = sortChipText,
            onClick = onCycleSort,
            colors = colors,
            modifier = Modifier.padding(end = 10.dp)
        )
        TvActionChip(
            text = stringResource(R.string.tv_action_search),
            onClick = onToggleSearch,
            colors = colors,
            modifier = Modifier.padding(end = 10.dp)
        )
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
            modifier = Modifier.padding(end = 10.dp)
        )
        if (selection.selectMode) {
            TvActionChip(
                text = stringResource(R.string.tv_action_rename),
                onClick = onRenameSelected,
                colors = colors,
                modifier = Modifier.padding(end = 10.dp)
            )
            TvActionChip(
                text = stringResource(R.string.tv_action_move),
                onClick = onMoveSelected,
                colors = colors,
                modifier = Modifier.padding(end = 10.dp)
            )
            TvActionChip(
                text = stringResource(R.string.tv_action_share),
                onClick = onShareSelected,
                colors = colors,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
    }
}

@Composable
private fun TvSearchRow(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    colors: TvColors,
    onSearchAction: () -> Unit
) {
    val pagePaddingHorizontal = dimensionResource(R.dimen.tv_page_padding_horizontal)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = pagePaddingHorizontal,
                top = 14.dp,
                end = pagePaddingHorizontal
            )
            .height(66.dp)
            .tvHardShadow(
                offset = 5.dp,
                shadowColor = colors.yellow,
                fillColor = colors.surfaceAlt,
                borderColor = colors.yellow,
                borderWidth = 2.dp,
                cornerRadius = 10.dp,
                shadowRadius = 10.dp
            )
            .padding(start = 22.dp, end = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = colors.yellow
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = tvSearchTextStyle(),
            cursorBrush = SolidColor(colors.paper),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tv_search_hint),
                            style = tvSearchTextStyle().copy(color = colors.muted)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun TvEmptyState(colors: TvColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .tvHardShadow(
                    offset = 6.dp,
                    shadowColor = colors.cyan,
                    fillColor = colors.surface,
                    borderColor = colors.paper,
                    borderWidth = 3.dp,
                    cornerRadius = 18.dp,
                    shadowRadius = 18.dp
                )
                .padding(start = 56.dp, top = 44.dp, end = 56.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
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
                    painter = painterResource(R.drawable.ic_photo_library),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.ink
                )
            }
            Text(
                text = stringResource(R.string.tv_empty_comics_title),
                modifier = Modifier.padding(top = 22.dp),
                style = tvEmptyTitleTextStyle()
            )
            Text(
                text = stringResource(R.string.tv_empty_comics_hint),
                modifier = Modifier.padding(top = 10.dp),
                style = tvEmptyHintTextStyle(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvComicCard(
    comic: Comic,
    selection: SelectionState,
    colors: TvColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?
) {
    val selected = selection.selectMode && comic.id in selection.selectedIds
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
        label = "tvComicFocusScale"
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
    val coverHeight = dimensionResource(R.dimen.tv_comic_cover_height)
    val selectSize = dimensionResource(R.dimen.tv_select_size)
    val progressInfo = comicProgressInfo(comic)

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
                .padding(start = 5.dp, top = 5.dp, end = 5.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight)
                    .background(colors.surfaceAlt)
            ) {
                ComicCover(
                    comic = comic,
                    isTv = true,
                    modifier = Modifier.fillMaxSize()
                )
                ComicProgressBar(
                    fraction = progressInfo.fraction,
                    isTv = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 5.dp, end = 5.dp, bottom = 6.dp)
                )
                if (selection.selectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = comic.title.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    style = tvCardTitleTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progressInfo.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    style = tvComicProgressTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = comicCacheText(comic),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    style = tvCardMetaTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun tvSortLabel(sort: Int): String = when (sort) {
    Collection.SORT_BY_NAME_REVERSE -> stringResource(R.string.tv_sort_name_desc)
    Collection.SORT_BY_SIZE -> stringResource(R.string.tv_sort_size)
    Collection.SORT_BY_PROGRESS -> stringResource(R.string.tv_sort_progress)
    Collection.SORT_BY_LAST_READ_TIME -> stringResource(R.string.tv_sort_last_read)
    else -> stringResource(R.string.tv_sort_name_asc)
}
