package com.wrz.reading.model;

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
}
