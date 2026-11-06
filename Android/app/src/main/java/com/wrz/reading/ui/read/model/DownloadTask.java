package com.wrz.reading.ui.read.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 视频下载任务（接收投屏时把发送端 HTTP 地址的视频下载到本地）。
 *
 * 状态流转：PENDING → DOWNLOADING ⇄ PAUSED / FAILED → COMPLETED。
 * 断点续传依赖下载目录中的 .part 临时文件：恢复时以 .part 实际长度作为
 * Range 起点，因此进程被杀后仍可从上次位置继续下载。
 */
@Entity(tableName = "download_tasks")
public class DownloadTask {

    /** 已创建，等待下载线程取走 */
    public static final int STATUS_PENDING = 0;
    /** 下载中 */
    public static final int STATUS_DOWNLOADING = 1;
    /** 已暂停（保留 .part 文件，可继续） */
    public static final int STATUS_PAUSED = 2;
    /** 下载完成 */
    public static final int STATUS_COMPLETED = 3;
    /** 下载失败（保留 .part 文件，可重试续传） */
    public static final int STATUS_FAILED = 4;

    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 源地址（发送端本地 HTTP 服务地址） */
    private String url;
    /** 视频标题 */
    private String title;
    /** 目标文件路径；下载过程中实际写入 filePath + ".part" */
    private String filePath;
    /** 文件总大小，-1 表示服务器未返回 Content-Length */
    private long totalSize;
    /** 已下载字节数 */
    private long downloadedSize;
    private int status;
    /** 失败原因，仅 STATUS_FAILED 时有值 */
    private String error;
    private long createdAt;
    private long updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public long getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(long downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 生成快照，用于跨线程传递与 UI 展示，避免下载线程修改导致读到中间状态 */
    public DownloadTask copy() {
        DownloadTask copy = new DownloadTask();
        copy.id = id;
        copy.url = url;
        copy.title = title;
        copy.filePath = filePath;
        copy.totalSize = totalSize;
        copy.downloadedSize = downloadedSize;
        copy.status = status;
        copy.error = error;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }
}
