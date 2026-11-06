package com.wrz.reading.ui.read.wrzcast;

import android.util.Log;

import com.google.gson.Gson;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.utils.ExecutorManager;
import com.wrz.reading.ui.read.dlna.DlnaManager;
import com.wrz.reading.ui.read.dlna.LocalVideoServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WrzCast 发送端：连接接收端 TCP 控制通道，下发投屏与控制指令，轮询播放状态。
 *
 * 本地文件投屏时复用 {@link LocalVideoServer} 以 HTTP 形式提供视频，
 * 接收端通过同一个局域网地址拉流播放。
 *
 * 线程模型：网络请求在共享线程池执行，回调也在该后台线程触发，
 * 调用方（VideoPlayerActivity / CastPollingController）需自行切回主线程更新 UI。
 */
public class WrzCastSender {

    private static final String TAG = "WrzCastSender";
    private static final int DEFAULT_PORT = 8989;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    public interface CastListener {
        void onSuccess();

        void onFailure(String message);
    }

    public interface SimpleCallback {
        void onSuccess();

        void onFailure(String message);
    }

    public interface PlaybackInfoListener {
        void onResult(long positionMs, long durationMs, String transportState);

        void onFailure(String message);
    }

    private final Gson gson = new Gson();
    private final Object ioLock = new Object();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    /** 当前 TCP 连接对应的接收端（仅连接层使用） */
    private WrzCastDevice device;
    /** 当前投屏目标（网络抖动重连后仍保留） */
    private volatile WrzCastDevice targetDevice;
    private int requestSeq = 0;

    private LocalVideoServer videoServer;
    private int serverPort = DEFAULT_PORT;
    /** 防止网络异常时每秒轮询在共享线程池堆积 */
    private final AtomicBoolean statusInFlight = new AtomicBoolean(false);

    // ===== 投屏 =====

