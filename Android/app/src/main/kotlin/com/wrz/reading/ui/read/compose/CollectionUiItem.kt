package com.wrz.reading.ui.read.compose

import com.wrz.reading.ui.compose.SelectionState
import com.wrz.reading.ui.read.fragment.CollectionFragment
import com.wrz.reading.ui.read.model.Collection

/**
 * 书库列表项 UI 数据：集合 + 漫画数量。
 * 与 [com.wrz.reading.ui.read.model.CollectionItem] 对应，但专供 Compose 层使用。
 */
internal data class CollectionUiItem(
    val collection: Collection,
    val comicCount: Int
) {
    val id: Long get() = collection.id

    /** 未分组入口：可展示、可选中，但不可重命名/删除 */
    val isUncategorized: Boolean get() = collection.id == CollectionFragment.UNCATEGORIZED_ID
}

/** 书库页沿用通用选择状态，保留原类型名以减少调用点改动。 */
internal typealias CollectionSelectionState = SelectionState
