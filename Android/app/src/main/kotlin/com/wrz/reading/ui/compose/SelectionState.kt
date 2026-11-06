package com.wrz.reading.ui.compose

/**
 * 通用选择状态：列表选择模式 + 选中 ID 集合。
 *
 * 书库与集合详情两个页面都使用同一套交互语义：
 * 长按进入选择模式并选中；选择模式下点击切换；退出选择模式清空选中；
 * 数据刷新后清空选中但保留选择模式。
 */
internal data class SelectionState(
    val selectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
) {
    fun toggleMode(): SelectionState =
        if (selectMode) {
            copy(selectMode = false, selectedIds = emptySet())
        } else {
            copy(selectMode = true)
        }

    fun selectOnLongPress(id: Long): SelectionState =
        copy(selectMode = true, selectedIds = selectedIds + id)

    fun toggleSelectionOnClick(id: Long): SelectionState =
        if (selectMode) {
            copy(selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)
        } else {
            this
        }

    fun clearSelection(): SelectionState = copy(selectedIds = emptySet())
}
