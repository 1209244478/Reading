package com.wrz.reading.ui.read.wrzcast;

/**
 * WrzCast 私有投屏协议常量与 JSON 消息结构。
 *
 * 传输方式：
 * <ul>
 *   <li>发现：发送端向 UDP {@link #DISCOVERY_PORT} 广播 discover，接收端单播回复 announce；</li>
 *   <li>控制：发送端与接收端建立 TCP 长连接，按行传输 JSON（每行一条消息），
 *       请求通过 requestId 与 result 响应配对；</li>
 *   <li>状态：发送端定时发送 status 请求，接收端返回播放位置/时长/传输状态。</li>
 * </ul>
 * 传输状态沿用 DLNA 命名（PLAYING / PAUSED_PLAYBACK / STOPPED / TRANSITIONING），
 * 便于发送端复用同一套投屏轮询逻辑。
 */
public final class WrzCastProtocol {

    public static final String PROTOCOL = "WrzCast";
    public static final int VERSION = 1;

    /** UDP 发现端口（固定） */
    public static final int DISCOVERY_PORT = 47890;
    /** TCP 控制端口（被占用时接收端依次递增，实际端口通过 announce 返回） */
    public static final int DEFAULT_CONTROL_PORT = 47891;

    public static final String TYPE_DISCOVER = "discover";
    public static final String TYPE_ANNOUNCE = "announce";
    public static final String TYPE_LOAD = "load";
    public static final String TYPE_PLAY = "play";
    public static final String TYPE_PAUSE = "pause";
    public static final String TYPE_SEEK = "seek";
    public static final String TYPE_STOP = "stop";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_RESULT = "result";

    public static final String STATE_PLAYING = "PLAYING";
    public static final String STATE_PAUSED = "PAUSED_PLAYBACK";
    public static final String STATE_STOPPED = "STOPPED";
    public static final String STATE_TRANSITIONING = "TRANSITIONING";

    private WrzCastProtocol() {
    }

    /** 协议消息（Gson 序列化，字段按消息类型选择性使用）。 */
    public static class Message {
        /** 消息类型：discover/announce/load/play/pause/seek/stop/status/result */
        public String type;
        public String protocol;
        public int version;

        // ===== 发现（announce）=====
        /** 接收端持久化设备 ID */
        public String id;
        /** 接收端显示名称 */
        public String name;
        /** TCP 控制端口 */
        public int port;

        // ===== 请求/响应 =====
        public String requestId;
        public boolean success;
        public String message;

        // ===== 投屏内容（load）=====
        public String uri;
        public String title;
        public String caster;
        /** 起始播放位置（毫秒） */
        public long position;

        // ===== 状态（status/result）=====
        public String state;
        public long duration;
    }
}
