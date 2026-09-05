package com.wrz.reading.ui.read.utils;

import androidx.recyclerview.widget.DiffUtil;

import com.wrz.reading.ui.read.model.CollectionItem;
import com.wrz.reading.ui.read.model.Comic;

import java.util.List;
import java.util.Objects;

public class DiffCallBack {

    /**
     * DiffUtil 回调：按 id 判定同一项，按可视字段判定内容是否变化。
     * Comic 未重写 equals，不能依赖 List.indexOf/contains。
     */
    public static class ComicDiffCallback extends DiffUtil.Callback {
        private final List<Comic> oldList;
        private final List<Comic> newList;

        public ComicDiffCallback(List<Comic> oldList, List<Comic> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Comic a = oldList.get(oldItemPosition);
            Comic b = newList.get(newItemPosition);
            return a.getId() == b.getId()
                    && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getCoverPath(), b.getCoverPath())
                    && Objects.equals(a.getFileType(), b.getFileType())
                    && a.getReadProgress() == b.getReadProgress()
                    && a.getVideoPosition() == b.getVideoPosition()
                    && a.getTotal() == b.getTotal()
                    && a.getLastRead() == b.getLastRead()
                    && a.isSelected() == b.isSelected()
                    && a.isHorizontal() == b.isHorizontal();
        }
    }

    public static class CollectionItemDiffCallback extends DiffUtil.Callback {
        private final List<CollectionItem> oldList;
        private final List<CollectionItem> newList;

        public CollectionItemDiffCallback(List<CollectionItem> oldList, List<CollectionItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getCollection().getId() == newList.get(newItemPosition).getCollection().getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CollectionItem a = oldList.get(oldItemPosition);
            CollectionItem b = newList.get(newItemPosition);
            return a.getCollection().getId() == b.getCollection().getId()
                    && Objects.equals(a.getCollection().getName(), b.getCollection().getName())
                    && Objects.equals(a.getCollection().getCreatedAt(), b.getCollection().getCreatedAt())
                    && a.getComicCount() == b.getComicCount();
        }
    }

}
