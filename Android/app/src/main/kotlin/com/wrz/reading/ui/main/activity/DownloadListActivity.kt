package com.wrz.reading.ui.main.activity

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.wrz.reading.R
import com.wrz.reading.common.BaseActivity
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.main.compose.DownloadListScreen
import com.wrz.reading.ui.main.utils.DialogHelper
import com.wrz.reading.ui.read.activity.VideoPlayerActivity
import com.wrz.reading.ui.read.data.VideoDownloadManager
import com.wrz.reading.ui.read.model.DownloadTask

/**
 * 下载列表 Compose 页面（Activity）。
 *
 * 保留原逻辑：
 * - onStart 注册下载监听并刷新，onStop 注销；
 * - 下载变化按 id 替换或插入到首位，删除按 id 移除；
 * - 暂停/继续、删除确认、完成后直接播放；
 * - 原列表空态仅在首次加载完成或数据变化后展示。
 */
class DownloadListActivity : BaseActivity(), VideoDownloadManager.Listener {

    private val downloadTasks = mutableStateListOf<DownloadTask>()
    private var loadedOnce by mutableStateOf(false)

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, DownloadListActivity::class.java))
        }
    }

    override fun getIntentData() {
    }

    override fun getLayoutId(): Int = R.layout.activity_download_list_compose

    override fun initToolBar() {
    }

    override fun initView() {
        val composeView = findViewById<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                DownloadListScreen(
                    tasks = downloadTasks,
                    showEmpty = loadedOnce && downloadTasks.isEmpty(),
                    onItemClick = ::openTask,
                    onActionClick = ::onActionClick,
                    onDeleteClick = ::confirmDelete
                )
            }
        }
    }

    override fun initData() {
    }

    override fun configView() {
    }

    override fun onStart() {
        super.onStart()
        VideoDownloadManager.getInstance().addListener(this)
        refresh()
    }

    override fun onStop() {
        VideoDownloadManager.getInstance().removeListener(this)
        super.onStop()
    }

    private fun refresh() {
        VideoDownloadManager.getInstance().loadTasks { tasks ->
            runOnUiIfAlive {
                downloadTasks.clear()
                downloadTasks.addAll(tasks)
                loadedOnce = true
            }
        }
    }

    override fun onDownloadChanged(task: DownloadTask) {
        runOnUiIfAlive {
            val index = downloadTasks.indexOfFirst { it.id == task.id }
            if (index >= 0) {
                downloadTasks[index] = task
            } else {
                downloadTasks.add(0, task)
            }
            loadedOnce = true
        }
    }

    override fun onDownloadRemoved(taskId: Long) {
        runOnUiIfAlive {
            val index = downloadTasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                downloadTasks.removeAt(index)
            }
            loadedOnce = true
        }
    }

    private fun onActionClick(task: DownloadTask) {
        if (task.status == DownloadTask.STATUS_DOWNLOADING ||
            task.status == DownloadTask.STATUS_PENDING
        ) {
            VideoDownloadManager.getInstance().pause(task.id)
        } else if (task.status == DownloadTask.STATUS_PAUSED ||
            task.status == DownloadTask.STATUS_FAILED
        ) {
            VideoDownloadManager.getInstance().resume(task.id)
        }
    }

    private fun confirmDelete(task: DownloadTask) {
        DialogHelper.showConfirmDialog(
            this,
            getString(R.string.confirm_deletion),
            getString(R.string.msg_download_delete_confirm, task.title)
        ) {
            VideoDownloadManager.getInstance().delete(task.id)
        }
    }

    private fun openTask(task: DownloadTask) {
        if (task.status != DownloadTask.STATUS_COMPLETED) {
            Toast.makeText(
                this,
                getString(R.string.msg_download_not_completed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        VideoPlayerActivity.start(this, -1L, task.filePath, task.title, false, 0)
    }
}
