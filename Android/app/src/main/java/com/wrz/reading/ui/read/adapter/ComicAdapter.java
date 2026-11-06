package com.wrz.reading.ui.read.adapter;

import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FileType;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.VideoUtils;

import java.io.File;
import java.util.List;

public class ComicAdapter extends BaseQuickAdapter<Comic, BaseViewHolder> {

    private boolean isSelectMode = false;

    public ComicAdapter(int layoutResId, @Nullable List<Comic> data) {
        super(layoutResId, data);
    }

    /**
     * 获取是否处于选择模式
     */
    public boolean isSelectMode() {
        return isSelectMode;
    }

    /**
     * 设置选择模式
     */
    public void setSelectMode(boolean selectMode) {
        this.isSelectMode = selectMode;
    }

    /**
     * 切换选择模式
     */
    public void toggleSelectMode() {
        this.isSelectMode = !this.isSelectMode;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Comic comic) {
        // 电视端卡片以 activated 状态显示青柠描边，选中反馈不只依赖角标
        holder.itemView.setActivated(isSelectMode && comic.isSelected());
        holder.setText(R.id.comic_title_text, comic.getTitle());

        ImageView coverImageView = holder.getView(R.id.comic_cover_image);
        String coverPath = comic.getCoverPath();
        FileType fileType = comic.getFileTypeEnum();

        // coverPath 的存在性已在数据加载时于后台线程预校验（失效的已被置空），
        // 此处不再做磁盘 stat，避免滚动时每个 item 都产生文件系统调用
        if (coverPath != null && !coverPath.isEmpty()) {
            Glide.with(holder.itemView)
                    .load(coverPath)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(coverImageView);
            holder.setGone(R.id.tv_cover, true);

        } else if (fileType == FileType.VIDEO) {
            // 旧视频数据（coverPath 为空）：异步提取非首帧，避免纯黑封面；
            // 同时持久化 coverPath，下次直接走上面的 Glide 文件加载分支。
            coverImageView.setImageResource(R.drawable.ic_launcher_background);
            final long comicId = comic.getId();
            final String videoPath = comic.getPath();
            // 用 tag 标记当前 ImageView 绑定的 comicId，回调时校验防止复用错位
            coverImageView.setTag(R.id.comic_cover_image, comicId);
            VideoUtils.runOnIoThread(() -> {
                Bitmap bmp = VideoUtils.extractThumbnail(new File(videoPath));
                if (bmp == null) return;
                String path = VideoUtils.saveThumbnail(bmp, comic.getTitle());
                if (path != null) {
                    comic.setCoverPath(path);
                    MyApplication.comicDatabase.comicDao().updateCoverPath(comicId, path);
                }
                holder.itemView.post(() -> {
                    Object tag = coverImageView.getTag(R.id.comic_cover_image);
                    if (tag instanceof Long && (Long) tag == comicId) {
                        coverImageView.setImageBitmap(bmp);
                    }
                });
            });

        } else {
            switch (fileType) {
                case PDF:
                    coverImageView.setImageResource(R.drawable.pdf_24px);
                    break;
                case EPUB:
                    coverImageView.setImageResource(R.drawable.menu_book_24px);
                    break;
                default:
                    holder.setText(R.id.tv_cover, comic.getTitle());
                    holder.setVisible(R.id.comic_cover_image, false);
                    break;
            }
        }

        ProgressBar comicProgressBar = holder.getView(R.id.comic_progress_bar);
        comicProgressBar.setMax((int) comic.getTotal());

        if (fileType == FileType.VIDEO || fileType == FileType.MUSIC) {
            // 视频进度为播放位置（毫秒），格式化为时间；视频无总时长字段，隐藏进度条
            holder.setText(R.id.comic_progress, VideoUtils.formatTime(comic.getVideoPosition()) + "-" + VideoUtils.formatTime(comic.getTotal()));
            comicProgressBar.setProgress((int) comic.getVideoPosition());

        } else {
            if (comic.getSubProgress() != null && !comic.getSubProgress().isEmpty() && fileType == FileType.ZIP) {
                holder.setText(R.id.comic_progress, (comic.getAddUpSubProgress() + 1) + "/" + comic.getTotal());
                comicProgressBar.setProgress((int) comic.getAddUpSubProgress());

            } else {
                holder.setText(R.id.comic_progress, (comic.getReadProgress() + 1) + "/" + comic.getTotal());
                comicProgressBar.setProgress(comic.getReadProgress());
            }
        }

        // 文件本体大小已在数据加载时于后台线程预计算，绑定时直接读内存字段
        long fileLength = comic.getFileSize();
        if (fileType == FileType.ZIP) {
            holder.setText(R.id.comic_cache, "大小:" + FileUtils.formatFileSizeToString(fileLength)
                    + "\n缓存大小:" + FileUtils.formatFileSizeToString(comic.getCacheSize()));
        } else if (fileType != FileType.IMAGE) {
            holder.setText(R.id.comic_cache, "大小:" + FileUtils.formatFileSizeToString(fileLength));
        } else {
            holder.setText(R.id.comic_cache, "缓存大小:" + FileUtils.formatFileSizeToString(comic.getCacheSize()));
        }

        if (isSelectMode) {
            holder.setGone(R.id.select_indicator, false);
            if (comic.isSelected()) {
                holder.setGone(R.id.checkIcon, false);
            } else {
                holder.setGone(R.id.checkIcon, true);
            }
        } else {
            holder.setGone(R.id.select_indicator, true);
        }
    }

}
