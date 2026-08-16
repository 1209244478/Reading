package com.wrz.reading.ui.read.adapter;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.CollectionItem;

import java.util.List;

/**
 * 集合列表适配器，支持选择模式（用于删除集合）。
 */
public class CollectionAdapter extends BaseQuickAdapter<CollectionItem, BaseViewHolder> {

    private boolean isSelectMode = false;

    public CollectionAdapter(@Nullable List<CollectionItem> data) {
        super(R.layout.item_collection, data);
    }

    public boolean isSelectMode() {
        return isSelectMode;
    }

    public void setSelectMode(boolean selectMode) {
        this.isSelectMode = selectMode;
    }

    public void toggleSelectMode() {
        this.isSelectMode = !this.isSelectMode;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, CollectionItem item) {
        Collection collection = item.getCollection();
        holder.setText(R.id.collection_name, collection.getName());
        holder.setText(R.id.collection_count, item.getComicCount() + " 本");

        ImageView icon = holder.getView(R.id.collection_icon);

        if (isSelectMode) {
            holder.setGone(R.id.select_indicator_collection, false);
            if (collection.isSelected()) {
                holder.setGone(R.id.check_icon_collection, false);
                icon.setAlpha(0.5f);
            } else {
                holder.setGone(R.id.check_icon_collection, true);
                icon.setAlpha(1f);
            }
        } else {
            holder.setGone(R.id.select_indicator_collection, true);
            icon.setAlpha(1f);
        }
    }
}