    /** 投屏本地视频文件。 */
    public void cast(WrzCastDevice dev, File videoFile, String title, long startPositionMs,
                     boolean isChangeCast, CastListener listener) {
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            if (isChangeCast) {
                closeSession();
            }
            stopServer();
            if (!startServer(videoFile)) {
                stopServer();
                fail(listener, "无法启动本地服务器");
                return;
            }
            String ip = DlnaManager.getLocalIpAddress();
            if (ip == null) {
                stopServer();
                fail(listener, "无法获取本机 IP，请确认已连接 WiFi");
                return;
            }
            String casterName = MyApplication.manager.getCastName();
            String casterParam;
            try {
                casterParam = URLEncoder.encode(casterName, "UTF-8");
            } catch (Exception e) {
                casterParam = "unknown";
            }
            String uri = "http://" + ip + ":" + serverPort + "/?caster=" + casterParam;

            WrzCastProtocol.Message load = new WrzCastProtocol.Message();
            load.type = WrzCastProtocol.TYPE_LOAD;
            load.uri = uri;
            load.title = title;
            load.caster = casterName;
            load.position = Math.max(startPositionMs, 0);
            sendLoad(dev, load, listener);
        });
    }

    /** 投屏在线视频（接收端转发到另一台设备时使用）。 */
    public void cast(WrzCastDevice dev, String uri, String title, long startPositionMs,
                     boolean isChangeCast, CastListener listener) {
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            if (isChangeCast) {
                closeSession();
            }
            stopServer();
            WrzCastProtocol.Message load = new WrzCastProtocol.Message();
            load.type = WrzCastProtocol.TYPE_LOAD;
            load.uri = uri;
            load.title = title;
            load.caster = MyApplication.manager.getCastName();
            load.position = Math.max(startPositionMs, 0);
            sendLoad(dev, load, listener);
        });
    }

    private void sendLoad(WrzCastDevice dev, WrzCastProtocol.Message load, CastListener listener) {
        targetDevice = dev;
        try {
            WrzCastProtocol.Message result = request(dev, load);
            if (result != null && result.success) {
                if (listener != null) listener.onSuccess();
            } else {
                targetDevice = null;
                stopServer();
                fail(listener, result != null && result.message != null ? result.message : "接收端无响应");
            }
        } catch (Exception e) {
            Log.w(TAG, "WrzCast 投屏失败", e);
            targetDevice = null;
            stopServer();
            fail(listener, e.getMessage() != null ? e.getMessage() : "连接接收端失败");
        }
    }

    // ===== 播放控制 =====

    public void pause(SimpleCallback callback) {
        sendCommand(WrzCastProtocol.TYPE_PAUSE, 0, callback);
    }

    public void resumePlay(SimpleCallback callback) {
        sendCommand(WrzCastProtocol.TYPE_PLAY, 0, callback);
    }

    public void seek(long positionMs, SimpleCallback callback) {
        sendCommand(WrzCastProtocol.TYPE_SEEK, positionMs, callback);
    }

    private void sendCommand(String type, long position, SimpleCallback callback) {
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            WrzCastDevice dev = targetDevice;
            if (dev == null) {
                fail(callback, "未在投屏");
                return;
            }
            WrzCastProtocol.Message req = new WrzCastProtocol.Message();
            req.type = type;
            req.position = Math.max(position, 0);
            try {
                WrzCastProtocol.Message result = request(dev, req);
                if (result != null && result.success) {
                    if (callback != null) callback.onSuccess();
                } else {
                    fail(callback, result != null && result.message != null ? result.message : "接收端无响应");
                }
            } catch (Exception e) {
                fail(callback, e.getMessage() != null ? e.getMessage() : "指令发送失败");
            }
        });
    }

    /** 停止投屏：通知接收端停止播放并释放连接与本地服务器。 */
    public void stopCast() {
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            WrzCastDevice dev = targetDevice;
            targetDevice = null;
            if (dev != null) {
                try {
                    WrzCastProtocol.Message stop = new WrzCastProtocol.Message();
                    stop.type = WrzCastProtocol.TYPE_STOP;
                    request(dev, stop);
                } catch (Exception ignored) {
                }
            }
            closeSession();
            stopServer();
        });
    }

    /** 查询接收端播放进度与状态。 */
    public void getPlaybackInfo(PlaybackInfoListener listener) {
        if (!statusInFlight.compareAndSet(false, true)) {
            if (listener != null) listener.onFailure("状态查询繁忙");
            return;
        }
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            try {
                WrzCastDevice dev = targetDevice;
                if (dev == null) {
                    if (listener != null) listener.onFailure("未在投屏");
                    return;
                }
                WrzCastProtocol.Message req = new WrzCastProtocol.Message();
                req.type = WrzCastProtocol.TYPE_STATUS;
                try {
                    WrzCastProtocol.Message result = request(dev, req);
                    if (result != null && result.success) {
                        if (listener != null) {
                            listener.onResult(result.position, result.duration, result.state);
                        }
                    } else if (listener != null) {
                        listener.onFailure(result != null && result.message != null ? result.message : "接收端无响应");
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onFailure(e.getMessage() != null ? e.getMessage() : "状态查询失败");
                    }
                }
            } finally {
                statusInFlight.set(false);
            }
        });
    }

    /** 释放资源（Activity 销毁时调用；关闭连接可能等待读超时，放到后台执行）。 */
    public void release() {
        ExecutorManager.getInstance().getThreadPool().execute(() -> {
            targetDevice = null;
            closeSession();
            stopServer();
        });
    }

    // ===== TCP 通道 =====

    /**
     * 发送请求并等待匹配 requestId 的 result 响应。
     * 同一连接上的请求串行执行，避免响应错配。
     */
    private WrzCastProtocol.Message request(WrzCastDevice dev, WrzCastProtocol.Message req)
            throws IOException {
        synchronized (ioLock) {
            ensureConnected(dev);
            try {
                req.requestId = String.valueOf(++requestSeq);
                writer.write(gson.toJson(req));
                writer.newLine();
                writer.flush();

                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        throw new IOException("接收端已断开连接");
                    }
                    WrzCastProtocol.Message response;
                    try {
                        response = gson.fromJson(line, WrzCastProtocol.Message.class);
                    } catch (Exception e) {
                        continue;
                    }
                    if (response != null
                            && WrzCastProtocol.TYPE_RESULT.equals(response.type)
                            && req.requestId.equals(response.requestId)) {
                        return response;
                    }
                }
            } catch (IOException e) {
                // 连接已失效：关闭后下次请求会重新建立连接
                closeSessionInternal();
                throw e;
            }
        }
    }

    /** 确保与目标设备已建立 TCP 连接（同一设备复用连接）。 */
    private void ensureConnected(WrzCastDevice dev) throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()
                && device != null
                && device.host.equals(dev.host) && device.port == dev.port) {
            return;
        }
        closeSessionInternal();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(dev.host, dev.port), CONNECT_TIMEOUT_MS);
        s.setSoTimeout(READ_TIMEOUT_MS);
        socket = s;
        reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        device = dev;
        LogUtil.d(TAG, "WrzCast 已连接: " + dev.host + ":" + dev.port);
    }

    private void closeSession() {
        synchronized (ioLock) {
            closeSessionInternal();
        }
    }

    /** 需在持有 ioLock 时调用。 */
    private void closeSessionInternal() {
        device = null;
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            reader = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ignored) {
            }
            writer = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            socket = null;
        }
    }

    // ===== 本地 HTTP 服务器 =====

    private boolean startServer(File videoFile) {
        serverPort = DEFAULT_PORT;
        videoServer = new LocalVideoServer(serverPort, videoFile);
        try {
            videoServer.start();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "端口 " + serverPort + " 启动失败，尝试递增", e);
        }
        for (int i = 0; i < 20; i++) {
            serverPort++;
            videoServer = new LocalVideoServer(serverPort, videoFile);
            try {
                videoServer.start();
                return true;
            } catch (Exception e) {
                // 继续尝试下一个端口
            }
        }
        return false;
    }

    private void stopServer() {
        if (videoServer != null) {
            try {
                videoServer.stop();
            } catch (Exception e) {
                Log.w(TAG, "停止本地服务器失败", e);
            }
            videoServer = null;
        }
    }

    private static void fail(CastListener listener, String message) {
        if (listener != null) listener.onFailure(message);
    }

    private static void fail(SimpleCallback callback, String message) {
        if (callback != null) callback.onFailure(message);
    }
}
