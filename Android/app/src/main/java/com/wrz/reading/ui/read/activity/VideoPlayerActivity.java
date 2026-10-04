package com.wrz.reading.ui.read.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
import androidx.media3.datasource.FileDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.read.dlna.DlnaManager;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.read.data.VideoPlaylistStore;
import com.wrz.reading.ui.read.data.VideoProgressRepository;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.view.dialog.PlayListDialog;

import org.fourthline.cling.model.meta.Device;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 应用内视频播放器，使用 ExoPlayer 播放本地视频文件。
 * 通过 Intent 传入 EXTRA_VIDEO_PATH（文件路径）和 EXTRA_TITLE（标题，可选）。
 * 支持通过 DLNA 将本地视频投屏到智能电视（小米电视、B 站电视版等）。
 * <p>职责拆分：播放进度持久化见 {@link VideoProgressRepository}，
 * 投屏进度轮询见 {@link CastPollingController}，手势交互见 {@link GestureHandler}。
 */
@SuppressWarnings({"rawtypes"})
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends BaseActivity implements CastPollingController.Host {

    static final String TAG = "VideoPlayerActivity";

    public static void start(Activity activity, long comicId,
                             String videoPath, String videoTitle,
                             boolean isFromRead, int requestCode) {
        Intent intent = new Intent(activity, VideoPlayerActivity.class);

        intent.putExtra(EXTRA_COMIC_ID, comicId);

        intent.putExtra(EXTRA_VIDEO_PATH, videoPath);
        intent.putExtra(EXTRA_TITLE, videoTitle);

        intent.putExtra(EXTRA_FROM_READ, isFromRead);

        activity.startActivityForResult(intent, requestCode);
    }

    public static boolean RenderStart(Context context,
                             String uri, String castTitle, String castName,
                             boolean receiverMode) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        intent.putExtra(EXTRA_CAST_URI, uri);
        intent.putExtra(EXTRA_CAST_TITLE, castTitle);

        intent.putExtra(EXTRA_CASTER_NAME, castName);

        intent.putExtra(EXTRA_RECEIVER_MODE, receiverMode);

        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "启动接收 Activity 失败", e);
        }
        return false;
    }

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
    public static final String EXTRA_FROM_READ = "from_read";
    public static final String EXTRA_CAST_URI = "cast_uri";
    public static final String EXTRA_CAST_TITLE = "cast_title";
    public static final String EXTRA_CASTER_NAME = "cast_name";


    ExoPlayer player;
    PlayerView player_view;
    private ImageButton cast_button;
    private TextView tv_name;
    private TextView tv_keep_watch;
    private ImageButton play_list;

    // 投屏控制浮层
    private FrameLayout cast_control_panel;
    private TextView cast_device_name;
    private TextView cast_title;
    private TextView cast_current_time;
    private TextView cast_duration;
    private SeekBar cast_seekbar;
    private ImageButton cast_play_pause;
    private ImageButton cast_stop;

    // 手势交互
    TextView gesture_indicator;

    // 统一主线程 Handler：投屏 castSeek 延迟 / 手势组件 / 接收端进度刷新共用
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 播放进度仓库：restoreProgress/saveProgress 的 DB 读写（ui/read/data） */
    private final VideoProgressRepository progressRepository = new VideoProgressRepository();

    /** 投屏进度轮询控制器：轮询 + 超时重试 + 进度缓存 */
    private final CastPollingController castPollingController = new CastPollingController(this);

    /** 手势处理组件：双击/长按/滑动 seek/亮度/音量/手势提示 */
    private final GestureHandler gestureHandler = new GestureHandler(this, mainHandler);



    private String videoPath;
    private String videoTitle;
    /**
     * 当前视频对应的 Comic 主键，-1 表示未传入（不持久化进度）
     */
    private long comicId = -1L;

    // ===== 接收模式（作为 DLNA MediaRenderer 播放器） =====
    private boolean receiverMode = false;

    private String receivedUri;
    /**
     * 接收端：投屏时 seek 早于 ExoPlayer prepare 完成，缓存等 STATE_READY 后执行
     */
    private long pendingSeekMs = 0;


    // ========================= 播放器相关 =========================

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
                DlnaRendererManager.getInstance().onReceiverPlayStateChanged(isPlaying);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // 接收端：player 准备好后执行缓存的 seek（投屏时 seek 早于 prepare 完成）
                if (receiverMode && playbackState == Player.STATE_READY && pendingSeekMs > 0) {
                    player.seekTo(pendingSeekMs);
                    pendingSeekMs = 0;
                } else if (playbackState == Player.STATE_ENDED) {
                    PlayNextVideo();
                }
            }
        });

        // 投屏按钮与播放器控制器联动显示/隐藏（接收模式下不联动）
        player_view.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    tv_name.setVisibility(visibility);
                    cast_button.setVisibility(visibility);
                    if (!receiverMode) {
                        play_list.setVisibility(visibility);
                    }
                });

        // 接收模式使用控制点推送的 http URI；普通模式用 FileDataSource 直读本地文件（无缓存）
        if (receiverMode) {
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(receivedUri)
                    .setMediaMetadata(
                            new MediaMetadata.Builder()
                                    .setTitle(title)
                                    .build())
                    .build();
            player.setMediaItem(mediaItem);
        } else {
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(
                    FileDataSource::new)
                    .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(videoPath))));
            player.setMediaSource(mediaSource);
        }
        player.prepare();
        player.setPlayWhenReady(true);
        // 仅普通模式恢复上次播放进度（接收模式不持久化）
        if (!receiverMode) {
            restoreProgress();
        }
    }

    /**
     * 播放下一个视频
     */
    @Override
    public void PlayNextVideo() {
        if (!receiverMode) {
            VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
            if (source == null) {
                return;
            }
            if (isFromRead) {
                List<ReadItem> subFiles = source.getSubFiles();
                if (subFiles != null && !subFiles.isEmpty()) {
                    int index = getNextSubIndex();

                    if (index != -1 && subFiles.get(index) != null) {
                        switchToSubFile(comicId, subFiles.get(index).getFile());
                    }
                }
            } else {
                List<Comic> display = source.getDisplayComics();
                if (!display.isEmpty()) {
                    int index = getComicIndex(comicId) + 1;

                    saveProgress();

                    if (index > 0 && index < display.size()) {
                        switchToComic(display.get(index));
                    } else {
                        // 当前是最后一项或未在列表中：回到列表开头
                        switchToComic(display.get(0));
                    }
                }
            }
        }
    }

    /**
     * 从 DB 读取上次保存的播放位置并 seekTo（DB 读写见 VideoProgressRepository）
     */
    private void restoreProgress() {
        progressRepository.restore(comicId, videoTitle, isFromRead, finalPos -> {
            if (player != null) {
                player.seekTo(finalPos);
            }
        });
    }

    /**
     * 保存当前播放进度到 DB（投屏中不保存本地进度，DB 读写见 VideoProgressRepository）
     */
    private void saveProgress() {
        if (comicId == -1L || player == null) return;
        progressRepository.save(comicId, videoTitle, player.getCurrentPosition(), player.getDuration(),
                isFromRead, isCasting, castDurationMs);
    }

    /**
     * 获取下一个视频index
     * @return index
     */
    public int getNextSubIndex() {
        VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
        List<ReadItem> subFiles = source != null ? source.getSubFiles() : null;
        if (subFiles == null || subFiles.isEmpty()) {
            return -1;
        }

        int index = -1;
        for (int i = 0; i < subFiles.size(); i++) {
            if (Objects.equals(videoPath, subFiles.get(i).getFile().getAbsolutePath())) {
                index = i;
                break;
            }
        }
        int nextIndex = -1;

        if (index != -1) {
            for (int i = index; i < subFiles.size(); i++) {
                if (FileUtils.isVideoFile(subFiles.get(index).getFile().getName())
                        && !Objects.equals(videoPath, subFiles.get(i).getFile().getAbsolutePath())) {
                    nextIndex = i;
                    break;
                }
            }

            if (nextIndex == -1) {
                for (int i = index; i < subFiles.size(); i++) {
                    if (FileUtils.isVideoFile(subFiles.get(index).getFile().getName())) {
                        nextIndex = i;
                        break;
                    }
                }
            }
        }

        return nextIndex;
    }

    /**
     * 获取当前视频的列表位置
     * @param comicId 当前视频的comicId
     * @return index
     */
    public int getComicIndex(long comicId) {
        VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
        List<Comic> display = source != null ? source.getDisplayComics() : null;
        if (display == null) {
            return -1;
        }
        int index = -1;
        for (int i = 0; i < display.size(); i++) {
            if (comicId == display.get(i).getId()) {
                index = i;
                break;
            }
        }
        return index;
    }

    // ========================= 投屏相关 =========================

    private DlnaManager dlnaManager;
    /**
     * 当前选中的投屏设备
     */
    private Device device;
    private AlertDialog deviceDialog;
    private ArrayAdapter<String> deviceAdapter;
    private final List<Device> devices = new ArrayList<>();

    // 投屏状态
    boolean isCasting = false;
    private boolean isCastPaused = false;

    /**
     * 最后一次轮询到的投屏进度，用于退出投屏时恢复本机播放位置
     */
    private long lastCastPositionMs = 0;

    private long castDurationMs = 0;

    /**
     * 投屏成功：显示浮层、停止本机播放、禁用 ExoPlayer 控制器、开始轮询
     */
    @SuppressLint("SetTextI18n")
    private void onCastStarted(Device device) {
        isCasting = true;
        isCastPaused = false;
        castDurationMs = 0;
        lastCastPositionMs = 0;
        cast_device_name.setText(getString(R.string.msg_casting_label, getDeviceName(device)));
        cast_title.setText(videoTitle);
        cast_current_time.setText("0:00");
        cast_duration.setText("0:00");
        cast_seekbar.setProgress(0);
        cast_play_pause.setImageResource(android.R.drawable.ic_media_pause);

        if (!receiverMode) {
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
        }
        // 开始轮询播放进度
        castPollingController.stopPoll();
        castPollingController.stopPollTimeOut();
        castPollingController.startPoll();
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
                            getString(R.string.msg_resume_failed, message), Toast.LENGTH_SHORT).show());
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
                            getString(R.string.msg_pause_failed, message), Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    /**
     * 停止投屏，从电视端最后进度恢复本机播放
     */
    @Override
    public void stopCasting() {
        if (!isCasting) return;
        final long resumePos = lastCastPositionMs;
        dlnaManager.stopCast();
        hideCastOverlay();
        Toast.makeText(this, getString(R.string.msg_cast_stopped), Toast.LENGTH_SHORT).show();
        // 恢复本机播放，跳到电视端最后进度
        if (player != null && !player.isPlaying()) {
            if (resumePos > 0) {
                player.seekTo(resumePos);
            }
            player.setPlayWhenReady(true);
        }
    }

    /**
     * 隐藏浮层并停止轮询
     */
    private void hideCastOverlay() {
        castPollingController.stopPoll();
        castPollingController.stopPollTimeOut();
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
     * 弹出设备列表对话框
     */
    private void showDeviceDialog() {
        ListView listView = new ListView(this);
        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(deviceAdapter);
        // 打开列表时主动触发一次搜索
        dlnaManager.startDiscovery();
        refreshDeviceList();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                device = devices.get(position);
                deviceDialog.dismiss();
                Toast.makeText(this, getString(R.string.msg_casting_to, getDeviceName(device)), Toast.LENGTH_SHORT).show();
                if (receiverMode) {
                    castUriToDevice(true);
                } else {
                    castToDevice(true);
                }
            }
        });

        deviceDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_select_cast_device))
                .setMessage(getString(R.string.msg_cast_searching))
                .setView(listView)
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setOnDismissListener(d -> {
                    deviceAdapter = null;
                    deviceDialog = null;
                })
                .create();
        deviceDialog.show();
    }

    /**
     * 设备列表变化时刷新对话框
     */
    private final DlnaManager.DeviceListener deviceListener = () ->
            runOnUiThread(VideoPlayerActivity.this::refreshDeviceList);

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
                ? getString(R.string.msg_cast_search_no_device)
                : getString(R.string.msg_cast_found_devices, devices.size()));
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

    /**
     * 投屏到设备（本地视频）
     * @param isChangeCast 是否改变了投屏设备，true:改变了投屏设备，false:还是原来的设备
     */
    @Override
    public void castToDevice(boolean isChangeCast) {
        long startPos = player != null ? player.getCurrentPosition() : 0;

        dlnaManager.cast(device, new File(videoPath), videoTitle, startPos, isChangeCast,
                new DlnaManager.CastListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(VideoPlayerActivity.this,
                                    getString(R.string.msg_cast_success), Toast.LENGTH_SHORT).show();
                            onCastStarted(device);
                            if (player != null && player.getCurrentPosition() > 0) {
                                mainHandler.postDelayed(() ->
                                        castSeek(player.getCurrentPosition()), 5000);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                VideoPlayerActivity.this, getString(R.string.msg_cast_failed, message),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    /**
     * 投屏到设备（在线视频）
     * @param isChangeCast 是否改变了投屏设备，true:改变了投屏设备，false:还是原来的设备
     */
    public void castUriToDevice(boolean isChangeCast) {
        long startPos = player != null ? player.getCurrentPosition() : 0;

        dlnaManager.cast(device, receivedUri, videoTitle, startPos, isChangeCast,
                new DlnaManager.CastListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(VideoPlayerActivity.this,
                                    getString(R.string.msg_cast_success), Toast.LENGTH_SHORT).show();
                            onCastStarted(device);
                            if (player != null && player.getCurrentPosition() > 0) {
                                mainHandler.postDelayed(() ->
                                        castSeek(player.getCurrentPosition()), 5000);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                VideoPlayerActivity.this, getString(R.string.msg_cast_failed, message),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }


    // ========================= CastPollingController.Host 实现 =========================

    @Override
    public boolean isCasting() {
        return isCasting;
    }

    @Override
    public boolean isCastPaused() {
        return isCastPaused;
    }

    @Override
    public void setCastPaused(boolean paused) {
        isCastPaused = paused;
    }

    @Override
    public void setCastPlayPauseIcon(int resId) {
        cast_play_pause.setImageResource(resId);
    }

    @Override
    public boolean isUserSeeking() {
        return isUserSeeking;
    }

    @Override
    public long getCastDurationMs() {
        return castDurationMs;
    }

    @Override
    public void setCastDurationMs(long durationMs) {
        castDurationMs = durationMs;
    }

    @Override
    public void showCastDurationText(long durationMs) {
        cast_duration.setText(DlnaManager.formatTimeReadable(durationMs));
    }

    @Override
    public void showCastCurrentTimeText(long positionMs) {
        cast_current_time.setText(DlnaManager.formatTimeReadable(positionMs));
    }

    @Override
    public void updateCastSeekBarProgress(long positionMs) {
        int progress = (int) (positionMs * cast_seekbar.getMax() / castDurationMs);
        cast_seekbar.setProgress(progress);
    }

    @Override
    public void setLastCastPositionMs(long positionMs) {
        lastCastPositionMs = positionMs;
    }

    @Override
    public DlnaManager getDlnaManager() {
        return dlnaManager;
    }


    // ========================= 生命周期 =========================

    @Override
    protected void onStart() {
        super.onStart();
        // 绑定 DLNA 服务并开始搜索设备（投屏发送）
        dlnaManager.bind(this);
        dlnaManager.setDeviceListener(deviceListener);
        dlnaManager.startDiscovery();

        if (receiverMode) {
            // 接收模式：注册 PlayerBridge 供 DlnaRendererManager 控制 ExoPlayer
            registerReceiverBridge();
            return;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        dlnaManager.setDeviceListener(null);
        if (receiverMode) {
            // Activity 不可见时暂停进度上报，但保持播放（音频继续）与桥接注册
            mainHandler.removeCallbacks(receiverUpdateRunnable);
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiverMode) {
            // 接收模式：不暂停播放、不保存进度，保持后台音频
            return;
        }
        // 暂停轮询，Activity 不可见时无需刷新 UI
        castPollingController.stopPoll();
        castPollingController.stopPollTimeOut();
        // 保存播放进度（在 pause 之前取 currentPosition，避免被 reset）
        if (!isFinishing() && !isDestroyed()) {
            saveProgress();
        }
        if (player != null && !isCasting && !FileUtils.isMusicFile(videoPath)) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (receiverMode) {
            return;
        }
        // 投屏中恢复轮询；未投屏则恢复本机播放
        if (isCasting) {
            castPollingController.startPoll();
        } else if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(receiverUpdateRunnable);
        gestureHandler.removeHintCallback();

        dlnaManager.release();

        if (receiverMode) {
            // 接收模式：通知管理器播放器已销毁，释放 ExoPlayer
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
        castPollingController.stopPoll();
        castPollingController.stopPollTimeOut();
        // 释放前再保存一次进度，覆盖 onPause 之后到退出之间的播放位置
        saveProgress();
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

    // ========================= 初始化 & 配置 =========================

    boolean isFromRead = false;

    /**
     * 获取Intent数据
     */
    @Override
    public void getIntentData() {
        receivedUri = getIntent().getStringExtra(EXTRA_CAST_URI);
        isFromRead = getIntent().getBooleanExtra(EXTRA_FROM_READ, false);
        receiverMode = getIntent().getBooleanExtra(EXTRA_RECEIVER_MODE, false);
        videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        comicId = getIntent().getLongExtra(EXTRA_COMIC_ID, -1L);

        if (receiverMode) {
            // 接收模式：标题来自控制点推送，不读取本地文件、不持久化进度
            videoPath = null;
            videoTitle = getIntent().getStringExtra(EXTRA_CAST_TITLE);
            if (videoTitle == null) {
                videoTitle = getString(R.string.title_cast_receive);
            }
            comicId = -1L;

        } else {
            // 普通模式：停止接收端服务（避免同时存在发送端和接收端）
            DlnaRendererManager.getInstance().stop();

            if (videoPath == null || !new File(videoPath).exists()) {
                Toast.makeText(this, getString(R.string.msg_video_not_found), Toast.LENGTH_SHORT).show();
                finish();
            }
            videoTitle = getIntent().getStringExtra(EXTRA_TITLE);
            if (videoTitle == null) {
                videoTitle = new File(videoPath).getName();
            }
        }
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_video_player;
    }


    @Override
    public void initToolBar() {

    }

    /**
     * 初始化所有View组件
     */
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


    /**
     * 接收端：投屏确认弹窗是否已处理
     */
    private boolean castConfirmDone = false;

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
        gestureHandler.setupGestures();

        if (receiverMode) {
            applyReceiverModeUi();
        } else {
            setupPlaylistButton();
        }
    }

    // =========== 接收模式（按钮事件 && 弹窗 ===========

    /**
     * 接收模式下弹出投屏确认对话框，用户点击"接受"后继续初始化。
     */
    private void showCastConfirmDialog() {
        String caster = getIntent().getStringExtra(EXTRA_CASTER_NAME);

        if (caster == null) {
            caster = "";
        }

        castConfirmDone = true;
        new AlertDialog.Builder(VideoPlayerActivity.this)
                .setTitle(getString(R.string.title_cast_request))
                .setMessage(getString(R.string.msg_cast_incoming, caster, videoTitle))
                .setPositiveButton(getString(R.string.accept), (d, w) -> configView())
                .setNegativeButton(getString(R.string.refuse), (d, w) -> {
                    DlnaRendererManager.getInstance().onStop();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 接收模式 UI：隐藏投屏发送按钮与控制浮层，提示已进入接收状态。
     */
    private void applyReceiverModeUi() {
        cast_button.setVisibility(View.VISIBLE);
        play_list.setVisibility(View.GONE);
        cast_control_panel.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.msg_cast_receiving), Toast.LENGTH_SHORT).show();
    }

    // =========== 普通模式（按钮事件 ===========

    /**
     * 绑定播放列表按钮，点击后弹出列表对话框，切换视频并自动投屏。
     */
    private void setupPlaylistButton() {
        play_list.setOnClickListener(v -> {
            saveProgress();
            VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
            List<Comic> playlist = source != null
                    ? source.getDisplayComics()
                    : new ArrayList<>();
            PlayListDialog dialog = new PlayListDialog(this, playlist);
            dialog.setListener(c -> {
                if (comicId != c.getId()) {
                    dialog.dismiss();
                    switchToComic(c);
                    if (isCasting) {
                        castToDevice(false);
                    }
                }
            });
            dialog.setOnSubItemClickListener((parentId, videoFile) -> {
                dialog.dismiss();
                switchToSubFile(parentId, videoFile);
            });
            dialog.show();
            dialog.setSubSelect(videoTitle);
            dialog.setCurrentId(comicId);
        });
    }

    /**
     * 一级目录 中 切换到指定 Comic 对应的视频并开始播放。
     */
    private void switchToComic(Comic comic) {
        isFromRead = false;

        comicId = comic.getId();
        videoPath = comic.getPath();
        videoTitle = comic.getTitle();

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(
                FileDataSource::new)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(videoPath))));
        tv_name.setText(videoTitle);
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
        // 仅普通模式恢复上次播放进度（接收模式不持久化）
        if (!receiverMode) {
            restoreProgress();
        }
    }

    /**
     * 切换到二级播放目录
     * @param parentId 二级播放目录 父母Id
     * @param videoFile 视频文件
     */
    private void switchToSubFile(long parentId, File videoFile) {
        isFromRead = true;
        comicId = parentId;
        videoPath = videoFile.getAbsolutePath();
        videoTitle = videoFile.getName();

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(
                FileDataSource::new)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(videoPath))));
        tv_name.setText(videoTitle);
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);

    }

    // =========== 公共事件 ===========

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
     * 用户正在拖动 SeekBar 时不更新进度（避免抖动）
     */
    private boolean isUserSeeking = false;

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
     * 投屏模式下 seek 到指定位置
     */
    private void castSeek(long targetMs) {
        String target = DlnaManager.formatTimeForSeek(targetMs);
        dlnaManager.seek(target, new DlnaManager.SimpleCallback() {
            @Override
            public void onSuccess() {
                // 拖动后立即拉取一次进度
                castPollingController.pollPlaybackInfo();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(VideoPlayerActivity.this,
                        getString(R.string.msg_seek_failed, message), Toast.LENGTH_SHORT).show();
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
        super.getOnBackPressedDispatcher();
        finish();
    }




    // ========================= 接收模式：PlayerBridge =========================

    private final Runnable receiverUpdateRunnable = this::updateReceiverPosition;

    private DlnaRendererManager.PlayerBridge playerBridge;

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
                public void setUri(String uri, String title, boolean isNewDevice, String casterName) {
                    if (player == null || uri == null) {
                        return;
                    }

                    if (isNewDevice) {
                        new AlertDialog.Builder(VideoPlayerActivity.this)
                                .setTitle(getString(R.string.title_cast_request))
                                .setMessage(getString(R.string.msg_cast_incoming, casterName, title))
                                .setPositiveButton(getString(R.string.accept), (d, w) -> {
                                    DlnaRendererManager.getInstance().setCurrentCasterName(casterName);
                                    playNewCast(uri, title);
                                })
                                .setNegativeButton(getString(R.string.refuse), (d, w) -> {
                                    DlnaRendererManager.getInstance().onStop();
                                    finish();
                                })
                                .setCancelable(false)
                                .show();
                    } else {
                        DlnaRendererManager.getInstance().setCurrentCasterName(casterName);
                        playNewCast(uri, title);
                    }
                }

                @Override
                public long getPositionMs() {
                    return castPollingController.cachedPositionMs;
                }

                @Override
                public long getDurationMs() {
                    return castPollingController.cachedDurationMs;
                }
            };
        }
        DlnaRendererManager.getInstance().registerBridge(playerBridge);
        // 同步当前传输状态：若控制点在 Activity 启动前已发 Pause，则暂停播放
        String state = DlnaRendererManager.getInstance().getTransportState();
        if ("PAUSED_PLAYBACK".equals(state) && player != null) {
            player.setPlayWhenReady(false);
        }
        mainHandler.removeCallbacks(receiverUpdateRunnable);
        mainHandler.post(receiverUpdateRunnable);
    }

    /**
     * 接收并播放新的视频
     * @param uri 视频链接
     * @param title
     */
    private void playNewCast(String uri, String title) {
        receivedUri = uri;
        videoTitle = title;
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
        if (isCasting) {
            if (receiverMode) {
                castUriToDevice(false);
            } else {
                castToDevice(false);
            }
        }
    }


    /**
     * 主线程定期读取 ExoPlayer 进度并写入 volatile 缓存（见 CastPollingController），供 Cling 线程查询。
     */
    private void updateReceiverPosition() {
        if (player == null) {
            mainHandler.postDelayed(receiverUpdateRunnable, 500);
            return;
        }
        if (!isCasting) {
            castPollingController.cachedPositionMs = player.getCurrentPosition();
            long d = player.getDuration();
            // getDuration() 在媒体未就绪时返回 C.TIME_UNSET（巨大负数），需当作 0 处理，
            // 否则接收端会向发送端返回非法时长字符串。
            castPollingController.cachedDurationMs = (d == C.TIME_UNSET) ? 0 : d;
        }
        mainHandler.postDelayed(receiverUpdateRunnable, 500);
    }


}
