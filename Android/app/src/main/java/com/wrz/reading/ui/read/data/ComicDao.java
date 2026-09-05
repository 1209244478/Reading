package com.wrz.reading.ui.read.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.wrz.reading.ui.read.model.Comic;

import java.util.List;

@Dao
public interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY lastRead DESC")
    List<Comic> getAllComics();

    @Query("SELECT * FROM comics ORDER BY title ASC")
    List<Comic> getAllComicsSortedByName();

    @Query("SELECT * FROM comics ORDER BY lastRead DESC")
    List<Comic> getAllComicsSortedByLastRead();

    @Query("SELECT * FROM comics ORDER BY id DESC")
    List<Comic> getAllComicsSortedByAddedDate();

    @Query("SELECT * FROM comics ORDER BY readProgress DESC")
    List<Comic> getAllComicsSortedByProgress();

    @Query("SELECT * FROM comics WHERE id = :id")
    Comic getComicById(long id);

    // ==================== 集合相关查询 ====================

    @Query("SELECT * FROM comics WHERE collectionId IS NULL ORDER BY title ASC")
    List<Comic> getUncategorizedComics();

    @Query("SELECT * FROM comics WHERE collectionId = :collectionId ORDER BY title ASC")
    List<Comic> getComicsByCollection(long collectionId);

    @Query("UPDATE comics SET collectionId = :collectionId WHERE id IN (:comicIds)")
    void moveToCollection(Long collectionId, List<Long> comicIds);

    @Query("UPDATE comics SET collectionId = NULL WHERE id IN (:comicIds)")
    void moveOutOfCollection(List<Long> comicIds);

    @Query("UPDATE comics SET collectionId = NULL WHERE collectionId = :collectionId")
    void clearCollectionReference(long collectionId);

    @Query("DELETE FROM comics WHERE collectionId = :collectionId")
    void deleteComicsInCollection(long collectionId);

    @Insert
    long insertComic(Comic comic);

    @Update
    void updateComic(Comic comic);

    @Delete
    void deleteComic(Comic comic);

    /**
     * 仅更新视频播放进度（毫秒）与最后阅读时间，避免整行更新覆盖其他字段。
     */
    @Query("UPDATE comics SET videoPosition = :positionMs, total = :total, lastRead = :lastRead WHERE id = :id")
    void updateVideoProgress(long id, long positionMs, long lastRead, long total);

    /**
     * 仅更新 percentMap，避免整行更新的潜在问题。
     */
    @Query("UPDATE comics SET percentMap = :percentMapJson WHERE id = :id")
    void updatePercentMap(long id, String percentMapJson);

    /**
     * 仅更新封面路径，用于旧视频懒生成封面后持久化。
     */
    @Query("UPDATE comics SET coverPath = :coverPath WHERE id = :id")
    void updateCoverPath(long id, String coverPath);

}