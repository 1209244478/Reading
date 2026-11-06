package com.wrz.reading.ui.main.compose

import com.wrz.reading.ui.read.model.DownloadTask

/** 下载任务进度条状态，逻辑与原 DownloadListAdapter 完全一致。 */
internal data class DownloadProgressInfo(
    val percent: Int?,
    val fraction: Float,
    val indeterminate: Boolean
)

internal fun downloadProgressInfo(
    status: Int,
    totalSize: Long,
    downloadedSize: Long
): DownloadProgressInfo {
    if (status == DownloadTask.STATUS_COMPLETED) {
        return DownloadProgressInfo(percent = 100, fraction = 1f, indeterminate = false)
    }

    val running = status == DownloadTask.STATUS_DOWNLOADING ||
        status == DownloadTask.STATUS_PENDING

    if (totalSize > 0) {
        val percent = (downloadedSize * 100 / totalSize)
            .coerceAtMost(100L)
            .toInt()
        return DownloadProgressInfo(
            percent = percent,
            fraction = percent / 100f,
            indeterminate = false
        )
    }

    return DownloadProgressInfo(
        percent = null,
        fraction = 0f,
        indeterminate = running
    )
}
