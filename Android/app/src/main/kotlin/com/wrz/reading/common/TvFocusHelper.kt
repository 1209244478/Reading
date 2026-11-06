package com.wrz.reading.common

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 电视端遥控器焦点辅助（Kotlin 版）：页面刷新后把焦点落到首个可操作卡片。
 */
object TvFocusHelper {

    @JvmStatic
    fun focusFirstIfNeeded(recyclerView: RecyclerView?) {
        if (recyclerView == null) {
            return
        }
        recyclerView.post {
            if (recyclerView.adapter == null || recyclerView.adapter?.itemCount == 0) {
                return@post
            }
            if (recyclerView.findFocus() != null) {
                return@post
            }
            val layoutManager = recyclerView.layoutManager
            if (layoutManager is LinearLayoutManager) {
                layoutManager.scrollToPositionWithOffset(0, 0)
            }
            val holder = recyclerView.findViewHolderForAdapterPosition(0)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                recyclerView.requestFocus()
            }
        }
    }
}
