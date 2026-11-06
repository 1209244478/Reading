package com.wrz.reading.ui.read.wrzcast;

/**
 * 局域网内发现的 WrzCast 接收端设备。
 */
public class WrzCastDevice {

    /** 接收端持久化设备 ID（用于去重与识别） */
    public final String id;
    /** 接收端显示名称 */
    public final String name;
    /** 接收端 IP */
    public final String host;
    /** TCP 控制端口 */
    public final int port;

    public WrzCastDevice(String id, String name, String host, int port) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
    }

    /** 同网络下可能通过多网卡重复上报，按 host:port 去重。 */
    public String key() {
        return host + ":" + port;
    }
}
