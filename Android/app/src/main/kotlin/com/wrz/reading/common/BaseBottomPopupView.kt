package com.wrz.reading.common

import android.content.Context
import com.lxj.xpopup.core.BottomPopupView

/**
 * XPopup 底部弹窗基类（Kotlin 版），保持 Java 子类的四个抽象方法签名。
 */
abstract class BaseBottomPopupView(context: Context) : BottomPopupView(context) {

    override fun getImplLayoutId(): Int = getLayoutId()

    override fun onCreate() {
        super.onCreate()
        initView()
        PressFeedback.applyTree(this)
    }

    override fun onShow() {
        super.onShow()
        OnShow()
    }

    override fun onDismiss() {
        super.onDismiss()
        OnDismiss()
    }

    abstract fun getLayoutId(): Int

    abstract fun initView()

    abstract fun OnShow()

    abstract fun OnDismiss()
}
