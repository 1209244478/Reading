package com.wrz.reading.ui.read.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.read.data.VideoDownloadManager;
import com.wrz.reading.ui.read.data.VideoPlaylistStore;
import com.wrz.reading.ui.read.data.VideoProgressRepository;
import com.wrz.reading.ui.read.dlna.DlnaManager;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.DownloadTask;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.utils.AudioTrackUtil;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.VideoUtils;
import com.wrz.reading.ui.read.view.dialog.PlayListDialog;
import com.wrz.reading.ui.read.wrzcast.WrzCastDevice;
import com.wrz.reading.ui.read.wrzcast.WrzCastDiscovery;
import com.wrz.reading.ui.read.wrzcast.WrzCastReceiverManager;
import com.wrz.reading.ui.read.wrzcast.WrzCastSender;

import org.fourthline.cling.model.meta.Device;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用内视频播放器，使用 ExoPlayer 播放本地视频文件。
 * 通过 Intent 传入 EXTRA_VIDEO_PATH（文件路径）和 EXTRA_TITLE（标题，可选）。
 * 支持将本地视频投屏到智能电视（DLNA），或投屏到另一台安装了本应用的设备（WrzCast 私有协议）。
 * <p>职责拆分：播放进度持久化见 {@link VideoProgressRepository}，
 * 投屏进度轮询见 {@link CastPollingController}，手势交互见 {@link GestureHandler}。
 */
