package com.wrz.reading.ui.wheel.adapter;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.wheel.model.Tag;

public class TagAdapter extends BaseQuickAdapter<Tag, BaseViewHolder> {
    public TagAdapter(int layoutResId) {
        super(layoutResId);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Tag tag) {
        holder.setText(R.id.tv_tag, tag.getTag());
    }
}
