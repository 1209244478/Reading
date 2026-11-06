package com.wrz.reading.ui.read.airplay

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import com.wrz.reading.app.MyApplication
import com.wrz.reading.ui.main.Log.LogUtil
import io.github.jqssun.airplay.bridge.LogListener
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.bridge.RaopCallbackHandler
import io.github.jqssun.airplay.discovery.MdnsService
import io.github.jqssun.airplay.discovery.NsdServiceManager
import io.github.jqssun.airplay.renderer.AudioRenderer
import io.github.jqssun.airplay.renderer.VideoRenderer
import java.io.File
import java.net.NetworkInterface
import java.util.Locale
import java.util.Random

/**
 * AirPlay / AirPlay 2 接收端管理器（单例）。
 *
 * 基于开源 UxPlay（通过 io.github.jqssun.android-airplay-server 的预编译原生库
 * libairplay_native.so）实现：原生层负责 RAOP/AirPlay 协议、FairPlay 配对、mDNS 记录、
 * 镜像视频 H.264/H.265 解包与音频 AAC/ALAC 解码；本类负责启动/停止服务、
 * 注册 NsdManager 服务、把镜像画面交给 [AirPlayReceiverActivity] 的 Surface 显示。
 *
 * 线程模型：RaopCallbackHandler 回调来自原生线程，UI 相关操作切回主线程。
 */
class AirPlayReceiverManager private constructor() : RaopCallbackHandler, LogListener {

    companion object {
        private const val TAG = "AirPlayReceiver"
        private const val SERVER_PORT = 7000
        private const val PREF_NAME = "airplay_prefs"
        private const val KEY_MAC = "fallback_mac"

        @Volatile
        private var instance: AirPlayReceiverManager? = null

        @JvmStatic
        fun getInstance(): AirPlayReceiverManager {
            return instance ?: synchronized(this) {
                instance ?: AirPlayReceiverManager().also { instance = it }
            }
        }

        /** 当前设备 ABI 是否包含可用的 AirPlay 原生库。 */
        @JvmStatic
        fun isSupported(): Boolean {
            return try {
                // 触发 NativeBridge 的 System.loadLibrary("airplay_native")
                NativeBridge.hashCode()
                true
            } catch (t: Throwable) {
                LogUtil.e(TAG, "AirPlay 原生库不可用: " + t.message)
                false
            }
        }
    }

    private var appContext: Context? = null
    private var handle = 0L
    private var videoRenderer: VideoRenderer? = null
    private val audioRenderer = AudioRenderer()

    private var nsdManager: NsdServiceManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activityLaunched = false

    @Volatile
    private var lastVolumeDb = 0f

