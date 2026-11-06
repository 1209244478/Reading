package com.wrz.reading.common

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.wrz.reading.R

/**
 * 电视遥控器输入框适配（Kotlin 版）。
 * 手机/平板保持系统默认；电视端由外层容器接收焦点，确认键才唤起输入法。
 */
object TvInputHelper {

    @JvmStatic
    fun bind(editText: EditText?) {
        if (editText == null) {
            return
        }
        val parent = editText.parent as? View
        bind(editText, parent)
    }

    @JvmStatic
    fun bind(editText: EditText?, focusTarget: View?) {
        if (editText == null) {
            return
        }
        if (!isTelevision(editText.context)) {
            return
        }
        if (focusTarget == null || focusTarget === editText) {
            bindSelfFocus(editText)
            return
        }

        editText.showSoftInputOnFocus = false
        editText.isFocusable = false
        editText.isFocusableInTouchMode = false

        focusTarget.isFocusable = true
        focusTarget.isFocusableInTouchMode = true
        focusTarget.isClickable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusTarget.defaultFocusHighlightEnabled = false
        }
        focusTarget.foreground = ContextCompat.getDrawable(
            focusTarget.context,
            R.drawable.bg_tv_focusable
        )
        focusTarget.setOnClickListener {
            activate(editText)
        }
        focusTarget.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN && isConfirmKey(keyCode)) {
                activate(editText)
                true
            } else {
                false
            }
        }

        editText.setOnClickListener {
            showIme(editText)
        }
        editText.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN && isConfirmKey(keyCode)) {
                showIme(editText)
                true
            } else {
                false
            }
        }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
                hideIme(editText)
            }
        }
    }

    private fun bindSelfFocus(editText: EditText) {
        editText.showSoftInputOnFocus = false
        editText.setOnClickListener {
            showIme(editText)
        }
        editText.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN && isConfirmKey(keyCode)) {
                showIme(editText)
                true
            } else {
                false
            }
        }
    }

    private fun activate(editText: EditText) {
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.requestFocus()
        editText.text?.let { editText.setSelection(it.length) }
        showIme(editText)
    }

    @JvmStatic
    fun showIme(editText: EditText?) {
        val target = editText ?: return
        target.requestFocus()
        val imm = target.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideIme(editText: EditText) {
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }

    private fun isTelevision(context: Context): Boolean {
        val configuration = context.resources.configuration
        return (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }
}
