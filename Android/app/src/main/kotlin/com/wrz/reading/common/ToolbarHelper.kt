package com.wrz.reading.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.Toolbar

/**
 * 工具栏显示/隐藏动画与状态栏联动（Kotlin 版）。
 */
class ToolbarHelper(
    private val toolbar: Toolbar?,
    private val windowProvider: WindowProvider
) {

    /** 提供 Window 的回调，避免 Fragment 在构造期持有尚未 attach 的 Activity */
    fun interface WindowProvider {
        fun getWindow(): Window?
    }

    private var isVisible = true

    init {
        if (toolbar != null) {
            isVisible = toolbar.visibility == View.VISIBLE
        }
    }

    fun isAvailable(): Boolean = toolbar != null

    fun isVisible(): Boolean = isVisible

    fun hideIfVisible() {
        if (isVisible) {
            hide()
        }
    }

    fun showIfNotVisible() {
        if (!isVisible) {
            show()
        }
    }

    fun toggle() {
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        val toolbar = toolbar ?: return
        toolbar.animate()
            .translationY(-toolbar.height - 8f)
            .setInterpolator(LinearInterpolator())
            .setDuration(ANIM_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setElevation(0f)
                    hideStatusBar()
                }
            })
        isVisible = false
    }

    private fun show() {
        val toolbar = toolbar ?: return
        toolbar.animate()
            .translationY(0f)
            .setInterpolator(LinearInterpolator())
            .setDuration(ANIM_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    setElevation(1f)
                }
            })
        isVisible = true
    }

    private fun setElevation(elevation: Float) {
        toolbar?.elevation = elevation
    }

    private fun hideStatusBar() {
        val window = windowProvider.getWindow() ?: return
        val attrs = window.attributes
        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
        window.attributes = attrs
    }

    companion object {
        private const val ANIM_DURATION = 180L
    }
}
