package com.wrz.reading.ui.read.compose

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.SelectionState
import com.wrz.reading.ui.compose.pressFeedback
import com.wrz.reading.ui.compose.rememberActionBarSize
import com.wrz.reading.ui.compose.themeColor
import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.Comic

private enum class PhoneDetailLayout {
    Portrait,
    Landscape,
    TabletLandscape
}

private data class SortOption(val value: Int, val label: String)

private val PhoneSortOptions = listOf(
    SortOption(Collection.SORT_BY_NAME, "名称-升序"),
    SortOption(Collection.SORT_BY_NAME_REVERSE, "名称-降序"),
    SortOption(Collection.SORT_BY_SIZE, "按大小排序"),
    SortOption(Collection.SORT_BY_PROGRESS, "按阅读进度排序"),
    SortOption(Collection.SORT_BY_LAST_READ_TIME, "按阅读时间排序")
)

/**
 * 集合详情页 Compose 实现。
 *
 * 手机/平板：Toolbar（返回/搜索/编辑/选择/溢出菜单）+ 可选搜索框 + 网格 + FAB；
 * 电视端：独立的新粗野主义标题/操作区/搜索条/网格。
 * 业务逻辑（导入、移动、删除、分享、排序、远程导入）由 Fragment 持有。
 */
@Composable
internal fun CollectionDetailScreen(
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
    onSortSelected: (Int) -> Unit,
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = context.resources.getBoolean(R.bool.is_tv)
    val isTablet = context.resources.getBoolean(R.bool.is_table)

    if (isTv) {
        TvCollectionDetailScreen(
            collectionName = collectionName,
            comics = comics,
            allComicsEmpty = allComicsEmpty,
            selection = selection,
            currentSort = currentSort,
            searchVisible = searchVisible,
            searchText = searchText,
            dataVersion = dataVersion,
            resumeTick = resumeTick,
            onBack = onBack,
            onToggleSearch = onToggleSearch,
            onSearchTextChange = onSearchTextChange,
            onCycleSort = onCycleSort,
            onRefresh = onRefresh,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected,
            onMoveSelected = onMoveSelected,
            onShareSelected = onShareSelected,
            onImportOrDelete = onImportOrDelete,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick
        )
        return
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layout = when {
        isLandscape && (isTablet || configuration.smallestScreenWidthDp >= 600) ->
            PhoneDetailLayout.TabletLandscape

        isLandscape -> PhoneDetailLayout.Landscape
        else -> PhoneDetailLayout.Portrait
    }
    val columns = if (isTablet || isLandscape) 4 else 3

    PhoneCollectionDetailScreen(
        collectionName = collectionName,
        comics = comics,
        allComicsEmpty = allComicsEmpty,
        selection = selection,
        currentSort = currentSort,
        searchVisible = searchVisible,
        searchText = searchText,
        columns = columns,
        layout = layout,
        onBack = onBack,
        onToggleSearch = onToggleSearch,
        onSearchTextChange = onSearchTextChange,
        onSortSelected = onSortSelected,
        onRefresh = onRefresh,
        onToggleSelectMode = onToggleSelectMode,
        onRenameSelected = onRenameSelected,
        onMoveSelected = onMoveSelected,
        onShareSelected = onShareSelected,
        onImportOrDelete = onImportOrDelete,
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick
    )
}

// ==================== 手机 / 平板 ====================

@Composable
private fun PhoneCollectionDetailScreen(
    collectionName: String,
    comics: List<Comic>,
    allComicsEmpty: Boolean,
    selection: SelectionState,
    currentSort: Int,
    searchVisible: Boolean,
    searchText: String,
    columns: Int,
    layout: PhoneDetailLayout,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onSortSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onImportOrDelete: () -> Unit,
    onItemClick: (Comic) -> Unit,
    onItemLongClick: (Comic) -> Unit
) {
    val gridState = rememberLazyGridState()
    val pageBackground = colorResource(R.color.ios_background)
    val toolbarBackground = if (layout == PhoneDetailLayout.Portrait) {
        colorResource(R.color.ios_card_background)
    } else {
        pageBackground
    }
    val fabBottom = when (layout) {
        PhoneDetailLayout.Portrait -> 82.dp
        PhoneDetailLayout.Landscape -> 20.dp
        PhoneDetailLayout.TabletLandscape -> 108.dp
    }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        DetailToolbar(
            title = collectionName,
            selection = selection,
            layout = layout,
            background = toolbarBackground,
            currentSort = currentSort,
            onBack = onBack,
            onToggleSearch = onToggleSearch,
            onRefresh = onRefresh,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected,
            onSortSelected = onSortSelected,
            onMoveSelected = onMoveSelected,
            onShareSelected = onShareSelected
        )

        if (searchVisible) {
            PhoneSearchField(
                text = searchText,
                onTextChange = onSearchTextChange,
                focusRequester = searchFocusRequester,
                onSearchAction = { keyboardController?.hide() }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (allComicsEmpty) {
                PhoneEmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(comics, key = { it.id }) { comic ->
                        PhoneComicCard(
                            comic = comic,
                            selection = selection,
                            onClick = { onItemClick(comic) },
                            onLongClick = { onItemLongClick(comic) }
                        )
                    }
                }
            }

            val fabSource = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = onImportOrDelete,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = fabBottom)
                    .hoverable(fabSource)
                    .pressFeedback(fabSource),
                backgroundColor = colorResource(R.color.ios_blue),
                contentColor = colorResource(R.color.white),
                interactionSource = fabSource
            ) {
                Icon(
                    painter = painterResource(
                        if (selection.selectMode) R.drawable.delete_24px else R.drawable.add_24px
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colorResource(R.color.white)
                )
            }
        }
    }
}

