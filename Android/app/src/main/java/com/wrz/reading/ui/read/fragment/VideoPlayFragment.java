//package com.wrz.reading.ui.read.fragment;
//
//import android.media.AudioManager;
//import android.net.Uri;
//import android.os.Bundle;
//import android.view.MotionEvent;
//import android.view.View;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.annotation.OptIn;
//import androidx.media3.common.MediaItem;
//import androidx.media3.common.util.UnstableApi;
//import androidx.media3.datasource.FileDataSource;
//import androidx.media3.exoplayer.ExoPlayer;
//import androidx.media3.exoplayer.source.MediaSource;
//import androidx.media3.exoplayer.source.ProgressiveMediaSource;
//import androidx.media3.ui.PlayerView;
//
//import com.wrz.reading.R;
//import com.wrz.reading.common.BaseFragment;
//import com.wrz.reading.ui.main.Log.LogUtil;
//
//import java.io.File;
//
///**
// * 视频播放页：由 ImageAdapter 挂载到 pager 页的 FragmentContainerView 上。
// * 播放器延迟到页面真正 attach 到窗口后才创建：
// * 1. 避免在布局/挂载完成前初始化导致画面不渲染；
// * 2. Activity 重建后 FragmentManager 恢复的孤儿 Fragment（视图无父容器）不会创建播放器，
// *    避免无画面泄漏声音；孤儿片段由 ImageAdapter 首次拿到 FM 时清扫移除。
// */
//public class VideoPlayFragment extends BaseFragment {
//
//    private static final String ARG_VIDEO_PATH = "videoPath";
//
//    public VideoPlayFragment(String videoPath) {
//        Bundle args = new Bundle();
//        args.putString(ARG_VIDEO_PATH, videoPath);
//        setArguments(args);
//    }
//
//    private String videoPath() {
//        Bundle args = getArguments();
//        return args == null ? null : args.getString(ARG_VIDEO_PATH);
//    }
//
//    public interface ClickListener {
//        void onClick();
//    }
//
//    ClickListener listener;
//
//    public void setListener(ClickListener listener) {
//        this.listener = listener;
//    }
//
//    @Override
//    public int getLayoutResId() {
//        return R.layout.fragment_video_player;
//    }
//
//    private ExoPlayer player;
//    private PlayerView player_view;
//
//    private final View.OnAttachStateChangeListener attachStateListener = new View.OnAttachStateChangeListener() {
//        @Override
//        public void onViewAttachedToWindow(@NonNull View v) {
//            initPlayerIfAttached();
//        }
//
//        @Override
//        public void onViewDetachedFromWindow(@NonNull View v) {
//            pausePlayback();
//        }
//    };
//
//    @Override
//    public void initView() {
//        player_view = findId(R.id.player_view);
//        if (parentView != null) {
//            parentView.addOnAttachStateChangeListener(attachStateListener);
//        }
//        player_view.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                float xPercent = event.getX() / (float) player_view.getWidth();
//                float yPercent = event.getY() / (float) player_view.getHeight();
//                LogUtil.e(TAG, "initView: yPercent=" + yPercent);
//                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && yPercent > 0.7 && yPercent < 0.9) {
//                    if (listener != null) {
//                        listener.onClick();
//                    }
//                    return true;
//                }
//                return false;
//            }
//        });
//        initPlayerIfAttached();
//    }
//
//    @Override
//    public void initData() {
//    }
//
//    @Override
//    public void configViews() {
//    }
//
//    @OptIn(markerClass = UnstableApi.class)
//    private void initPlayerIfAttached() {
//        if (player != null || parentView == null || !parentView.isAttachedToWindow()) return;
//        String path = videoPath();
//        if (path == null) return;
//
//        player = new ExoPlayer.Builder(activity).build();
//        player_view.setPlayer(player);
//
//        MediaSource mediaSource = new ProgressiveMediaSource.Factory(FileDataSource::new)
//                .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(path))));
//        player.setMediaSource(mediaSource);
//
//        player.prepare();
//        player.setPlayWhenReady(true);
//    }
//
//    public void resumePlayback() {
//        if (player != null) {
//            player.setPlayWhenReady(true);
//        }
//    }
//
//    public void pausePlayback() {
//        if (player != null) {
//            player.setPlayWhenReady(false);
//        }
//    }
//
//    @Override
//    public void onPause() {
//        super.onPause();
//        pausePlayback();
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        if (parentView != null) {
//            parentView.removeOnAttachStateChangeListener(attachStateListener);
//        }
//        if (player != null) {
//            player.release();
//            player = null;
//        }
//        if (player_view != null) {
//            player_view.setPlayer(null);
//        }
//    }
//
//    @Override
//    public void goBack() {
//
//    }
//}