    @Synchronized
    fun start(context: Context) {
        if (handle != 0L) {
            return
        }
        val app = context.applicationContext
        appContext = app
        if (!isSupported()) {
            return
        }
        try {
            acquireLocks(app)
            // 以前台服务保持进程存活（与 DLNA 接收端一致）
            try {
                val serviceIntent = Intent(app, AirPlayReceiverService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "启动 AirPlay 前台服务失败", t)
            }

            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            NativeBridge.nativeSetDefaultStreamValues(
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0,
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
            )

            val renderer = VideoRenderer(app)
            videoRenderer = renderer

            val name = MyApplication.manager.castName.ifBlank { "Reading" }
            val keyFile = File(app.filesDir, "airplay.pem").absolutePath
            handle = NativeBridge.nativeInit(this, getHwAddr(app), name, keyFile, true, false)
            if (handle == 0L) {
                LogUtil.e(TAG, "nativeInit 失败")
                cleanup()
                return
            }
            audioRenderer.attachEngine(handle)

            val (rawW, rawH) = rawDisplaySize(app)
            val h265 = renderer.selectDecoders(rawW, rawH, 60, null)
            val (w, h) = displaySize(app)
            renderer.setResolution(w, h)

            NativeBridge.nativeSetDisplaySize(handle, w, h, 60)
            NativeBridge.nativeSetCodecs(handle, true, true)
            // 只接收镜像与音频；AirPlay Video（HLS）需要额外的媒体播放器，暂不启用
            NativeBridge.nativeSetHlsEnabled(handle, false)
            NativeBridge.nativeSetFeature(handle, NativeBridge.FT_MIRROR, true)
            NativeBridge.nativeSetFeature(handle, NativeBridge.FT_AUDIO, true)
            NativeBridge.nativeSetH265Enabled(handle, h265)
            NativeBridge.nativeSetLang(
                handle,
                Locale.getDefault().toLanguageTag().replace(',', ':')
            )

            val port = NativeBridge.nativeStart(handle, SERVER_PORT)
            if (port < 0) {
                LogUtil.e(TAG, "nativeStart 失败")
                cleanup()
                return
            }

            val nsd = NsdServiceManager(app)
            nsd.acquireMulticastLock()
            nsdManager = nsd
            val raopTxt = NativeBridge.nativeGetRaopTxtRecords(handle) ?: emptyMap()
            val airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(handle) ?: emptyMap()
            val raopName = NativeBridge.nativeGetRaopServiceName(handle) ?: name
            val resolvedName = NativeBridge.nativeGetServerName(handle) ?: name
            nsd.register(
                listOf(
                    MdnsService(raopName, "_raop._tcp", port, raopTxt),
                    MdnsService(resolvedName, "_airplay._tcp", port, airplayTxt)
                )
            )
            LogUtil.d(TAG, "AirPlay 接收端已启动: $resolvedName port=$port")
        } catch (t: Throwable) {
            LogUtil.e(TAG, "AirPlay 接收端启动失败", t)
            cleanup()
        }
    }

    @Synchronized
    fun stop() {
        activityLaunched = false
        nsdManager?.release()
        nsdManager = null
        audioRenderer.detachEngine()
        val h = handle
        handle = 0L
        if (h != 0L) {
            try {
                NativeBridge.nativeStop(h)
            } catch (t: Throwable) {
                Log.w(TAG, "nativeStop 失败", t)
            }
            try {
                NativeBridge.nativeDestroy(h)
            } catch (t: Throwable) {
                Log.w(TAG, "nativeDestroy 失败", t)
            }
        }
        videoRenderer?.release()
        videoRenderer = null
        releaseLocks()
        appContext?.stopService(Intent(appContext, AirPlayReceiverService::class.java))
        LogUtil.d(TAG, "AirPlay 接收端已停止")
    }

    private fun cleanup() {
        nsdManager?.release()
        nsdManager = null
        audioRenderer.detachEngine()
        val h = handle
        handle = 0L
        if (h != 0L) {
            try {
                NativeBridge.nativeStop(h)
                NativeBridge.nativeDestroy(h)
            } catch (ignored: Throwable) {
            }
        }
        videoRenderer?.release()
        videoRenderer = null
        releaseLocks()
        appContext?.stopService(Intent(appContext, AirPlayReceiverService::class.java))
    }

    // ===== 画面显示 =====

    fun setSurface(surface: Surface) {
        videoRenderer?.setSurface(surface)
    }

    fun clearSurface(surface: Surface) {
        videoRenderer?.clearSurface(surface)
    }

    /** 接收 Activity 销毁时调用：本会话内不再自动拉起画面。 */
    fun onActivityDestroyed() {
        mainHandler.post { activityLaunched = false }
    }

