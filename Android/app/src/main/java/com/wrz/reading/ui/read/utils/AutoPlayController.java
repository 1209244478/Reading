package com.wrz.reading.ui.read.utils;

import android.os.Handler;
import android.os.Looper;

/**
 * 阅读器自动播放控制器。
 * <p>从 ReaderActivity 提取，封装自动播放的定时循环与间隔切换逻辑，
 * 使 Activity 不再持有 Handler/Runnable 与播放状态字段。
 * <p>调用方在销毁时调用 {@link #destroy()} 释放回调。
 */
public class AutoPlayController {

    /** 默认播放间隔（毫秒） */


    private boolean isPlaying = false;
    private long delay;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickAction;

    private final Runnable loopRunnable = new Runnable() {
        @Override
        public void run() {
            if (tickAction != null) tickAction.run();
            handler.postDelayed(this, delay);
        }
    };

    /**
     * @param tickAction 每次触发的回调（通常为翻到下一页）
     */
    public AutoPlayController(Runnable tickAction) {
        this.tickAction = tickAction;
    }

    /** 开始自动播放 */
    public void start() {
        if (isPlaying) return;
        isPlaying = true;
        handler.postDelayed(loopRunnable, delay);
    }

    /** 停止自动播放 */
    public void stop() {
        if (!isPlaying) return;
        isPlaying = false;
        handler.removeCallbacks(loopRunnable);
    }

    /**
     * 切换播放间隔。若正在播放则重启定时器使新间隔立即生效。
     */
    public void setDelay(long newDelay) {
        this.delay = newDelay;
        if (isPlaying) {
            handler.removeCallbacks(loopRunnable);
            handler.postDelayed(loopRunnable, this.delay);
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public long getDelay() {
        return delay;
    }

    /** Activity onDestroy 时调用，移除所有未执行回调 */
    public void destroy() {
        stop();
    }
}
