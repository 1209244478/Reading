package com.wrz.reading.ui.read.utils;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import androidx.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 音轨探测与重封装工具（用于"投屏后选音频"）。
 *
 * <p>背景：DLNA AVTransport 协议没有音轨选择命令，电视端自行解析容器并播放
 * 默认音轨。要在投屏时换音轨，只能改变推给电视的媒体内容本身——本工具用
 * {@link MediaExtractor}+{@link MediaMuxer} 把"视频轨+指定音轨"复制为单音轨
 * MP4 临时文件（不重编码，纯样本复制，I/O 级速度），再由 DlnaManager 把该
 * 临时文件投给电视。
 *
 * <p>编码兼容性：MP4 封装仅支持 H.264/H.265 视频与 AAC 音频；其余编码
 * （DTS/AC3/FLAC、VP9 等）{@link #remuxWithAudioTrack} 返回 false，
 * 由调用方回退为投原文件并提示。
 */
public final class AudioTrackUtil {

    /** 容器内一条音轨的信息 */
    public static class TrackInfo {
        /** MediaExtractor 中的音轨 trackIndex */
        public final int index;
        /** 语言码（如 eng/jpn），可能为 null */
        @Nullable
        public final String language;
        /** 音轨 MIME（如 audio/mp4a-latm） */
        @Nullable
        public final String mime;

        TrackInfo(int index, @Nullable String language, @Nullable String mime) {
            this.index = index;
            this.language = language;
            this.mime = mime;
        }
    }

    /** 重封装进度回调（0-100 整数百分比）；返回 false 表示用户取消，处理立即中止并清理半成品 */
    public interface ProgressListener {
        boolean onProgress(int percent);
    }

    private AudioTrackUtil() {
    }

    /** 列出容器内所有音轨；解析失败或无音轨返回空列表 */
    public static List<TrackInfo> getAudioTracks(String path) {
        List<TrackInfo> result = new ArrayList<>();
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME)
                        ? format.getString(MediaFormat.KEY_MIME) : null;
                if (mime != null && mime.startsWith("audio/")) {
                    String language = format.containsKey(MediaFormat.KEY_LANGUAGE)
                            ? format.getString(MediaFormat.KEY_LANGUAGE) : null;
                    result.add(new TrackInfo(i, language, mime));
                }
            }
        } catch (Exception e) {
            // 文件损坏或格式不支持：视为无音轨信息
        } finally {
            try {
                extractor.release();
            } catch (Exception ignore) {
            }
        }
        return result;
    }

    /** 视频/音频编码是否可复制进 MP4 封装（不重编码） */
    private static boolean isRemuxableMime(@Nullable String mime) {
        if (mime == null) {
            return false;
        }
        return MediaFormat.MIMETYPE_VIDEO_AVC.equals(mime)       // H.264
                || mime.startsWith("video/hev")                  // H.265/HEVC（hevc/hev1）
                || MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime);  // AAC
    }

    /**
     * 重封装：仅保留视频轨与指定音轨，输出单音轨 MP4。阻塞方法，须在后台线程调用。
     *
     * @return true 成功；false 失败或被取消（半成品文件已删除）
     */
    public static boolean remuxWithAudioTrack(String srcPath, int audioTrackIndex,
                                              String outPath, @Nullable ProgressListener listener) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean success = false;
        int lastPercent = -1;
        try {
            extractor.setDataSource(srcPath);
            int videoTrack = -1;
            MediaFormat videoFormat = null;
            MediaFormat audioFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME)
                        ? format.getString(MediaFormat.KEY_MIME) : null;
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/")) {
                    if (videoTrack == -1 && isRemuxableMime(mime)) {
                        videoTrack = i;
                        videoFormat = format;
                    }
                } else if (i == audioTrackIndex) {
                    if (!isRemuxableMime(mime)) {
                        return false;
                    }
                    audioFormat = format;
                }
            }
            if (videoTrack == -1 || audioFormat == null) {
                return false;
            }
            extractor.selectTrack(videoTrack);
            extractor.selectTrack(audioTrackIndex);

            long durationUs = videoFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? videoFormat.getLong(MediaFormat.KEY_DURATION) : 0L;

            muxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoOut = muxer.addTrack(videoFormat);
            int audioOut = muxer.addTrack(audioFormat);
            muxer.start();

            int maxInputSize = 1024 * 1024;
            if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxInputSize = Math.max(maxInputSize, videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            ByteBuffer buffer = ByteBuffer.allocate(maxInputSize);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }
                int srcTrack = extractor.getSampleTrackIndex();
                int dstTrack = srcTrack == videoTrack ? videoOut : audioOut;
                info.set(0, sampleSize, extractor.getSampleTime(), extractor.getSampleFlags());
                muxer.writeSampleData(dstTrack, buffer, info);
                extractor.advance();
                if (listener != null && durationUs > 0) {
                    int percent = (int) (extractor.getSampleTime() * 100 / durationUs);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        if (!listener.onProgress(percent)) {
                            return false; // 用户取消
                        }
                    }
                }
            }
            muxer.stop();
            success = true;
            return true;
        } catch (Exception e) {
            // 编码/封装不兼容等异常：按失败处理
            return false;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignore) {
            }
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignore) {
                }
            }
            if (!success) {
                // 清理半成品，避免脏缓存被复用
                new File(outPath).delete();
            }
        }
    }

    /** 重封装输出缓存文件：cacheDir/cast_remux/&lt;hash&gt;.mp4（同源文件同音轨复用） */
    public static File getRemuxCacheFile(Context context, String srcPath, int audioTrackIndex) {
        File dir = new File(context.getCacheDir(), "cast_remux");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String key = Integer.toHexString((srcPath + "#" + audioTrackIndex).hashCode());
        return new File(dir, key + ".mp4");
    }

    /** 清理重封装缓存目录中除 keep 外的所有文件（控制缓存体积） */
    public static void clearRemuxCacheExcept(Context context, @Nullable File keep) {
        File dir = new File(context.getCacheDir(), "cast_remux");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.equals(keep)) {
                file.delete();
            }
        }
    }
}
