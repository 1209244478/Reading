package com.wrz.reading.ui.main.compose

import com.wrz.reading.ui.read.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun completedAlwaysFull() {
        val info = downloadProgressInfo(
            DownloadTask.STATUS_COMPLETED,
            totalSize = 0,
            downloadedSize = 123
        )
        assertEquals(100, info.percent)
        assertEquals(1f, info.fraction)
        assertFalse(info.indeterminate)
    }

    @Test
    fun knownTotalUsesPercentAndClampsAt100() {
        val info = downloadProgressInfo(
            DownloadTask.STATUS_DOWNLOADING,
            totalSize = 1000,
            downloadedSize = 250
        )
        assertEquals(25, info.percent)
        assertEquals(0.25f, info.fraction)
        assertFalse(info.indeterminate)

        val overflow = downloadProgressInfo(
            DownloadTask.STATUS_DOWNLOADING,
            totalSize = 10,
            downloadedSize = 15
        )
        assertEquals(100, overflow.percent)
        assertEquals(1f, overflow.fraction)
    }

    @Test
    fun unknownTotalWhileRunningIsIndeterminate() {
        val info = downloadProgressInfo(
            DownloadTask.STATUS_DOWNLOADING,
            totalSize = -1,
            downloadedSize = 512
        )
        assertNull(info.percent)
        assertEquals(0f, info.fraction)
        assertTrue(info.indeterminate)
    }

    @Test
    fun unknownTotalWhenNotRunningIsDeterminateZero() {
        val info = downloadProgressInfo(
            DownloadTask.STATUS_PAUSED,
            totalSize = 0,
            downloadedSize = 512
        )
        assertNull(info.percent)
        assertEquals(0f, info.fraction)
        assertFalse(info.indeterminate)
    }
}
