package com.wrz.reading.ui.read.adapter;

import android.annotation.SuppressLint;
import android.view.View;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.File;
import java.util.List;

public class PreviewAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

    private int centerPage = -1;

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<File> pages, int center) {
        this.setNewInstance(pages);
        this.centerPage = center;
        notifyDataSetChanged();
    }

    public void setCenterPage(int newPage) {
        int old = centerPage;
        centerPage = newPage;

        if (old != -1) this.notifyItemChanged(old);
        if (newPage != -1) this.notifyItemChanged(newPage);
    }

    public PreviewAdapter() {
        super(R.layout.preview_thumb_item);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, File file) {
        boolean isCurrent = (holder.getAbsoluteAdapterPosition() == centerPage);

        android.widget.ImageView thumbImage = holder.getView(R.id.thumb_image);

        Glide.with(holder.itemView)
                .load(file)
                .override(162, 216) // 3x dp
                .centerCrop()
                .into(thumbImage);

        holder.getView(R.id.thumb_highlight).setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        holder.setText(R.id.thumb_page_num, (holder.getAbsoluteAdapterPosition() + 1) +
                "\n(" + FileUtils.isWhatFile(file.getName()) + ")");
    }
}