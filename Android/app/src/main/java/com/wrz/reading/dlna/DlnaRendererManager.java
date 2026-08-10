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

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.read.activity.VideoPlayerActivity;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;

import java.util.UUID;

/**
 * DLNA 投屏接收端（MediaRenderer）管理器（单例）。
 *
 * 职责：绑定 Cling 的 AndroidUpnpService（与 {@link DlnaManager} 独立的 ServiceConnection，
 * 同一服务可被多次绑定），构建并注册本机 MediaRenderer 设备（含 AVTransport 与
 * RenderingControl 两个服务），接收控制点的播放控制指令并转发给当前注册的
 * {@link PlayerBridge}（即接收模式的 VideoPlayerActivity）。
 *
 * 线程模型：AVTransport/RenderingControl 动作由 Cling 后台线程调用，状态字段使用 volatile
 * 保证可见性；对 ExoPlayer 的操作（play/pause/seek/setUri）通过主线程 Handler 派发，
 * 因为 ExoPlayer 只能在主线程访问。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DlnaRendererManager {

    private static final String TAG = "DlnaRendererManager";

    private static final String PREF_NAME = "dlna_prefs";
    private static final String KEY_RECEIVER = "pref_receive_cast";
    private static final String KEY_UDN = "renderer_udn";

    private static volatile DlnaRendererManager instance;

    private Context appContext;
    private AndroidUpnpService upnpService;
    private LocalDevice rendererDevice;
    private volatile boolean started = false;

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ===== 接收端运行时状态（volatile，跨线程可见） =====
    private volatile String currentUri;
    private volatile String currentTitle;
    /** 发送端设备名（从 SetAVTransportURI 的 URL query 参数 caster 解析） */
    private volatile String currentCasterName;
    private volatile String transportState = "NO_MEDIA_PRESENT";
    private volatile PlayerBridge bridge;
    private volatile boolean activityLaunched = false;
    /** onSeek 在 Activity/bridge 就绪前到达时缓存的 seek 位置，registerBridge 时执行。 */
    private volatile long pendingSeekMs = 0;

    /** 由接收模式 VideoPlayerActivity 实现，桥接 ExoPlayer 控制。 */
    public interface PlayerBridge {
        void play();

        void pause();

        void stop();

        void seekTo(long ms);

        void setUri(String uri, String title);

        long getPositionMs();

        long getDurationMs();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            upnpService = (AndroidUpnpService) service;
            if (!started) {
                // 绑定期间已被 stop，放弃使用
                upnpService = null;
                return;
            }
            acquireLocks();
            registerRendererDevice();
            Log.d(TAG, "Cling 服务已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            upnpService = null;
            rendererDevice = null;
        }
    };

    private DlnaRendererManager() {
    }

    public static DlnaRendererManager getInstance() {
        if (instance == null) {
            synchronized (DlnaRendererManager.class) {
                if (instance == null) {
                    instance = new DlnaRendererManager();
                }
            }
        }
        return instance;
    }

    /** 是否已开启接收投屏（持久化于独立 SharedPreferences）。 */
    public static boolean isReceiverEnabled(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_RECEIVER, false);
    }

    public static void setReceiverEnabled(Context ctx, boolean enabled) {
        ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RECEIVER, enabled)
                .apply();
    }

    /** 获取或创建持久化的 MediaRenderer UDN（跨重启稳定，不与其他设备冲突）。 */
    private UDN getOrCreateUdn() {
        android.content.SharedPreferences sp = appContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_UDN, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_UDN, id).apply();
        }
        return new UDN(UUID.fromString(id));
    }

    /** 启动接收端：以前台服务方式启动并绑定 Cling 服务，服务就绪后注册 MediaRenderer 设备。 */
    public void start(Context context) {
        if (started) {
            return;
        }
        appContext = context.getApplicationContext();
        started = true;
        Intent intent = new Intent(appContext, CustomAndroidUpnpServiceImpl.class);
        // 先以前台服务启动，保证退出 Activity 后进程不被回收、设备持续可发现
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "startService 失败", e);
        }
        boolean ok = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!ok) {
            Log.w(TAG, "bindService 失败");
            started = false;
        }
    }

    /** 停止接收端：注销设备、释放锁、解绑并停止服务。 */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        unregisterRendererDevice();
        if (appContext != null) {
            try {
                appContext.unbindService(serviceConnection);
            } catch (Exception e) {
                Log.w(TAG, "unbindService 失败", e);
            }
            try {
                Intent intent = new Intent(appContext, CustomAndroidUpnpServiceImpl.class);
                appContext.stopService(intent);
            } catch (Exception e) {
                Log.w(TAG, "stopService 失败", e);
            }
        }
        upnpService = null;
        releaseLocks();
        bridge = null;
        activityLaunched = false;
        pendingSeekMs = 0;
        transportState = "NO_MEDIA_PRESENT";
        currentUri = null;
        currentTitle = null;
    }

    public void reStart(Context context) {
        stop();

        new Handler().postDelayed(() -> {
            start(context);
        }, 4000);
    }

    /** 构建 MediaRenderer 本地设备（AVTransport + RenderingControl）并注册到 Registry。 */
    private void registerRendererDevice() {
        if (upnpService == null) {
            return;
        }
        try {
            LocalService avt = new AnnotationLocalServiceBinder().read(RendererAVTransportService.class);
            avt.setManager(new DefaultServiceManager<>(avt, RendererAVTransportService.class));

            LocalService rc = new AnnotationLocalServiceBinder().read(RendererRenderingControlService.class);
            rc.setManager(new DefaultServiceManager<>(rc, RendererRenderingControlService.class));

            // Android 上 UDN.uniqueSystemIdentifier() 会抛异常（依赖系统属性），
            // 改用持久化的随机 UUID，保证设备身份跨重启稳定且不与其他设备冲突。
            UDN udn = getOrCreateUdn();
            rendererDevice = new LocalDevice(
                    new DeviceIdentity(udn),
                    new UDADeviceType("MediaRenderer", 1),
                    new DeviceDetails(MyApplication.manager.getCastName(), new ManufacturerDetails("WRZ")),
                    new LocalService[]{avt, rc});

            upnpService.getRegistry().addDevice(rendererDevice);
            Log.d(TAG, "MediaRenderer 设备注册成功: " + udn);
        } catch (Exception e) {
            Log.e(TAG, "注册 MediaRenderer 设备失败", e);
        }
    }

    private void unregisterRendererDevice() {
        if (upnpService != null && rendererDevice != null) {
            try {
                upnpService.getRegistry().removeDevice(rendererDevice);
            } catch (Exception e) {
                Log.w(TAG, "注销 MediaRenderer 设备失败", e);
            }
        }
        rendererDevice = null;
    }

    // ===== PlayerBridge 注册 =====

    public void registerBridge(PlayerBridge b) {
        this.bridge = b;
        // 若 onSeek 在 Activity 创建前到达已缓存 seek，现在执行
        if (pendingSeekMs > 0) {
            final long seek = pendingSeekMs;
            pendingSeekMs = 0;
            Log.d(TAG, "registerBridge: 执行缓存 seek=" + seek);
            mainHandler.post(() -> b.seekTo(seek));
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
        transportState = "STOPPED";
    }

    /**
     * 接收端 ExoPlayer 播放/暂停状态变化时调用（如用户在接收端控制器本地暂停）。
     * 同步传输状态，供发送端轮询 GetTransportInfo 时获取真实播放/暂停。
     * 注意：不在此处理自然结束（STATE_ENDED 时 isPlaying=false 会进入 PAUSED_PLAYBACK，
     * 按需求自然播完无需退出投屏）。
     */
    public void onReceiverPlayStateChanged(boolean isPlaying) {
        String newState = isPlaying ? "PLAYING" : "PAUSED_PLAYBACK";
        transportState = newState;
        Log.d(TAG, "onReceiverPlayStateChanged: " + newState);
    }

    /** 当前传输状态（供 Activity 同步初始播放状态）。 */
    public String getTransportState() {
        return transportState;
    }

    // ===== AVTransport 回调（Cling 后台线程调用） =====

    public void onSetUri(String uri, String metaData) {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        String title = parseTitleFromMeta(metaData);
        currentUri = uri;
        currentTitle = title;
        currentCasterName = parseCasterFromUri(uri);
        transportState = "TRANSITIONING";
        Log.d(TAG, "onSetUri: " + uri + " title=" + title);

        final PlayerBridge b = bridge;
        if (b != null) {
            // 已有播放器在运行，切换媒体源
            mainHandler.post(() -> b.setUri(uri, title));
        } else {
            launchReceiverActivityIfNeeded();
        }
    }

    public void onPlay() {
        transportState = "PLAYING";
        Log.d(TAG, "onPlay");
        final PlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::play);
        } else {
            // 播放器尚未启动（可能 SetAVTransportURI 后立即 Play），拉起 Activity
            launchReceiverActivityIfNeeded();
        }
    }

    public void onPause() {
        transportState = "PAUSED_PLAYBACK";
        Log.d(TAG, "onPause");
        final PlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::pause);
        }
    }

    public void onStop() {
        transportState = "STOPPED";
        Log.d(TAG, "onStop");
        final PlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(b::stop);
        }
    }

    public void onSeek(String unit, String target) {
        // 仅支持按相对时间定位
        if (!"REL_TIME".equals(unit)) {
            Log.d(TAG, "onSeek 忽略不支持的模式: " + unit);
            return;
        }
        final long ms = DlnaManager.parseTimeToMs(target);
        Log.d(TAG, "onSeek: " + target + " -> " + ms + "ms");
        final PlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(() -> b.seekTo(ms));
        } else {
            // Activity 尚未创建，缓存等 registerBridge 时执行
            Log.d(TAG, "onSeek: bridge 未就绪，缓存 seek=" + ms);
            pendingSeekMs = ms;
        }
    }

    /** 若尚未拉起接收 Activity，则以 receiver 模式启动它。 */
    private void launchReceiverActivityIfNeeded() {
        if (activityLaunched) {
            return;
        }
        if (appContext == null) {
            return;
        }
        activityLaunched = true;
        Intent intent = new Intent(appContext, VideoPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VideoPlayerActivity.EXTRA_RECEIVER_MODE, true);
        intent.putExtra(VideoPlayerActivity.EXTRA_CAST_URI, currentUri);
        intent.putExtra(VideoPlayerActivity.EXTRA_CAST_TITLE, currentTitle);
        intent.putExtra(VideoPlayerActivity.EXTRA_CASTER_NAME, currentCasterName);
        try {
            appContext.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "启动接收 Activity 失败", e);
            activityLaunched = false;
        }
    }

    // ===== 状态查询（返回响应 Bean） =====

    public RendererAVTransportService.PositionInfoResponse getPositionInfo() {
        final PlayerBridge b = bridge;
        long pos = 0;
        long dur = 0;
        if (b != null) {
            pos = b.getPositionMs();
            dur = b.getDurationMs();
        }
        Log.d(TAG, "getPositionInfo 被调用: bridge=" + (b != null)
                + " pos=" + pos + " dur=" + dur);
        String uri = currentUri;
        return new RendererAVTransportService.PositionInfoResponse(
                new UnsignedIntegerFourBytes(uri != null ? 1 : 0),
                dur > 0 ? DlnaManager.formatTimeForSeek(dur) : "0:00:00.000",
                "",
                uri != null ? uri : "",
                pos > 0 ? DlnaManager.formatTimeForSeek(pos) : "0:00:00.000",
                "NOT_IMPLEMENTED",
                new UnsignedIntegerFourBytes(0),
                new UnsignedIntegerFourBytes(0));
    }

    public RendererAVTransportService.TransportInfoResponse getTransportInfo() {
        Log.d(TAG, "getTransportInfo 被调用: state=" + transportState);
        return new RendererAVTransportService.TransportInfoResponse(
                transportState, "OK", "1");
    }

    public RendererAVTransportService.MediaInfoResponse getMediaInfo() {
        final PlayerBridge b = bridge;
        long dur = (b != null) ? b.getDurationMs() : 0;
        String uri = currentUri;
        return new RendererAVTransportService.MediaInfoResponse(
                new UnsignedIntegerFourBytes(uri != null ? 1 : 0),
                dur > 0 ? DlnaManager.formatTimeForSeek(dur) : "0:00:00.000",
                uri != null ? uri : "",
                "",
                "NOT_IMPLEMENTED",
                "NOT_IMPLEMENTED",
                "NETWORK",
                "NOT_IMPLEMENTED",
                "NOT_IMPLEMENTED");
    }

    // ===== 工具方法 =====

    /** 从 DIDL-Lite 元数据中提取 dc:title（兼容 CDATA 包裹）。 */
    private static String parseTitleFromMeta(String meta) {
        if (meta == null) {
            return "";
        }
        int start = meta.indexOf("<dc:title>");
        if (start < 0) {
            return "";
        }
        start += "<dc:title>".length();
        int end = meta.indexOf("</dc:title>", start);
        if (end < 0) {
            return "";
        }
        String title = meta.substring(start, end).trim();
        if (title.startsWith("<![CDATA[")) {
            title = title.substring(9);
            if (title.endsWith("]]>")) {
                title = title.substring(0, title.length() - 3);
            }
        }
        return title;
    }

    /** 从 SetAVTransportURI 的 URL query 参数 caster 解析发送端设备名。 */
    public static String parseCasterFromUri(String uri) {
        if (uri == null) return null;
        int idx = uri.indexOf("caster=");
        if (idx < 0) return null;
        String val = uri.substring(idx + 7);
        int amp = val.indexOf('&');
        if (amp >= 0) val = val.substring(0, amp);
        try {
            return java.net.URLDecoder.decode(val, "UTF-8");
        } catch (Exception e) {
            return val;
        }
    }

    // ===== 锁（参考 DlnaManager 的实现，保持接收期间网络/CPU 唤醒） =====

    private void acquireLocks() {
        // 组播锁：接收 SSDP M-SEARCH
        if (multicastLock == null) {
            WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                try {
                    multicastLock = wifi.createMulticastLock("cling-dlna-renderer");
                    multicastLock.setReferenceCounted(false);
                    multicastLock.acquire();
                } catch (Exception e) {
                    Log.w(TAG, "获取 MulticastLock 失败", e);
                    multicastLock = null;
                }
            }
        }
        // WiFi 锁：保持 WiFi 高性能
        if (wifiLock == null) {
            WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                try {
                    wifiLock = wifi.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dlna-renderer-wifi");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                } catch (Exception e) {
                    Log.w(TAG, "获取 WifiLock 失败", e);
                    wifiLock = null;
                }
            }
        }
        // 唤醒锁：保持 CPU 唤醒
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                try {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dlna-renderer-cpu");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                } catch (Exception e) {
                    Log.w(TAG, "获取 WakeLock 失败", e);
                    wakeLock = null;
                }
            }
        }
    }

    private void releaseLocks() {
        if (multicastLock != null) {
            try {
                multicastLock.release();
            } catch (Exception ignored) {
            }
            multicastLock = null;
        }
        if (wifiLock != null) {
            try {
                wifiLock.release();
            } catch (Exception ignored) {
            }
            wifiLock = null;
        }
        if (wakeLock != null) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }
}
