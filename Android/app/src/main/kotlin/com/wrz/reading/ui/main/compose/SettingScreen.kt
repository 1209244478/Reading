package com.wrz.reading.ui.main.compose

import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.ReadingToolbar
import com.wrz.reading.ui.compose.pressFeedback
import com.wrz.reading.ui.read.dlna.DlnaRendererManager

/**
 * 设置页 Compose 实现。
 *
 * 结构与原布局一致：工具栏 + 接收投屏开关 + 接收方式 + 本设备名称 + 下载列表。
 * 原布局中“退出阅读后清理”整行 visibility=gone，Compose 版同样不渲染。
 * 接收方式单选弹窗仍使用原 Android AlertDialog，保持系统样式与协议支持校验。
 */
@Composable
internal fun SettingScreen(
    receiveEnabled: Boolean,
    receiveMode: DlnaRendererManager.ReceiveMode,
    castName: String,
    onReceiveEnabledChange: (Boolean) -> Unit,
    onReceiveModeClick: () -> Unit,
    onNameClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val toolbarBackground = if (isLandscape) {
        colorResource(R.color.ios_background)
    } else {
        colorResource(R.color.ios_card_background)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ios_background))
    ) {
        ReadingToolbar(
            title = stringResource(R.string.setting),
            background = toolbarBackground
        ) { }

        ReceiveCastRow(
            receiveEnabled = receiveEnabled,
            onReceiveEnabledChange = onReceiveEnabledChange
        )

        SettingClickableRow(
            title = stringResource(R.string.setting_receive_mode),
            value = stringResource(receiveModeLabelRes(receiveMode)),
            onClick = onReceiveModeClick
        )

        SettingClickableRow(
            title = "本设备名称",
            value = castName,
            onClick = onNameClick
        )

        SettingClickableRow(
            title = stringResource(R.string.setting_download_list),
            description = stringResource(R.string.setting_download_list_desc),
            onClick = onDownloadClick
        )
    }
}

@Composable
private fun ReceiveCastRow(
    receiveEnabled: Boolean,
    onReceiveEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_receive_cast),
                style = SettingTitleStyle,
                color = colorResource(R.color.ios_text_primary)
            )
            Text(
                text = stringResource(R.string.setting_receive_cast_desc),
                style = SettingDescStyle,
                color = colorResource(android.R.color.darker_gray)
            )
        }
        Switch(
            checked = receiveEnabled,
            onCheckedChange = onReceiveEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorResource(R.color.white),
                checkedTrackColor = colorResource(R.color.ios_blue)
            )
        )
    }
}

@Composable
private fun SettingClickableRow(
    title: String,
    value: String? = null,
    description: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .hoverable(interactionSource)
            .pressFeedback(interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = SettingTitleStyle,
                color = colorResource(R.color.ios_text_primary)
            )
            if (value != null) {
                Text(
                    text = value,
                    style = SettingValueStyle,
                    color = colorResource(R.color.ios_blue)
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = SettingDescStyle,
                    color = colorResource(android.R.color.darker_gray)
                )
            }
        }

        if (focused || pressed) {
            val fill = if (focused) Color(0x18FFDD3D) else Color(0x26FF5C8A)
            val stroke = if (focused) Color(0xFFFFDD3D) else Color(0xFFFF5C8A)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(fill)
                    .border(3.dp, stroke, shape)
            )
        }
    }
}

private val SettingTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 16.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val SettingValueStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 16.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val SettingDescStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 12.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)
