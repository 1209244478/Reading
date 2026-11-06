package com.wrz.reading.ui.wheel.fragment

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.wrz.reading.R
import com.wrz.reading.app.MyApplication
import com.wrz.reading.common.BaseDialog
import com.wrz.reading.common.BaseFragment
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.wheel.activity.CreateEditActivity
import com.wrz.reading.ui.wheel.activity.WheelListActivity
import com.wrz.reading.ui.wheel.compose.WheelScreen
import com.wrz.reading.ui.wheel.compose.WheelUiState
import com.wrz.reading.ui.wheel.model.Request
import com.wrz.reading.ui.wheel.model.Wheel
import com.wrz.reading.ui.wheel.view.wheelview.WheelView

/**
 * 转盘页 Compose 实现。
 *
 * WheelView 的绘制、旋转、震动与选项点击保留在原自定义 View 中，
 * 页面框架、标题/结果、按钮禁用逻辑与电视/横屏布局迁移到 Compose。
 *
 * 保留的原行为：splashOnResume 每次回到页面重载并 setItems（当前页重置为第一组）、
 * 旋转中离开页面时停止动画但保留 isSpinning 状态、暂停时持久化转盘。
 */
class WheelComposeFragment : BaseFragment() {

    private var wheel by mutableStateOf<Wheel?>(null)
    private var uiState by mutableStateOf(WheelUiState())
    private var currentIndex by mutableIntStateOf(0)
    private var wheelRequestTick by mutableIntStateOf(0)

    private var wheelView: WheelView? = null
    private var explanationDialog: BaseDialog? = null

    override fun getLayoutResId(): Int = R.layout.fragment_wheel_compose

    override fun initView() {
        splashOnResume = true

        // 原实现每次视图重建都会得到新的 TextView/按钮（结果清空、按钮恢复可用），
        // 但 isSpinning 字段保留。这里同步复位展示状态，避免 Compose 状态跨视图存活。
        uiState = WheelUiState(isSpinning = uiState.isSpinning)
        currentIndex = 0

        val composeView = findId<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                WheelScreen(
                    wheel = wheel,
                    currentIndex = currentIndex,
                    wheelRequestTick = wheelRequestTick,
                    uiState = uiState,
                    onWheelViewCreated = { wheelView = it },
                    onWheelViewReleased = { view ->
                        if (wheelView === view) {
                            wheelView = null
                        }
                    },
                    onTempSelected = ::onTempSelected,
                    onItemSelected = ::onItemSelected,
                    onAllSelected = ::onAllSelected,
                    onTitleClick = ::showExplanation,
                    onMenuClick = ::showWheelList,
                    onStartClick = ::startSpinning,
                    onNextClick = ::nextWheel,
                    onEditClick = ::editWheel,
                    onCardClick = ::resetWheel,
                    onPointerClick = ::startSpinning
                )
            }
        }
    }

    override fun initData() {
        singleThread.execute {
            val loaded = MyApplication.wheelRepository.getLastWheel() ?: return@execute
            runOnUiIfAlive {
                if (!Wheel.isSame(loaded, wheel)) {
                    wheel = loaded
                    configViews()
                }
            }
        }
    }

    override fun configViews() {
        // 与旧实现一致：每次回到页面都会 setItems，把当前组重置为第一组
        wheelRequestTick++
        if (wheel != null) {
            currentIndex = 0
        }
    }

    override fun onPause() {
        super.onPause()
        wheelView?.stopSpin()
        val currentWheel = wheel
        singleThread.execute {
            MyApplication.wheelRepository.updateWheel(currentWheel)
        }
    }

    override fun onDestroyView() {
        wheelView = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        explanationDialog?.dismiss()
        explanationDialog = null
        super.onDestroy()
    }

    override fun goBack() {
    }

    // ==================== 旋转与结果 ====================

    private fun startSpinning() {
        if (uiState.isSpinning) return
        val currentWheel = wheel ?: return
        val view = wheelView ?: return

        val empty = try {
            view.isListEmpty()
        } catch (e: Exception) {
            true
        }
        if (empty) {
            Toast.makeText(
                activity,
                getString(R.string.message_empty_options),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        uiState = uiState.onStart()
        if (currentWheel.list.isNullOrEmpty()) {
            return
        }
        view.startSpin()
    }

    private fun onTempSelected(item: String) {
        uiState = uiState.onTempSelected(item)
    }

    private fun onItemSelected(item: String) {
        uiState = uiState.onItemSelected(item)
        Toast.makeText(
            activity,
            getString(R.string.msg_wheel_choice, item),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onAllSelected() {
        uiState = uiState.onAllSelected()
        Toast.makeText(
            activity,
            getString(R.string.notification_all_selected),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun resetWheel() {
        if (uiState.isSpinning) return
        uiState = uiState.onReset()
        wheelView?.reset()
    }

    private fun nextWheel() {
        val view = wheelView ?: return
        if (wheel == null) return
        view.setNext()
        currentIndex = view.current
    }

    // ==================== 页面跳转 ====================

    private fun editWheel() {
        val currentWheel = wheel ?: return
        val activity = activity ?: return
        CreateEditActivity.start(activity, currentWheel.id, -1, Request.Edit_Wheel)
    }

    private fun showWheelList() {
        val activity = activity ?: return
        WheelListActivity.start(activity)
    }

    private fun showExplanation() {
        val currentWheel = wheel ?: return
        val message = currentWheel.explanation ?: return
        if (message.isEmpty()) return

        val activity = activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val title = getString(R.string.wheel_explanation)
        if (explanationDialog == null) {
            explanationDialog = object : BaseDialog(activity) {
                override fun getLayoutId(): Int = R.layout.dialog_comfirm

                override fun initView() {
                    setText(R.id.dialog_title, title)
                    setText(R.id.dialog_subtitle, message)
                    setText(R.id.btn_confirm, context.getString(R.string.btn_confirm))
                    setClick(R.id.btn_cancel) { dismiss() }
                    setClick(R.id.btn_confirm) { dismiss() }
                }
            }
        }
        explanationDialog?.show()
        explanationDialog?.setText(R.id.dialog_title, title)
    }
}
