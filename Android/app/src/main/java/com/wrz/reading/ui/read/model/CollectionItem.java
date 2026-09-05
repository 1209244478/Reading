package com.wrz.reading.ui.read.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 集合列表项数据：集合 + 漫画数量
 */
public class CollectionItem {
    private final Collection collection;
    private final int comicCount;

    public CollectionItem(Collection collection, int comicCount) {
        this.collection = collection;
        this.comicCount = comicCount;
    }

    public Collection getCollection() {
        return collection;
    }

    public int getComicCount() {
        return comicCount;
    }

    /** 深拷贝列表：创建新的 Collection 对象，避免修改原始数据 */
    public static List<CollectionItem> deepCopy(List<CollectionItem> data) {
        List<CollectionItem> copy = new ArrayList<>();
        if (data == null) return copy;
        for (CollectionItem item : data) {
            Collection c = new Collection();
            c.setId(item.getCollection().getId());
            c.setName(item.getCollection().getName());
            c.setCreatedAt(item.getCollection().getCreatedAt());
            copy.add(new CollectionItem(c, item.getComicCount()));
        }
        return copy;
    }
}