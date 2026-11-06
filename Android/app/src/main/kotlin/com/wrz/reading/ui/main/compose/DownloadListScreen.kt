package com.wrz.reading.ui.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wrz.reading.R
import com.wrz.reading.ui.compose.rememberActionBarSize
import com.wrz.reading.ui.main.adapter.DownloadListAdapter
import com.wrz.reading.ui.read.model.DownloadTask

/**
 * 下载列表 Compose 页面。
 *
 * 上半部分为原 layout_topbar 的居中标题与 1dp 分隔线；列表项结构、进度条、
 * 状态颜色、暂停/继续/删除操作与原 item_download / DownloadListAdapter 对齐。
 */
@Composable
internal fun DownloadListScreen(
    tasks: List<DownloadTask>,
    showEmpty: Boolean,
    onItemClick: (DownloadTask) -> Unit,
    onActionClick: (DownloadTask) -> Unit,
    onDeleteClick: (DownloadTask) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ios_background))
    ) {
        DownloadTopBar()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (tasks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        DownloadItem(
                            task = task,
                            onClick = { onItemClick(task) },
                            onActionClick = { onActionClick(task) },
                            onDeleteClick = { onDeleteClick(task) }
                        )
                    }
                }
            }

            if (showEmpty && tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_download_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        letterSpacing = 0.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    ),
                    color = colorResource(R.color.ios_text_secondary)
                )
            }
        }
    }
}

@Composable
private fun DownloadTopBar() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberActionBarSize())
                .background(colorResource(R.color.ios_background)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.title_download_list),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.02f).em,
                    platformStyle = PlatformTextStyle(includeFontPadding = true)
                ),
                color = colorResource(R.color.ios_text_primary)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorResource(R.color.gray_200))
        )
    }
}

@Composable
private fun DownloadItem(
    task: DownloadTask,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = downloadProgressInfo(
        status = task.status,
        totalSize = task.totalSize,
        downloadedSize = task.downloadedSize
    )
    val statusColor = downloadStatusColor(task.status)
    val actionLabel = downloadActionLabel(task.status)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ios_card_background))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.title.orEmpty(),
                modifier = Modifier.weight(1f),
                style = DownloadTitleStyle,
                color = colorResource(R.color.ios_text_primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = downloadStatusText(task.status),
                modifier = Modifier.padding(start = 8.dp),
                style = DownloadSubtitleStyle,
                color = statusColor,
                maxLines = 1
            )
        }

        if (progress.indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = colorResource(R.color.ios_blue),
                backgroundColor = colorResource(R.color.ios_border)
            )
        } else {
            LinearProgressIndicator(
                progress = progress.fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = colorResource(R.color.ios_blue),
                backgroundColor = colorResource(R.color.ios_border)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = downloadProgressText(task, progress),
                modifier = Modifier.weight(1f),
                style = DownloadSubtitleStyle,
                color = colorResource(R.color.ios_text_secondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress.percent != null) {
                Text(
                    text = stringResource(R.string.download_percent, progress.percent),
                    modifier = Modifier.padding(start = 8.dp),
                    style = DownloadSubtitleStyle,
                    color = colorResource(R.color.ios_text_secondary),
                    maxLines = 1
                )
            }
            if (actionLabel != null) {
                DownloadTextAction(
                    text = actionLabel,
                    color = colorResource(R.color.ios_blue),
                    modifier = Modifier.padding(start = 4.dp),
                    onClick = onActionClick
                )
            }
            DownloadTextAction(
                text = stringResource(R.string.btn_delete),
                color = colorResource(R.color.ios_text_secondary),
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun DownloadTextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = DownloadActionStyle,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun downloadStatusText(status: Int): String = when (status) {
    DownloadTask.STATUS_PENDING -> stringResource(R.string.download_status_pending)
    DownloadTask.STATUS_DOWNLOADING -> stringResource(R.string.download_status_downloading)
    DownloadTask.STATUS_PAUSED -> stringResource(R.string.download_status_paused)
    DownloadTask.STATUS_COMPLETED -> stringResource(R.string.download_status_completed)
    else -> stringResource(R.string.download_status_failed)
}

@Composable
private fun downloadStatusColor(status: Int): Color = when {
    status == DownloadTask.STATUS_COMPLETED -> colorResource(R.color.ios_blue)
    status == DownloadTask.STATUS_FAILED -> colorResource(R.color.ios_red)
    else -> colorResource(R.color.ios_text_secondary)
}

@Composable
private fun downloadActionLabel(status: Int): String? {
    val running = status == DownloadTask.STATUS_DOWNLOADING ||
        status == DownloadTask.STATUS_PENDING
    return when {
        running -> stringResource(R.string.btn_download_pause)
        status == DownloadTask.STATUS_PAUSED || status == DownloadTask.STATUS_FAILED ->
            stringResource(R.string.btn_download_resume)

        else -> null
    }
}

@Composable
private fun downloadProgressText(
    task: DownloadTask,
    progress: DownloadProgressInfo
): String {
    val done = DownloadListAdapter.formatSize(task.downloadedSize)
    return when {
        task.status == DownloadTask.STATUS_COMPLETED -> stringResource(
            R.string.download_progress_sizes,
            done,
            done
        )

        task.totalSize > 0 -> stringResource(
            R.string.download_progress_sizes,
            done,
            DownloadListAdapter.formatSize(task.totalSize)
        )

        else -> stringResource(R.string.download_progress_unknown, done)
    }
}

private val DownloadTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 17.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val DownloadSubtitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 14.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

private val DownloadActionStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 14.sp,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)
