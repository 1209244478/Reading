package com.wrz.reading.ui.read.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.wrz.reading.ui.read.model.Collection;

import java.util.List;

@Dao
public interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    List<Collection> getAllCollections();

    @Query("SELECT * FROM collections WHERE id = :id")
    Collection getCollectionById(long id);

    @Insert
    long insertCollection(Collection collection);

    @Update
    void updateCollection(Collection collection);

    @Delete
    void deleteCollection(Collection collection);

    /**
     * 获取集合中的漫画数量
     */
    @Query("SELECT COUNT(*) FROM comics WHERE collectionId = :collectionId")
    int getComicCountInCollection(long collectionId);
}
