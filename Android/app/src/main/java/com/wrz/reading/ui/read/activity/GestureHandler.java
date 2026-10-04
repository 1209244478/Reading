package com.wrz.reading.ui.read.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.wrz.reading.R;
import com.wrz.reading.ui.read.dlna.DlnaManager;

/**
 * 视频播放器手势处理组件（从 VideoPlayerActivity 机械拆分，行为不变）：
 * 单击切换控制器显隐、双击左/中/右分区（快退/暂停/快进）、长按连续快进快退、
 * 横向滑动相对 seek、左半屏纵向滑动调亮度、右半屏纵向滑动调音量，
 * 并负责手势提示浮层的显示与自动隐藏。
 * 播放器/视图操作直接通过构造传入的宿主 Activity（同包）完成；
 * 长按快进与提示隐藏共用同一个主线程 Handler（由宿主传入）。
 */
@OptIn(markerClass = UnstableApi.class)
public class GestureHandler {

    private final VideoPlayerActivity host;
    // 统一主线程 Handler：长按连续快进/手势提示自动隐藏共用
    private final Handler mainHandler;

    /**
     * 双击快进/快退步长（毫秒）
     */
    private static final long DOUBLE_TAP_SEEK_MS = 10_000L;
    private static final long LONG_TAP_SEEK_MS = 1_000L;
    private static final long LONG_TAP_REPEAT_INTERVAL_MS = 300L;


    /**
     * 横向滑动：一屏宽度对应的 seek 量（毫秒），相对起拖位置增减
     */
    private static final long SWIPE_SEEK_MS_PER_WIDTH = 90_000L;

    private GestureDetector gestureDetector;

    // ==== 音量相关 ====
    private AudioManager audioManager;
    private int streamMaxVolume;

    private static final int MODE_NONE = 0, MODE_SEEK = 1, MODE_BRIGHTNESS = 2, MODE_VOLUME = 3;
    private int gestureMode = MODE_NONE;

    private float downX, downY;
    private boolean leftHalf;

    private long seekStartPosMs;
    private long seekTargetPosMs;


    private float startBrightness;

    private int startVolume;

