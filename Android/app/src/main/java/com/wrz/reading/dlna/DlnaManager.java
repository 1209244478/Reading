package com.wrz.reading.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.fourthline.cling.support.avtransport.callback.GetPositionInfo;
import org.fourthline.cling.support.avtransport.callback.Pause;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.Seek;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.avtransport.callback.Stop;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.ProtocolInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.VideoItem;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * DLNA 投屏管理器（单例）。
 * 负责：绑定 Cling 的 AndroidUpnpService、搜索局域网内的 MediaRenderer 设备、
 * 启动本地 HTTP 服务器提供视频、调用电视的 AVTransport 进行投屏与停止。
 *
 * 说明：Cling 的 Device/Service/ActionInvocation 均为泛型类型，
 * 此处为简化使用使用原始类型（raw type），相关 unchecked 警告可忽略。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DlnaManager {

    private static final String TAG = "DlnaManager";
    private static final int DEFAULT_PORT = 8989;
    /** AVTransport 服务类型，MediaRenderer 设备必须提供该服务才能投屏 */
    private static final UDAServiceType AV_TRANSPORT = new UDAServiceType("AVTransport", 1);

    private static volatile DlnaManager instance;

    private Context appContext;
    private AndroidUpnpService upnpService;
    private boolean bound = false;

    private LocalVideoServer videoServer;
    private int serverPort = DEFAULT_PORT;
    private Device castingDevice;
    /** 组播锁：Android 默认丢弃组播包，必须持有该锁才能收到 SSDP NOTIFY 设备宣告 */
    private WifiManager.MulticastLock multicastLock;
    /** WiFi 锁：投屏期间保持 WiFi 高性能，防止息屏后 WiFi 低功耗导致拉流中断 */
    private WifiManager.WifiLock wifiLock;
    /** 唤醒锁：投屏期间保持 CPU 唤醒，让 LocalVideoServer 持续响应电视拉流请求 */
    private PowerManager.WakeLock wakeLock;

    private DeviceListener deviceListener;

    /** 设备列表变化回调（在 Cling 后台线程触发，调用方需自行切回主线程） */
    public interface DeviceListener {
        void onDeviceChanged();
    }

    /** 投屏结果回调 */
    public interface CastListener {
        void onSuccess();

        void onFailure(String message);
    }

    /** 播放进度查询回调（在 Cling 后台线程触发） */
    public interface PlaybackInfoListener {
        void onResult(long positionMs, long durationMs);

        void onFailure(String message);
    }

    /** 简单的成功/失败回调，用于 pause/resume/seek 等 */
    public interface SimpleCallback {
        void onSuccess();

        void onFailure(String message);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            upnpService = (AndroidUpnpService) service;
            upnpService.getRegistry().addListener(registryListener);
            // 显式获取组播锁，确保能收到 SSDP NOTIFY 设备宣告
            acquireMulticastLock();
            Log.d(TAG, "Cling 服务已连接，multicastLock held=" + (multicastLock != null && multicastLock.isHeld()));
            // 服务就绪后立即搜索一次
            upnpService.getControlPoint().search();
            Log.d(TAG, "search() 已调用");
            // 8 秒后打印 registry 状态，用于诊断是否收到任何设备
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (upnpService != null) {
                    java.util.Collection<Device> all = upnpService.getRegistry().getDevices();
                    Log.d(TAG, "8s 后 registry 设备总数=" + all.size());
                    for (Device d : all) {
                        Log.d(TAG, "  设备: " + d.getDisplayString()
                                + " type=" + d.getType()
                                + " services=" + d.getServices().length);
                    }
                }
            }, 8000);
            notifyDeviceChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            upnpService = null;
        }
    };

    private final RegistryListener registryListener = new DefaultRegistryListener() {
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            Log.d(TAG, "discovery started: " + device.getDisplayString());
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            Log.w(TAG, "discovery FAILED: " + device.getDisplayString(), ex);
        }

        @Override
        public void deviceAdded(Registry registry, Device device) {
            Log.d(TAG, "deviceAdded: " + device.getDisplayString()
                    + " type=" + device.getType()
                    + " services=" + device.getServices().length);
            // 仅关心带 AVTransport 服务的设备（即 MediaRenderer）
            if (device.findService(AV_TRANSPORT) != null) {
                Log.d(TAG, "  含 AVTransport，通知刷新");
                notifyDeviceChanged();
            } else {
                Log.d(TAG, "  无 AVTransport 服务");
            }
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            // 设备描述异步加载完成后会触发 updated，此时服务才可用
            if (device.findService(AV_TRANSPORT) != null) {
                notifyDeviceChanged();
            }
        }

        @Override
        public void deviceRemoved(Registry registry, Device device) {
            if (device.findService(AV_TRANSPORT) != null) {
                notifyDeviceChanged();
            }
        }
    };

    private DlnaManager() {
    }

    public static DlnaManager getInstance() {
        if (instance == null) {
            synchronized (DlnaManager.class) {
                if (instance == null) {
                    instance = new DlnaManager();
                }
            }
        }
        return instance;
    }

    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    /** 绑定 Cling 的 AndroidUpnpService */
    public void bind(Context context) {
        if (bound) {
            return;
        }
        appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, CustomAndroidUpnpServiceImpl.class);
        bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /** 解绑服务 */
    public void unbind() {
        if (!bound) {
            return;
        }
        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
        }
        try {
            appContext.unbindService(serviceConnection);
        } catch (Exception e) {
            Log.w(TAG, "unbindService 失败", e);
        }
        bound = false;
        upnpService = null;
        releaseMulticastLock();
    }

    /** 获取组播锁，确保能接收 SSDP NOTIFY 组播包 */
    private void acquireMulticastLock() {
        if (multicastLock != null) {
            return;
        }
        WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            return;
        }
        multicastLock = wifi.createMulticastLock("cling-dlna");
        multicastLock.setReferenceCounted(false);
        try {
            multicastLock.acquire();
        } catch (Exception e) {
            Log.w(TAG, "acquireMulticastLock 失败", e);
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

    /**
     * 获取保活锁（WiFi 锁 + 唤醒锁），防止手机息屏后 WiFi 进入低功耗、CPU 休眠，
     * 导致 LocalVideoServer 无法响应电视拉流而中断播放。
     * 在投屏成功启动本地服务器后调用。
     */
    private void acquireKeepAliveLocks() {
        // WiFi 锁：保持 WiFi 高性能连接
        if (wifiLock == null) {
            WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                try {
                    wifiLock = wifi.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dlna-cast-wifi");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                    Log.d(TAG, "WifiLock 已获取（WIFI_MODE_FULL_HIGH_PERF）");
                } catch (Exception e) {
                    Log.w(TAG, "获取 WifiLock 失败", e);
                    wifiLock = null;
                }
            }
        }
        // 唤醒锁：保持 CPU 唤醒，让 NanoHTTPD 持续响应
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                try {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dlna-cast-cpu");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                    Log.d(TAG, "WakeLock 已获取（PARTIAL_WAKE_LOCK）");
                } catch (Exception e) {
                    Log.w(TAG, "获取 WakeLock 失败", e);
                    wakeLock = null;
                }
            }
        }
    }

    /** 释放保活锁（投屏停止时调用） */
    private void releaseKeepAliveLocks() {
        if (wifiLock != null) {
            try {
                wifiLock.release();
                Log.d(TAG, "WifiLock 已释放");
            } catch (Exception ignored) {
            }
            wifiLock = null;
        }
        if (wakeLock != null) {
            try {
                wakeLock.release();
                Log.d(TAG, "WakeLock 已释放");
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    /** 主动触发一次设备搜索 */
    public void startDiscovery() {
        if (upnpService != null) {
            upnpService.getControlPoint().search();
        }
    }

    /** 返回当前已发现的 MediaRenderer 设备列表 */
    public List<Device> getDevices() {
        List<Device> result = new ArrayList<>();
        if (upnpService == null) {
            return result;
        }
        Collection<Device> all = upnpService.getRegistry().getDevices();
        for (Device d : all) {
            if (d.findService(AV_TRANSPORT) != null) {
                result.add(d);
            }
        }
        return result;
    }

    private void notifyDeviceChanged() {
        if (deviceListener != null) {
            deviceListener.onDeviceChanged();
        }
    }

    /**
     * 投屏到指定设备。
     *
     * @param device     目标 MediaRenderer 设备
     * @param videoFile  本地视频文件
     * @param title      视频标题（用于电视端显示）
     * @param listener   投屏结果回调（在 Cling 后台线程触发）
     */
    public void cast(Device device, File videoFile, String title, CastListener listener) {
        if (upnpService == null) {
            if (listener != null) listener.onFailure("DLNA 服务未就绪");
            return;
        }
        // 先停止之前的投屏
        stopCastInternal();

        castingDevice = device;

        // 启动本地 HTTP 服务器，端口被占用则递增
        serverPort = DEFAULT_PORT;
        if (!startServer(videoFile)) {
            if (listener != null) listener.onFailure("无法启动本地服务器");
            return;
        }
        // 获取保活锁，防止手机息屏后 WiFi/CPU 休眠导致电视拉流中断
        acquireKeepAliveLocks();

        String ip = getLocalIpAddress();
        if (ip == null) {
            stopServer();
            releaseKeepAliveLocks();
            if (listener != null) listener.onFailure("无法获取本机 IP，请确认已连接 WiFi");
            return;
        }
        final String videoUrl = "http://" + ip + ":" + serverPort + "/";
        String mimeType = LocalVideoServer.getMimeType(videoFile.getName());
        String protocolInfo = "http-get:*:" + mimeType + ":*";

        // 手动构造 DIDL-Lite 元数据（不用 DIDLParser，因其内部用 seamless SAXParser
        // 会设置 Android 不支持的 disallow-doctype-decl feature 导致异常）
        String metadata = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                + " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"1\" parentID=\"0\" restricted=\"1\">"
                + "<dc:title>" + escapeXml(title) + "</dc:title>"
                + "<upnp:class>object.item.videoItem</upnp:class>"
                + "<res protocolInfo=\"" + escapeXml(protocolInfo) + "\" size=\""
                + videoFile.length() + "\">" + escapeXml(videoUrl) + "</res>"
                + "</item></DIDL-Lite>";

        final Service avTransport = device.findService(AV_TRANSPORT);
        if (avTransport == null) {
            stopServer();
            if (listener != null) listener.onFailure("设备不支持 AVTransport");
            return;
        }

        final ControlPoint cp = upnpService.getControlPoint();
        final CastListener finalListener = listener;

        // 设置投屏地址
        cp.execute(new SetAVTransportURI(avTransport, videoUrl, metadata) {
            @Override
            public void success(ActionInvocation invocation) {
                // 地址设置成功后调用 Play
                cp.execute(new Play(avTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        if (finalListener != null) finalListener.onSuccess();
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        if (finalListener != null) finalListener.onFailure("播放失败: " + defaultMsg);
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                if (finalListener != null) finalListener.onFailure("设置投屏地址失败: " + defaultMsg);
            }
        });
    }

    /** 停止投屏并关闭本地 HTTP 服务器 */
    public void stopCast() {
        stopCastInternal();
    }

    private void stopCastInternal() {
        if (upnpService != null && castingDevice != null) {
            Service avTransport = castingDevice.findService(AV_TRANSPORT);
            if (avTransport != null) {
                try {
                    upnpService.getControlPoint().execute(new Stop(avTransport) {
                        @Override
                        public void success(ActionInvocation invocation) {
                            // 停止投屏无需处理结果
                        }

                        @Override
                        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                            Log.w(TAG, "Stop 投屏失败: " + defaultMsg);
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Stop 投屏失败", e);
                }
            }
        }
        castingDevice = null;
        stopServer();
        releaseKeepAliveLocks();
    }

    /** 当前是否正在投屏 */
    public boolean isCasting() {
        return castingDevice != null;
    }

    /** 获取当前投屏目标设备（可能为 null） */
    public Device getCastingDevice() {
        return castingDevice;
    }

    /**
     * 查询电视端当前播放进度。
     * 在 Cling 后台线程回调，调用方需自行切回主线程。
     */
    public void getPlaybackInfo(PlaybackInfoListener listener) {
        if (upnpService == null || castingDevice == null) {
            if (listener != null) listener.onFailure("未在投屏");
            return;
        }
        Service avTransport = castingDevice.findService(AV_TRANSPORT);
        if (avTransport == null) {
            if (listener != null) listener.onFailure("设备不支持 AVTransport");
            return;
        }
        final PlaybackInfoListener finalListener = listener;
        try {
            upnpService.getControlPoint().execute(new GetPositionInfo(avTransport) {
                @Override
                public void received(ActionInvocation invocation, PositionInfo positionInfo) {
                    if (finalListener == null) return;
                    long pos = parseTimeToMs(positionInfo.getRelTime());
                    long dur = parseTimeToMs(positionInfo.getTrackDuration());
                    finalListener.onResult(pos, dur);
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    if (finalListener != null) finalListener.onFailure(defaultMsg);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "getPlaybackInfo 异常", e);
            if (finalListener != null) finalListener.onFailure(e.getMessage());
        }
    }

    /** 暂停电视端播放 */
    public void pause(SimpleCallback callback) {
        executeSimple(new PauseCallbackFactory() {
            @Override
            public ActionCallback create(Service avTransport) {
                return new Pause(avTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        if (callback != null) callback.onSuccess();
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        if (callback != null) callback.onFailure(defaultMsg);
                    }
                };
            }
        }, callback);
    }

    /** 恢复电视端播放 */
    public void resumePlay(SimpleCallback callback) {
        executeSimple(new PauseCallbackFactory() {
            @Override
            public ActionCallback create(Service avTransport) {
                return new Play(avTransport) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        if (callback != null) callback.onSuccess();
                    }

                    @Override
                    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                        if (callback != null) callback.onFailure(defaultMsg);
                    }
                };
            }
        }, callback);
    }

    /**
     * 拖动到指定时间。target 格式 "H:MM:SS.mmm"（如 "0:01:30.000"），
     * 也接受 "M:SS" / "MM:SS" 简写，内部会归一化。
     */
    public void seek(String target, SimpleCallback callback) {
        if (upnpService == null || castingDevice == null) {
            if (callback != null) callback.onFailure("未在投屏");
            return;
        }
        Service avTransport = castingDevice.findService(AV_TRANSPORT);
        if (avTransport == null) {
            if (callback != null) callback.onFailure("设备不支持 AVTransport");
            return;
        }
        String normalized = normalizeSeekTarget(target);
        final SimpleCallback finalCallback = callback;
        try {
            upnpService.getControlPoint().execute(new Seek(avTransport, normalized) {
                @Override
                public void success(ActionInvocation invocation) {
                    if (finalCallback != null) finalCallback.onSuccess();
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    if (finalCallback != null) finalCallback.onFailure(defaultMsg);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "seek 异常", e);
            if (finalCallback != null) finalCallback.onFailure(e.getMessage());
        }
    }

    private interface PauseCallbackFactory {
        ActionCallback create(Service avTransport);
    }

    private void executeSimple(PauseCallbackFactory factory, SimpleCallback callback) {
        if (upnpService == null || castingDevice == null) {
            if (callback != null) callback.onFailure("未在投屏");
            return;
        }
        Service avTransport = castingDevice.findService(AV_TRANSPORT);
        if (avTransport == null) {
            if (callback != null) callback.onFailure("设备不支持 AVTransport");
            return;
        }
        try {
            upnpService.getControlPoint().execute(factory.create(avTransport));
        } catch (Exception e) {
            Log.w(TAG, "executeSimple 异常", e);
            if (callback != null) callback.onFailure(e.getMessage());
        }
    }

    /**
     * 解析 DLNA 时间字符串为毫秒。
     * 接受 "H:MM:SS.mmm"、"H:MM:SS"、"M:SS" 等格式。
     * "NOT_IMPLEMENTED" 或非法值返回 0。
     */
    public static long parseTimeToMs(String time) {
        if (time == null) return 0;
        time = time.trim();
        if (time.isEmpty() || "NOT_IMPLEMENTED".equalsIgnoreCase(time)) {
            return 0;
        }
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return (long) (h * 3600000 + m * 60000 + s * 1000);
            } else if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                double s = Double.parseDouble(parts[1]);
                return (long) (m * 60000 + s * 1000);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return 0;
    }

    /** 将毫秒格式化为 "H:MM:SS.mmm"（Seek 入参要求） */
    public static String formatTimeForSeek(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        long millis = ms % 1000;
        return String.format("%d:%02d:%02d.%03d", h, m, s, millis);
    }

    /** 将毫秒格式化为用户可读时间（"M:SS" 或 "H:MM:SS"） */
    public static String formatTimeReadable(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }

    /** 将用户输入/简写时间归一化为 "H:MM:SS.mmm" */
    private static String normalizeSeekTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            return "0:00:00.000";
        }
        target = target.trim();
        // 已是 H:MM:SS(.mmm) 格式
        if (target.contains(":")) {
            long ms = parseTimeToMs(target);
            return formatTimeForSeek(ms);
        }
        // 纯数字当作秒数
        try {
            long sec = Long.parseLong(target);
            return formatTimeForSeek(sec * 1000);
        } catch (NumberFormatException e) {
            return "0:00:00.000";
        }
    }

    /** 彻底释放资源（Activity 销毁时调用） */
    public void release() {
        stopCastInternal();
        unbind();
    }

    /** XML 特殊字符转义 */
    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private boolean startServer(File videoFile) {
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

    /** 获取本机局域网 IPv4 地址（优先 wlan，排除 loopback） */
    private static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp() || intf.isLoopback()) {
                    continue;
                }
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.w(TAG, "获取本机 IP 失败", e);
        }
        return null;
    }
}
