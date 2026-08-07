package com.wrz.reading.ui.wheel.adapter;

import static com.wrz.reading.model.Wheel.TYPE_CONTENT;
import static com.wrz.reading.model.Wheel.TYPE_TITLE;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.model.Wheel;

public class WheelTemplateListAdapter extends BaseMultiItemQuickAdapter<Wheel, BaseViewHolder> {
    public WheelTemplateListAdapter() {
        super();
        addItemType(TYPE_TITLE, R.layout.item_wheel_title);
        addItemType(TYPE_CONTENT, R.layout.item_wheel);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Wheel wheel) {
        if (wheel.getType() == TYPE_TITLE) {
            // 分类标题项：title 字段即分类名
            holder.setText(R.id.tv_title, wheel.getTitle());
        } else {
            holder.setText(R.id.tv_option, wheel.getTitle());

            if (wheel.getSubtitle() == null || wheel.getSubtitle().isBlank()) {
                holder.setText(R.id.tv_count, getContext().getString(R.string.message_option_count, String.valueOf(wheel.getList().size())));
            } else {
                holder.setText(R.id.tv_count, wheel.getSubtitle() + ":"
                        + getContext().getString(R.string.message_option_count, String.valueOf(wheel.getList().size())));
            }

            holder.setGone(R.id.tv_count, false);

            if (wheel.getEmoji() != null && !wheel.getEmoji().isBlank()) {
                holder.setText(R.id.tv_emoji, wheel.getEmoji());
            }
        }
    }
}
