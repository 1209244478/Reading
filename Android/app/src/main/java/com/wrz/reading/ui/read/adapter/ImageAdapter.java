package com.wrz.reading.ui.read.adapter;

import android.annotation.SuppressLint;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.view.ReaderPhotoView;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class ImageAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

    public interface OnComicClickListener {
        void onComicClick();
        void onScroll();
        void onSwipe(boolean next);
        void onVideoClick(File videoFile);
    }

    private final OnComicClickListener listener;
    private boolean zoomMode = false;

    public ImageAdapter(int layoutResId, List<File> data, OnComicClickListener listener) {
        super(layoutResId, data);
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setZoomMode(boolean enabled) {
        if (this.zoomMode == enabled) return;
        this.zoomMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isZoomMode() {
        return zoomMode;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void convert(@NotNull BaseViewHolder holder, File imageFile) {
        ReaderPhotoView photoView = holder.getView(R.id.image_view);
        boolean isVideo = FileUtils.isVideoFile(imageFile.getName());
        boolean isMusic = FileUtils.isMusicFile(imageFile.getName());

        if (isVideo) {
            Glide.with(holder.itemView)
                    .asBitmap()
                    .load(imageFile)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(photoView);
        } else {
            Glide.with(holder.itemView)
                    .load(imageFile)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(photoView);
        }

        if (zoomMode && !isVideo && !isMusic) {
            photoView.setZoomable(true);
            photoView.setOnPhotoTapListener((view, x, y) -> listener.onComicClick());
            photoView.setOnMatrixChangeListener(rect -> listener.onScroll());
        } else {
            photoView.setZoomable(false);
            photoView.setOnTapListener((xPercent, yPercent) -> {
                if (isVideo || isMusic) {
                    if (yPercent < 0.3f || yPercent > 0.7f) {
                        listener.onComicClick();
                    } else {
                        listener.onVideoClick(imageFile);
                    }
                } else {
                    if (xPercent < 0.33f) {
                        listener.onSwipe(false);
                    } else if (xPercent > 0.67f) {
                        listener.onSwipe(true);
                    } else {
                        listener.onComicClick();
                    }
                }
            });
            photoView.setOnMoveListener(listener::onScroll);
        }
    }
}
