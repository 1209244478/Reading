package com.wrz.reading.ui.read.model;

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
}