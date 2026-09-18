package com.wrz.reading.ui.read.adapter;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.activity.BaseReaderActivity;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.model.SubProgress;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.view.ReaderPhotoView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageAdapter extends BaseMultiItemQuickAdapter<ReadItem, BaseViewHolder> {

    private int selected_position = -1;

    public void setPosition(int position) {
        LogUtil.e("ImageAdapter", "setPosition position=" + position);
        int old_position = selected_position;
        selected_position = position;

        notifyItemChanged(position);
        if (old_position != -1) {
            File file = this.getData().get(old_position).getFile();
            ExoPlayer player;
            if (!playerMap.isEmpty() && playerMap.get(file.getName()) != null) {
                player = playerMap.get(file.getName());
                if (player != null) {
                    player.release();
                }
                playerMap.remove(file.getName());
            }
            notifyItemChanged(old_position);
        }
    }

    public ImageAdapter(@Nullable List<ReadItem> data, OnComicClickListener listener) {
        super(data);
        this.listener = listener;

        addItemType(ReadItem.TYPE_IMAGE, R.layout.image_item);
        addItemType(ReadItem.TYPE_VIDEO, R.layout.video_item);
        addItemType(ReadItem.TYPE_MUSIC, R.layout.video_item);
    }

    final Map<String, ExoPlayer> playerMap = new HashMap<>();

    public void releaseAllPlayer() {
        ExoPlayer player;
        for (Map.Entry<String, ExoPlayer> map: playerMap.entrySet()) {
            if (!playerMap.isEmpty() && map.getValue() != null) {
                player = map.getValue();
                if (player != null) {
                    saveProgress(player, map.getKey());
                    player.release();
                }
            }
        }
        playerMap.clear();
    }

    private void saveProgress(ExoPlayer player, String fileName) {
        long pos = player.getCurrentPosition();
        long duration = player.getDuration();

        if (duration <= 0) {
            duration = pos;
        }
        // 已基本播放完毕（剩余 < 3 秒）则重置为 0，避免下次恢复到结尾定格
        if (duration > 0 && pos >= duration - 3000L) {
            pos = 0L;
        }

        SubProgress.updateSubProgress(fileName, player.getCurrentPosition(), subList);
        SubProgress.updateSubProgress(fileName + "_total", duration, subList);
    }

    public void setCurrentPlayStatus(boolean play) {
        File file = this.getData().get(selected_position).getFile();
        ExoPlayer player;
        if (!playerMap.isEmpty() && playerMap.get(file.getName()) != null) {
            player = playerMap.get(file.getName());
            if (player != null) {
                saveProgress(player, file.getName());
                player.setPlayWhenReady(play);
            }
        }
    }

    ArrayList<SubProgress> subList;

    public void setSubList(ArrayList<SubProgress> subList) {
        this.subList = subList;
    }

    @SuppressLint("ClickableViewAccessibility")
    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void convert(@NonNull BaseViewHolder holder, ReadItem readItem) {
        File file = readItem.getFile();

        boolean isVideo = FileUtils.isVideoFile(file.getName());
        boolean isMusic = FileUtils.isMusicFile(file.getName());

        if ((isVideo || isMusic)) {
            // 视频页：整页挂载为 VideoPlayFragment 内联播放
            ExoPlayer player;
            if (selected_position == holder.getLayoutPosition()) {
                PlayerView player_view = holder.getView(R.id.player_view);
                player = new ExoPlayer.Builder(holder.itemView.getContext()).build();

                playerMap.put(file.getName(), player);
                player_view.setPlayer(player);
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(FileDataSource::new)
                        .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)));
                player.setMediaSource(mediaSource);
                player.prepare();
                player.setPlayWhenReady(true);

                long pos = SubProgress.getSubProgress(file.getName(), subList);

                player.seekTo(pos);

                player_view.setControllerVisibilityListener(
                        (PlayerView.ControllerVisibilityListener) visibility -> {
                            if (listener != null) {
                                listener.onVideoClick();
                            }
                        });
                player_view.setOnTouchListener((v, event) -> {
                    float yPercent = event.getY() / (float) player_view.getHeight();
                    LogUtil.e("ImageAdapter", "setOnTouchListener: yPercent=" + yPercent);
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && yPercent > 0.7 && yPercent < 0.9) {
                        if (listener != null) {
                            listener.onComicClick();
                        }
                        return true;
                    }
                    return false;
                });
            }
            return;
        }

        holder.setVisible(R.id.image_view, true);
        ReaderPhotoView photoView = holder.getView(R.id.image_view);

        Glide.with(holder.itemView)
                .load(file)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(photoView);

        if (zoomMode && !isMusic) {
            photoView.setZoomable(true);
            photoView.setOnPhotoTapListener((view, x, y) -> listener.onComicClick());
            photoView.setOnMatrixChangeListener(rect -> listener.onScroll());
        } else {
            photoView.setZoomable(false);
            photoView.setOnTapListener((xPercent, yPercent) -> {
                if (isMusic) {
                    if (yPercent < 0.3f || yPercent > 0.7f) {
                        listener.onComicClick();
                    } else {
                        listener.onVideoJump(file);
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

    public interface OnComicClickListener {
        void onComicClick();
        void onScroll();
        void onSwipe(boolean next);
        void onVideoClick();
        void onVideoJump(File videoFile);
    }

    private final OnComicClickListener listener;
    private boolean zoomMode = false;

    @SuppressLint("NotifyDataSetChanged")
    public void setZoomMode(boolean enabled) {
        if (this.zoomMode == enabled) return;
        this.zoomMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isZoomMode() {
        return zoomMode;
    }
}
