package com.wrz.reading.ui.random.fragment

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.lxj.xpopup.XPopup
import com.wrz.reading.R
import com.wrz.reading.app.MyApplication
import com.wrz.reading.common.BaseFragment
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.main.popupView.RandomSettingPopup
import com.wrz.reading.ui.random.compose.RandomNumberGenerator
import com.wrz.reading.ui.random.compose.RandomScreen
import java.util.Random

/**
 * 随机数页面的 Compose 实现。
 *
 * 业务逻辑（设置读取、随机数生成、设置弹窗、Toast）与原 [RandomFragment] 保持一致，
 * 仅将视图层替换为 Compose。原 Fragment 与布局保留作为回退备份。
 */
class RandomComposeFragment : BaseFragment() {

    /** 已生成的随机数，对应原 Fragment 的 list 字段 */
    private val items = mutableStateListOf<String>()

    private var randomStart by mutableIntStateOf(0)
    private var randomEnd by mutableIntStateOf(0)
    private var randomQuantity by mutableIntStateOf(1)

    /** 递增后触发列表滚动到末尾，对应原 rv_list.post { scrollToPosition(last) } */
    private var scrollToEndRequest by mutableIntStateOf(0)

    override fun getLayoutResId(): Int = R.layout.fragment_random_compose

    override fun initView() {
        splashOnResume = true
        val composeView = findId<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                RandomScreen(
                    items = items,
                    start = randomStart,
                    end = randomEnd,
                    scrollToEndRequest = scrollToEndRequest,
                    onGenerateClick = ::generateUnique,
                    onClearClick = ::clearAll,
                    onSettingsClick = ::showSettingPopup
                )
            }
        }
    }

    override fun initData() {
        reloadSettings()
    }

    override fun configViews() {
        // Compose 随状态自动刷新，无需手动更新控件
    }

    override fun goBack() {
        // 与旧实现一致：返回由 MainActivity 的 NavController 处理
    }

    /** 与原 initData() 一致：每次回到页面都从偏好设置刷新范围与数量 */
    private fun reloadSettings() {
        val manager = MyApplication.manager ?: return
        randomStart = manager.getRandomStart()
        randomEnd = manager.getRandomEnd()
        randomQuantity = manager.getRandomQuantity()
    }

    /** 与原实现一致：XPopup 设置弹窗保持不变，保存后仅刷新数据 */
    private fun showSettingPopup() {
        val ctx = context ?: return
        val popup = RandomSettingPopup(ctx)
        popup.setOnSaveListener { reloadSettings() }
        XPopup.Builder(ctx)
            .hasShadowBg(false)
            .isViewMode(true)
            .autoOpenSoftInput(false)
            .isDestroyOnDismiss(false)
            .asCustom(popup)
            .show()
    }

    /** 与原 generateUnique() 完全一致的生成规则（含不允许重复时的重试与耗尽提示） */
    private fun generateUnique() {
        val ctx = context ?: return
        val random = Random()
        for (i in 0 until randomQuantity) {
            val number = RandomNumberGenerator.generate(
                random = random,
                start = randomStart,
                end = randomEnd,
                existing = items,
                allowDuplicates = MyApplication.manager.getRandomAllowDuplicates()
            )
            if (number == RandomNumberGenerator.EXHAUSTED) {
                Toast.makeText(ctx, getString(R.string.msg_max_count_reached), Toast.LENGTH_SHORT)
                    .show()
                break
            }
            items.add(number.toString())
        }
        scrollToEndRequest++
    }

    private fun clearAll() {
        items.clear()
    }
}
