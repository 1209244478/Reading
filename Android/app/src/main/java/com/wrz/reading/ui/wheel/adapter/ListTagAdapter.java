package com.wrz.reading.ui.wheel.adapter;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.wheel.model.Tag;

public class ListTagAdapter extends BaseQuickAdapter<Tag, BaseViewHolder> {

    public ListTagAdapter() {
        super(R.layout.item_list_tag);
    }

    public void NoSetAll() {
        for (int i = 0; i < this.getData().size(); i++) {
            if ("全部".equals(this.getData().get(i).getTag())) {
                this.getData().get(i).setSelected(false);
                this.notifyItemChanged(i);
                break;
            }
        }
    }

    public void setAllSelected() {
        for (Tag t : this.getData()) {
            t.setSelected("全部".equals(t.getTag()));
        }
        this.notifyDataSetChanged();
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Tag tag) {
        holder.setText(R.id.tv_tag, tag.getTag());

        holder.itemView.setSelected(tag.isSelected());
    }
}
