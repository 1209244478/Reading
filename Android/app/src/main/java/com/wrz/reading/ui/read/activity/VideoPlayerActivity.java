package com.wrz.reading.ui.read.activity;

import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.read.dlna.DlnaManager;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.fragment.CollectionDetailFragment;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.view.dialog.PlayListDialog;

import org.fourthline.cling.model.meta.Device;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用内视频播放器，使用 ExoPlayer 播放本地视频文件。
 * 通过 Intent 传入 EXTRA_VIDEO_PATH（文件路径）和 EXTRA_TITLE（标题，可选）。
 * 支持通过 DLNA 将本地视频投屏到智能电视（小米电视、B 站电视版等）。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class VideoPlayerActivity extends BaseActivity {

    private static final String TAG = "VideoPlayerActivity";
    /**
     * 投屏进度轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MS = 1000L;
    /**
     * 单次轮询超时：超过该时间未收到 GetPositionInfo 回调视为接收端无响应
     */
    private static final long POLL_TIMEOUT_MS = 5000L;

    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_TITLE = "title";
    /**
     * 视频 Comic 主键 ID，传入后启用播放进度保存/恢复；不传则不持久化
     */
    public static final String EXTRA_COMIC_ID = "comic_id";
    /**
     * 投屏接收模式：由 DlnaRendererManager 拉起本 Activity 作为 DLNA 接收端播放器。
     * 该模式下不绑定 DlnaManager（投屏发送），不读取本地文件路径，不保存进度。
     */
    public static final String EXTRA_RECEIVER_MODE = "receiver_mode";
    public static final String EXTRA_CAST_URI = "cast_uri";
    public static final String EXTRA_CAST_TITLE = "cast_title";
    public static final String EXTRA_CASTER_NAME = "cast_name";

    private ExoPlayer player;
    private PlayerView player_view;
    private ImageButton cast_button;
    private TextView tv_name;
    private TextView tv_keep_watch;
    private ImageButton play_list;

    // ===== 手势交互常量与状态 =====
    /**
     * 双击快进/快退步长（毫秒）
     */
    private static final long DOUBLE_TAP_SEEK_MS = 10_000L;
    /**
     * 横向滑动：一屏宽度对应的 seek 量（毫秒），相对起拖位置增减
     */
    private static final long SWIPE_SEEK_MS_PER_WIDTH = 90_000L;
    /**
     * 手势提示自动隐藏延时（毫秒）
     */
    private static final int GESTURE_HINT_HIDE_MS = 600;

    private TextView gesture_indicator;
    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private int streamMaxVolume;

    private static final int MODE_NONE = 0, MODE_SEEK = 1, MODE_BRIGHTNESS = 2, MODE_VOLUME = 3;
    private int gestureMode = MODE_NONE;
    private float downX, downY;
    private boolean leftHalf;
    private long seekStartPosMs;
    private long seekTargetPosMs;
    private float startBrightness;
    private int startVolume;

    /**
     * 后台 IO 线程：加载/持久化播放进度
     */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VideoPlayer-IO");
        t.setDaemon(true);
        return t;
    });

    // 投屏控制浮层
    private FrameLayout cast_control_panel;
    private TextView cast_device_name;
    private TextView cast_title;
    private TextView cast_current_time;
    private TextView cast_duration;
    private SeekBar cast_seekbar;
    private ImageButton cast_play_pause;
    private ImageButton cast_stop;

    private String videoPath;
    private String videoTitle;
    /**
     * 当前视频对应的 Comic 主键，-1 表示未传入（不持久化进度）
     */
    private long comicId = -1L;

    // ===== 接收模式（作为 DLNA MediaRenderer 播放器） =====
    private boolean receiverMode = false;
    /**
     * 接收端：投屏时 seek 早于 ExoPlayer prepare 完成，缓存等 STATE_READY 后执行
     */
    private long pendingSeekMs = 0;
    /**
     * 接收端：投屏确认弹窗是否已处理
     */
    private boolean castConfirmDone = false;
    /**
     * 缓存的播放位置/时长，由主线程定期从 ExoPlayer 读取并写入 volatile，
     * 供 DlnaRendererManager 在 Cling 线程查询 GetPositionInfo 时读取。
     */
    private volatile long cachedPositionMs = 0;
    private volatile long cachedDurationMs = 0;
    private final Handler receiverUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable receiverUpdateRunnable = this::updateReceiverPosition;
    private DlnaRendererManager.PlayerBridge playerBridge;

    private DlnaManager dlnaManager;
    /** 当前选中的投屏设备 */
    private Device device;
    private AlertDialog deviceDialog;
    private ArrayAdapter<String> deviceAdapter;
    private final List<Device> devices = new ArrayList<>();

    // 投屏状态
    private boolean isCasting = false;
    private boolean isCastPaused = false;
    /**
     * 用户正在拖动 SeekBar 时不更新进度（避免抖动）
     */
    private boolean isUserSeeking = false;
    private long castDurationMs = 0;
    /**
     * 最后一次轮询到的投屏进度，用于退出投屏时恢复本机播放位置
     */
    private long lastCastPositionMs = 0;
    private final Handler castHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollPlaybackInfo;
    /**
     * 标记本次 poll 是否已超时（超时后迟到的回调忽略）
     */
    private boolean pollTimedOut = false;
    /**
     * 单次 poll 超时检测：超过 POLL_TIMEOUT_MS 未回调则判定接收端无响应
     */
    private final Runnable pollTimeoutRunnable = () -> {
        pollTimedOut = true;
        Log.w(TAG, "poll 超时: GetPositionInfo " + POLL_TIMEOUT_MS
                + "ms 无响应（接收端未回复），继续轮询");
        if (isCasting) {
            castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    };

    /**
     * 设备列表变化时刷新对话框
     */
    private final DlnaManager.DeviceListener deviceListener = () ->
            runOnUiThread(VideoPlayerActivity.this::refreshDeviceList);


    // ===== 播放器初始化 =====

    @OptIn(markerClass = UnstableApi.class)
    private void initPlayer(String videoPath, String title) {
        player = new ExoPlayer.Builder(this).build();
        player_view.setPlayer(player);

        // 接收模式：监听 ExoPlayer 播放/暂停状态变化，同步传输状态供发送端轮询
        // （接收端通过自带控制器本地暂停时，发送端 GetTransportInfo 才能拿到 PAUSED_PLAYBACK）
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!receiverMode) return;
                Log.d(TAG, "接收端 isPlayingChanged=" + isPlaying);
                DlnaRendererManager.getInstance().onReceiverPlayStateChanged(isPlaying);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // 接收端：player 准备好后执行缓存的 seek（投屏时 seek 早于 prepare 完成）
                if (receiverMode && playbackState == Player.STATE_READY && pendingSeekMs > 0) {
                    Log.d(TAG, "接收端 STATE_READY，执行缓存 seek=" + pendingSeekMs);
                    player.seekTo(pendingSeekMs);
                    pendingSeekMs = 0;
                }
            }
        });

        // 投屏按钮与播放器控制器联动显示/隐藏（接收模式下不联动）
        player_view.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    if (!receiverMode) {
                        cast_button.setVisibility(visibility);
                        tv_name.setVisibility(visibility);
                        play_list.setVisibility(visibility);
                    }
                });

        // 接收模式使用控制点推送的 http URI；普通模式播放本地文件
        String uri = receiverMode ? getIntent().getStringExtra(EXTRA_CAST_URI) : "file://" + videoPath;
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(title)
                                .build())
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
        // 仅普通模式恢复上次播放进度（接收模式不持久化）
        if (!receiverMode) {
            restoreProgress();
        }
    }

    /**
     * 从 DB 读取上次保存的播放位置并 seekTo
     */
    private void restoreProgress() {
        if (comicId == -1L) return;
        ioExecutor.execute(() -> {
            Comic loaded = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (loaded == null || loaded.getVideoPosition() <= 0/* || loaded.getFileType().equals(FileType.MUSIC.getCode())*/)
                return;
            final long pos = loaded.getVideoPosition();

            runOnUiThread(() -> {
                if (player != null) {
                    player.seekTo(pos);
                }
            });
        });
    }

    /**
     * 保存当前播放进度到 DB（投屏中不保存本地进度）
     */
    private void saveProgress() {
        if (comicId == -1L || player == null) return;
        long pos;
        if (isCasting) {
            pos = castDurationMs;
        } else {
            pos = player.getCurrentPosition();
        }
        long duration = player.getDuration();
        // 已基本播放完毕（剩余 < 3 秒）则重置为 0，避免下次恢复到结尾定格
        if (duration > 0 && pos >= duration - 3000L) {
            pos = 0L;
        }
        final long finalPos = pos;
        final long lastRead = System.currentTimeMillis();
        ioExecutor.execute(() ->
                MyApplication.comicDatabase.comicDao().updateVideoProgress(comicId, finalPos, lastRead, duration));
    }

    /**
     * 弹出设备列表对话框
     */
    private void showDeviceDialog() {
        ListView listView = new ListView(this);
        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(deviceAdapter);
        // 打开列表时主动触发一次搜索，避免注册表缓存过期/为空时列表一直空白
        dlnaManager.startDiscovery();
        refreshDeviceList();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                device = devices.get(position);
                deviceDialog.dismiss();
                Toast.makeText(this, "正在投屏到 " + getDeviceName(device), Toast.LENGTH_SHORT).show();
                castToDevice(false);
            }
        });

        deviceDialog = new AlertDialog.Builder(this)
                .setTitle("选择投屏设备")
                .setMessage("搜索中...")
                .setView(listView)
                .setNegativeButton("取消", null)
                .setOnDismissListener(d -> {
                    deviceAdapter = null;
                    deviceDialog = null;
                })
                .create();
        deviceDialog.show();
    }

    public void castToDevice(boolean isChangeCast) {
        long startPos = player != null ? player.getCurrentPosition() : 0;
        Log.d(TAG, "投屏按钮: startPos=" + startPos + " player=" + (player != null));
        dlnaManager.cast(device, new File(videoPath), videoTitle, startPos, isChangeCast,
                new DlnaManager.CastListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(VideoPlayerActivity.this,
                                    "投屏成功", Toast.LENGTH_SHORT).show();
                            onCastStarted(device);
                            if (player != null && player.getCurrentPosition() > 0) {
                                new Handler().postDelayed(() -> {
                                    castSeek(player.getCurrentPosition());
                                }, 5000);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                VideoPlayerActivity.this, "投屏失败: " + message,
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    /**
     * 刷新设备列表与对话框提示文案
     */
    private void refreshDeviceList() {
        if (deviceAdapter == null || deviceDialog == null || !deviceDialog.isShowing()) {
            return;
        }
        devices.clear();
        devices.addAll(dlnaManager.getDevices());
        List<String> names = new ArrayList<>();
        for (Device d : devices) {
            names.add(getDeviceName(d));
        }
        deviceAdapter.clear();
        deviceAdapter.addAll(names);
        deviceAdapter.notifyDataSetChanged();
        deviceDialog.setMessage(devices.isEmpty()
                ? "搜索中...未发现设备"
                : "已发现 " + devices.size() + " 个设备，点击投屏");
    }

    /**
     * 获取设备显示名称
     */
    private static String getDeviceName(Device device) {
        try {
            String name = device.getDetails().getFriendlyName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Exception e) {
            // ignore
        }
        return device.getDisplayString();
    }

    // ===== 投屏控制 =====

    /**
     * 投屏成功：显示浮层、停止本机播放、禁用 ExoPlayer 控制器、开始轮询
     */
    private void onCastStarted(Device device) {
        Log.d(TAG, "onCastStarted: device=" + getDeviceName(device));
        isCasting = true;
        isCastPaused = false;
        castDurationMs = 0;
        lastCastPositionMs = 0;
        cast_device_name.setText("投屏中：" + getDeviceName(device));
        cast_title.setText(videoTitle);
        cast_current_time.setText("0:00");
        cast_duration.setText("0:00");
        cast_seekbar.setProgress(0);
        cast_play_pause.setImageResource(android.R.drawable.ic_media_pause);
        cast_control_panel.setVisibility(View.VISIBLE);
        // 隐藏投屏按钮（投屏中不可切换设备，需先停止）
        cast_button.setVisibility(View.GONE);
        tv_name.setVisibility(View.GONE);
        play_list.setVisibility(View.GONE);
        // 禁用 ExoPlayer 自带控制器，避免误操作本地播放（统一由浮层控制电视端）
        player_view.setUseController(false);
        // 停止本机播放（保留位置，退出投屏时从电视端进度恢复）
        if (player != null) {
            player.pause();
        }
        // 开始轮询播放进度
        castHandler.removeCallbacks(pollRunnable);
        castHandler.removeCallbacks(pollTimeoutRunnable);
        castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    /**
     * 隐藏浮层并停止轮询
     */
    private void hideCastOverlay() {
        castHandler.removeCallbacks(pollRunnable);
        castHandler.removeCallbacks(pollTimeoutRunnable);
        isCasting = false;
        isCastPaused = false;
        castDurationMs = 0;
        cast_control_panel.setVisibility(View.GONE);
        // 恢复投屏按钮
        cast_button.setVisibility(View.VISIBLE);
        tv_name.setVisibility(View.VISIBLE);
        if (!receiverMode) {
            play_list.setVisibility(View.VISIBLE);
        }
        // 恢复 ExoPlayer 控制器
        player_view.setUseController(true);
    }

    /**
     * 切换暂停/播放
     */
    private void togglePlayPause() {
        if (!isCasting) return;
        if (isCastPaused) {
            dlnaManager.resumePlay(new DlnaManager.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isCastPaused = false;
                        cast_play_pause.setImageResource(android.R.drawable.ic_media_pause);
                    });
                }

                @Override
                public void onFailure(String message) {
                    runOnUiThread(() -> Toast.makeText(VideoPlayerActivity.this,
                            "恢复播放失败: " + message, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            dlnaManager.pause(new DlnaManager.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isCastPaused = true;
                        cast_play_pause.setImageResource(android.R.drawable.ic_media_play);
                    });
                }

                @Override
                public void onFailure(String message) {
                    runOnUiThread(() -> Toast.makeText(VideoPlayerActivity.this,
                            "暂停失败: " + message, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    /**
     * 停止投屏，从电视端最后进度恢复本机播放
     */
    private void stopCasting() {
        if (!isCasting) return;
        final long resumePos = lastCastPositionMs;
        dlnaManager.stopCast();
        hideCastOverlay();
        Toast.makeText(this, "已停止投屏", Toast.LENGTH_SHORT).show();
        // 恢复本机播放，跳到电视端最后进度
        if (player != null && !player.isPlaying()) {
            if (resumePos > 0) {
                player.seekTo(resumePos);
            }
            player.setPlayWhenReady(true);
        }
    }

    /**
     * 轮询电视端播放进度并刷新 UI
     */
    private void pollPlaybackInfo() {
        if (!isCasting) return;
        Log.d(TAG, "poll: 调用 getPlaybackInfo");
        pollTimedOut = false;
        castHandler.removeCallbacks(pollTimeoutRunnable);
        castHandler.postDelayed(pollTimeoutRunnable, POLL_TIMEOUT_MS);
        dlnaManager.getPlaybackInfo(new DlnaManager.PlaybackInfoListener() {
            @Override
            public void onResult(long positionMs, long durationMs, String transportState) {
                runOnUiThread(() -> {
                    castHandler.removeCallbacks(pollTimeoutRunnable);
                    // 超时已触发并已重新调度，忽略迟到响应
                    if (pollTimedOut) return;
                    if (!isCasting) return;
                    Log.d(TAG, "poll onResult: pos=" + positionMs + " dur=" + durationMs + " state=" + transportState);
                    // 接收端已停止/结束播放：退出投屏态（stopCasting 会发送 Stop 清理接收端）
                    if ("STOPPED".equals(transportState)) {
                        Log.d(TAG, "接收端状态 STOPPED，结束投屏");
                        stopCasting();
                        return;
                    }

                    // 同步播放/暂停按钮（接收端可能自行暂停/恢复）
                    if ("PAUSED_PLAYBACK".equals(transportState)) {
                        if (!isCastPaused) {
                            isCastPaused = true;
                            cast_play_pause.setImageResource(android.R.drawable.ic_media_play);
                        }
                    } else if ("PLAYING".equals(transportState)) {
                        if (isCastPaused) {
                            isCastPaused = false;
                            cast_play_pause.setImageResource(android.R.drawable.ic_media_pause);
                        }
                    }
                    if (!isUserSeeking && durationMs > 0) {
                        castDurationMs = durationMs;
                        cast_duration.setText(DlnaManager.formatTimeReadable(durationMs));
                    }
                    if (positionMs > 0) {
                        lastCastPositionMs = positionMs;
                    }
                    cast_current_time.setText(DlnaManager.formatTimeReadable(positionMs));
                    // 用户拖动时不更新 SeekBar，避免抖动
                    if (!isUserSeeking && castDurationMs > 0) {
                        int progress = (int) (positionMs * cast_seekbar.getMax() / castDurationMs);
                        cast_seekbar.setProgress(progress);
                    }
                    // 安排下一次轮询
                    castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    castHandler.removeCallbacks(pollTimeoutRunnable);
                    if (pollTimedOut) return;
                    Log.w(TAG, "getPlaybackInfo 失败: " + message);
                    // 失败时也继续轮询，避免一次网络抖动就停掉
                    if (isCasting) {
                        castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    }
                });
            }
        });
    }

    // ===== 生命周期 =====

    @Override
    protected void onStart() {
        super.onStart();
        if (receiverMode) {
            // 接收模式：注册 PlayerBridge 供 DlnaRendererManager 控制 ExoPlayer
            registerReceiverBridge();
            return;
        }
        // 普通模式：绑定 DLNA 服务并开始搜索设备（投屏发送）
        dlnaManager.bind(this);
        dlnaManager.setDeviceListener(deviceListener);
        dlnaManager.startDiscovery();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverMode) {
            // Activity 不可见时暂停进度上报，但保持播放（音频继续）与桥接注册
            receiverUpdateHandler.removeCallbacks(receiverUpdateRunnable);
            return;
        }
        dlnaManager.setDeviceListener(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiverMode) {
            // 接收模式：不暂停播放、不保存进度，保持后台音频
            return;
        }
        // 暂停轮询，Activity 不可见时无需刷新 UI
        castHandler.removeCallbacks(pollRunnable);
        castHandler.removeCallbacks(pollTimeoutRunnable);
        // 保存播放进度（在 pause 之前取 currentPosition，避免被 reset）
        saveProgress();
        if (player != null && !isCasting && !isMusic()) {
            player.pause();
        }
    }

    private boolean isMusic() {
        return FileUtils.isMusicFile(videoPath);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (receiverMode) {
            return;
        }
        // 投屏中恢复轮询；未投屏则恢复本机播放
        if (isCasting) {
            castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        } else if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        receiverUpdateHandler.removeCallbacks(receiverUpdateRunnable);
        gestureHandler.removeCallbacks(hideHintRunnable);
        if (receiverMode) {
            // 接收模式：通知管理器播放器已销毁，释放 ExoPlayer，但不释放 DlnaManager
            if (playerBridge != null) {
                DlnaRendererManager.getInstance().onPlayerDestroyed();
            }
            if (player != null) {
                player.release();
                player = null;
            }
            return;
        }
        // 普通模式：停止轮询、停止投屏、停止本地 HTTP 服务、解绑 DLNA 服务
        castHandler.removeCallbacks(pollRunnable);
        castHandler.removeCallbacks(pollTimeoutRunnable);
        // 释放前再保存一次进度，覆盖 onPause 之后到退出之间的播放位置
        saveProgress();
        dlnaManager.release();
        isCasting = false;
        if (player != null) {
            player.release();
            player = null;
        }

        boolean enabled = DlnaRendererManager.isReceiverEnabled(MyApplication.app);
        if (enabled) {
            DlnaRendererManager.getInstance().start(MyApplication.app);
        }
    }

    // ===== 初始化 & 配置 =====

    @Override
    public void getIntentData() {
        receiverMode = getIntent().getBooleanExtra(EXTRA_RECEIVER_MODE, false);
        if (receiverMode) {
            // 接收模式：标题来自控制点推送，不读取本地文件、不持久化进度
            videoPath = null;
            videoTitle = getIntent().getStringExtra(EXTRA_CAST_TITLE);
            if (videoTitle == null) {
                videoTitle = "投屏接收";
            }
            comicId = -1L;
            return;
        }
        // 普通模式：停止接收端服务（避免同时存在发送端和接收端）
        DlnaRendererManager.getInstance().stop();

        videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if (videoPath == null || !new File(videoPath).exists()) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            finish();
        }
        videoTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (videoTitle == null) {
            videoTitle = new File(videoPath).getName();
        }
        comicId = getIntent().getLongExtra(EXTRA_COMIC_ID, -1L);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_video_player;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        // 沉浸式播放：隐藏状态栏与导航栏，从屏幕边缘滑动可临时唤出
        hideToolbar();

        tv_name = findViewById(R.id.tv_name);

        player_view = findViewById(R.id.player_view);
        cast_button = findViewById(R.id.cast_button);
        cast_control_panel = findViewById(R.id.cast_control_panel);
        cast_device_name = findViewById(R.id.cast_device_name);
        cast_title = findViewById(R.id.cast_title);
        cast_current_time = findViewById(R.id.cast_current_time);
        cast_duration = findViewById(R.id.cast_duration);
        cast_seekbar = findViewById(R.id.cast_seekbar);
        cast_play_pause = findViewById(R.id.cast_play_pause);
        cast_stop = findViewById(R.id.cast_stop);
        gesture_indicator = findViewById(R.id.gesture_indicator);

        play_list = findViewById(R.id.play_list);

        dlnaManager = DlnaManager.getInstance();

        tv_keep_watch = findViewById(R.id.tv_keep_watch);
    }

    @Override
    public void initData() {

    }

    @Override
    public void configView() {
        // 接收模式：先弹投屏确认框，用户接受后才初始化播放器
        if (receiverMode && !castConfirmDone) {
            showCastConfirmDialog();
            return;
        }

        setupCastControlListeners();
        setupCastSeekBar();

        initPlayer(videoPath, videoTitle);
        tv_name.setText(videoTitle);

        // 手势交互：双击快进/退、横向滑动 seek、纵向滑动调亮度/音量
        setupGestures();

        if (receiverMode) {
            applyReceiverModeUi();
        } else {
            setupPlaylistButton();
        }
    }

    /**
     * 接收模式下弹出投屏确认对话框，用户点击"接受"后继续初始化。
     */
    private void showCastConfirmDialog() {
        String caster = getIntent().getStringExtra(EXTRA_CASTER_NAME);
        if (caster != null && !caster.isEmpty()) {
            castConfirmDone = true;
            new AlertDialog.Builder(this)
                    .setTitle("投屏请求")
                    .setMessage(caster + " 要向你投屏\n" + videoTitle)
                    .setPositiveButton("接受", (d, w) -> configView())
                    .setNegativeButton("拒绝", (d, w) -> {
                        DlnaRendererManager.getInstance().onStop();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        castConfirmDone = true;
    }

    /**
     * 绑定投屏相关按钮的点击事件：投屏按钮、返回观看、暂停/播放、停止投屏。
     */
    private void setupCastControlListeners() {
        cast_button.setOnClickListener(v -> {
            if (isCasting) {
                cast_control_panel.setVisibility(View.VISIBLE);
                cast_button.setVisibility(View.GONE);
                tv_name.setVisibility(View.GONE);
                play_list.setVisibility(View.GONE);
                player_view.setUseController(false);
            } else {
                showDeviceDialog();
            }
        });
        tv_keep_watch.setOnClickListener(v -> {
            player_view.setUseController(true);
            cast_control_panel.setVisibility(View.GONE);
            cast_button.setVisibility(View.VISIBLE);
        });
        cast_play_pause.setOnClickListener(v -> togglePlayPause());
        cast_stop.setOnClickListener(v -> stopCasting());
    }

    /**
     * 绑定投屏 SeekBar 的拖动事件，用于控制电视端播放进度。
     */
    private void setupCastSeekBar() {
        cast_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && castDurationMs > 0) {
                    long ms = progress * castDurationMs / seekBar.getMax();
                    cast_current_time.setText(DlnaManager.formatTimeReadable(ms));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (castDurationMs <= 0) return;
                long targetMs = seekBar.getProgress() * castDurationMs / seekBar.getMax();
                castSeek(targetMs);
            }
        });
    }

    /**
     * 接收模式 UI：隐藏投屏发送按钮与控制浮层，提示已进入接收状态。
     */
    private void applyReceiverModeUi() {
        cast_button.setVisibility(View.GONE);
        play_list.setVisibility(View.GONE);
        cast_control_panel.setVisibility(View.GONE);
        Toast.makeText(this, "接收投屏中", Toast.LENGTH_SHORT).show();
    }

    /**
     * 绑定播放列表按钮，点击后弹出列表对话框，切换视频并自动投屏。
     */
    private void setupPlaylistButton() {
        play_list.setOnClickListener(v -> {
            PlayListDialog dialog = new PlayListDialog(this, CollectionDetailFragment.displayComics);
            dialog.setListener(c -> {
                if (comicId != c.getId()) {
                    dialog.dismiss();
                    saveProgress();
                    switchToComic(c);
                    if (isCasting) {
                        castToDevice(true);
                    }
                }
            });
            dialog.show();
            dialog.setCurrentId(comicId);
        });
    }

    /**
     * 切换到指定 Comic 对应的视频并开始播放。
     */
    private void switchToComic(Comic comic) {
        comicId = comic.getId();
        videoPath = comic.getPath();
        videoTitle = comic.getTitle();

        MediaItem mi = new MediaItem.Builder()
                .setUri("file://" + videoPath)
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(videoTitle)
                                .build())
                .build();
        tv_name.setText(videoTitle);
        player.setMediaItem(mi);
        player.prepare();
        player.setPlayWhenReady(true);
    }


    /**
     * 投屏模式下 seek 到指定位置
     */
    private void castSeek(long targetMs) {
        String target = DlnaManager.formatTimeForSeek(targetMs);
        Log.d(TAG, "跳转到 " + target);
        dlnaManager.seek(target, new DlnaManager.SimpleCallback() {
            @Override
            public void onSuccess() {
                // 拖动后立即拉取一次进度
                pollPlaybackInfo();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(VideoPlayerActivity.this,
                        "拖动失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void goBack() {
        if (receiverMode) {
            // 接收模式直接退出，由 onDestroy 通知管理器清理
            finish();
            return;
        }
        // 投屏中按返回键先停止投屏（不退出 Activity），再次按返回才退出
        if (isCasting) {
            stopCasting();
            return;
        }
        super.onBackPressed();
        finish();
    }

    // ===== 手势交互 =====

    /**
     * 在 player_view 上挂手势：
     * <ul>
     *   <li>单击：切换控制器显示/隐藏（替代 PlayerView 默认点击行为）</li>
     *   <li>双击左半屏：后退 {@link #DOUBLE_TAP_SEEK_MS}；双击右半屏：前进</li>
     *   <li>横向滑动：相对起拖位置 seek（一屏宽度 = {@link #SWIPE_SEEK_MS_PER_WIDTH}）</li>
     *   <li>左半屏纵向滑动：调节屏幕亮度；右半屏纵向滑动：调节媒体音量</li>
     * </ul>
     * 投屏到电视（isCasting）或无播放器时禁用，交还默认处理。
     */
    private void setupGestures() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            streamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (player_view.isControllerFullyVisible()) {
                    player_view.hideController();
                } else {
                    player_view.showController();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (player == null || isCasting) return true;
                long dur = player.getDuration();
                if (dur <= 0) return true;
                boolean backward = e.getX() < player_view.getWidth() / 2f;
                long target = player.getCurrentPosition()
                        + (backward ? -DOUBLE_TAP_SEEK_MS : DOUBLE_TAP_SEEK_MS);
                target = Math.max(0, Math.min(target, dur));
                player.seekTo(target);
                showGestureHint((backward ? "◀ " : "▶ ")
                        + DlnaManager.formatTimeReadable(target));
                return true;
            }
        });
        player_view.setOnTouchListener((v, event) -> handleGestureTouch(event));
    }

    private boolean handleGestureTouch(MotionEvent event) {
        if (player == null || isCasting) {
            gestureMode = MODE_NONE;
            return false;
        }
        // 交给 GestureDetector 识别单击/双击
        gestureDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                leftHalf = downX < player_view.getWidth() / 2f;
                gestureMode = MODE_NONE;
                seekStartPosMs = player.getCurrentPosition();
                seekTargetPosMs = seekStartPosMs;
                startBrightness = getWindow().getAttributes().screenBrightness;
                if (startBrightness < 0) startBrightness = 0.5f; // 未设过则按 50% 起算
                startVolume = audioManager != null
                        ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                break;

            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX;
                float dy = y - downY;
                float slop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (gestureMode == MODE_NONE) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)
                            && player.getDuration() > 0) {
                        gestureMode = MODE_SEEK;
                    } else if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                        gestureMode = leftHalf ? MODE_BRIGHTNESS : MODE_VOLUME;
                    }
                }
                if (gestureMode == MODE_SEEK) {
                    long dur = player.getDuration();
                    long delta = (long) (dx / player_view.getWidth() * SWIPE_SEEK_MS_PER_WIDTH);
                    long target = seekStartPosMs + delta;
                    target = Math.max(0, Math.min(target, dur));
                    seekTargetPosMs = target;
                    showGestureHint(DlnaManager.formatTimeReadable(target)
                            + " / " + DlnaManager.formatTimeReadable(dur));
                } else if (gestureMode == MODE_BRIGHTNESS) {
                    float b = startBrightness - dy / player_view.getHeight();
                    applyBrightness(Math.max(0f, Math.min(1f, b)));
                } else if (gestureMode == MODE_VOLUME) {
                    int v = startVolume
                            - (int) (dy / player_view.getHeight() * streamMaxVolume);
                    applyVolume(Math.max(0, Math.min(v, streamMaxVolume)));
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (gestureMode == MODE_SEEK) {
                    player.seekTo(seekTargetPosMs);
                }
                gestureMode = MODE_NONE;
                break;
            default:
                break;
        }
        return true;
    }

    private void applyBrightness(float b) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b;
        getWindow().setAttributes(lp);
        showGestureHint("亮度 " + (int) (b * 100) + "%");
    }

    private void applyVolume(int v) {
        if (audioManager == null) return;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        showGestureHint("音量 " + (streamMaxVolume > 0 ? v * 100 / streamMaxVolume : 0) + "%");
    }

    private void showGestureHint(String text) {
        if (gesture_indicator == null) return;
        gesture_indicator.setText(text);
        gesture_indicator.setVisibility(View.VISIBLE);
        gestureHandler.removeCallbacks(hideHintRunnable);
        gestureHandler.postDelayed(hideHintRunnable, GESTURE_HINT_HIDE_MS);
    }

    private final Runnable hideHintRunnable = () -> {
        if (gesture_indicator != null) {
            gesture_indicator.setVisibility(View.GONE);
        }
    };

    // ===== 接收模式：PlayerBridge =====

    @OptIn(markerClass = UnstableApi.class)
    private void registerReceiverBridge() {
        if (playerBridge == null) {
            playerBridge = new DlnaRendererManager.PlayerBridge() {
                @Override
                public void play() {
                    if (player != null) {
                        player.setPlayWhenReady(true);
                    }
                }

                @Override
                public void pause() {
                    if (player != null) {
                        player.setPlayWhenReady(false);
                    }
                }

                @Override
                public void stop() {
                    if (player != null) {
                        player.stop();
                    }
                    finish();
                }

                @Override
                public void seekTo(long ms) {
                    // 投屏刚 SetURI+Play 后 ExoPlayer 可能还在 BUFFERING，
                    // 此时 seekTo 会被忽略；缓存等 STATE_READY 后执行
                    if (player == null || player.getPlaybackState() != Player.STATE_READY) {
                        pendingSeekMs = ms;
                    } else {
                        player.seekTo(ms);
                    }
                }

                @Override
                public void setUri(String uri, String title) {
                    if (player == null || uri == null) {
                        return;
                    }

                    MediaItem mi = new MediaItem.Builder()
                            .setUri(uri)
                            .setMediaMetadata(
                                    new MediaMetadata.Builder()
                                            .setTitle(title)
                                            .build())
                            .build();
                    tv_name.setText(title);
                    player.setMediaItem(mi);
                    player.prepare();
                    player.setPlayWhenReady(true);
                }

                @Override
                public long getPositionMs() {
                    return cachedPositionMs;
                }

                @Override
                public long getDurationMs() {
                    return cachedDurationMs;
                }
            };
        }
        DlnaRendererManager.getInstance().registerBridge(playerBridge);
        // 同步当前传输状态：若控制点在 Activity 启动前已发 Pause，则暂停播放
        String state = DlnaRendererManager.getInstance().getTransportState();
        if ("PAUSED_PLAYBACK".equals(state) && player != null) {
            player.setPlayWhenReady(false);
        }
        receiverUpdateHandler.removeCallbacks(receiverUpdateRunnable);
        receiverUpdateHandler.post(receiverUpdateRunnable);
    }

    /**
     * 主线程定期读取 ExoPlayer 进度并写入 volatile 缓存，供 Cling 线程查询。
     */
    private void updateReceiverPosition() {
        if (player == null) {
            receiverUpdateHandler.postDelayed(receiverUpdateRunnable, 500);
            return;
        }
        cachedPositionMs = player.getCurrentPosition();
        Log.d(TAG, "updateReceiverPosition: cachedPositionMs:" + cachedPositionMs);
        long d = player.getDuration();
        // getDuration() 在媒体未就绪时返回 C.TIME_UNSET（巨大负数），需当作 0 处理，
        // 否则接收端会向发送端返回非法时长字符串。
        cachedDurationMs = (d == C.TIME_UNSET) ? 0 : d;
        receiverUpdateHandler.postDelayed(receiverUpdateRunnable, 500);
    }


}
