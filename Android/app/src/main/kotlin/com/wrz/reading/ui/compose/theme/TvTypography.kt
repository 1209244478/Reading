package com.wrz.reading.ui.compose.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wrz.reading.R

/**
 * 电视端新粗野主义字体：对齐 values/tv_styles.xml 中声明的
 * sans-serif-black / sans-serif-condensed / sans-serif 字体族。
 */
private val TvBlackFont = FontFamily(Typeface.create("sans-serif-black", Typeface.BOLD))

private val TvCondensedFont =
    FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private val TvCondensedBoldFont =
    FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

@Composable
internal fun tvDisplayTextStyle(): TextStyle = TextStyle(
    fontFamily = TvBlackFont,
    fontSize = 48.sp,
    letterSpacing = 0.03.em,
    color = colorResource(R.color.tv_paper)
)

@Composable
internal fun tvKickerTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 15.sp,
    letterSpacing = 0.22.em,
    color = colorResource(R.color.tv_yellow)
)

@Composable
internal fun tvActionChipTextStyle(color: Color): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 17.sp,
    letterSpacing = 0.08.em,
    color = color
)

@Composable
internal fun tvCtaLabelTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 18.sp,
    color = colorResource(R.color.tv_ink)
)

@Composable
internal fun tvCardTitleTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 21.sp,
    color = colorResource(R.color.tv_paper)
)

@Composable
internal fun tvCardMetaTextStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 12.sp,
    color = colorResource(R.color.tv_muted)
)

@Composable
internal fun tvBadgeTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 14.sp,
    color = colorResource(R.color.tv_ink)
)

@Composable
internal fun tvDetailTitleTextStyle(): TextStyle = TextStyle(
    fontFamily = TvBlackFont,
    fontSize = 32.sp,
    letterSpacing = 0.03.em,
    color = colorResource(R.color.tv_paper)
)

@Composable
internal fun tvSummaryTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedFont,
    fontSize = 17.sp,
    letterSpacing = 0.04.em,
    color = colorResource(R.color.tv_muted)
)

@Composable
internal fun tvComicProgressTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 14.sp,
    color = colorResource(R.color.tv_yellow)
)

@Composable
internal fun tvCoverTitleTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedBoldFont,
    fontSize = 22.sp,
    color = colorResource(R.color.tv_muted)
)

@Composable
internal fun tvEmptyTitleTextStyle(): TextStyle = TextStyle(
    fontFamily = TvBlackFont,
    fontSize = 30.sp,
    letterSpacing = 0.02.em,
    color = colorResource(R.color.tv_paper)
)

@Composable
internal fun tvEmptyHintTextStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 16.sp,
    color = colorResource(R.color.tv_muted)
)

@Composable
internal fun tvSearchTextStyle(): TextStyle = TextStyle(
    fontFamily = TvCondensedFont,
    fontSize = 19.sp,
    color = colorResource(R.color.tv_paper)
)
