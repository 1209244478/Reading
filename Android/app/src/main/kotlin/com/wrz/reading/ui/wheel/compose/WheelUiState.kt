package com.wrz.reading.ui.wheel.compose

internal enum class WheelResultKind {
    NONE,
    SPINNING,
    TEMP,
    RESULT,
    ALL_SELECTED
}

/**
 * 转盘页按钮与结果区状态，逐项对齐原 WheelFragment 的控制逻辑：
 * - 开始旋转：开始/编辑/菜单禁用，结果区显示“正在旋转…”；
 * - 转动过程：临时结果跟随指针变化；
 * - 最终结果：开始/编辑/菜单恢复，结果区显示“选择了：X”；
 * - 全部选中：开始/菜单恢复，编辑保持禁用（原实现的既有行为）；
 * - 重置：开始恢复，结果清空，编辑/菜单保持原状态。
 */
internal data class WheelUiState(
    val isSpinning: Boolean = false,
    val startEnabled: Boolean = true,
    val editEnabled: Boolean = true,
    val menuEnabled: Boolean = true,
    val resultKind: WheelResultKind = WheelResultKind.NONE,
    val resultValue: String? = null
) {
    fun onStart(): WheelUiState =
        if (isSpinning) {
            this
        } else {
            copy(
                isSpinning = true,
                startEnabled = false,
                editEnabled = false,
                menuEnabled = false,
                resultKind = WheelResultKind.SPINNING,
                resultValue = null
            )
        }

    fun onTempSelected(value: String): WheelUiState =
        copy(resultKind = WheelResultKind.TEMP, resultValue = value)

    fun onItemSelected(value: String): WheelUiState =
        copy(
            isSpinning = false,
            startEnabled = true,
            editEnabled = true,
            menuEnabled = true,
            resultKind = WheelResultKind.RESULT,
            resultValue = value
        )

    fun onAllSelected(): WheelUiState =
        copy(
            isSpinning = false,
            startEnabled = true,
            menuEnabled = true,
            resultKind = WheelResultKind.ALL_SELECTED,
            resultValue = null
        )

    fun onReset(): WheelUiState =
        copy(
            isSpinning = false,
            startEnabled = true,
            resultKind = WheelResultKind.NONE,
            resultValue = null
        )
}
