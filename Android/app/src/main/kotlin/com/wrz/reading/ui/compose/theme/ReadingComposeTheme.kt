package com.wrz.reading.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wrz.reading.R

/**
 * 与 XML 主题（Theme.Reading / MaterialComponents DayNight）对齐的 Compose 主题。
 *
 * 颜色全部取自项目既有资源，values-night 覆盖后夜间模式自动生效；
 * 字体的标题样式对齐 TextAppearance.MaterialComponents.Headline6。
 */
private val ReadingTypography = Typography(
    h6 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.0125.em
    )
)

@Composable
fun ReadingComposeTheme(content: @Composable () -> Unit) {
    val colors = lightColors(
        primary = colorResource(R.color.ios_blue),
        primaryVariant = colorResource(R.color.ios_blue),
        secondary = colorResource(R.color.teal_200),
        secondaryVariant = colorResource(R.color.teal_700),
        background = colorResource(R.color.ios_background),
        surface = colorResource(R.color.ios_card_background),
        onPrimary = colorResource(R.color.white),
        onSecondary = colorResource(R.color.black),
        onBackground = colorResource(R.color.ios_text_primary),
        onSurface = colorResource(R.color.ios_text_primary),
        error = colorResource(R.color.apple_red)
    ).copy(isLight = !isSystemInDarkTheme())

    MaterialTheme(
        colors = colors,
        typography = ReadingTypography,
        content = content
    )
}
