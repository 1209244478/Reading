package com.wrz.reading.ui.read.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选择模式状态回归测试：覆盖进入/退出、长按选中、选择模式下点击切换、刷新后清除选中。
 */
class CollectionSelectionStateTest {

    @Test
    fun initialStateIsNotSelecting() {
        val state = CollectionSelectionState()
        assertFalse(state.selectMode)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun toggleModeEntersAndExitsClearingSelection() {
        val selecting = CollectionSelectionState().toggleMode()
        assertTrue(selecting.selectMode)

        val withSelection = selecting.selectOnLongPress(10L)
        assertTrue(10L in withSelection.selectedIds)

        val exited = withSelection.toggleMode()
        assertFalse(exited.selectMode)
        assertTrue(exited.selectedIds.isEmpty())
    }

    @Test
    fun longPressEntersSelectModeAndSelects() {
        val state = CollectionSelectionState().selectOnLongPress(7L)
        assertTrue(state.selectMode)
        assertEquals(setOf(7L), state.selectedIds)
    }

    @Test
    fun longPressOnAlreadySelectedKeepsItSelected() {
        val state = CollectionSelectionState()
            .selectOnLongPress(7L)
            .selectOnLongPress(7L)
        assertEquals(setOf(7L), state.selectedIds)
    }

    @Test
    fun clickOutsideSelectModeDoesNothing() {
        val state = CollectionSelectionState().toggleSelectionOnClick(3L)
        assertFalse(state.selectMode)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun clickInSelectModeTogglesSelection() {
        val selected = CollectionSelectionState().toggleMode().toggleSelectionOnClick(3L)
        assertEquals(setOf(3L), selected.selectedIds)

        val deselected = selected.toggleSelectionOnClick(3L)
        assertTrue(deselected.selectedIds.isEmpty())
        assertTrue(deselected.selectMode)
    }

    @Test
    fun clearSelectionKeepsSelectMode() {
        val state = CollectionSelectionState()
            .selectOnLongPress(1L)
            .selectOnLongPress(2L)
            .clearSelection()
        assertTrue(state.selectMode)
        assertTrue(state.selectedIds.isEmpty())
    }
}