    private fun launchReceiverActivity() {
        if (activityLaunched) {
            return
        }
        val ctx = appContext ?: return
        activityLaunched = true
        mainHandler.post {
            try {
                val intent = Intent(ctx, AirPlayReceiverActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ctx.startActivity(intent)
            } catch (t: Throwable) {
                activityLaunched = false
                Log.w(TAG, "拉起 AirPlay 接收画面失败", t)
            }
        }
    }

    // ===== RaopCallbackHandler（原生线程回调） =====

    override fun onVideoData(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {
        videoRenderer?.feedFrame(data, ntpTimeNs, isH265)
    }

    override fun onAudioFormat(ct: Int, spf: Int, usingScreen: Boolean) {
        audioRenderer.start()
        audioRenderer.setFormat(ct, spf)
    }

    override fun onVideoSize(srcW: Float, srcH: Float, w: Float, h: Float) {
        if (w > 0f && h > 0f) {
            videoRenderer?.setResolution(w.toInt(), h.toInt())
        }
        launchReceiverActivity()
    }

    override fun onClientVolume(db: Float) {
        lastVolumeDb = db
        val gain = if (db <= -144f) 0f else Math.pow(10.0, db / 20.0).toFloat()
        audioRenderer.setClientGain(gain.coerceIn(0f, 1f))
    }

    override fun onClientVolumeQuery(): Float = lastVolumeDb

    override fun onAudioTeardown() {
        audioRenderer.stop()
    }

    override fun onConnectionInit() {
        // 音频投送不需要界面；镜像在 onMirrorRunning/onVideoSize 时再拉起画面
    }

    override fun onConnectionDestroy() {
        audioRenderer.stop()
        videoRenderer?.stopSession()
        activityLaunched = false
    }

    override fun onConnectionReset(reason: Int) {
        LogUtil.d(TAG, "AirPlay 连接重置: $reason")
    }

    override fun onDisplayPin(pin: String) {
        // 未启用 PIN 配对
    }

    override fun onMetadata(data: ByteArray) {
    }

    override fun onCoverArt(data: ByteArray) {
    }

    override fun onProgress(start: Long, curr: Long, end: Long) {
    }

    override fun onDacpId(dacpId: String, activeRemote: String) {
    }

    override fun onMirrorRunning(running: Boolean) {
        if (running) {
            videoRenderer?.startSession()
            launchReceiverActivity()
        } else {
            videoRenderer?.stopSession()
            activityLaunched = false
        }
    }

    override fun onVideoPlay(location: String, startPositionSeconds: Float) {
        // 未启用 AirPlay Video（HLS）
    }

    override fun onVideoScrub(positionSeconds: Float) {
    }

    override fun onVideoRate(rate: Float) {
    }

    override fun onVideoStop() {
    }

    override fun onVideoSessionPoll() {
    }

    override fun onLog(msg: String) {
        Log.d(TAG, msg)
    }

    // ===== 设备信息与系统资源 =====

    private fun rawDisplaySize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    private fun displaySize(context: Context): Pair<Int, Int> {
        val (rawW, rawH) = rawDisplaySize(context)
        var w = rawW
        var h = rawH
        val (maxW, maxH) = videoRenderer?.maxResolution() ?: (1920 to 1080)
        w = w.coerceAtMost(maxW).coerceAtLeast(320)
        h = h.coerceAtMost(maxH).coerceAtLeast(320)
        return w to h
    }

    /** 优先使用 wlan/eth MAC；取不到时使用持久化的本地管理地址，保证设备身份稳定。 */
    private fun getHwAddr(context: Context): ByteArray {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val mac = iface.hardwareAddress
                    if (mac != null && mac.size == 6 && mac.any { it != 0.toByte() }) {
                        return mac
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hex = sp.getString(KEY_MAC, null)?.takeIf { it.length == 12 } ?: run {
            val bytes = ByteArray(6)
            Random().nextBytes(bytes)
            // 本地管理 + 单播地址
            bytes[0] = ((bytes[0].toInt() and 0xFE) or 0x02).toByte()
            val newHex = bytes.joinToString("") { "%02x".format(it) }
            sp.edit().putString(KEY_MAC, newHex).apply()
            newHex
        }
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun acquireLocks(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reading:airplay")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } catch (t: Throwable) {
            Log.w(TAG, "获取 WakeLock 失败", t)
        }
        try {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, "reading:airplay-wifi"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "获取 WifiLock 失败", t)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.release()
        } catch (ignored: Throwable) {
        }
        wakeLock = null
        try {
            wifiLock?.release()
        } catch (ignored: Throwable) {
        }
        wifiLock = null
    }
}
