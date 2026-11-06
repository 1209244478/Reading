package com.wrz.reading.ui.wheel.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转盘按钮/结果状态回归测试：覆盖旋转、结果、全部选中与重置分支。
 */
class WheelUiStateTest {

    @Test
    fun startDisablesStartEditAndMenu() {
        val state = WheelUiState().onStart()
        assertTrue(state.isSpinning)
        assertFalse(state.startEnabled)
        assertFalse(state.editEnabled)
        assertFalse(state.menuEnabled)
        assertEquals(WheelResultKind.SPINNING, state.resultKind)
    }

    @Test
    fun startWhileSpinningIsIgnored() {
        val spinning = WheelUiState().onStart()
        assertEquals(spinning, spinning.onStart())
    }

    @Test
    fun tempSelectionUpdatesResultValue() {
        val state = WheelUiState().onStart().onTempSelected("A")
        assertEquals(WheelResultKind.TEMP, state.resultKind)
        assertEquals("A", state.resultValue)
        assertTrue(state.isSpinning)
    }

    @Test
    fun itemSelectedEnablesAllButtons() {
        val state = WheelUiState()
            .onStart()
            .onItemSelected("B")
        assertFalse(state.isSpinning)
        assertTrue(state.startEnabled)
        assertTrue(state.editEnabled)
        assertTrue(state.menuEnabled)
        assertEquals(WheelResultKind.RESULT, state.resultKind)
        assertEquals("B", state.resultValue)
    }

    @Test
    fun allSelectedKeepsEditDisabledAfterSpin() {
        val state = WheelUiState()
            .onStart()
            .onAllSelected()
        assertFalse(state.isSpinning)
        assertTrue(state.startEnabled)
        assertTrue(state.menuEnabled)
        assertFalse("原实现全部选中后不恢复编辑按钮", state.editEnabled)
        assertEquals(WheelResultKind.ALL_SELECTED, state.resultKind)
    }

    @Test
    fun resetEnablesStartButKeepsEditAndMenu() {
        val state = WheelUiState()
            .onStart()
            .onAllSelected()
            .onReset()
        assertFalse(state.isSpinning)
        assertTrue(state.startEnabled)
        assertFalse(state.editEnabled)
        assertTrue(state.menuEnabled)
        assertEquals(WheelResultKind.NONE, state.resultKind)
        assertEquals(null, state.resultValue)
    }
}
