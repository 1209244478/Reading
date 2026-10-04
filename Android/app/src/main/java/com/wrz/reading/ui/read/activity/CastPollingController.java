package com.wrz.reading.ui.read.activity;

import android.os.Handler;
import android.os.Looper;

import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.dlna.DlnaManager;

/**
 * 投屏进度轮询控制器（从 VideoPlayerActivity 机械拆分，行为不变）：
 * 投屏中按固定间隔通过 DLNA GetPositionInfo 拉取电视端播放进度并回调宿主刷新 UI，
 * 处理单次轮询超时（超时后继续下一轮），并维护跨线程读取的进度缓存
 * （cachedPositionMs/cachedDurationMs，供接收端 PlayerBridge 在 Cling 线程查询）。
 * 需要触碰 Activity 投屏状态与 UI 的操作，经构造传入的 {@link Host} 回调完成；
 * 轮询的启停调用点（onCastStarted/hideCastOverlay/onPause/onResume/onDestroy）由宿主负责，保持原时序。
 */
public class CastPollingController {

    private static final String TAG = VideoPlayerActivity.TAG;

    /**
     * 宿主回调：由 VideoPlayerActivity 实现，供轮询读写投屏状态、刷新投屏 UI 与联动停止投屏。
     */
    public interface Host {
        void runOnUiThread(Runnable action);

        boolean isCasting();

        boolean isCastPaused();

        void setCastPaused(boolean paused);

        void setCastPlayPauseIcon(int resId);

        boolean isUserSeeking();

        long getCastDurationMs();

        void setCastDurationMs(long durationMs);

        void showCastDurationText(long durationMs);

        void showCastCurrentTimeText(long positionMs);

        void updateCastSeekBarProgress(long positionMs);

        void setLastCastPositionMs(long positionMs);

        DlnaManager getDlnaManager();

        void stopCasting();

        void PlayNextVideo();

        void castToDevice(boolean isChangeCast);
    }

    private final Host host;

    /**
     * 投屏进度轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MS = 1000L;
    /**
     * 单次轮询超时：超过该时间未收到 GetPositionInfo 回调视为接收端无响应
     */
    private static final long POLL_TIMEOUT_MS = 5000L;

    // 统一主线程 Handler：投屏轮询/超时调度
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollPlaybackInfo;

    /**
     * 标记本次 poll 是否已超时（超时后迟到的回调忽略）
     */
    private boolean pollTimedOut = false;

    /**
     * 单次 poll 超时检测：超过 POLL_TIMEOUT_MS 未回调则判定接收端无响应
     */
    private final Runnable pollTimeoutRunnable = () -> {
        pollTimedOut = true;
        LogUtil.w(TAG, "poll 超时: GetPositionInfo " + POLL_TIMEOUT_MS + "ms 无响应（接收端未回复），继续轮询");

        startPoll();
    };

    /**
     * 缓存的播放位置/时长，由投屏轮询或接收端刷新在主线程写入 volatile，
     * 供 DlnaRendererManager 在 Cling 线程查询 GetPositionInfo 时读取。
     */
    volatile long cachedPositionMs = 0;
    volatile long cachedDurationMs = 0;

    public CastPollingController(Host host) {
        this.host = host;
    }

    /**
     * 开启投屏轮询
     */
    public void startPoll() {
        if (host.isCasting()) {
            mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    /**
     * 开关闭投屏轮询
     */
    public void stopPoll() {
        mainHandler.removeCallbacks(pollRunnable);
    }

    /**
     * 开启单次 poll 超时检测
     */
    public void startPollTimeOut() {
        mainHandler.postDelayed(pollTimeoutRunnable, POLL_TIMEOUT_MS);
    }

    /**
     * 关闭单次 poll 超时检测
     */
    public void stopPollTimeOut() {
        mainHandler.removeCallbacks(pollTimeoutRunnable);
    }

    /**
     * 轮询电视端播放进度并刷新 UI
     */
    public void pollPlaybackInfo() {
        if (!host.isCasting()) return;

        pollTimedOut = false;
        stopPollTimeOut();
        startPollTimeOut();
        host.getDlnaManager().getPlaybackInfo(new DlnaManager.PlaybackInfoListener() {
            @Override
            public void onResult(long positionMs, long durationMs, String transportState) {
                host.runOnUiThread(() -> {
                    LogUtil.e(TAG, "pollPlaybackInfo: positionMs=" + positionMs + " durationMs=" + durationMs + " transportState=" + transportState);
                    stopPollTimeOut();
                    // 超时已触发并已重新调度，忽略迟到响应
                    if (pollTimedOut) return;
                    if (!host.isCasting()) return;

                    // 接收端已停止/结束播放：退出投屏态（stopCasting 会发送 Stop 清理接收端）
                    if ("STOPPED".equals(transportState)) {
                        host.stopCasting();
                        return;
                    }

                    // 同步播放/暂停按钮（接收端可能自行暂停/恢复）
                    if ("PAUSED_PLAYBACK".equals(transportState)) {
                        if (!host.isCastPaused()) {
                            host.setCastPaused(true);
                            host.setCastPlayPauseIcon(android.R.drawable.ic_media_play);
                        }
                    } else if ("PLAYING".equals(transportState)) {
                        if (host.isCastPaused()) {
                            host.setCastPaused(false);
                            host.setCastPlayPauseIcon(android.R.drawable.ic_media_pause);
                        }
                    }
                    if (!host.isUserSeeking() && durationMs > 0) {
                        host.setCastDurationMs(durationMs);
                        host.showCastDurationText(durationMs);
                    }
                    if (positionMs > 0) {
                        host.setLastCastPositionMs(positionMs);
                    }

                    cachedPositionMs = positionMs;
                    cachedDurationMs = durationMs;
                    host.showCastCurrentTimeText(positionMs);
                    // 用户拖动时不更新 SeekBar，避免抖动
                    if (!host.isUserSeeking() && host.getCastDurationMs() > 0) {
                        host.updateCastSeekBarProgress(positionMs);
                    }
                    if (positionMs >= durationMs && "PAUSED_PLAYBACK".equals(transportState)) {
                        host.PlayNextVideo();
                        if (host.isCasting()) {
                            host.castToDevice(false);
                        }
                    }

                    // 安排下一次轮询
                    startPoll();
                });
            }

            @Override
            public void onFailure(String message) {
                host.runOnUiThread(() -> {
                    stopPollTimeOut();
                    if (pollTimedOut) return;

                    // 失败时也继续轮询，避免一次网络抖动就停掉
                    startPoll();
                });
            }
        });
    }
}
