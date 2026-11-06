package com.wrz.reading.ui.compose

import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wrz.reading.R

/**
 * 读取主题中的 ?attr/actionBarSize，避免硬编码 56dp 后与设备/主题覆盖不一致。
 */
@Composable
fun rememberActionBarSize(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            val px = TypedValue.complexToDimensionPixelSize(
                typedValue.data,
                context.resources.displayMetrics
            )
            with(density) { px.toDp() }
        } else {
            56.dp
        }
    }
}

/**
 * 与 XML Toolbar 对齐的通用顶栏：标题保持 Headline6 样式，操作区固定在右端。
 * 对应布局中 AppBarLayout + Toolbar 的固定（不滚动）形态。
 */
@Composable
fun ReadingToolbar(
    title: String,
    modifier: Modifier = Modifier,
    background: Color = colorResource(R.color.ios_card_background),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rememberActionBarSize())
            .background(background)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.h6,
            color = colorResource(R.color.ios_text_primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}
