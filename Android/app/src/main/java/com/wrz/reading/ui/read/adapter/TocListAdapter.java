package com.wrz.reading.ui.read.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.BookMixAToc;

import java.util.List;

public class TocListAdapter extends BaseQuickAdapter<BookMixAToc.mixToc.Chapters, BaseViewHolder> {

    private int currentChapter;

    public TocListAdapter(int layoutResId, @Nullable List<BookMixAToc.mixToc.Chapters> data) {
        super(layoutResId, data);

    }

    public void setCurrentChapter(int chapter) {
        currentChapter = chapter;
        notifyDataSetChanged();
    }


    @Override
    protected void convert(@NonNull BaseViewHolder holder, BookMixAToc.mixToc.Chapters chapters) {
        int position = holder.getLayoutPosition();

        Context mContext = holder.itemView.getContext();

        TextView tvTocItem = holder.getView(R.id.tvTocItem);
        tvTocItem.setText(chapters.title);
        Drawable drawable;
        if (currentChapter == position + 1) {
            tvTocItem.setTextColor(ContextCompat.getColor(mContext, R.color.light_black));
            drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_toc_item_download);
        } else {
            tvTocItem.setTextColor(ContextCompat.getColor(mContext, R.color.light_black));
            drawable = ContextCompat.getDrawable(mContext, R.drawable.ic_toc_item_normal);
        }
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        tvTocItem.setCompoundDrawables(drawable, null, null, null);
    }

}
