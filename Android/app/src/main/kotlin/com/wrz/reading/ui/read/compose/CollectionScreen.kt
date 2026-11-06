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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.ReadingToolbar
import com.wrz.reading.ui.compose.pressFeedback
import com.wrz.reading.ui.compose.themeColor
import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.utils.FileUtils

private enum class PhoneCollectionLayout {
    Portrait,
    Landscape,
    TabletLandscape
}

/**
 * 书库首页 Compose 实现。
 *
 * 手机/平板沿用原 CoordinatorLayout + Toolbar + RecyclerView 网格 + FAB 结构；
 * 电视端使用独立的新粗野主义页面（见 [TvCollectionScreen]）。
 */
@Composable
internal fun CollectionScreen(
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = context.resources.getBoolean(R.bool.is_tv)
    val isTablet = context.resources.getBoolean(R.bool.is_table)

    if (isTv) {
        TvCollectionScreen(
            items = items,
            selection = selection,
            resumeTick = resumeTick,
            dataVersion = dataVersion,
            onRefresh = onRefresh,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected,
            onCreateOrDelete = onCreateOrDelete,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onSettingClick = onSettingClick
        )
        return
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layout = when {
        isLandscape && (isTablet || configuration.smallestScreenWidthDp >= 600) ->
            PhoneCollectionLayout.TabletLandscape

        isLandscape -> PhoneCollectionLayout.Landscape
        else -> PhoneCollectionLayout.Portrait
    }
    val columns = if (isTablet || isLandscape) 4 else 3

    PhoneCollectionScreen(
        items = items,
        selection = selection,
        layout = layout,
        columns = columns,
        onRefresh = onRefresh,
        onToggleSelectMode = onToggleSelectMode,
        onRenameSelected = onRenameSelected,
        onCreateOrDelete = onCreateOrDelete,
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick
    )
}

// ==================== 手机 / 平板 ====================

@Composable
private fun PhoneCollectionScreen(
    items: List<CollectionUiItem>,
    selection: CollectionSelectionState,
    layout: PhoneCollectionLayout,
    columns: Int,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit,
    onCreateOrDelete: () -> Unit,
    onItemClick: (CollectionUiItem) -> Unit,
    onItemLongClick: (CollectionUiItem) -> Unit
) {
    val gridState = rememberLazyGridState()
    val pageBackground = colorResource(R.color.ios_background)
    val toolbarBackground = if (layout == PhoneCollectionLayout.Portrait) {
        colorResource(R.color.ios_card_background)
    } else {
        pageBackground
    }
    val fabBottom = when (layout) {
        PhoneCollectionLayout.Portrait -> 82.dp
        PhoneCollectionLayout.Landscape -> 20.dp
        PhoneCollectionLayout.TabletLandscape -> 108.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        CollectionToolbar(
            selection = selection,
            layout = layout,
            background = toolbarBackground,
            onRefresh = onRefresh,
            onToggleSelectMode = onToggleSelectMode,
            onRenameSelected = onRenameSelected
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    PhoneCollectionCard(
                        item = item,
                        selection = selection,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
                }
            }

            val fabSource = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = onCreateOrDelete,
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
private fun CollectionToolbar(
    selection: CollectionSelectionState,
    layout: PhoneCollectionLayout,
    background: Color,
    onRefresh: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onRenameSelected: () -> Unit
) {
    ReadingToolbar(
        title = stringResource(R.string.tv_library_title),
        background = background
    ) {
        ToolbarIconButton(
            iconRes = R.drawable.ic_refresh,
            contentDescription = stringResource(R.string.refresh),
            tint = colorResource(R.color.ios_text_primary),
            onClick = onRefresh
        )
        if (selection.selectMode) {
            Spacer(Modifier.width(4.dp))
            ToolbarIconButton(
                iconRes = R.drawable.ic_edit,
                contentDescription = stringResource(R.string.edit_name),
                tint = colorResource(R.color.ios_blue),
                onClick = onRenameSelected
            )
            Spacer(Modifier.width(4.dp))
        }
        // 原布局中选择按钮始终有 4dp marginStart；横屏额外有 4dp marginEnd
        Spacer(Modifier.width(4.dp))
        ToolbarIconButton(
            iconRes = if (selection.selectMode) R.drawable.ic_close else R.drawable.ic_photo_library,
            contentDescription = stringResource(R.string.into_select_mode),
            tint = colorResource(R.color.ios_text_primary),
            onClick = onToggleSelectMode
        )
        if (layout != PhoneCollectionLayout.Portrait) {
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun ToolbarIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneCollectionCard(
    item: CollectionUiItem,
    selection: CollectionSelectionState,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val collection = item.collection
    val selected = selection.selectMode && item.id in selection.selectedIds
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(colorResource(R.color.ios_card_background))
            .border(1.dp, colorResource(R.color.ios_border), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (selected) 0.5f else 1f),
                tint = colorResource(R.color.ios_blue)
            )
            Text(
                text = collection.name.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                style = PhoneCardNameStyle,
                color = colorResource(R.color.ios_text_primary),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.comicCount} 本",
                modifier = Modifier.padding(top = 4.dp),
                style = PhoneCardCountStyle,
                color = colorResource(R.color.ios_text_secondary)
            )
            Text(
                text = cacheText(collection),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = PhoneCardCacheStyle,
                color = colorResource(R.color.text_secondary),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (selection.selectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(themeColor(com.google.android.material.R.attr.colorSurface))
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

        // 对应 item_collection.xml 的 foreground bg_tv_focusable（焦点黄、按压粉）
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

private fun cacheText(collection: Collection): String =
    "缓存大小 " + FileUtils.formatFileSizeToString(collection.cacheSize) +
        "\n文件大小 " + FileUtils.formatFileSizeToString(collection.fileSize)

// 原 item_collection.xml 的 TextView 默认 includeFontPadding=true，这里显式保留以对齐卡片高度
private val PhoneCardNameStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val PhoneCardCountStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 12.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val PhoneCardCacheStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 8.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)