    // ===== 长按连续快进/快退 =====
    private boolean longSeekForward;
    private final Runnable longSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (host.player == null || host.isCasting) return;
            long dur = host.player.getDuration();
            if (dur <= 0) return;
            long target = host.player.getCurrentPosition() + (longSeekForward ? LONG_TAP_SEEK_MS : -LONG_TAP_SEEK_MS);
            target = Math.max(0, Math.min(target, dur));
            host.player.seekTo(target);
            showGestureHint((longSeekForward ? "▶ " : "◀ ") + DlnaManager.formatTimeReadable(target));
            mainHandler.postDelayed(this, LONG_TAP_REPEAT_INTERVAL_MS);
        }
    };

    /**
     * 手势提示自动隐藏延时（毫秒）
     */
    private static final int GESTURE_HINT_HIDE_MS = 600;

    private final Runnable hideHintRunnable = new Runnable() {
        @Override
        public void run() {
            if (host.gesture_indicator != null) {
                host.gesture_indicator.setVisibility(View.GONE);
            }
        }
    };

    public GestureHandler(VideoPlayerActivity host, Handler mainHandler) {
        this.host = host;
        this.mainHandler = mainHandler;
    }

    /**
     * 宿主 onDestroy 时移除提示隐藏任务（与原 Activity 中的 removeCallbacks 等价）
     */
    public void removeHintCallback() {
        mainHandler.removeCallbacks(hideHintRunnable);
    }

    /**
     * 在 player_view 上挂手势：
     * <ul>
     *   <li>单击：切换控制器显示/隐藏（替代 PlayerView 默认点击行为）</li>
     *   <li>双击左半屏：后退 {@link #DOUBLE_TAP_SEEK_MS}；双击右半屏：前进</li>
     *   <li>横向滑动：相对起拖位置 seek（一屏宽度 = {@link #SWIPE_SEEK_MS_PER_WIDTH}）</li>
     *   <li>左半屏纵向滑动：调节屏幕亮度；右半屏纵向滑动：调节媒体音量</li>
     * </ul>
     * 投屏到电视（isCasting）或无播放器时禁用，交还默认处理。
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupGestures() {
        audioManager = (AudioManager) host.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        gestureDetector = new GestureDetector(host, new GestureDetector.SimpleOnGestureListener() {
            @OptIn(markerClass = UnstableApi.class)
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (host.player_view.isControllerFullyVisible()) {
                    host.player_view.hideController();
                } else {
                    host.player_view.showController();
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                if (host.player == null || host.isCasting) return;
                long dur = host.player.getDuration();
                if (dur <= 0) return;
                longSeekForward = e.getX() >= host.player_view.getWidth() / 2f;
                longSeekRunnable.run(); // 立即执行一次
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (host.player == null || host.isCasting) return true;
                long dur = host.player.getDuration();
                if (dur <= 0) return true;

                if (e.getX() < host.player_view.getWidth() / 3f) {
                    long target = host.player.getCurrentPosition() - DOUBLE_TAP_SEEK_MS;
                    target = Math.max(0, Math.min(target, dur));
                    host.player.seekTo(target);
                    showGestureHint(("◀ ")
                            + DlnaManager.formatTimeReadable(target));
                } else if (e.getX() > (2 * host.player_view.getWidth()) / 3f) {
                    long target = host.player.getCurrentPosition() + DOUBLE_TAP_SEEK_MS;
                    target = Math.max(0, Math.min(target, dur));
                    host.player.seekTo(target);
                    showGestureHint(("▶ ")
                            + DlnaManager.formatTimeReadable(target));
                } else {
                    if (host.player.isPlaying()) {
                        host.player.pause();
                    } else {
                        host.player.setPlayWhenReady(true);
                    }
                }

                return true;
            }
        });
        host.player_view.setOnTouchListener((v, event) -> handleGestureTouch(event));
    }

    private boolean handleGestureTouch(MotionEvent event) {
        if (host.player == null || host.isCasting) {
            gestureMode = MODE_NONE;
            return false;
        }
        // 交给 GestureDetector 识别单击/双击
        gestureDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                leftHalf = downX < host.player_view.getWidth() / 2f;
                gestureMode = MODE_NONE;
                seekStartPosMs = host.player.getCurrentPosition();
                seekTargetPosMs = seekStartPosMs;
                startBrightness = host.getWindow().getAttributes().screenBrightness;
                if (startBrightness < 0) startBrightness = 0.5f; // 未设过则按 50% 起算
                startVolume = audioManager != null
                        ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                break;

            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX;
                float dy = y - downY;
                float slop = ViewConfiguration.get(host).getScaledTouchSlop();
                if (gestureMode == MODE_NONE) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)
                            && host.player.getDuration() > 0) {
                        gestureMode = MODE_SEEK;
                    } else if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                        gestureMode = leftHalf ? MODE_BRIGHTNESS : MODE_VOLUME;
                    }
                }
                if (gestureMode == MODE_SEEK) {
                    long dur = host.player.getDuration();
                    long delta = (long) (dx / host.player_view.getWidth() * SWIPE_SEEK_MS_PER_WIDTH);
                    long target = seekStartPosMs + delta;
                    target = Math.max(0, Math.min(target, dur));
                    seekTargetPosMs = target;
                    showGestureHint(DlnaManager.formatTimeReadable(target)
                            + " / " + DlnaManager.formatTimeReadable(dur));
                } else if (gestureMode == MODE_BRIGHTNESS) {
                    float b = startBrightness - dy / host.player_view.getHeight();
                    applyBrightness(Math.max(0f, Math.min(1f, b)));
                } else if (gestureMode == MODE_VOLUME) {
                    int v = startVolume
                            - (int) (dy / host.player_view.getHeight() * streamMaxVolume);
                    applyVolume(Math.max(0, Math.min(v, streamMaxVolume)));
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mainHandler.removeCallbacks(longSeekRunnable);
                if (gestureMode == MODE_SEEK) {
                    host.player.seekTo(seekTargetPosMs);
                }
                gestureMode = MODE_NONE;
                break;
            default:
                break;
        }
        return true;
    }

    private void applyBrightness(float b) {
        WindowManager.LayoutParams lp = host.getWindow().getAttributes();
        lp.screenBrightness = b;
        host.getWindow().setAttributes(lp);
        showGestureHint(host.getString(R.string.msg_brightness, (int) (b * 100)));
    }

    private void applyVolume(int v) {
        if (audioManager == null) return;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        showGestureHint(host.getString(R.string.msg_volume, streamMaxVolume > 0 ? v * 100 / streamMaxVolume : 0));
    }

    private void showGestureHint(String text) {
        if (host.gesture_indicator == null) return;
        host.gesture_indicator.setText(text);
        host.gesture_indicator.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideHintRunnable);
        mainHandler.postDelayed(hideHintRunnable, GESTURE_HINT_HIDE_MS);
    }
}
