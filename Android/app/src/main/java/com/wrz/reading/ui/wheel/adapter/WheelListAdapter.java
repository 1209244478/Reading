package com.wrz.reading.ui.wheel.adapter;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.model.Wheel;

public class WheelListAdapter extends BaseQuickAdapter<Wheel, BaseViewHolder> {
    public WheelListAdapter(int layoutResId) {
        super(layoutResId);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Wheel wheel) {
        holder.setText(R.id.tv_option, wheel.getTitle());

        holder.setText(R.id.tv_count, getContext().getString(R.string.message_option_count, String.valueOf(wheel.getList().size())));

        holder.setGone(R.id.tv_count, false);

        if (wheel.isLastTime()) {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_wheel_selected);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_wheel);
        }

        if (wheel.getEmoji() != null && !wheel.getEmoji().isBlank()) {
            holder.setText(R.id.tv_emoji, wheel.getEmoji());
        }
    }

}

