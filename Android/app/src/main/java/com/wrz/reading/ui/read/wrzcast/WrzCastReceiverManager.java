package com.wrz.reading.ui.read.wrzcast;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.activity.VideoPlayerActivity;
import com.wrz.reading.ui.read.cast.CastPlayerBridge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WrzCast 接收端管理器（单例）。
 *
 * 职责：在局域网内响应 UDP discover 广播（返回本机名称与控制端口），
 * 通过 TCP 接收发送端的 load/play/pause/seek/stop/status 指令，
 * 并转发给当前注册的 {@link CastPlayerBridge}（即接收模式的 VideoPlayerActivity）。
 *
 * 线程模型：网络 IO 在独立线程/线程池执行；对 ExoPlayer 的操作通过主线程 Handler 派发。
 */
public class WrzCastReceiverManager {

    private static final String TAG = "WrzCastReceiver";
    private static final String PREF_NAME = "wrzcast_prefs";
    private static final String KEY_DEVICE_ID = "device_id";

    private static volatile WrzCastReceiverManager instance;

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private volatile boolean started = false;

    private ServerSocket serverSocket;
    private DatagramSocket discoverySocket;
    private Thread acceptThread;
    private Thread discoveryThread;
    private ExecutorService clientExecutor;
    /** 已连接的发送端 socket（stop 时主动关闭以解除阻塞读） */
    private final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());
    private WifiManager.MulticastLock multicastLock;

    /** 实际使用的 TCP 控制端口（默认端口被占用时递增） */
    private volatile int controlPort = WrzCastProtocol.DEFAULT_CONTROL_PORT;
    private volatile String deviceId;

    // ===== 接收端运行时状态 =====
    private volatile CastPlayerBridge bridge;
    private volatile boolean activityLaunched = false;
    private volatile String currentUri;
    private volatile String currentTitle;
    private volatile String currentCaster = "";
    private volatile String transportState = WrzCastProtocol.STATE_STOPPED;
    private volatile long pendingSeekMs = 0;

    private WrzCastReceiverManager() {
    }

    public static WrzCastReceiverManager getInstance() {
        if (instance == null) {
            synchronized (WrzCastReceiverManager.class) {
                if (instance == null) {
                    instance = new WrzCastReceiverManager();
                }
            }
        }
        return instance;
    }

    /** 启动接收端：打开 TCP 控制端口并开始响应 UDP 设备发现。 */
    public void start(Context context) {
        if (started) {
            return;
        }
        appContext = context.getApplicationContext();
        started = true;
        acquireMulticastLock();
        deviceId = getOrCreateDeviceId();
        clientExecutor = Executors.newCachedThreadPool();
        startControlServer();
        if (serverSocket == null) {
            return;
        }
        startDiscoveryResponder();
        LogUtil.d(TAG, "WrzCast 接收端已启动, port=" + controlPort
                + ", server=" + (serverSocket != null));
    }

    /** 停止接收端：关闭所有端口与线程。 */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
        if (discoverySocket != null) {
            try {
                discoverySocket.close();
            } catch (Exception ignored) {
            }
            discoverySocket = null;
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
        synchronized (clientSockets) {
            for (Socket client : new ArrayList<>(clientSockets)) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
            clientSockets.clear();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            clientExecutor = null;
        }
        releaseMulticastLock();
        bridge = null;
        activityLaunched = false;
        pendingSeekMs = 0;
        transportState = WrzCastProtocol.STATE_STOPPED;
        currentUri = null;
        currentTitle = null;
        currentCaster = "";
    }

    public boolean isStarted() {
        return started;
    }

    // ===== 端口与发现 =====

    private void startControlServer() {
        for (int i = 0; i < 20; i++) {
            int port = WrzCastProtocol.DEFAULT_CONTROL_PORT + i;
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(port));
                serverSocket = ss;
                controlPort = port;
                break;
            } catch (Exception e) {
                Log.w(TAG, "TCP 端口 " + port + " 绑定失败，尝试下一个", e);
            }
        }
        if (serverSocket == null) {
            LogUtil.e(TAG, "WrzCast 控制端口全部绑定失败");
            return;
        }
        acceptThread = new Thread(() -> {
            while (started) {
                ServerSocket ss = serverSocket;
                if (ss == null) {
                    break;
                }
                try {
                    Socket client = ss.accept();
                    clientSockets.add(client);
                    ExecutorService executor = clientExecutor;
                    if (executor != null) {
                        executor.execute(() -> handleClient(client));
                    } else {
                        clientSockets.remove(client);
                        client.close();
                    }
                } catch (Exception e) {
                    if (started) {
                        Log.w(TAG, "WrzCast accept 失败", e);
                    }
                    break;
                }
            }
        }, "wrzcast-accept");
        acceptThread.start();
    }

    private void startDiscoveryResponder() {
        try {
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(WrzCastProtocol.DISCOVERY_PORT));
            socket.setSoTimeout(500);
            discoverySocket = socket;
        } catch (Exception e) {
            LogUtil.e(TAG, "WrzCast UDP 发现端口绑定失败", e);
            return;
        }
        discoveryThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (started) {
                DatagramSocket socket = discoverySocket;
                if (socket == null) {
                    break;
                }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (Exception e) {
                    break;
                }
                handleDiscoveryPacket(socket, packet);
            }
        }, "wrzcast-discovery-responder");
        discoveryThread.start();
    }

    private void handleDiscoveryPacket(DatagramSocket socket, DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            WrzCastProtocol.Message request = gson.fromJson(json, WrzCastProtocol.Message.class);
            if (request == null
                    || !WrzCastProtocol.TYPE_DISCOVER.equals(request.type)
                    || !WrzCastProtocol.PROTOCOL.equals(request.protocol)) {
                return;
            }
            if (serverSocket == null) {
                return;
            }
            WrzCastProtocol.Message announce = new WrzCastProtocol.Message();
            announce.type = WrzCastProtocol.TYPE_ANNOUNCE;
            announce.protocol = WrzCastProtocol.PROTOCOL;
            announce.version = WrzCastProtocol.VERSION;
            announce.id = deviceId;
            announce.name = MyApplication.manager.getCastName();
            announce.port = controlPort;
            byte[] data = gson.toJson(announce).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
        } catch (Exception e) {
            Log.w(TAG, "响应 WrzCast 发现失败", e);
        }
    }

    // ===== TCP 控制通道 =====

    private void handleClient(Socket client) {
        try (Socket ignored = client;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (started && (line = reader.readLine()) != null) {
                WrzCastProtocol.Message request;
                try {
                    request = gson.fromJson(line, WrzCastProtocol.Message.class);
                } catch (Exception e) {
                    continue;
                }
                if (request == null || request.type == null) {
                    continue;
                }
                WrzCastProtocol.Message result = handleRequest(request);
                writer.write(gson.toJson(result));
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            if (started) {
                LogUtil.d(TAG, "WrzCast 客户端断开: " + e.getMessage());
            }
        } finally {
            clientSockets.remove(client);
        }
    }

    private WrzCastProtocol.Message handleRequest(WrzCastProtocol.Message request) {
        WrzCastProtocol.Message result = new WrzCastProtocol.Message();
        result.type = WrzCastProtocol.TYPE_RESULT;
        result.requestId = request.requestId;
        switch (request.type) {
            case WrzCastProtocol.TYPE_LOAD:
                onLoad(request.uri, request.title, request.caster, request.position);
                result.success = true;
                break;
            case WrzCastProtocol.TYPE_PLAY:
                onPlay();
                result.success = true;
                break;
            case WrzCastProtocol.TYPE_PAUSE:
                onPause();
                result.success = true;
                break;
            case WrzCastProtocol.TYPE_SEEK:
                onSeek(request.position);
                result.success = true;
                break;
            case WrzCastProtocol.TYPE_STOP:
                onStop();
                result.success = true;
                break;
            case WrzCastProtocol.TYPE_STATUS:
                result.success = true;
                CastPlayerBridge b = bridge;
                result.position = b != null ? b.getPositionMs() : 0;
                result.duration = b != null ? b.getDurationMs() : 0;
                result.state = transportState;
                break;
            default:
                result.success = false;
                result.message = "不支持的消息类型";
                break;
        }
        return result;
    }

    // ===== 播放控制（网络线程调用） =====

    private void onLoad(String uri, String title, String caster, long position) {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        final String safeTitle = title == null ? "" : title;
        final String safeCaster = caster == null ? "" : caster;
        final long startMs = Math.max(position, 0);
        final boolean isNewDevice = !Objects.equals(safeCaster, currentCaster);

        currentUri = uri;
        currentTitle = safeTitle;
        transportState = WrzCastProtocol.STATE_TRANSITIONING;
        LogUtil.d(TAG, "onLoad: " + uri + " title=" + safeTitle
                + " caster=" + safeCaster + " newDevice=" + isNewDevice);

        final CastPlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(() -> {
                CastPlayerBridge current = bridge;
                if (current == null) {
                    return;
                }
                current.setUri(uri, safeTitle, isNewDevice, safeCaster);
                if (startMs > 0) {
                    current.seekTo(startMs);
                }
            });
        } else {
            currentCaster = safeCaster;
            pendingSeekMs = startMs;
            launchReceiverActivityIfNeeded();
        }
    }

    private void onPlay() {
        transportState = WrzCastProtocol.STATE_PLAYING;
        LogUtil.d(TAG, "onPlay");
        final CastPlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::play);
        } else {
            launchReceiverActivityIfNeeded();
        }
    }

    private void onPause() {
        transportState = WrzCastProtocol.STATE_PAUSED;
        LogUtil.d(TAG, "onPause");
        final CastPlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::pause);
        }
    }

    private void onSeek(long positionMs) {
        final long ms = Math.max(positionMs, 0);
        LogUtil.d(TAG, "onSeek: " + ms + "ms");
        final CastPlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(() -> {
                CastPlayerBridge current = bridge;
                if (current != null) {
                    current.seekTo(ms);
                }
            });
        } else {
            pendingSeekMs = ms;
        }
    }

    /** 停止接收（也用于接收端用户拒绝投屏时清理状态）。 */
    public void onStop() {
        transportState = WrzCastProtocol.STATE_STOPPED;
        activityLaunched = false;
        pendingSeekMs = 0;
        LogUtil.d(TAG, "onStop");
        final CastPlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::stop);
        }
    }

    private void launchReceiverActivityIfNeeded() {
        if (activityLaunched || appContext == null) {
            return;
        }
        activityLaunched = VideoPlayerActivity.RenderStart(
                appContext, currentUri, currentTitle, currentCaster, true);
    }

    // ===== PlayerBridge 注册 =====

    public void registerBridge(CastPlayerBridge b) {
        this.bridge = b;
        if (pendingSeekMs > 0) {
            final long seek = pendingSeekMs;
            pendingSeekMs = 0;
            LogUtil.d(TAG, "registerBridge: 执行缓存 seek=" + seek);
            mainHandler.post(() -> {
                CastPlayerBridge current = bridge;
                if (current != null) {
                    current.seekTo(seek);
                }
            });
        }
    }

    public void unregisterBridge() {
        this.bridge = null;
    }

    /** 接收模式 Activity 销毁时调用，清理桥接与活动标记。 */
    public void onPlayerDestroyed() {
        bridge = null;
        activityLaunched = false;
        pendingSeekMs = 0;
        transportState = WrzCastProtocol.STATE_STOPPED;
    }

    /** 接收端 ExoPlayer 播放/暂停状态变化时同步传输状态。 */
    public void onReceiverPlayStateChanged(boolean isPlaying) {
        transportState = isPlaying
                ? WrzCastProtocol.STATE_PLAYING
                : WrzCastProtocol.STATE_PAUSED;
    }

    public String getTransportState() {
        return transportState;
    }

    /** 接收端确认新发送端投屏后更新当前发送端名称。 */
    public void setCurrentCasterName(String casterName) {
        currentCaster = casterName == null ? "" : casterName;
    }

    // ===== 持久化与锁 =====

    private String getOrCreateDeviceId() {
        return ensureDeviceId(appContext);
    }

    /** 读取（不存在时生成）本机 WrzCast 设备 ID；发送端发现时用于过滤本机。 */
    public static String ensureDeviceId(Context context) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    private void acquireMulticastLock() {
        if (multicastLock != null) {
            return;
        }
        WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            return;
        }
        try {
            multicastLock = wifi.createMulticastLock("wrzcast-receiver");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception e) {
            Log.w(TAG, "获取 MulticastLock 失败", e);
            multicastLock = null;
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null) {
            try {
                multicastLock.release();
            } catch (Exception ignored) {
            }
            multicastLock = null;
        }
    }
}
