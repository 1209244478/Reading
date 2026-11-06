package com.wrz.reading.ui.read.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.wrz.reading.ui.read.model.DownloadTask;

import java.util.List;

@Dao
public interface DownloadDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    List<DownloadTask> getAll();

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    DownloadTask getById(long id);

    /** 按源地址查找已有任务（同一视频避免重复下载） */
    @Query("SELECT * FROM download_tasks WHERE url = :url ORDER BY id DESC LIMIT 1")
    DownloadTask findByUrl(String url);

    @Insert
    long insert(DownloadTask task);

    @Update
    void update(DownloadTask task);

    @Query("DELETE FROM download_tasks WHERE id = :id")
    void deleteById(long id);

    /** 进程重启后把未完成状态统一重置为暂停（保留 .part 断点） */
    @Query("UPDATE download_tasks SET status = :toStatus WHERE status = :fromStatus")
    void updateStatus(int fromStatus, int toStatus);
}
