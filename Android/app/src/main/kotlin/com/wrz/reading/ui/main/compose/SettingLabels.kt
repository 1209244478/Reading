package com.wrz.reading.ui.main.compose

import androidx.annotation.StringRes
import com.wrz.reading.R
import com.wrz.reading.ui.read.dlna.DlnaRendererManager.ReceiveMode

/** 接收方式的展示文案，弹窗与 Compose 设置页共用同一映射。 */
@StringRes
internal fun receiveModeLabelRes(mode: ReceiveMode): Int = when (mode) {
    ReceiveMode.WRZCAST -> R.string.receive_mode_wrzcast
    ReceiveMode.AIRPLAY -> R.string.receive_mode_airplay
    ReceiveMode.CHROMECAST -> R.string.receive_mode_chromecast
    ReceiveMode.ALL -> R.string.receive_mode_all
    else -> R.string.receive_mode_dlna
}
