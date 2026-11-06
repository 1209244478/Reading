package com.wrz.reading.common

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.wrz.reading.R
import com.wrz.reading.ui.main.dialog.LoadingDialog
import com.wrz.reading.ui.main.utils.ExecutorManager
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * Fragment 基类（Kotlin 版）。字段使用 @JvmField 保持 Java 子类直接访问兼容。
 */
abstract class BaseFragment : Fragment() {

    @JvmField
    val TAG: String = javaClass.simpleName

    @JvmField
    protected var parentView: View? = null

    @JvmField
    protected var activity: FragmentActivity? = null

    @JvmField
    protected var inflater: LayoutInflater? = null

    @JvmField
    var singleThread: ExecutorService = ExecutorManager.getInstance().singleThreadExecutor

    @JvmField
    var threadPool: ExecutorService = ExecutorManager.getInstance().threadPool

    @JvmField
    protected var mContext: Context? = null

    private var loadingDialog: LoadingDialog? = null

    @JvmField
    var mCommonToolbar: Toolbar? = null

    private var toolbarHelper: ToolbarHelper? = null

    @JvmField
    var splashOnResume: Boolean = false

    @JvmField
    var mIsToolBarVisible: Boolean = true

    abstract fun getLayoutResId(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(getLayoutResId(), container, false)
        parentView = view
        this.activity = getSupportActivity()
        mContext = this.activity
        this.inflater = inflater

        val executorManager = ExecutorManager.getInstance()
        singleThread = executorManager.singleThreadExecutor
        threadPool = executorManager.threadPool
        executorManager.addReference()

        mCommonToolbar = view.findViewById<Toolbar>(R.id.toolbar)
        mCommonToolbar?.let { toolbar ->
            toolbarHelper = ToolbarHelper(toolbar) { this.activity?.window }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData()
        configViews()
        PressFeedback.applyTree(parentView)
    }

    abstract fun initView()

    abstract fun initData()

    override fun onResume() {
        super.onResume()
        if (splashOnResume) {
            initData()
            configViews()
        }
    }

    abstract fun configViews()

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        this.activity = activity as FragmentActivity
    }

    override fun onDetach() {
        super.onDetach()
        this.activity = null
        mContext = null
    }

    /**
     * 安全地在主线程执行 UI 更新：Fragment 仍在宿主 Activity 上且未销毁时才执行。
     */
    protected fun runOnUiIfAlive(action: Runnable) {
        val act = this.activity
        if (act != null && isAdded) {
            act.runOnUiThread {
                if (isAdded) {
                    action.run()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ExecutorManager.getInstance().removeReference()
    }

    override fun onDestroy() {
        dismissLoading()
        super.onDestroy()
    }

    fun <T : View> findId(@IdRes id: Int): T? = parentView?.findViewById(id)

    fun getSupportActivity(): FragmentActivity? = super.getActivity()

    fun getApplicationContext(): Context? {
        return this.activity?.applicationContext ?: super.getActivity()?.applicationContext
    }

    protected fun getParentView(): View? = parentView

    fun getLoadingDialog(): LoadingDialog? {
        if (loadingDialog == null) {
            val act = this.activity ?: getSupportActivity()
            if (act == null || act.isFinishing || act.isDestroyed) {
                return null
            }
            loadingDialog = LoadingDialog.instance(act).also {
                it.setCancelable(false)
            }
        }
        return loadingDialog
    }

    fun hideLoading() {
        loadingDialog?.hide()
    }

    fun showLoading() {
        val dialog = getLoadingDialog()
        if (dialog != null && !dialog.isShowing) {
            dialog.show()
        }
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

    protected fun setClick(view: View?, listener: View.OnClickListener?) {
        if (view != null) {
            view.isFocusable = true
            view.setOnClickListener(listener)
        }
    }

    protected fun setClick(view: View?, runnable: Runnable?) {
        if (view != null && runnable != null) {
            view.isFocusable = true
            view.setOnClickListener { runnable.run() }
        }
    }

    protected fun setClick(viewId: Int, listener: View.OnClickListener?) {
        val view = findId<View>(viewId)
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

    protected fun setText(text: String?, vararg views: Int) {
        if (text.isNullOrEmpty()) {
            return
        }
        for (viewId in views) {
            val view = findId<TextView>(viewId)
            if (view != null) {
                view.text = text
            }
        }
    }

    protected fun gone(vararg views: View?) {
        for (view in views) {
            view?.visibility = View.GONE
        }
    }

    protected fun visible(vararg views: View?) {
        for (view in views) {
            view?.visibility = View.VISIBLE
        }
    }

    protected fun isVisible(view: View?): Boolean = view?.visibility == View.VISIBLE

    fun hideToolBarIfVisible() {
        val helper = toolbarHelper ?: return
        helper.hideIfVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    fun showToolBarIfNotVisible() {
        val helper = toolbarHelper ?: return
        helper.showIfNotVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    fun toggleToolBarVisibleOrGone() {
        val helper = toolbarHelper ?: return
        helper.toggle()
        mIsToolBarVisible = helper.isVisible()
    }

    fun showToolbar() {
        val helper = toolbarHelper ?: return
        helper.showIfNotVisible()
        mIsToolBarVisible = helper.isVisible()
    }

    fun initBackCallback() {
        val act = getSupportActivity() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            act.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                OnBackInvokedCallback { goBack() }
            )
        } else {
            act.onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        goBack()
                    }
                }
            )
        }
    }

    fun isLand(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun isTv(): Boolean = resources.getBoolean(R.bool.is_tv)

    abstract fun goBack()

    fun isStorageDirectReadable(): Boolean {
        val root = Environment.getExternalStorageDirectory()
        val files: Array<File>? = root?.listFiles()
        return files != null && files.isNotEmpty()
    }
}
