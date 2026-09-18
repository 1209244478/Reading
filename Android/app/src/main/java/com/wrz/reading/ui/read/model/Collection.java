package com.wrz.reading.ui.read.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 漫画集合，用于分组管理漫画。一本漫画最多属于一个集合（collectionId 可为 null 表示未分组）。
 */
@Entity(tableName = "collections")
public class Collection {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private long createdAt;

    private long cacheSize = 0; // 缓存大小

    /*-------------------------------排序相关------------------------------------*/

    /**
     * 按名称正序
     */
    public static final int SORT_BY_NAME = 0;

    /**
     * 按名称倒序
     */
    public static final int SORT_BY_NAME_REVERSE = 1;

    /**
     * 按阅读进度
     */
    public static final int SORT_BY_PROGRESS = 2;
    /**
     * 按修改时间
     */
    public static final int SORT_BY_LAST_READ_TIME = 3;

    /**
     * 按大小
     */
    public static final int SORT_BY_SIZE = 4;

    public static final int SORT_DEFAULT = SORT_BY_NAME;


    // 非持久化：仅用于 UI 选择模式
    @Ignore
    private boolean isSelected = false;

    public Collection() {
    }

    public static Collection create(String name) {
        Collection c = new Collection();
        c.name = name;
        c.createdAt = System.currentTimeMillis();
        return c;
    }

    public static Collection deepCopy(CollectionItem collectionItem) {
        if (collectionItem == null) return null;

        Collection c = new Collection();
        c.setId(collectionItem.getCollection().getId());

        c.setCreatedAt(collectionItem.getCollection().getCreatedAt());
        c.setCacheSize(collectionItem.getCollection().getCacheSize());

        return c;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(long cacheSize) {
        this.cacheSize = cacheSize;
    }

}
