package com.wrz.reading.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.wrz.reading.model.Wheel;

import java.util.List;

@Dao
public interface WheelDao {

    // ==================== 模板 ====================

    @Query("SELECT * FROM wheels WHERE isTemplate = 1 ORDER BY category ASC, id ASC")
    List<Wheel> getAllTemplates();

    @Query("DELETE FROM wheels WHERE isTemplate = 1")
    void deleteAllTemplates();

    @Query("SELECT * FROM wheels WHERE isTemplate = 1 AND id = :id LIMIT 1")
    Wheel getTemplateById(String id);

    // ==================== 用户转盘 ====================

    @Query("SELECT * FROM wheels WHERE isTemplate = 0 ORDER BY id ASC")
    List<Wheel> getAllUserWheels();

    @Query("DELETE FROM wheels WHERE isTemplate = 0")
    void deleteAllUserWheels();

    @Query("SELECT * FROM wheels WHERE isTemplate = 0 AND id = :id LIMIT 1")
    Wheel getUserWheelById(String id);

    @Query("SELECT * FROM wheels WHERE isTemplate = 0 AND isLastTime = 1 LIMIT 1")
    Wheel getLastUsedWheel();

    @Query("SELECT COUNT(*) FROM wheels WHERE isTemplate = 0")
    int getUserWheelCount();

    @Query("UPDATE wheels SET isLastTime = 0 WHERE isTemplate = 0")
    void clearAllLastTime();

    @Query("SELECT * FROM wheels WHERE id = :id LIMIT 1")
    Wheel getById(String id);

    // ==================== 通用 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Wheel wheel);

    @Insert
    void insert(Wheel wheel);

    @Update
    void update(Wheel wheel);

    @Delete
    void delete(Wheel wheel);

    @Query("DELETE FROM wheels")
    void deleteAll();
}
