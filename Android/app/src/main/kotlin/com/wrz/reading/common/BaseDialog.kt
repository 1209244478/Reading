package com.wrz.reading.common

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import com.wrz.reading.R

/**
 * 自定义 Dialog 基类（Kotlin 版）。
 * 保留 parentView 字段、setText/setClick 重载、show/dismiss 容错与 Java 子类覆写入口。
 */
abstract class BaseDialog @JvmOverloads constructor(
    context: Context,
    @StyleRes themeResId: Int = R.style.CustomDialogTheme
) : Dialog(context, themeResId) {

    @JvmField
    var parentView: View? = null

    private var listener: ShowOrDismissListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val view = LayoutInflater.from(context).inflate(getLayoutId(), null)
        parentView = view
        setContentView(view)

        setDialogSize()
        initView()

        if (isPressFeedbackEnabled()) {
            PressFeedback.applyTree(parentView)
        }
    }

    protected open fun isPressFeedbackEnabled(): Boolean = true

    abstract fun getLayoutId(): Int

    abstract fun initView()

    /** 子类可覆写以自定义窗口尺寸，默认 WRAP_CONTENT × WRAP_CONTENT */
    open fun setDialogSize() {
        applySize(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    open fun onShow() {
    }

    open fun onDismiss() {
    }

    protected fun applySize(width: Int, height: Int) {
        window?.setLayout(width, height)
    }

    override fun show() {
        try {
            super.show()
        } catch (e: Exception) {
            return
        }
        listener?.show()
        onShow()
    }

    override fun dismiss() {
        if (isShowing) {
            try {
                super.dismiss()
            } catch (e: Exception) {
                // 宿主已销毁时可能抛出窗口异常，忽略
            }
        }
        listener?.dismiss()
        onDismiss()
    }

    fun <T : View> findId(@IdRes id: Int): T? = parentView?.findViewById(id)

    fun setText(view: View?, text: String?) {
        if (view is TextView && text != null) {
            view.text = text
        }
    }

    fun setText(@IdRes id: Int, text: String?) {
        val view = findId<View>(id) ?: return
        if (view is TextView && text != null) {
            view.text = text
        }
    }

    fun setClick(view: View?, listener: View.OnClickListener?) {
        if (view != null) {
            view.setOnClickListener(listener)
        }
    }

    fun setClick(@IdRes id: Int, listener: View.OnClickListener?) {
        val view = findId<View>(id) ?: return
        view.isFocusable = true
        view.setOnClickListener(listener)
    }

    fun setClick(listener: View.OnClickListener?, vararg views: View?) {
        for (view in views) {
            if (view != null) {
                view.setOnClickListener(listener)
            }
        }
    }

    interface ShowOrDismissListener {
        fun show()

        fun dismiss()
    }

    fun setListener(listener: ShowOrDismissListener?) {
        this.listener = listener
    }
}
