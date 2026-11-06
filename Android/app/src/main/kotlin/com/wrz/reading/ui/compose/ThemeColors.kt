package com.wrz.reading.ui.compose

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 按当前主题解析颜色属性（如 ?attr/colorSurface），保证夜间模式与原 XML 一致。
 */
@Composable
fun themeColor(@AttrRes attr: Int): Color {
    val context = LocalContext.current
    return remember(context, attr) {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(attr, typedValue, true)) {
            Color(typedValue.data)
        } else {
            Color.Transparent
        }
    }
}
