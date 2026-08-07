package com.wrz.reading.ui.wheel.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;

import java.util.List;

public class EmojiAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public EmojiAdapter(int layoutResId, @Nullable List<String> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, String s) {
        holder.setText(R.id.tv_emoji, s);

        if (holder.getLayoutPosition() == selected_position) {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_emoji);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_null);
        }
    }

    private int selected_position = -1;

    public void setPosition(int position) {
        int old_position = selected_position;
        selected_position = position;

        notifyItemChanged(position);
        notifyItemChanged(old_position);
    }



}