@Composable
private fun DetailToolbar(
    title: String,
    selection: SelectionState,
    layout: PhoneDetailLayout,
    background: Color,
    currentSort: Int,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onSortSelected: (Int) -> Unit,
    onMoveSelected: () -> Unit,
    onShareSelected: () -> Unit
) {
    var mainMenuVisible by remember { mutableStateOf(false) }
    var sortMenuVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rememberActionBarSize())
            .background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailIconButton(
            iconRes = R.drawable.ic_back,
            contentDescription = "返回",
            tint = colorResource(R.color.ios_text_primary),
            size = 56.dp,
            onClick = onBack
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.h6,
            color = colorResource(R.color.ios_text_primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        DetailIconButton(
            iconRes = R.drawable.ic_search,
            contentDescription = stringResource(R.string.search),
            tint = colorResource(R.color.ios_text_primary),
            size = 40.dp,
            modifier = Modifier.padding(start = 4.dp),
            onClick = onToggleSearch
        )
        if (selection.selectMode) {
            DetailIconButton(
                iconRes = R.drawable.ic_edit,
                contentDescription = stringResource(R.string.edit_name),
                tint = colorResource(R.color.ios_blue),
                size = 40.dp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                onClick = onRenameSelected
            )
        }
        DetailIconButton(
            iconRes = if (selection.selectMode) R.drawable.ic_close else R.drawable.ic_photo_library,
            contentDescription = stringResource(R.string.into_select_mode),
            tint = colorResource(R.color.ios_text_primary),
            size = 40.dp,
            modifier = Modifier.padding(
                start = 4.dp,
                end = if (layout == PhoneDetailLayout.Portrait) 0.dp else 4.dp
            ),
            onClick = onToggleSelectMode
        )

        Box {
            DetailIconButton(
                iconRes = R.drawable.ic_more_vert,
                contentDescription = "更多",
                tint = colorResource(R.color.ios_text_primary),
                size = 48.dp,
                onClick = { mainMenuVisible = true }
            )

            DropdownMenu(
                expanded = mainMenuVisible,
                onDismissRequest = { mainMenuVisible = false }
            ) {
                DropdownMenuItem(
                    onClick = {
                        mainMenuVisible = false
                        sortMenuVisible = true
                    }
                ) {
                    Row(
                        modifier = Modifier.widthIn(min = 190.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("排序和显示", modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(-90f),
                            tint = colorResource(R.color.ios_text_secondary)
                        )
                    }
                }
                if (selection.selectMode) {
                    DropdownMenuItem(onClick = {
                        mainMenuVisible = false
                        onMoveSelected()
                    }) {
                        Text("移动", modifier = Modifier.widthIn(min = 190.dp))
                    }
                    DropdownMenuItem(onClick = {
                        mainMenuVisible = false
                        onShareSelected()
                    }) {
                        Text("分享", modifier = Modifier.widthIn(min = 190.dp))
                    }
                }
                DropdownMenuItem(onClick = {
                    mainMenuVisible = false
                    onRefresh()
                }) {
                    Text("刷新", modifier = Modifier.widthIn(min = 190.dp))
                }
            }

            DropdownMenu(
                expanded = sortMenuVisible,
                onDismissRequest = { sortMenuVisible = false }
            ) {
                PhoneSortOptions.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            sortMenuVisible = false
                            onSortSelected(option.value)
                        }
                    ) {
                        Row(
                            modifier = Modifier.widthIn(min = 190.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option.label, modifier = Modifier.weight(1f))
                            if (currentSort == option.value) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colorResource(R.color.ios_blue)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
}

@Composable
private fun PhoneSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearchAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(themeColor(com.google.android.material.R.attr.colorSurface))
            .border(1.dp, colorResource(R.color.ios_border), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = colorResource(R.color.ios_text_secondary)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    color = colorResource(R.color.ios_text_primary)
                ),
                cursorBrush = SolidColor(colorResource(R.color.ios_blue)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 14.sp,
                                    color = colorResource(R.color.ios_text_placeholder)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        if (text.isNotEmpty()) {
            IconButton(
                onClick = { onTextChange("") },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colorResource(R.color.ios_text_secondary)
                )
            }
        }
    }
}

@Composable
private fun PhoneEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_photo_library),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .alpha(0.4f),
            tint = colorResource(R.color.ios_text_secondary)
        )
        Text(
            text = "还没有漫画",
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colorResource(R.color.ios_text_primary),
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            )
        )
        Text(
            text = "点击右下角按钮添加漫画",
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = colorResource(R.color.ios_text_secondary),
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneComicCard(
    comic: Comic,
    selection: SelectionState,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selected = selection.selectMode && comic.id in selection.selectedIds
    val shape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val progressInfo = comicProgressInfo(comic)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        backgroundColor = themeColor(com.google.android.material.R.attr.colorSurface),
        elevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ComicCover(
                    comic = comic,
                    isTv = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = comic.title.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        style = ComicTitleStyle,
                        color = colorResource(R.color.text_primary),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = progressInfo.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = ComicBodyStyle,
                        color = colorResource(R.color.text_secondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comicCacheText(comic),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = ComicCacheStyle,
                        color = colorResource(R.color.text_secondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    ComicProgressBar(
                        fraction = progressInfo.fraction,
                        isTv = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    if (selection.selectMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 10.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    themeColor(com.google.android.material.R.attr.colorSurface)
                                )
                                .border(2.dp, Color(0xFFBDBDBD), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }
            }

            if (focused || pressed) {
                val fill = if (focused) Color(0x18FFDD3D) else Color(0x26FF5C8A)
                val stroke = if (focused) Color(0xFFFFDD3D) else Color(0xFFFF5C8A)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(fill)
                        .border(3.dp, stroke, shape)
                )
            }
        }
    }
}

@Composable
internal fun ComicProgressBar(
    fraction: Float,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        Box(
            modifier = modifier
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colorResource(R.color.tv_surface_alt))
                .border(2.dp, colorResource(R.color.tv_paper), RoundedCornerShape(4.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorResource(R.color.tv_yellow))
                        .border(2.dp, colorResource(R.color.tv_ink), RoundedCornerShape(4.dp))
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colorResource(R.color.progress_track))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorResource(R.color.secondary))
                )
            }
        }
    }
}

private val ComicTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val ComicBodyStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 12.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val ComicCacheStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 8.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)