@SuppressWarnings({"rawtypes"})
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends BaseActivity implements CastPollingController.Host {


    @Override
    public int getLayoutId() {
        return R.layout.activity_video_player;
    }

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
     * 投屏接收模式：由 DlnaRendererManager / WrzCastReceiverManager 拉起本 Activity 作为接收端播放器。
     * 该模式下不绑定 DlnaManager（投屏发送），不读取本地文件路径，不保存进度。
     */
    public static final String EXTRA_RECEIVER_MODE = "receiver_mode";
    public static final String EXTRA_FROM_READ = "from_read";
    public static final String EXTRA_CAST_URI = "cast_uri";
    public static final String EXTRA_CAST_TITLE = "cast_title";
    public static final String EXTRA_CASTER_NAME = "cast_name";


    ExoPlayer player;
    PlayerView player_view;
    /** 右侧中部锁定按钮：锁定时仅保留此按钮用于解锁 */
    private ImageButton lock_button;
    private View playerTopBar;
    private boolean controlsLocked;
    /** TV 遥控器上下键切换控制栏时使用（PlayerView 未提供 isControllerVisible） */
    private boolean tvControllerVisible = true;
    private View cast_button;
    private TextView tv_name;
    private TextView tv_keep_watch;
    private View play_list;
    /**
     * 顶栏音轨按钮：多音轨时显示（本地/接收模式切播放器音轨；投屏中隐藏）
     */
    private View audio_track_button;
    /**
     * 顶栏下载按钮：仅接收投屏模式显示（把发送端视频下载到本地）
     */
    private View download_button;
    /**
     * 投屏浮层"音轨"入口：多音轨视频投屏后显示（重封装后重投）
     */
    private TextView cast_audio_track;

    // ===== 电视端自定义控制台（layout-land-television/activity_video_player.xml） =====
    private boolean tvUi;
    private boolean tvControllerReady;
    private boolean tvControlsVisible;
    private View tvTopBar;
    private View tvControlPanel;
    private View tvBtnPlayPause;
    private View tvBtnRewind;
    private View tvBtnForward;
    private View tvBtnBack;
    private ImageView tvPlayState;
    private ImageView tvPlayPauseIcon;
    private TextView tvPlayPauseLabel;
    private SeekBar tvSeekbar;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    /** 最近一次视频尺寸：用于按原始宽高比手动约束 PlayerView，避免竖屏/旋转视频被拉伸 */
    private VideoSize lastVideoSize = VideoSize.UNKNOWN;
    /** 电视端 SeekBar 由遥控器方向键改变时，节流后再执行 seek */
    private long pendingTvSeekMs;
    private boolean tvSeekbarTracking;

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

    /**
     * 播放进度仓库：restoreProgress/saveProgress 的 DB 读写（ui/read/data）
     */
    private final VideoProgressRepository progressRepository = new VideoProgressRepository();

    /**
     * 投屏进度轮询控制器：轮询 + 超时重试 + 进度缓存
     */
    private final CastPollingController castPollingController = new CastPollingController(this);

    /**
     * 手势处理组件：双击/长按/滑动 seek/亮度/音量/手势提示
     */
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
        if (isTv()) {
            // 明确使用按比例缩放，竖屏视频在横屏电视上应显示为左右黑边而不是铺满拉伸
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            player_view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }

        // 接收模式：监听 ExoPlayer 播放/暂停状态变化，同步传输状态供发送端轮询
        // （接收端通过自带控制器本地暂停时，发送端 GetTransportInfo 才能拿到 PAUSED_PLAYBACK）
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // 电视端接收模式也使用自定义控制台，需同步播放/暂停外观
                if (tvUi) {
                    updateTvPlayPauseUi();
                }
                if (tvUi) {
                    if (isPlaying) {
                        if (tvControlsVisible) {
                            mainHandler.removeCallbacks(tvHideRunnable);
                            mainHandler.postDelayed(tvHideRunnable, 6_000);
                        }
                    } else {
                        // 暂停时保持控制台，方便继续播放/拖动进度
                        showTvControls(false);
                    }
                }
                if (!receiverMode) {

                    return;
                }
                DlnaRendererManager.getInstance().onReceiverPlayStateChanged(isPlaying);
                WrzCastReceiverManager.getInstance().onReceiverPlayStateChanged(isPlaying);
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
                if (tvUi && playbackState == Player.STATE_READY) {
                    updateTvProgressUi();
                }
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                // 本地/接收模式：多音轨时显示顶栏音轨按钮（投屏模式由探测结果控制）
                updateLocalAudioTrackButton(tracks);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                lastVideoSize = videoSize;
                applyVideoSizeFit();
            }
        });

        // 投屏按钮与播放器控制器联动显示/隐藏（接收模式下不联动）
        player_view.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    tvControllerVisible = visibility == View.VISIBLE;
                    // 锁定时即便播放器控制器被外部唤起，也只保留锁定按钮
                    int contentVisibility = controlsLocked ? View.GONE : visibility;
                    tv_name.setVisibility(contentVisibility);
                    cast_button.setVisibility(contentVisibility);
                    audio_track_button.setVisibility(
                            localAudioSelectable ? contentVisibility : View.GONE);
                    download_button.setVisibility(
                            receiverMode ? contentVisibility : View.GONE);
                    if (!receiverMode) {
                        play_list.setVisibility(contentVisibility);
                    }
                    if (playerTopBar != null) {
                        playerTopBar.setVisibility(
                                controlsLocked ? View.GONE : visibility);
                    }
                    if (lock_button != null) {
                        lock_button.setVisibility(
                                controlsLocked ? View.VISIBLE : visibility);
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
                    int index = getComicIndex(comicId);

                    saveProgress();

                    getNextComicIndex(index, display);
                }
            }
        }
    }

    private void getNextComicIndex(int index, List<Comic> display) {
        int next = index + 1;
        if (next > 0 && next < display.size()) {
            if (FileUtils.isVideoFile(display.get(next).getPath()) || FileUtils.isMusicFile(display.get(next).getPath())) {
                switchToComic(display.get(next));
            } else {
                getNextComicIndex(next, display);
            }
        } else {
            // 当前是最后一项或未在列表中：回到列表开头
            switchToComic(display.get(0));
        }
    }

    /**
     * 从 DB 读取上次保存的播放位置并 seekTo（DB 读写见 VideoProgressRepository）
     */
    private void restoreProgress() {
        if (!FileUtils.isMusicFile(videoPath)) {
            progressRepository.restore(comicId, videoTitle, isFromRead, finalPos -> {
                if (player != null) {
                    player.seekTo(finalPos);
                }
            });
        }
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
     *
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
     *
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
     * 当前选中的投屏设备（DLNA）
     */
    private Device device;
    /**
     * WrzCast 发送端：私有协议连接与控制
     */
    private WrzCastSender wrzCastSender;
    /**
     * WrzCast 设备发现（打开设备弹窗时启动）
     */
    private WrzCastDiscovery wrzCastDiscovery;
    /**
     * 当前选中的投屏设备（WrzCast）
     */
    private WrzCastDevice wrzCastDevice;
    /**
     * 当前投屏是否经由 WrzCast 私有协议（false 表示 DLNA）
     */
    private boolean castViaWrzCast = false;
    private AlertDialog deviceDialog;
    private ArrayAdapter<String> deviceAdapter;
    /**
     * 设备列表条目：DLNA 与 WrzCast 设备统一展示
     */
    private final List<CastTarget> castTargets = new ArrayList<>();

    /**
     * 投屏目标：dlnaDevice 与 wrzDevice 二选一。
     */
    private static class CastTarget {
        final String name;
        final Device dlnaDevice;
        final WrzCastDevice wrzDevice;

        CastTarget(String name, Device dlnaDevice, WrzCastDevice wrzDevice) {
            this.name = name;
            this.dlnaDevice = dlnaDevice;
            this.wrzDevice = wrzDevice;
        }
    }

    // 投屏状态
    boolean isCasting = false;
    private boolean isCastPaused = false;

    /**
     * 最后一次轮询到的投屏进度，用于退出投屏时恢复本机播放位置
     */
    private long lastCastPositionMs = 0;

    private long castDurationMs = 0;

    // ===== 音轨选择状态 =====
    /**
     * 播放器侧（本地/接收）是否存在多条可播音轨（决定顶栏音轨按钮显隐）
     */
    private boolean localAudioSelectable = false;
    /**
     * 投屏模式：当前文件探测到的音轨列表（>1 条时非空，null 表示单音轨/未探测）
     */
    private List<AudioTrackUtil.TrackInfo> castAudioTracks;
    /**
     * 投屏模式：当前投出的音轨索引，-1 表示原文件默认音轨
     */
    private int castAudioTrackIndex = -1;
    /**
     * 投屏目标为重封装临时文件（onCastStarted 时跳过重新探测并保留音轨选择）
     */
    private boolean castingRemuxedFile = false;
    /**
     * 重封装进度弹窗
     */
    private AlertDialog remuxDialog;

    /**
     * 投屏成功：显示浮层、停止本机播放、禁用 ExoPlayer 控制器、开始轮询
     */
    @SuppressLint("SetTextI18n")
    private void onCastStarted(String deviceName) {
        isCasting = true;
        isCastPaused = false;
        castDurationMs = 0;
        lastCastPositionMs = 0;
        cast_device_name.setText(getString(R.string.msg_casting_label, deviceName));
        cast_title.setText(videoTitle);
        cast_current_time.setText("0:00");
        cast_duration.setText("0:00");
        cast_seekbar.setProgress(0);
        cast_play_pause.setImageResource(android.R.drawable.ic_media_pause);

        if (!receiverMode) {
            if (tvUi) {
                // 投屏时隐藏本机控制台，由投屏浮层接管
                tvControlsVisible = false;
                tvTopBar.setVisibility(View.GONE);
                tvControlPanel.setVisibility(View.GONE);
                mainHandler.removeCallbacks(tvProgressRunnable);
                mainHandler.removeCallbacks(tvHideRunnable);
            }
            cast_control_panel.setVisibility(View.VISIBLE);
            // 隐藏投屏按钮（投屏中不可切换设备，需先停止）
            cast_button.setVisibility(View.GONE);
            tv_name.setVisibility(View.GONE);
            play_list.setVisibility(View.GONE);
            audio_track_button.setVisibility(View.GONE);
            download_button.setVisibility(View.GONE);
            cast_audio_track.setVisibility(View.GONE);
            // 禁用 ExoPlayer 自带控制器，避免误操作本地播放（统一由浮层控制电视端）
            player_view.setUseController(false);
            // 电视端把焦点交给投屏浮层的播放按钮，方向键可继续在浮层内移动
            if (tvUi && cast_play_pause != null) {
                cast_play_pause.post(() -> cast_play_pause.requestFocus());
            }
            // 停止本机播放（保留位置，退出投屏时从电视端进度恢复）
            if (player != null) {
                player.pause();
            }
        }
        // 开始轮询播放进度
        castPollingController.stopPoll();
        castPollingController.stopPollTimeOut();
        castPollingController.startPoll();
        // 音轨入口：重投的临时文件沿用已有选择；普通投屏则探测原文件音轨
        if (castingRemuxedFile) {
            castingRemuxedFile = false;
            cast_audio_track.setVisibility(View.VISIBLE);
        } else if (!castViaWrzCast) {
            // WrzCast 由接收端直接拉取原文件播放，不涉及发送端音轨重封装
            probeCastAudioTracks();
        }
    }

    /**
     * 切换暂停/播放
     */
    private void togglePlayPause() {
        if (!isCasting) return;
        if (isCastPaused) {
            if (castViaWrzCast) {
                wrzCastSender.resumePlay(new WrzCastSender.SimpleCallback() {
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
            }
        } else {
            if (castViaWrzCast) {
                wrzCastSender.pause(new WrzCastSender.SimpleCallback() {
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
    }

    /**
     * 停止投屏，从电视端最后进度恢复本机播放
     */
    @Override
    public void stopCasting() {
        if (!isCasting) return;
        final long resumePos = lastCastPositionMs;
        if (castViaWrzCast) {
            wrzCastSender.stopCast();
        } else {
            dlnaManager.stopCast();
        }
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
        castViaWrzCast = false;
        wrzCastDevice = null;
        device = null;
        castDurationMs = 0;
        cast_control_panel.setVisibility(View.GONE);
        cast_audio_track.setVisibility(View.GONE);
        castAudioTracks = null;
        castAudioTrackIndex = -1;
        // 恢复投屏按钮
        cast_button.setVisibility(View.VISIBLE);
        tv_name.setVisibility(View.VISIBLE);
        // 下载按钮仅接收模式可用
        download_button.setVisibility(receiverMode ? View.VISIBLE : View.GONE);
        if (!receiverMode) {
            play_list.setVisibility(View.VISIBLE);
        }
        // 恢复 ExoPlayer 控制器（电视端保持关闭，继续使用自定义控制台）
        if (tvUi) {
            player_view.setUseController(false);
            showTvControls(true);
        } else {
            player_view.setUseController(true);
        }
        // 恢复本地音轨按钮显隐（多音轨时随控制器显示）
        if (player != null) {
            updateLocalAudioTrackButton(player.getCurrentTracks());
        }
    }


    /**
     * 弹出设备列表对话框（DLNA 与 WrzCast 设备统一展示）
     */
    private void showDeviceDialog() {
        ListView listView = new ListView(this);
        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(deviceAdapter);
        // 打开列表时主动触发一次搜索（DLNA + WrzCast）
        dlnaManager.startDiscovery();
        wrzCastDiscovery.start(wrzCastDeviceListener);
        refreshDeviceList();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < castTargets.size()) {
                CastTarget target = castTargets.get(position);
                device = target.dlnaDevice;
                wrzCastDevice = target.wrzDevice;
                castViaWrzCast = target.wrzDevice != null;
                deviceDialog.dismiss();
                Toast.makeText(this, getString(R.string.msg_casting_to, target.name), Toast.LENGTH_SHORT).show();
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
                    wrzCastDiscovery.stop();
                })
                .create();
        deviceDialog.show();
    }

    /**
     * DLNA 设备列表变化时刷新对话框
     */
    private final DlnaManager.DeviceListener deviceListener = () ->
            runOnUiThread(VideoPlayerActivity.this::refreshDeviceList);

    /**
     * WrzCast 设备列表变化时刷新对话框（回调已切回主线程）
     */
    private final WrzCastDiscovery.Listener wrzCastDeviceListener =
            () -> runOnUiThread(VideoPlayerActivity.this::refreshDeviceList);

    /**
     * 刷新设备列表与对话框提示文案
     */
    private void refreshDeviceList() {
        if (deviceAdapter == null || deviceDialog == null || !deviceDialog.isShowing()) {
            return;
        }
        castTargets.clear();
        for (Device d : dlnaManager.getDevices()) {
            castTargets.add(new CastTarget(getDeviceName(d), d, null));
        }
        for (WrzCastDevice d : wrzCastDiscovery.getDevices()) {
            castTargets.add(new CastTarget(
                    getString(R.string.msg_wrzcast_device_name, d.name), null, d));
        }
        List<String> names = new ArrayList<>();
        for (CastTarget target : castTargets) {
            names.add(target.name);
        }
        deviceAdapter.clear();
        deviceAdapter.addAll(names);
        deviceAdapter.notifyDataSetChanged();
        deviceDialog.setMessage(castTargets.isEmpty()
                ? getString(R.string.msg_cast_search_no_device)
                : getString(R.string.msg_cast_found_devices, castTargets.size()));
    }

    /**
     * 当前投屏目标的显示名称（DLNA 设备名或 WrzCast 设备名）。
     */
    private String getCurrentCastTargetName() {
        if (castViaWrzCast && wrzCastDevice != null) {
            return getString(R.string.msg_wrzcast_device_name, wrzCastDevice.name);
        }
        return getDeviceName(device);
    }

    /**
     * 获取设备显示名称
     */
    private static String getDeviceName(Device device) {
        if (device == null) {
            return "";
        }
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
     * DLNA 投屏结果回调
     */
    private final DlnaManager.CastListener dlnaCastListener = new DlnaManager.CastListener() {
        @Override
        public void onSuccess() {
            onCastSuccess();
        }

        @Override
        public void onFailure(String message) {
            onCastFailure(message);
        }
    };

    /**
     * WrzCast 投屏结果回调
     */
    private final WrzCastSender.CastListener wrzCastCastListener = new WrzCastSender.CastListener() {
        @Override
        public void onSuccess() {
            onCastSuccess();
        }

        @Override
        public void onFailure(String message) {
            onCastFailure(message);
        }
    };

    private void onCastSuccess() {
        runOnUiThread(() -> {
            Toast.makeText(VideoPlayerActivity.this,
                    getString(R.string.msg_cast_success), Toast.LENGTH_SHORT).show();
            onCastStarted(getCurrentCastTargetName());
            if (player != null && player.getCurrentPosition() > 0) {
                mainHandler.postDelayed(() ->
                        castSeek(player.getCurrentPosition()), 5000);
            }
        });
    }

    private void onCastFailure(String message) {
        runOnUiThread(() -> Toast.makeText(
                VideoPlayerActivity.this, getString(R.string.msg_cast_failed, message),
                Toast.LENGTH_LONG).show());
    }

    /**
     * 投屏到设备（本地视频）
     *
     * @param isChangeCast 是否改变了投屏设备，true:改变了投屏设备，false:还是原来的设备
     */
    @Override
    public void castToDevice(boolean isChangeCast) {
        long startPos = player != null ? player.getCurrentPosition() : 0;

        if (castViaWrzCast) {
            if (wrzCastDevice == null) {
                return;
            }
            wrzCastSender.cast(wrzCastDevice, new File(videoPath), videoTitle,
                    startPos, isChangeCast, wrzCastCastListener);
        } else {
            if (device == null) {
                return;
            }
            dlnaManager.cast(device, new File(videoPath), videoTitle,
                    startPos, isChangeCast, dlnaCastListener);
        }
    }

    /**
     * 投屏到设备（在线视频）
     *
     * @param isChangeCast 是否改变了投屏设备，true:改变了投屏设备，false:还是原来的设备
     */
    public void castUriToDevice(boolean isChangeCast) {
        long startPos = player != null ? player.getCurrentPosition() : 0;

        if (castViaWrzCast) {
            if (wrzCastDevice == null) {
                return;
            }
            wrzCastSender.cast(wrzCastDevice, receivedUri, videoTitle,
                    startPos, isChangeCast, wrzCastCastListener);
        } else {
            if (device == null) {
                return;
            }
            dlnaManager.cast(device, receivedUri, videoTitle,
                    startPos, isChangeCast, dlnaCastListener);
        }
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
    public void requestCastPlaybackInfo(DlnaManager.PlaybackInfoListener listener) {
        if (castViaWrzCast) {
            wrzCastSender.getPlaybackInfo(new WrzCastSender.PlaybackInfoListener() {
                @Override
                public void onResult(long positionMs, long durationMs, String transportState) {
                    listener.onResult(positionMs, durationMs, transportState);
                }

                @Override
                public void onFailure(String message) {
                    listener.onFailure(message);
                }
            });
        } else {
            dlnaManager.getPlaybackInfo(listener);
        }
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
        wrzCastDiscovery.stop();
        if (receiverMode) {
            // Activity 不可见时暂停进度上报，但保持播放（音频继续）与桥接注册
            mainHandler.removeCallbacks(receiverUpdateRunnable);
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tvUi) {
            mainHandler.removeCallbacks(tvProgressRunnable);
            mainHandler.removeCallbacks(tvHideRunnable);
            mainHandler.removeCallbacks(tvSeekRunnable);
            mainHandler.removeCallbacks(castSeekRunnable);
        }
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
        if (!tvUi && controlsLocked && lock_button != null) {
            // 锁屏状态下从后台返回时，防止播放器控制器重新露出
            if (playerTopBar != null) {
                playerTopBar.setVisibility(View.GONE);
            }
            lock_button.setVisibility(View.VISIBLE);
            player_view.hideController();
        }
        if (receiverMode) {
            return;
        }
        // 投屏中恢复轮询；未投屏则恢复本机播放
        if (isCasting) {
            castPollingController.startPoll();
        } else if (player != null) {
            player.setPlayWhenReady(true);
        }
        if (tvUi && !isCasting) {
            showTvControls(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(receiverUpdateRunnable);
        mainHandler.removeCallbacks(tvProgressRunnable);
        mainHandler.removeCallbacks(tvHideRunnable);
        mainHandler.removeCallbacks(tvSeekRunnable);
        mainHandler.removeCallbacks(castSeekRunnable);
        gestureHandler.removeHintCallback();

        // 关闭重封装进度弹窗，避免窗口泄漏
        if (remuxDialog != null && remuxDialog.isShowing()) {
            try {
                remuxDialog.dismiss();
            } catch (Exception e) {
                // 异步回调时窗口可能已失效，忽略
            }
        }
        remuxDialog = null;

        dlnaManager.release();
        wrzCastSender.release();
        wrzCastDiscovery.stop();

        if (receiverMode) {
            // 接收模式：通知管理器播放器已销毁，释放 ExoPlayer
            DlnaRendererManager.getInstance().onPlayerDestroyed();
            WrzCastReceiverManager.getInstance().onPlayerDestroyed();
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
            DlnaRendererManager.getInstance().reStart(MyApplication.app);
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


    /**
     * 播放器有自定义手势与控制器显隐动画，不绑定全局按压缩放反馈
     */
    @Override
    protected boolean isPressFeedbackEnabled() {
        return false;
    }

    /**
     * 初始化所有View组件
     */
    @Override
    public void initView() {
        tv_name = findViewById(R.id.tv_name);

        player_view = findViewById(R.id.player_view);
        playerTopBar = findViewById(R.id.player_top_bar);
        lock_button = findViewById(R.id.lock_button);
        if (lock_button != null) {
            updateLockUi();
            setClick(lock_button, v -> toggleLock());
        }
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
        audio_track_button = findViewById(R.id.audio_track_button);
        download_button = findViewById(R.id.download_button);
        setClick(download_button, v -> startDownload());
        cast_audio_track = findViewById(R.id.cast_audio_track);

        // 电视端自定义控制台：仅在 layout-land-television 中存在
        tvTopBar = findViewById(R.id.tv_top_bar);
        tvControlPanel = findViewById(R.id.tv_control_panel);
        tvBtnPlayPause = findViewById(R.id.tv_btn_play_pause);
        tvBtnRewind = findViewById(R.id.tv_btn_rewind);
        tvBtnForward = findViewById(R.id.tv_btn_forward);
        tvBtnBack = findViewById(R.id.tv_btn_back);
        tvPlayState = findViewById(R.id.tv_play_state);
        tvPlayPauseIcon = findViewById(R.id.tv_play_pause_icon);
        tvPlayPauseLabel = findViewById(R.id.tv_play_pause_label);
        tvSeekbar = findViewById(R.id.tv_seekbar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvDuration = findViewById(R.id.tv_duration);
        tvUi = isTv() && tvControlPanel != null;
        if (tvUi) {
            initTvController();
        }

        dlnaManager = DlnaManager.getInstance();
        wrzCastSender = new WrzCastSender();
        wrzCastDiscovery = new WrzCastDiscovery(this);

        tv_keep_watch = findViewById(R.id.tv_keep_watch);

        setClick(R.id.tv_keep_black, v -> {
            visible(R.id.view_blank);
            startBrightness = this.getWindow().getAttributes().screenBrightness;
            applyBrightness(0f);
        });
        setClick(R.id.view_blank, v -> {
            gone(R.id.view_blank);
            applyBrightness(startBrightness);
        });
    }

    private float startBrightness;

    private void applyBrightness(float b) {
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        lp.screenBrightness = b;
        this.getWindow().setAttributes(lp);
    }

    /** 锁定/解锁按钮位于右侧中部：锁定时只有该按钮可操作 */
    private void toggleLock() {
        if (tvUi || isCasting || lock_button == null) {
            return;
        }
        controlsLocked = !controlsLocked;
        updateLockUi();
        if (controlsLocked) {
            if (playerTopBar != null) {
                playerTopBar.setVisibility(View.GONE);
            }
            player_view.hideController();
            lock_button.setVisibility(View.VISIBLE);
        } else {
            if (playerTopBar != null) {
                playerTopBar.setVisibility(View.VISIBLE);
            }
            player_view.showController();
            lock_button.setVisibility(View.VISIBLE);
        }
    }

    private void updateLockUi() {
        if (lock_button == null) {
            return;
        }
        if (controlsLocked) {
            lock_button.setImageResource(R.drawable.ic_lock);
            lock_button.setContentDescription(getString(R.string.cd_unlock_screen));
        } else {
            lock_button.setImageResource(R.drawable.ic_lock_open);
            lock_button.setContentDescription(getString(R.string.cd_lock_screen));
        }
    }

    /** 供 GestureHandler 判断：锁定时吞掉所有手势，避免误触 */
    boolean isControlsLocked() {
        return controlsLocked;
    }

    // ==================== 电视端自定义控制台 ====================

    /**
     * 绑定遥控器控制台：播放/暂停、±10s、返回、进度条。
     * 控制台显隐与自动收起由 showTvControls / hideTvControls 管理。
     */
    private void initTvController() {
        tvControllerReady = true;
        setClick(tvBtnPlayPause, v -> {
            toggleTvPlayPause();
            showTvControls(false);
        });
        setClick(tvBtnRewind, v -> {
            seekBy(-10_000);
            showTvControls(false);
        });
        setClick(tvBtnForward, v -> {
            seekBy(10_000);
            showTvControls(false);
        });
        setClick(tvBtnBack, v -> goBack());
        tvSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                long duration = currentTvDurationMs();
                if (duration <= 0) {
                    return;
                }
                pendingTvSeekMs = progress * duration / seekBar.getMax();
                tvCurrentTime.setText(VideoUtils.formatTime(pendingTvSeekMs));
                // 遥控器连按方向键时，等按键稳定后再真正 seek，避免频繁 seek 卡顿
                mainHandler.removeCallbacks(tvSeekRunnable);
                mainHandler.postDelayed(tvSeekRunnable, 180);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                tvSeekbarTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                tvSeekbarTracking = false;
                mainHandler.removeCallbacks(tvSeekRunnable);
                applyTvSeek(pendingTvSeekMs);
            }
        });
        player_view.setUseController(false);
        updateTvPlayPauseUi();
        updateTvProgressUi();
        applyVideoSizeFit();
    }

    private final Runnable tvProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!tvUi || !tvControlsVisible || isCasting) {
                return;
            }
            updateTvProgressUi();
            mainHandler.postDelayed(this, 500);
        }
    };

    /**
     * 播放中 6 秒无操作自动收起控制台；暂停时保持可见，方便继续操作。
     */
    private final Runnable tvHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!tvUi || !tvControlsVisible || isCasting) {
                return;
            }
            if (player != null && player.isPlaying()) {
                hideTvControls();
            }
        }
    };

    private final Runnable tvSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvUi) {
                applyTvSeek(pendingTvSeekMs);
            }
        }
    };

    private void showTvControls(boolean focusPlayButton) {
        if (!tvUi || isCasting) {
            return;
        }
        tvControlsVisible = true;
        tvTopBar.setVisibility(View.VISIBLE);
        tvControlPanel.setVisibility(View.VISIBLE);
        syncTvDockVisibility();
        updateTvPlayPauseUi();
        updateTvProgressUi();

        mainHandler.removeCallbacks(tvProgressRunnable);
        mainHandler.post(tvProgressRunnable);
        mainHandler.removeCallbacks(tvHideRunnable);
        mainHandler.postDelayed(tvHideRunnable, 6_000);
        if (focusPlayButton && tvBtnPlayPause != null) {
            tvBtnPlayPause.requestFocus();
        }
    }

    private void hideTvControls() {
        if (!tvUi) {
            return;
        }
        tvControlsVisible = false;
        tvTopBar.setVisibility(View.GONE);
        tvControlPanel.setVisibility(View.GONE);
        mainHandler.removeCallbacks(tvProgressRunnable);
        mainHandler.removeCallbacks(tvHideRunnable);
        if (player_view != null) {
            player_view.requestFocus();
        }
    }

    /**
     * 控制台内按钮显隐：接收模式只保留下载，投屏中隐藏投屏入口，音轨仅在多轨时出现。
     */
    private void syncTvDockVisibility() {
        if (play_list != null) {
            play_list.setVisibility(receiverMode ? View.GONE : View.VISIBLE);
        }
        if (download_button != null) {
            download_button.setVisibility(receiverMode ? View.VISIBLE : View.GONE);
        }
        if (cast_button != null) {
            cast_button.setVisibility(isCasting ? View.GONE : View.VISIBLE);
        }
        if (audio_track_button != null) {
            audio_track_button.setVisibility(
                    localAudioSelectable && !isCasting ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleTvPlayPause() {
        if (isCasting) {
            togglePlayPause();
        } else if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.setPlayWhenReady(true);
            }
        }
        updateTvPlayPauseUi();
    }

    private void seekBy(long deltaMs) {
        if (isCasting) {
            long duration = castDurationMs;
            long position = castPollingController.cachedPositionMs;
            long target = Math.max(0, position + deltaMs);
            if (duration > 0) {
                target = Math.min(target, duration);
            }
            if (duration > 0) {
                castSeek(target);
            }
            return;
        }
        if (player == null) {
            return;
        }
        long target = Math.max(0, player.getCurrentPosition() + deltaMs);
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 0 && target > duration) {
            target = duration;
        }
        player.seekTo(target);
        updateTvProgressUi();
    }

    private void applyTvSeek(long targetMs) {
        long target = Math.max(0, targetMs);
        long duration = currentTvDurationMs();
        if (duration > 0) {
            target = Math.min(target, duration);
        }
        if (isCasting) {
            castSeek(target);
        } else if (player != null) {
            player.seekTo(target);
        }
        updateTvProgressUi();
    }

    private long currentTvDurationMs() {
        if (isCasting) {
            return Math.max(0, castDurationMs);
        }
        if (player == null) {
            return 0;
        }
        long duration = player.getDuration();
        return duration == C.TIME_UNSET || duration < 0 ? 0 : duration;
    }

    private long currentTvPositionMs() {
        if (isCasting) {
            return Math.max(0, castPollingController.cachedPositionMs);
        }
        return player != null ? Math.max(0, player.getCurrentPosition()) : 0;
    }

    private void updateTvProgressUi() {
        if (!tvUi || tvSeekbar == null) {
            return;
        }
        long position = currentTvPositionMs();
        long duration = currentTvDurationMs();
        if (tvCurrentTime != null) {
            tvCurrentTime.setText(VideoUtils.formatTime(position));
        }
        if (tvDuration != null) {
            tvDuration.setText(VideoUtils.formatTime(duration));
        }
        if (!tvSeekbarTracking && duration > 0) {
            int progress = (int) Math.min(1000, position * 1000 / duration);
            tvSeekbar.setProgress(progress);
        }
    }

    private void updateTvPlayPauseUi() {
        if (!tvUi) {
            return;
        }
        boolean playing = isCasting
                ? !isCastPaused
                : player != null && player.isPlaying();
        if (tvPlayPauseIcon != null) {
            tvPlayPauseIcon.setImageResource(playing
                    ? R.drawable.ic_tv_pause : R.drawable.ic_tv_play);
        }
        if (tvPlayPauseLabel != null) {
            tvPlayPauseLabel.setText(playing
                    ? R.string.tv_action_pause : R.string.tv_action_play);
        }
        if (tvPlayState != null) {
            tvPlayState.setImageResource(R.drawable.ic_tv_play);
            tvPlayState.setVisibility(playing ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * 按视频原始宽高比（含旋转元数据）约束 PlayerView 尺寸，居中显示。
     * 这样竖屏视频在横屏电视上会以左右黑边呈现，绝不会被拉伸铺满。
     */
    private void applyVideoSizeFit() {
        if (player_view == null || lastVideoSize == null || !isTv()) {
            return;
        }
        VideoSize size = lastVideoSize;
        if (size.width <= 0 || size.height <= 0) {
            return;
        }
        View parent = (View) player_view.getParent();
        if (parent == null) {
            return;
        }
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            parent.post(this::applyVideoSizeFit);
            return;
        }
        float ratio = (size.width * size.pixelWidthHeightRatio) / size.height;
        if (size.unappliedRotationDegrees == 90 || size.unappliedRotationDegrees == 270) {
            ratio = 1f / ratio;
        }
        if (ratio <= 0 || Float.isNaN(ratio) || Float.isInfinite(ratio)) {
            return;
        }
        int targetWidth;
        int targetHeight;
        if (parentWidth / (float) parentHeight > ratio) {
            targetHeight = parentHeight;
            targetWidth = Math.round(parentHeight * ratio);
        } else {
            targetWidth = parentWidth;
            targetHeight = Math.round(parentWidth / ratio);
        }
        ViewGroup.LayoutParams params = player_view.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) params;
        if (layoutParams.width == targetWidth && layoutParams.height == targetHeight
                && layoutParams.gravity == Gravity.CENTER) {
            return;
        }
        layoutParams.width = targetWidth;
        layoutParams.height = targetHeight;
        layoutParams.gravity = Gravity.CENTER;
        player_view.setLayoutParams(layoutParams);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 手机/平板旋转后重新按当前容器尺寸计算视频显示区域
        if (player_view != null) {
            player_view.post(this::applyVideoSizeFit);
        }
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

        // 音轨选择：顶栏按钮（本地/接收）+ 投屏浮层入口
        setupAudioTrackButton();

        if (receiverMode) {
            applyReceiverModeUi();
        } else {
            setupPlaylistButton();
        }

        if (tvUi) {
            // 电视端关闭默认触摸控制器，启动后短暂展示自定义控制台
            player_view.setUseController(false);
            showTvControls(false);
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
                    WrzCastReceiverManager.getInstance().onStop();
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
        download_button.setVisibility(View.VISIBLE);
        play_list.setVisibility(View.GONE);
        cast_control_panel.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.msg_cast_receiving), Toast.LENGTH_SHORT).show();
    }

    /**
     * 接收模式：把当前接收中的视频地址加入下载列表（支持暂停/恢复/断点续传）。
     * 非接收模式不显示下载按钮，也不会走到这里。
     */
    private void startDownload() {
        if (!receiverMode) {
            return;
        }
        final String url = receivedUri;
        if (url == null || !url.startsWith("http")) {
            Toast.makeText(this, getString(R.string.msg_download_no_source), Toast.LENGTH_SHORT).show();
            return;
        }
        VideoDownloadManager.getInstance().enqueue(url, videoTitle, (result, task) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            int msgRes;
            if (result == VideoDownloadManager.ENQUEUE_ADDED) {
                msgRes = R.string.msg_download_added;
            } else if (result == VideoDownloadManager.ENQUEUE_EXISTS) {
                if (task != null && task.getStatus() == DownloadTask.STATUS_COMPLETED) {
                    msgRes = R.string.msg_download_completed;
                } else if (task != null && (task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                        || task.getStatus() == DownloadTask.STATUS_PENDING)) {
                    msgRes = R.string.msg_download_in_progress;
                } else {
                    msgRes = R.string.msg_download_exists;
                }
            } else {
                msgRes = R.string.msg_download_failed_retry;
            }
            Toast.makeText(this, getString(msgRes), Toast.LENGTH_SHORT).show();
        });
    }

    // =========== 普通模式（按钮事件 ===========

    /**
     * 绑定播放列表按钮，点击后弹出列表对话框，切换视频并自动投屏。
     */
    private void setupPlaylistButton() {
        setClick(play_list, v -> {
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

    // =========== 音轨选择（本地/接收：播放器切轨；投屏：重封装后重投） ===========

    /**
     * 绑定音轨入口点击：顶栏按钮走播放器切轨，投屏浮层入口走重封装重投
     */
    private void setupAudioTrackButton() {
        setClick(audio_track_button, v -> showPlayerAudioTrackDialog());
        setClick(cast_audio_track, v -> showCastAudioTrackDialog());
    }

    /**
     * 播放器 onTracksChanged 回调：多音轨时允许顶栏按钮随控制器显示（投屏中隐藏）
     */
    private void updateLocalAudioTrackButton(Tracks tracks) {
        localAudioSelectable = countSelectableAudioTracks(tracks) > 1;
        audio_track_button.setVisibility(
                localAudioSelectable && !isCasting ? View.VISIBLE : View.GONE);
    }

    /**
     * 统计可播放的音频轨道数
     */
    private static int countSelectableAudioTracks(Tracks tracks) {
        if (tracks == null) {
            return 0;
        }
        int count = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSupported(i)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 本地/接收模式：列出播放器音轨供选择（TrackSelectionOverride 即时生效）
     */
    private void showPlayerAudioTrackDialog() {
        if (player == null) {
            return;
        }
        Tracks tracks = player.getCurrentTracks();
        List<String> labels = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();
        labels.add(getString(R.string.audio_track_auto));
        overrides.add(null);
        int checked = 0;
        int seq = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                seq++;
                Format format = group.getTrackFormat(i);
                String label;
                if (format.label != null && !format.label.isEmpty()) {
                    label = format.label;
                } else if (format.language != null && !format.language.isEmpty()) {
                    label = getString(R.string.audio_track_with_lang, seq, format.language);
                } else {
                    label = getString(R.string.audio_track_number, seq);
                }
                labels.add(label);
                overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                if (group.isTrackSelected(i)) {
                    checked = labels.size() - 1;
                }
            }
        }
        if (labels.size() <= 2) {
            // 自动 + 单音轨：无需选择
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_select_audio_track))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    TrackSelectionParameters.Builder pb =
                            player.getTrackSelectionParameters().buildUpon();
                    pb.clearOverridesOfType(C.TRACK_TYPE_AUDIO);
                    TrackSelectionOverride override = overrides.get(which);
                    if (override != null) {
                        pb.addOverride(override);
                    }
                    player.setTrackSelectionParameters(pb.build());
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /**
     * 投屏模式：后台探测当前文件音轨，多于一条时在浮层显示"音轨"入口
     */
    private void probeCastAudioTracks() {
        castAudioTracks = null;
        cast_audio_track.setVisibility(View.GONE);
        // WrzCast 接收端直接拉取原文件播放，音轨重封装仅适用于 DLNA
        if (castViaWrzCast || videoPath == null) {
            return;
        }
        final String path = videoPath;
        threadPool.execute(() -> {
            List<AudioTrackUtil.TrackInfo> tracks = AudioTrackUtil.getAudioTracks(path);
            runOnUiIfAlive(() -> {
                // 期间可能已停止投屏或切换视频，结果作废
                if (!isCasting || !path.equals(videoPath)) {
                    return;
                }
                if (tracks.size() > 1) {
                    castAudioTracks = tracks;
                    cast_audio_track.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    /**
     * 投屏模式音轨选择：默认投原文件；指定音轨先重封装为单音轨临时文件再重投
     */
    private void showCastAudioTrackDialog() {
        if (castAudioTracks == null || castAudioTracks.isEmpty()) {
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.audio_track_default));
        for (int i = 0; i < castAudioTracks.size(); i++) {
            AudioTrackUtil.TrackInfo track = castAudioTracks.get(i);
            String display = (track.language != null && !track.language.isEmpty())
                    ? getString(R.string.audio_track_with_lang, i + 1, track.language)
                    : getString(R.string.audio_track_number, i + 1);
            labels.add(display);
        }
        int checked = castAudioTrackIndex + 1; // -1（默认）对应下标 0
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_select_audio_track))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    handleCastAudioSelection(which - 1);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /**
     * 应用投屏音轨选择。
     *
     * @param trackIndex -1=默认（直接重投原文件）；>=0=重封装该音轨后重投
     */
    private void handleCastAudioSelection(int trackIndex) {
        if (trackIndex == castAudioTrackIndex || device == null) {
            return;
        }
        if (trackIndex == -1) {
            castAudioTrackIndex = -1;
            castingRemuxedFile = false;
            recastToDevice(new File(videoPath));
            return;
        }
        final String srcPath = videoPath;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        remuxDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_processing_audio))
                .setMessage(getString(R.string.msg_remux_preparing, 0))
                .setNegativeButton(getString(R.string.btn_cancel), (d, w) -> cancelled.set(true))
                .setCancelable(true)
                .show();
        remuxDialog.setOnCancelListener(d -> cancelled.set(true));
        threadPool.execute(() -> {
            File outFile = AudioTrackUtil.getRemuxCacheFile(this, srcPath, trackIndex);
            boolean ok = outFile.length() > 0
                    || AudioTrackUtil.remuxWithAudioTrack(srcPath, trackIndex,
                    outFile.getAbsolutePath(), percent -> {
                        runOnUiIfAlive(() -> {
                            if (remuxDialog != null && remuxDialog.isShowing()) {
                                remuxDialog.setMessage(getString(R.string.msg_remux_preparing, percent));
                            }
                        });
                        return !cancelled.get();
                    });
            runOnUiIfAlive(() -> {
                if (remuxDialog != null) {
                    remuxDialog.dismiss();
                    remuxDialog = null;
                }
                if (!ok) {
                    if (!cancelled.get()) {
                        // 编码不兼容等原因失败：电视端保持原文件继续播放
                        Toast.makeText(this, getString(R.string.msg_remux_failed),
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                if (!isCasting || !srcPath.equals(videoPath)) {
                    // 期间已停止投屏或切换视频：缓存作废
                    AudioTrackUtil.clearRemuxCacheExcept(this, null);
                    return;
                }
                castAudioTrackIndex = trackIndex;
                // 只保留当前选中音轨的缓存，控制存储占用
                AudioTrackUtil.clearRemuxCacheExcept(this, outFile);
                castingRemuxedFile = true;
                recastToDevice(outFile);
            });
        });
    }

    /**
     * 用指定文件重投当前设备（换音轨），从电视端最后进度继续
     */
    private void recastToDevice(File videoFile) {
        final Device target = device;
        dlnaManager.cast(target, videoFile, videoTitle, lastCastPositionMs, true,
                new DlnaManager.CastListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(VideoPlayerActivity.this,
                                    getString(R.string.msg_recast_audio_success),
                                    Toast.LENGTH_SHORT).show();
                            onCastStarted(getDeviceName(target));
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> {
                            castingRemuxedFile = false;
                            Toast.makeText(VideoPlayerActivity.this,
                                    getString(R.string.msg_recast_audio_failed, message),
                                    Toast.LENGTH_LONG).show();
                            // 原投屏已被 Stop 打断，恢复本机播放兜底
                            hideCastOverlay();
                        });
                    }
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
        clearPlayerAudioOverride();
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
     *
     * @param parentId  二级播放目录 父母Id
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
        clearPlayerAudioOverride();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);

    }

    /**
     * 切换视频时清掉上一部视频的音轨强制选择，恢复自动选轨
     */
    private void clearPlayerAudioOverride() {
        if (player != null) {
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .build());
        }
    }

    // =========== 公共事件 ===========

    /**
     * 绑定投屏相关按钮的点击事件：投屏按钮、返回观看、暂停/播放、停止投屏。
     */
    private void setupCastControlListeners() {
        setClick(cast_button, v -> {
            if (isCasting) {
                cast_control_panel.setVisibility(View.VISIBLE);
                cast_button.setVisibility(View.GONE);
                tv_name.setVisibility(View.GONE);
                play_list.setVisibility(View.GONE);
                if (tvUi) {
                    tvTopBar.setVisibility(View.GONE);
                    tvControlPanel.setVisibility(View.GONE);
                }
                player_view.setUseController(false);
            } else {
                showDeviceDialog();
            }
        });
        setClick(tv_keep_watch, v -> {
            if (tvUi) {
                player_view.setUseController(false);
                showTvControls(true);
            } else {
                player_view.setUseController(true);
            }
            cast_control_panel.setVisibility(View.GONE);
            cast_button.setVisibility(View.VISIBLE);
        });
        setClick(cast_play_pause, v -> togglePlayPause());
        setClick(cast_stop, v -> stopCasting());
    }

    /**
     * 用户正在拖动 SeekBar 时不更新进度（避免抖动）
     */
    private boolean isUserSeeking = false;

    /** 投屏进度条由遥控器方向键改变时的待执行 seek */
    private long pendingCastSeekMs;

    private final Runnable castSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (castDurationMs > 0) {
                castSeek(pendingCastSeekMs);
            }
        }
    };

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
                    // 遥控器方向键不会触发 onStopTrackingTouch，需节流后主动 seek
                    if (!isUserSeeking) {
                        pendingCastSeekMs = ms;
                        mainHandler.removeCallbacks(castSeekRunnable);
                        mainHandler.postDelayed(castSeekRunnable, 180);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                mainHandler.removeCallbacks(castSeekRunnable);
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
        if (castViaWrzCast) {
            wrzCastSender.seek(targetMs, new WrzCastSender.SimpleCallback() {
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
            return;
        }
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
        // 投屏中按返回键先停止投屏（不退出 Activity），再次按返回才退出
        if (isCasting) {
            stopCasting();
            return;
        }
        if (isTv()) {
            if (tvTopBar.getVisibility() == View.VISIBLE || tvControlPanel.getVisibility() == View.VISIBLE) {
                gone(R.id.tv_top_bar);
                gone(R.id.tv_control_panel);
                tvControllerVisible = false;
                return;
            }
        } else {
            if (tvControllerVisible) {
                player_view.hideController();

                tvControllerVisible = false;
                return;
            }
        }
        super.goBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN /*&& !receiverMode*/) {
            boolean handled = tvUi
                    ? handleTvKeyUi(event.getKeyCode())
                    : handleTvKey(event.getKeyCode());
            if (handled) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 电视端按键策略：
     * <ul>
     *   <li>控制台收起：OK 播放/暂停，← → 快退/快进 10s，↑ ↓ 呼出控制台</li>
     *   <li>控制台展开：方向键在块状按钮/进度条间移动焦点，OK 激活焦点项</li>
     *   <li>进度条聚焦时左右键按 SeekBar 原生逻辑调整进度，节流后真正 seek</li>
     * </ul>
     */
    private boolean handleTvKeyUi(int keyCode) {
        if ((deviceDialog != null && deviceDialog.isShowing())
                || (remuxDialog != null && remuxDialog.isShowing())) {
            return false;
        }

        // 投屏浮层内的按钮自行处理方向键与 OK；媒体键仍保留快速进退能力
        if (isCasting) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    toggleTvPlayPause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-10_000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(10_000);
                    return true;
                default:
                    return false;
            }
        }

        if (!tvControlsVisible) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    toggleTvPlayPause();
                    showTvControls(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-10_000);
                    showTvControls(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(10_000);
                    showTvControls(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showTvControls(true);
                    return true;
                default:
                    return false;
            }
        }

        View focus = getCurrentFocus();
        boolean focusInPanel = focus != null && tvControlPanel != null
                && isDescendant(tvControlPanel, focus);

        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                toggleTvPlayPause();
                showTvControls(false);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-10_000);
                showTvControls(false);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(10_000);
                showTvControls(false);
                return true;
            default:
                break;
        }

        if (focusInPanel) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // 从按钮行向上进入进度条
                if (!(focus instanceof SeekBar)) {
                    tvSeekbar.requestFocus();
                    return true;
                }
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 进度条上向下回到播放键；按钮行向下收起控制台
                if (focus instanceof SeekBar) {
                    tvBtnPlayPause.requestFocus();
                } else {
                    hideTvControls();
                }
                return true;
            }
            // 其余按键交给焦点系统与具体控件（OK 点击、左右移动焦点、SeekBar 调整进度）
            return false;
        }

        // 焦点在播放画面上时，方向键仍按全局快捷键处理
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                toggleTvPlayPause();
                showTvControls(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-10_000);
                showTvControls(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(10_000);
                showTvControls(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                showTvControls(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                hideTvControls();
                return true;
            default:
                return false;
        }
    }

    private boolean isDescendant(View root, View child) {
        View current = child;
        while (current != null) {
            if (current == root) {
                return true;
            }
            if (!(current.getParent() instanceof View)) {
                return false;
            }
            current = (View) current.getParent();
        }
        return false;
    }

    /** 电视遥控器按键：OK 播放/暂停，左右快退/快进 10s，上下显示/隐藏控制栏。 */
    private boolean handleTvKey(int keyCode) {
        if ((deviceDialog != null && deviceDialog.isShowing())
                || (remuxDialog != null && remuxDialog.isShowing())) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (isCasting) {
                    togglePlayPause();
                    return true;
                }
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.setPlayWhenReady(true);
                    }
                    player_view.showController();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isCasting) {
                    long target = lastCastPositionMs
                            + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 10000 : -10000);
                    if (target < 0) {
                        target = 0;
                    }
                    if (castDurationMs > 0 && target > castDurationMs) {
                        target = castDurationMs;
                    }
                    castSeek(target);
                    return true;
                }
                if (player != null) {
                    long duration = player.getDuration();
                    long target = player.getCurrentPosition()
                            + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 10000 : -10000);
                    if (target < 0) {
                        target = 0;
                    }
                    if (duration != C.TIME_UNSET && target > duration) {
                        target = duration;
                    }
                    player.seekTo(target);
                    player_view.showController();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (player_view != null && cast_control_panel.getVisibility() == View.GONE) {
                    if (tvControllerVisible) {
                        player_view.hideController();
                        tvControllerVisible = false;
                    } else {
                        player_view.showController();
                        tvControllerVisible = true;
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return handleTvKey(KeyEvent.KEYCODE_DPAD_LEFT);
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return handleTvKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            default:
                return false;
        }
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
                                    WrzCastReceiverManager.getInstance().setCurrentCasterName(casterName);
                                    playNewCast(uri, title);
                                })
                                .setNegativeButton(getString(R.string.refuse), (d, w) -> {
                                    DlnaRendererManager.getInstance().onStop();
                                    WrzCastReceiverManager.getInstance().onStop();
                                    finish();
                                })
                                .setCancelable(false)
                                .show();
                    } else {
                        DlnaRendererManager.getInstance().setCurrentCasterName(casterName);
                        WrzCastReceiverManager.getInstance().setCurrentCasterName(casterName);
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
        WrzCastReceiverManager.getInstance().registerBridge(playerBridge);
        // 同步当前传输状态：若控制点在 Activity 启动前已发 Pause，则暂停播放
        String dlnaState = DlnaRendererManager.getInstance().getTransportState();
        String wrzState = WrzCastReceiverManager.getInstance().getTransportState();
        if (player != null
                && ("PAUSED_PLAYBACK".equals(dlnaState) || "PAUSED_PLAYBACK".equals(wrzState))) {
            player.setPlayWhenReady(false);
        }
        mainHandler.removeCallbacks(receiverUpdateRunnable);
        mainHandler.post(receiverUpdateRunnable);
    }

    /**
     * 接收并播放新的视频
     *
     * @param uri   视频链接
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
        clearPlayerAudioOverride();
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
