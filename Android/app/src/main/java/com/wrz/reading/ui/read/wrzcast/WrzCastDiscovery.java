package com.wrz.reading.ui.read.wrzcast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.wrz.reading.ui.main.Log.LogUtil;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WrzCast 发送端设备发现：周期性向局域网广播 UDP discover，
 * 收集接收端回复的 announce，回调通知设备列表变化。
 *
 * 线程模型：扫描在独立后台线程执行，{@link Listener} 回调切回主线程。
 */
public class WrzCastDiscovery {

    private static final String TAG = "WrzCastDiscovery";
    /** 每轮广播后等待回复的时长 */
    private static final long SCAN_WINDOW_MS = 1500L;
    /** 两轮扫描之间的间隔 */
    private static final long SCAN_INTERVAL_MS = 1000L;

    public interface Listener {
        void onDevicesChanged();
    }

    private final Gson gson = new Gson();
    private final Map<String, WrzCastDevice> devices = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 本机设备 ID：过滤掉本机接收端，避免投屏到自己 */
    private final String localDeviceId;

    private volatile boolean running = false;
    private volatile DatagramSocket socket;
    private Thread scanThread;
    private Listener listener;

    public WrzCastDiscovery(Context context) {
        localDeviceId = WrzCastReceiverManager.ensureDeviceId(context);
    }

    /** 开始（或继续）扫描；重复调用不会创建新线程。 */
    public void start(Listener l) {
        listener = l;
        if (running) {
            return;
        }
        devices.clear();
        running = true;
        scanThread = new Thread(this::scanLoop, "wrzcast-discovery");
        scanThread.start();
    }

    /** 停止扫描并清空设备列表。 */
    public void stop() {
        running = false;
        listener = null;
        DatagramSocket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        socket = null;
        Thread t = scanThread;
        if (t != null) {
            t.interrupt();
            scanThread = null;
        }
        devices.clear();
    }

    /** 当前已发现的设备快照。 */
    public List<WrzCastDevice> getDevices() {
        return new ArrayList<>(devices.values());
    }

    private void scanLoop() {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(300);
            socket = s;

            WrzCastProtocol.Message discover = new WrzCastProtocol.Message();
            discover.type = WrzCastProtocol.TYPE_DISCOVER;
            discover.protocol = WrzCastProtocol.PROTOCOL;
            discover.version = WrzCastProtocol.VERSION;
            byte[] payload = gson.toJson(discover).getBytes(StandardCharsets.UTF_8);
            List<InetAddress> targets = getBroadcastAddresses();

            while (running) {
                sendDiscover(s, payload, targets);
                boolean changed = false;
                long deadline = System.currentTimeMillis() + SCAN_WINDOW_MS;
                while (running && System.currentTimeMillis() < deadline) {
                    byte[] buf = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    try {
                        s.receive(packet);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    changed |= handleAnnounce(packet);
                }
                if (changed) {
                    notifyDevicesChanged();
                }
                try {
                    Thread.sleep(SCAN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            if (running) {
                LogUtil.e(TAG, "WrzCast 发现异常", e);
            }
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
            socket = null;
        }
    }

    /** 向所有可能的广播地址发送一轮 discover。 */
    private void sendDiscover(DatagramSocket s, byte[] payload, List<InetAddress> targets) {
        for (InetAddress addr : targets) {
            try {
                s.send(new DatagramPacket(payload, payload.length, addr, WrzCastProtocol.DISCOVERY_PORT));
            } catch (Exception e) {
                LogUtil.d(TAG, "发送 WrzCast 广播失败: " + addr);
            }
        }
    }

    /** 解析 announce 并加入设备列表，返回是否有变化。 */
    private boolean handleAnnounce(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            WrzCastProtocol.Message msg = gson.fromJson(json, WrzCastProtocol.Message.class);
            if (msg == null
                    || !WrzCastProtocol.TYPE_ANNOUNCE.equals(msg.type)
                    || !WrzCastProtocol.PROTOCOL.equals(msg.protocol)
                    || msg.port <= 0) {
                return false;
            }
            String host = packet.getAddress().getHostAddress();
            if (msg.id != null && msg.id.equals(localDeviceId)) {
                // 本机接收端，忽略
                return false;
            }
            String name = (msg.name == null || msg.name.isEmpty()) ? host : msg.name;
            WrzCastDevice device = new WrzCastDevice(
                    msg.id == null ? host : msg.id, name, host, msg.port);
            return devices.put(device.key(), device) == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 本机各网卡的广播地址 + 受限广播地址（255.255.255.255）。 */
    private static List<InetAddress> getBroadcastAddresses() {
        Set<InetAddress> result = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast instanceof Inet4Address) {
                        result.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            result.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
        }
        return new ArrayList<>(result);
    }

    private void notifyDevicesChanged() {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onDevicesChanged();
            }
        });
    }
}
