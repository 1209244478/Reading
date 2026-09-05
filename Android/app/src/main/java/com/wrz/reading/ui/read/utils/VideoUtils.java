package com.wrz.reading.ui.read.utils;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.wrz.reading.ui.main.Log.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视频工具类：提取非首帧作为封面（避免首帧纯黑），并保存为 JPEG 文件。
 * 所有耗时操作均通过 {@link #runOnIoThread(Runnable)} 在单线程后台执行。
 */
public final class VideoUtils {

    private static final String TAG = "VideoUtils";
    /** 取帧时间点：1 秒（微秒），避开多数视频开头黑场 */
    private static final long FRAME_AT_TIME_US = 10_000_000L;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VideoUtils-IO");
        t.setDaemon(true);
        return t;
    });

    private VideoUtils() {
    }

    /**
     * 提取视频非首帧作为封面。优先取 1 秒处关键帧，失败则取中段帧，最后兜底首帧。
     *
     * @param videoFile 视频文件
     * @return 缩略图 Bitmap，失败返回 null
     */
    public static Bitmap extractThumbnail(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            return null;
        }
        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(videoFile.getAbsolutePath());
            Bitmap bmp = mmr.getFrameAtTime(FRAME_AT_TIME_US,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp == null) {
                long durationMs = parseDurationMs(mmr);
                if (durationMs > 0) {
                    bmp = mmr.getFrameAtTime((durationMs / 2) * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
            }
            if (bmp == null) {
                bmp = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            return bmp;
        } catch (Exception e) {
            LogUtil.e(TAG, "extractThumbnail failed: " + videoFile.getName(), e);
            return null;
        } finally {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long parseDurationMs(MediaMetadataRetriever mmr) {
        try {
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null && !d.isEmpty()) {
                return Long.parseLong(d);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    /**
     * 将缩略图保存为 JPEG 文件到漫画目录。
     *
     * @return 文件绝对路径；失败返回 null
     */
    public static String saveThumbnail(Bitmap bmp, String title) {
        if (bmp == null) {
            return null;
        }
        File dir = CompressionUtil.getComicsDirectory();
        if (dir == null) {
            return null;
        }
        File file = new File(dir, "." + sanitize(title) + "_video.jpg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                return file.getAbsolutePath();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "saveThumbnail failed", e);
        }
        return null;
    }

    /**
     * 提取并保存视频缩略图。供导入时同步调用（应在后台线程执行）。
     *
     * @return 缩略图文件绝对路径；失败返回空串
     */
    public static String generateThumbnailFile(File videoFile, String title) {
        Bitmap bmp = extractThumbnail(videoFile);
        String path = saveThumbnail(bmp, title);
        return path != null ? path : "";
    }

    /**
     * 在后台 IO 线程执行任务。
     */
    public static void runOnIoThread(Runnable runnable) {
        IO.execute(runnable);
    }

    /**
     * 将毫秒格式化为可读时间：不足 1 小时显示 "M:SS"，超过则 "H:MM:SS"。
     */
    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return "video";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
