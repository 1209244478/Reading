package com.wrz.reading.common

import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wrz.reading.R
import com.wrz.reading.ui.main.Log.LogUtil
import com.wrz.reading.ui.main.dialog.LoadingDialog
import com.wrz.reading.ui.main.utils.ExecutorManager
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * Activity 基类（Kotlin 版）。字段使用 @JvmField 保持 Java 子类直接访问兼容。
 */
abstract class BaseActivity : AppCompatActivity() {

    @JvmField
    val TAG: String = javaClass.simpleName

    @JvmField
    var singleThread: ExecutorService = ExecutorManager.getInstance().singleThreadExecutor

    @JvmField
    var threadPool: ExecutorService = ExecutorManager.getInstance().threadPool

    private var loadingDialog: LoadingDialog? = null

    @JvmField
    var mCommonToolbar: Toolbar? = null

    private var toolbarHelper: ToolbarHelper? = null

    @JvmField
    var mIsToolBarVisible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用共享线程池，并引用计数
        val executorManager = ExecutorManager.getInstance()
        singleThread = executorManager.singleThreadExecutor
        threadPool = executorManager.threadPool
        executorManager.addReference()

        setContentView(getLayoutId())
        LogUtil.e(TAG, "setContentView.")

        getIntentData()
        LogUtil.e(TAG, "getIntentData.")

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        mCommonToolbar = toolbar
        if (toolbar != null) {
            toolbarHelper = ToolbarHelper(toolbar) { window }
            initToolBar()
            setSupportActionBar(toolbar)
            toolbar.setNavigationOnClickListener { goBack() }
        }

        LogUtil.e(TAG, "setSupportActionBar.")

        initView()
        initData()
        LogUtil.e(TAG, "initData.")
        configView()
        LogUtil.e(TAG, "configView.")

        if (isPressFeedbackEnabled()) {
            PressFeedback.applyTree(window?.decorView)
        }

        applyLandscapeSystemBar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })
    }

    protected open fun isPressFeedbackEnabled(): Boolean = true

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    protected open fun applyLandscapeSystemBar() {
        val window = window ?: return
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    protected fun hideSystemBar() {
        val window = window ?: return
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    override fun onDestroy() {
        dismissLoading()
        super.onDestroy()
        ExecutorManager.getInstance().removeReference()
    }

    abstract fun getIntentData()

    abstract fun getLayoutId(): Int

    protected open fun initToolBar() {
    }

    abstract fun initView()

    abstract fun initData()

    abstract fun configView()

    protected open fun goBack() {
        finish()
    }

    open fun hideToolBarIfVisible() {
        val helper = toolbarHelper ?: return
        helper.hideIfVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    open fun showToolBarIfNotVisible() {
        val helper = toolbarHelper ?: return
        helper.showIfNotVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    open fun toggleToolBarVisibleOrGone() {
        val helper = toolbarHelper ?: return
        helper.toggle()
        mIsToolBarVisible = helper.isVisible()
    }

    open fun showToolbar() {
        val helper = toolbarHelper ?: return
        helper.showIfNotVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    open fun hideToolbar() {
        val window = window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    protected fun setClick(view: View?, listener: View.OnClickListener?) {
        if (view != null) {
            view.isFocusable = true
            view.setOnClickListener(listener)
        }
    }

    protected fun setClick(viewId: Int, listener: View.OnClickListener?) {
        val view = findViewById<View>(viewId)
        if (view != null) {
            view.isFocusable = true
            view.setOnClickListener(listener)
        }
    }

    protected fun setClick(listener: View.OnClickListener?, vararg views: View?) {
        for (view in views) {
            if (view != null) {
                view.isFocusable = true
                view.setOnClickListener(listener)
            }
        }
    }

    protected fun setText(text: String?, vararg views: View?) {
        for (view in views) {
            if (view is TextView) {
                view.text = text
            }
        }
    }

    protected fun setText(strId: Int, vararg views: View?) {
        for (view in views) {
            if (view is TextView) {
                view.setText(strId)
            }
        }
    }

    protected fun setText(text: String?, vararg views: Int) {
        for (viewId in views) {
            val view = findViewById<View>(viewId)
            if (view is TextView) {
                view.text = text
            }
        }
    }

    protected fun setText(strId: Int, vararg views: Int) {
        for (viewId in views) {
            val view = findViewById<View>(viewId)
            if (view is TextView) {
                view.setText(strId)
            }
        }
    }

    protected fun gone(vararg views: View?) {
        for (view in views) {
            view?.visibility = View.GONE
        }
    }

    protected fun gone(vararg views: Int) {
        for (viewId in views) {
            findViewById<View>(viewId)?.visibility = View.GONE
        }
    }

    protected fun visible(vararg views: View?) {
        for (view in views) {
            view?.visibility = View.VISIBLE
        }
    }

    protected fun visible(vararg views: Int) {
        for (viewId in views) {
            findViewById<View>(viewId)?.visibility = View.VISIBLE
        }
    }

    /**
     * 安全地在主线程执行 UI 更新：Activity 未销毁时才执行。
     */
    protected fun runOnUiIfAlive(action: Runnable) {
        if (!isDestroyed && !isFinishing) {
            runOnUiThread(action)
        }
    }

    fun getLoadingDialog(): LoadingDialog? {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialog.instance(this).also {
                it.setCancelable(false)
            }
        }
        return loadingDialog
    }

    fun hideLoading() {
        loadingDialog?.hide()
    }

    fun showLoading() {
        if (isDestroyed || isFinishing) {
            return
        }
        getLoadingDialog()?.show()
    }

    fun dismissLoading() {
        val dialog = loadingDialog ?: return
        try {
            dialog.dismiss()
        } catch (e: Exception) {
            // 异步回调时宿主可能已销毁，忽略窗口异常
        }
        loadingDialog = null
    }

    fun isLand(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun isTv(): Boolean = resources.getBoolean(R.bool.is_tv)

    fun isStorageDirectReadable(): Boolean {
        val root = Environment.getExternalStorageDirectory()
        val files: Array<File>? = root?.listFiles()
        return files != null && files.isNotEmpty()
    }
}
