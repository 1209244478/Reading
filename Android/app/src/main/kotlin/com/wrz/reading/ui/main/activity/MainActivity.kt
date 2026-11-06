package com.wrz.reading.ui.main.activity

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.navigation.NavigationBarView
import com.wrz.reading.R
import com.wrz.reading.common.BaseActivity
import com.wrz.reading.ui.main.Log.LogUtil
import com.wrz.reading.ui.main.view.BottomNavBar

/**
 * 应用主壳层（Kotlin 化）。
 *
 * 该 Activity 承载的是 Fragment NavHost、悬浮玻璃底栏/HyperOS inset 适配与
 * 横竖屏导航组件切换，不属于可逐页替换的 Compose 内容页。为避免在本轮重写
 * Compose NavHost 或重新引入已规避的 inset 测量问题，保留现有 XML 与自定义
 * BottomNavBar / NavigationRailView，仅将控制逻辑迁移到 Kotlin。
 */
class MainActivity : BaseActivity() {

    private var navController: NavController? = null

    // 竖屏/平板为 BottomNavBar，手机横屏为 NavigationRailView
    private var bottomNavBar: BottomNavBar? = null
    private var railView: NavigationBarView? = null

    private val logTag = "MainActivity"

    override fun getIntentData() {
    }

    override fun getLayoutId(): Int = R.layout.activity_main

    override fun initToolBar() {
    }

    override fun initView() {
        val navView = findViewById<View>(R.id.bottom_navigation)
        if (navView is BottomNavBar) {
            bottomNavBar = navView
        } else if (navView is NavigationBarView) {
            railView = navView
        }

        val navContainer = findViewById<View>(R.id.bottom_nav_container) ?: return
        val config = resources.configuration
        val railMode = bottomNavBar == null && railView != null &&
            config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            config.smallestScreenWidthDp < 600

        ViewCompat.setOnApplyWindowInsetsListener(navContainer) { v, insets ->
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            val density = v.resources.displayMetrics.density
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (railMode) {
                val rightInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right
                lp.rightMargin = (8 * density).toInt() + rightInset
            } else {
                val buttonNav = bottomInset >= (32 * density).toInt()
                lp.bottomMargin = if (buttonNav) {
                    (8 * density).toInt() + bottomInset
                } else {
                    (8 * density).toInt()
                }
            }
            v.layoutParams = lp
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun initData() {
    }

    override fun configView() {
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupNavigation()
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHostFragment == null) {
                LogUtil.e(logTag, "NavHostFragment not found")
                return
            }

            val controller = navHostFragment.navController
            navController = controller

            if (bottomNavBar != null) {
                bottomNavBar?.setOnItemSelectedListener { itemId ->
                    navigateToTopDestination(itemId)
                }
                controller.currentDestination?.let { destination ->
                    bottomNavBar?.setSelectedItemId(destination.id)
                }
            } else if (railView != null) {
                railView?.let { NavigationUI.setupWithNavController(it, controller) }
            }

            controller.addOnDestinationChangedListener { _, destination, _ ->
                LogUtil.d(logTag, "Navigating to: " + destination.label)
                val destId = destination.id
                val topDestination = destId == R.id.mainFragment ||
                    destId == R.id.wheelFragment ||
                    destId == R.id.randomFragment ||
                    destId == R.id.settingFragment
                val tabId = if (topDestination) destId else R.id.mainFragment

                if (bottomNavBar != null) {
                    bottomNavBar?.setSelectedItemId(tabId)
                } else if (railView != null) {
                    railView?.menu?.findItem(tabId)?.isChecked = true
                }
            }
        } catch (e: Exception) {
            LogUtil.e(logTag, "Error setting up navigation: ", e)
        }
    }

    private fun navigateToTopDestination(itemId: Int): Boolean {
        val controller = navController ?: return false
        if (controller.currentDestination?.id == itemId) {
            return true
        }

        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(controller.graph.startDestinationId, true, true)
            .build()
        return try {
            controller.navigate(itemId, null, options)
            true
        } catch (e: IllegalArgumentException) {
            LogUtil.e(logTag, "Navigate to $itemId failed", e)
            false
        }
    }

    override fun goBack() {
        val controller = navController
        if (controller != null && controller.navigateUp()) {
            return
        }
        super.goBack()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            goBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun getNavController(): NavController? = navController
}
