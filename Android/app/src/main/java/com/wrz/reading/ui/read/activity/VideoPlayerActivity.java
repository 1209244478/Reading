package com.wrz.reading.ui.read.activity;

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
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.dlna.DlnaManager;
import com.wrz.reading.model.Comic;

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
    private static final long POLL_INTERVAL_MS = 1500L;

    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_TITLE = "title";
    /**
     * 视频 Comic 主键 ID，传入后启用播放进度保存/恢复；不传则不持久化
     */
    public static final String EXTRA_COMIC_ID = "comic_id";

    private ExoPlayer player;
    private PlayerView player_view;
    private ImageButton cast_button;

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

    private DlnaManager dlnaManager;
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
     * 设备列表变化时刷新对话框
     */
    private final DlnaManager.DeviceListener deviceListener = () ->
            runOnUiThread(VideoPlayerActivity.this::refreshDeviceList);


    @OptIn(markerClass = UnstableApi.class)
    private void initPlayer(String videoPath, String title) {
        player = new ExoPlayer.Builder(this).build();
        player_view.setPlayer(player);

        // 投屏按钮与播放器控制器联动显示/隐藏
        player_view.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility ->
                        cast_button.setVisibility(visibility));

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri("file://" + videoPath)
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(title)
                                .build())
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
        // 恢复上次播放进度（异步加载，不阻塞首帧）
        restoreProgress();
    }

    /**
     * 从 DB 读取上次保存的播放位置并 seekTo
     */
    private void restoreProgress() {
        if (comicId == -1L) return;
        ioExecutor.execute(() -> {
            Comic loaded = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (loaded == null || loaded.getVideoPosition() <= 0) return;
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
        if (comicId == -1L || player == null || isCasting) return;
        long pos = player.getCurrentPosition();
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
        refreshDeviceList();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                Device device = devices.get(position);
                deviceDialog.dismiss();
                Toast.makeText(this, "正在投屏到 " + getDeviceName(device), Toast.LENGTH_SHORT).show();
                dlnaManager.cast(device, new File(videoPath), videoTitle,
                        new DlnaManager.CastListener() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    Toast.makeText(VideoPlayerActivity.this,
                                            "投屏成功", Toast.LENGTH_SHORT).show();
                                    onCastStarted(device);
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
        // 禁用 ExoPlayer 自带控制器，避免误操作本地播放（统一由浮层控制电视端）
        player_view.setUseController(false);
        // 停止本机播放（保留位置，退出投屏时从电视端进度恢复）
        if (player != null) {
            player.pause();
        }
        // 开始轮询播放进度
        castHandler.removeCallbacks(pollRunnable);
        castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    /**
     * 隐藏浮层并停止轮询
     */
    private void hideCastOverlay() {
        castHandler.removeCallbacks(pollRunnable);
        isCasting = false;
        isCastPaused = false;
        castDurationMs = 0;
        cast_control_panel.setVisibility(View.GONE);
        // 恢复投屏按钮
        cast_button.setVisibility(View.VISIBLE);
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
        if (player != null) {
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
        dlnaManager.getPlaybackInfo(new DlnaManager.PlaybackInfoListener() {
            @Override
            public void onResult(long positionMs, long durationMs) {
                runOnUiThread(() -> {
                    if (!isCasting) return;
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
                    Log.w(TAG, "getPlaybackInfo 失败: " + message);
                    // 失败时也继续轮询，避免一次网络抖动就停掉
                    if (isCasting) {
                        castHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    }
                });
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 绑定 DLNA 服务并开始搜索设备
        dlnaManager.bind(this);
        dlnaManager.setDeviceListener(deviceListener);
        dlnaManager.startDiscovery();
    }

    @Override
    protected void onStop() {
        super.onStop();
        dlnaManager.setDeviceListener(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停轮询，Activity 不可见时无需刷新 UI
        castHandler.removeCallbacks(pollRunnable);
        // 保存播放进度（在 pause 之前取 currentPosition，避免被 reset）
        saveProgress();
        if (player != null && !isCasting) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        // 退出时清理：停止轮询、停止投屏、停止本地 HTTP 服务、解绑 DLNA 服务
        castHandler.removeCallbacks(pollRunnable);
        // 释放前再保存一次进度，覆盖 onPause 之后到退出之间的播放位置
        saveProgress();
        dlnaManager.release();
        isCasting = false;
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void getIntentData() {
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

        dlnaManager = DlnaManager.getInstance();
    }

    @Override
    public void initData() {

    }

    @Override
    public void configView() {
        cast_button.setOnClickListener(v -> showDeviceDialog());
        cast_play_pause.setOnClickListener(v -> togglePlayPause());
        cast_stop.setOnClickListener(v -> stopCasting());
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
                String target = DlnaManager.formatTimeForSeek(targetMs);
                Log.d(TAG, "用户拖动到 " + target);
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
        });

        initPlayer(videoPath, videoTitle);
    }

    @Override
    public void goBack() {
        // 投屏中按返回键先停止投屏（不退出 Activity），再次按返回才退出
        if (isCasting) {
            stopCasting();
            return;
        }
        super.onBackPressed();
        finish();
    }

}
