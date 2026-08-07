package com.wrz.reading.ui.wheel.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;

import java.util.List;

/**
 * 颜色选择网格适配器，从 OptionListActivity 抽取。
 */
public class ColorGridAdapter extends BaseQuickAdapter<Integer, BaseViewHolder> {

    private int selectedColor;

    public void setSelectedColor(int color) {
        int oldPos = this.getData().indexOf(selectedColor);
        selectedColor = color;
        int newPos = this.getData().indexOf(color);
        if (oldPos >= 0) notifyItemChanged(oldPos);
        if (newPos >= 0) notifyItemChanged(newPos);
    }

    public ColorGridAdapter(@Nullable List<Integer> data, int selectedColor) {
        super(R.layout.item_color_grid, data);
        this.selectedColor = selectedColor;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Integer color) {
        // 创建圆形背景
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);


        View colorBlock = holder.getView(R.id.color_block);
        View colorCheck = holder.getView(R.id.color_check);

        colorBlock.setBackground(drawable);

        boolean selected = color == selectedColor;
        colorCheck.setVisibility(selected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(color);
        });
    }

    private OnColorClickListener listener;

    public interface OnColorClickListener {
        void onClick(int color);
    }

    public void setOnColorClickListener(OnColorClickListener listener) {
        this.listener = listener;
    }
}
