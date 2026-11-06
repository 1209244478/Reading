package com.wrz.reading.ui.main.compose

import com.wrz.reading.R
import com.wrz.reading.ui.read.dlna.DlnaRendererManager.ReceiveMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingLabelsTest {

    @Test
    fun receiveModeLabelsMatchStrings() {
        assertEquals(R.string.receive_mode_dlna, receiveModeLabelRes(ReceiveMode.DLNA))
        assertEquals(R.string.receive_mode_wrzcast, receiveModeLabelRes(ReceiveMode.WRZCAST))
        assertEquals(R.string.receive_mode_airplay, receiveModeLabelRes(ReceiveMode.AIRPLAY))
        assertEquals(
            R.string.receive_mode_chromecast,
            receiveModeLabelRes(ReceiveMode.CHROMECAST)
        )
        assertEquals(R.string.receive_mode_all, receiveModeLabelRes(ReceiveMode.ALL))
    }
}
