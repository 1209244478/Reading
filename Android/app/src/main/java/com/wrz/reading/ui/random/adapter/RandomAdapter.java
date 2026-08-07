package com.wrz.reading.ui.random.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;

import java.util.List;

public class RandomAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public RandomAdapter(@Nullable List<String> data) {
        super(R.layout.item_random, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, String s) {
        if (s.isEmpty()) {
            holder.setText(R.id.randomText, "?");
        } else {
            holder.setText(R.id.randomText, s);
        }
    }
}
