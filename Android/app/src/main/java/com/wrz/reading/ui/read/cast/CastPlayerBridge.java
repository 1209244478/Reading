package com.wrz.reading.ui.read.cast;

/**
 * 投屏接收端播放器桥接接口（DLNA / WrzCast 等接收协议共用）。
 *
 * 由接收模式的 VideoPlayerActivity 实现，接收管理器在协议线程收到控制指令后，
 * 通过主线程 Handler 调用本接口操作 ExoPlayer。
 */
public interface CastPlayerBridge {

    void play();

    void pause();

    void stop();

    void seekTo(long ms);

    void setUri(String uri, String title, boolean isNewDevice, String casterName);

    long getPositionMs();

    long getDurationMs();
}
