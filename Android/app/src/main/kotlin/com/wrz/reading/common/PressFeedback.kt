package com.wrz.reading.common

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import android.widget.AbsSeekBar
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.wrz.reading.R

/**
 * 全局点击反馈（Kotlin 版）：按压缩放 + 释放回弹 + 悬停微动效。
 * 保持 Java 静态调用方式（@JvmStatic），行为与原实现一致。
 */
object PressFeedback {

    private const val PRESSED_SCALE = 0.94f
    private const val HOVER_SCALE = 1.03f
    private const val PRESS_DURATION_MS = 120L
    private const val RELEASE_DURATION_MS = 200L
    private const val HOVER_DURATION_MS = 150L

    private val PRESS_INTERPOLATOR: Interpolator = DecelerateInterpolator()
    private val RELEASE_INTERPOLATOR: Interpolator = OvershootInterpolator(1.6f)

    private val CHILD_LISTENER = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(child: View) {
            apply(child)
        }

        override fun onChildViewDetachedFromWindow(child: View) {
            reset(child)
        }
    }

    @JvmStatic
    fun applyTree(root: View?) {
        if (root == null) {
            return
        }
        traverse(root)
    }

    private fun traverse(view: View) {
        if (view is RecyclerView) {
            view.addOnChildAttachStateChangeListener(CHILD_LISTENER)
        }
        apply(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverse(view.getChildAt(i))
            }
        }
    }

    @JvmStatic
    fun apply(view: View?) {
        if (view == null ||
            view.getTag(R.id.tag_press_feedback) != null ||
            view is WebView ||
            view is PhotoView ||
            view is AbsSeekBar ||
            (!view.hasOnClickListeners() && !view.isClickable)
        ) {
            return
        }
        view.setTag(R.id.tag_press_feedback, true)
        view.setOnTouchListener(PressScaleListener())
        view.setOnHoverListener(HoverScaleListener())
    }

    @JvmStatic
    fun reset(view: View?) {
        if (view == null) {
            return
        }
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun animateScale(
        view: View,
        target: Float,
        durationMs: Long,
        interpolator: Interpolator
    ) {
        view.animate().cancel()
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(durationMs)
            .setInterpolator(interpolator)
            .start()
    }

    private class PressScaleListener : View.OnTouchListener {
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    animateScale(view, PRESSED_SCALE, PRESS_DURATION_MS, PRESS_INTERPOLATOR)

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    animateScale(view, 1f, RELEASE_DURATION_MS, RELEASE_INTERPOLATOR)
            }
            return false
        }
    }

    private class HoverScaleListener : View.OnHoverListener {
        override fun onHover(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER ->
                    animateScale(view, HOVER_SCALE, HOVER_DURATION_MS, PRESS_INTERPOLATOR)

                MotionEvent.ACTION_HOVER_EXIT ->
                    animateScale(view, 1f, RELEASE_DURATION_MS, RELEASE_INTERPOLATOR)
            }
            return false
        }
    }
}
