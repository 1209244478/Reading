package com.wrz.reading.ui.read.dlna;

import android.annotation.SuppressLint;
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
import com.wrz.reading.common.LocalNetworkPermission;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.activity.VideoPlayerActivity;
import com.wrz.reading.ui.read.airplay.AirPlayReceiverManager;
import com.wrz.reading.ui.read.wrzcast.WrzCastReceiverManager;

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

import java.util.Objects;
import java.util.UUID;

/**
 * DLNA 投屏接收端（MediaRenderer）管理器（单例）。
 *
 * 职责：绑定 Cling 的 AndroidUpnpService（与 {@link DlnaManager} 独立的 ServiceConnection，
 * 同一服务可被多次绑定），构建并注册本机 MediaRenderer 设备（含 AVTransport 与
 * RenderingControl 两个服务），接收控制点的播放控制指令并转发给当前注册的
 * {@link PlayerBridge}（即接收模式的 VideoPlayerActivity）。
 *
 * 接收方式（{@link ReceiveMode}）持久化在独立 SharedPreferences 中，{@link #start(Context)}
 * 按当前方式启停对应协议（DLNA / WrzCast / AirPlay）；Chromecast 接收端受认证硬件限制
 * 暂未实现，仅保留扩展入口。
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
    private static final String KEY_RECEIVER_MODE = "pref_receive_mode";
    private static final String KEY_UDN = "renderer_udn";

    private static volatile DlnaRendererManager instance;

    private Context appContext;
    private AndroidUpnpService upnpService;
    private LocalDevice rendererDevice;
    private volatile boolean started = false;
    /** DLNA 的 Cling 服务是否已成功绑定（未绑定时 stop 不再 unbind）。 */
    private volatile boolean serviceBound = false;

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ===== 接收端运行时状态（volatile，跨线程可见） =====
    private volatile String currentUri;
    private volatile String currentTitle;
    /** 发送端设备名（从 SetAVTransportURI 的 URL query 参数 caster 解析） */
    private volatile String currentCasterName = "";
    private volatile String transportState = "NO_MEDIA_PRESENT";
    private volatile PlayerBridge bridge;
    private volatile boolean activityLaunched = false;
    /** onSeek 在 Activity/bridge 就绪前到达时缓存的 seek 位置，registerBridge 时执行。 */
    private volatile long pendingSeekMs = 0;

    public void setCurrentCasterName(String currentCasterName) {
        this.currentCasterName = currentCasterName;
    }

    /** 由接收模式 VideoPlayerActivity 实现，桥接 ExoPlayer 控制。 */
    public interface PlayerBridge extends com.wrz.reading.ui.read.cast.CastPlayerBridge {
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
            LogUtil.d(TAG, "Cling 服务已连接");
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

    /**
     * 接收投屏协议。DLNA 使用 Cling MediaRenderer 实现，WrzCast 为 App 间私有协议，
     * AirPlay 使用内置 UxPlay 原生库；Chromecast 接收端需要认证硬件，当前仅保留扩展入口。
     */
    public enum ReceiveMode {
        /** 仅 DLNA */
        DLNA,
        /** 仅 WrzCast（App 间私有协议） */
        WRZCAST,
        /** 仅 AirPlay */
        AIRPLAY,
        /** 仅 Chromecast */
        CHROMECAST,
        /** 全部协议（当前 DLNA + WrzCast + AirPlay 实际生效） */
        ALL;

        public boolean includesDlna() {
            return this == DLNA || this == ALL;
        }

        public boolean includesChromecast() {
            return this == CHROMECAST || this == ALL;
        }

        public boolean includesWrzCast() {
            return this == WRZCAST || this == ALL;
        }

        public boolean includesAirPlay() {
            return this == AIRPLAY || this == ALL;
        }
    }

    /** 当前接收方式（持久化于独立 SharedPreferences，默认 DLNA）。 */
    public static ReceiveMode getReceiveMode(Context ctx) {
        String name = ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RECEIVER_MODE, ReceiveMode.DLNA.name());
        try {
            return ReceiveMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ReceiveMode.DLNA;
        }
    }

    public static void setReceiveMode(Context ctx, ReceiveMode mode) {
        ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECEIVER_MODE, mode.name())
                .apply();
    }

    /**
     * 当前设备是否支持 Chromecast 接收端。
     * Cast 接收端需要 Google 认证的 Cast 运行时（如 Android TV 内置 Chromecast），
     * 官方 SDK 未向手机/平板开放接收能力，因此当前统一返回 false；
     * 后续在 Android TV 接入 Cast SDK 后在此按设备类型放开。
     */
    public static boolean isChromecastReceiverSupported(Context ctx) {
        return false;
    }

    /**
     * 当前设备是否支持 AirPlay 接收端。
     * 由内置的 UxPlay 预编译原生库（libairplay_native.so）提供 AirPlay/RAOP 协议能力，
     * 设备 ABI 无对应原生库时返回 false。
     */
    public static boolean isAirPlayReceiverSupported(Context ctx) {
        return AirPlayReceiverManager.isSupported();
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

    /** 启动接收端：按当前接收方式启动对应协议。 */
    public void start(Context context) {
        if (started) {
            return;
        }
        appContext = context.getApplicationContext();
        // Android 16+：SSDP/mDNS/局域网 Socket 需要本地网络权限，未授权时跳过启动
        // （避免 NsdManager/Socket 抛 SecurityException），授权后可再次 start
        if (!LocalNetworkPermission.isGranted(appContext)) {
            LogUtil.w(TAG, "缺少本地网络权限，接收端未启动");
            return;
        }
        started = true;
        ReceiveMode mode = getReceiveMode(appContext);
        LogUtil.d(TAG, "启动接收端，接收方式: " + mode);
        if (mode.includesDlna()) {
            startDlnaReceiver();
        }
        if (mode.includesChromecast()) {
            startChromecastReceiver();
        }
        if (mode.includesWrzCast()) {
            WrzCastReceiverManager.getInstance().start(appContext);
        }
        if (mode.includesAirPlay()) {
            startAirPlayReceiver();
        }
    }

    /** 启动 DLNA 接收端（Cling MediaRenderer）。 */
    private void startDlnaReceiver() {
        // 必须在 bindService 之前获取 MulticastLock，否则 Cling 初始化时发送 SSDP 会因 EPERM 失败
        acquireLocks();
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
        serviceBound = ok;
        if (!ok) {
            Log.w(TAG, "bindService 失败");
            started = false;
        }
    }

    /**
     * 启动 Chromecast 接收端。
     * 官方 Cast 接收端仅运行在 Google Cast 认证设备上，手机/平板暂不支持，
     * 这里保留扩展入口，待接入 Cast SDK 后实现。
     */
    private void startChromecastReceiver() {
        LogUtil.d(TAG, "当前设备暂不支持 Chromecast 接收端，已跳过");
    }

    /** 停止接收端：按当前接收方式停止对应协议。 */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        stopDlnaReceiver();
        stopChromecastReceiver();
        stopAirPlayReceiver();
        // 无条件停止：切换接收方式后 reStart 时，当前 mode 已是新值
        WrzCastReceiverManager.getInstance().stop();
        releaseLocks();
        bridge = null;
        activityLaunched = false;
        pendingSeekMs = 0;
        transportState = "NO_MEDIA_PRESENT";
        currentUri = null;
        currentTitle = null;
    }

    /** 停止 DLNA 接收端：注销设备、解绑并停止 Cling 服务。 */
    private void stopDlnaReceiver() {
        unregisterRendererDevice();
        if (appContext != null) {
            if (serviceBound) {
                try {
                    appContext.unbindService(serviceConnection);
                } catch (Exception e) {
                    Log.w(TAG, "unbindService 失败", e);
                }
                serviceBound = false;
            }
            try {
                Intent intent = new Intent(appContext, CustomAndroidUpnpServiceImpl.class);
                appContext.stopService(intent);
            } catch (Exception e) {
                Log.w(TAG, "stopService 失败", e);
            }
        }
        upnpService = null;
    }

    /** 停止 Chromecast 接收端（预留：接入 Cast SDK 后在此注销）。 */
    private void stopChromecastReceiver() {
    }

    /**
     * 启动 AirPlay 接收端（UxPlay 原生库，支持镜像与音频）。
     */
    private void startAirPlayReceiver() {
        AirPlayReceiverManager.getInstance().start(appContext);
    }

    /** 停止 AirPlay 接收端。 */
    private void stopAirPlayReceiver() {
        AirPlayReceiverManager.getInstance().stop();
    }

    public void reStart(Context context) {
        stop();

        mainHandler.postDelayed(() -> {
            // 延迟期间用户可能已关闭接收开关，避免重新拉起接收端
            if (appContext != null && isReceiverEnabled(appContext)) {
                start(context);
            }
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
            LogUtil.d(TAG, "MediaRenderer 设备注册成功: " + udn);
        } catch (Exception e) {
            LogUtil.e(TAG, "注册 MediaRenderer 设备失败", e);
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
            LogUtil.d(TAG, "registerBridge: 执行缓存 seek=" + seek);
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
        LogUtil.d(TAG, "onReceiverPlayStateChanged: " + newState);
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
        String newCasterName = parseCasterFromUri(uri);
        boolean isNewDevice = !Objects.equals(newCasterName, currentCasterName);
        transportState = "TRANSITIONING";
        LogUtil.d(TAG, "onSetUri: " + uri + " title=" + title + " newCasterName=" + newCasterName + " isNewDevice=" + isNewDevice);

        final PlayerBridge b = bridge;
        if (b != null) {
            // 已有播放器在运行，切换媒体源
            mainHandler.post(() -> b.setUri(uri, title, isNewDevice, newCasterName));
        } else {
            currentCasterName = newCasterName;
            launchReceiverActivityIfNeeded();
        }
    }

    public void onPlay() {
        transportState = "PLAYING";
        LogUtil.d(TAG, "onPlay");
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
        LogUtil.d(TAG, "onPause");
        final PlayerBridge b = bridge;
            if (b != null) {
                mainHandler.post(b::pause);
            }

    }

    public void onStop() {
        transportState = "STOPPED";
        LogUtil.d(TAG, "onStop");
        final PlayerBridge b = bridge;
        mainHandler.postDelayed(() -> {
            LogUtil.d(TAG, "onStop:" + transportState);
            if ("STOPPED".equals(transportState)) {
                if (b != null) {
                    mainHandler.post(b::stop);
                }
            }
        }, 1000);
    }

    public void onSeek(String unit, String target) {
        // 仅支持按相对时间定位
        if (!"REL_TIME".equals(unit)) {
            LogUtil.d(TAG, "onSeek 忽略不支持的模式: " + unit);
            return;
        }
        final long ms = DlnaManager.parseTimeToMs(target);
        LogUtil.d(TAG, "onSeek: " + target + " -> " + ms + "ms");
        final PlayerBridge b = bridge;
        if (b != null) {
            mainHandler.post(() -> b.seekTo(ms));
        } else {
            // Activity 尚未创建，缓存等 registerBridge 时执行
            LogUtil.d(TAG, "onSeek: bridge 未就绪，缓存 seek=" + ms);
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
        /*activityLaunched = true;*/

        activityLaunched = VideoPlayerActivity.RenderStart(appContext,
                currentUri, currentTitle, currentCasterName, true);

        /*Intent intent = new Intent(appContext, VideoPlayerActivity.class);
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
        }*/
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
        LogUtil.d(TAG, "getPositionInfo 被调用: bridge=" + (b != null)
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
        LogUtil.d(TAG, "getTransportInfo 被调用: state=" + transportState);
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

    @SuppressLint("InvalidWakeLockTag")
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
