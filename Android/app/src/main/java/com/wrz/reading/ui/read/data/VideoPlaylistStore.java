package com.wrz.reading.ui.read.data;

import androidx.annotation.Nullable;

import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.ReadItem;

import java.util.Collections;
import java.util.List;

/**
 * 视频播放列表状态源：解耦 VideoPlayerActivity 与列表页（ReaderActivity / CollectionDetailFragment）
 * 之间原本通过 public static 字段（ReaderActivity.imageFiles、CollectionDetailFragment.displayComics、
 * BaseReaderActivity.comic）形成的跨组件耦合。
 *
 * 约定：
 * - 列表页在启动 VideoPlayerActivity 前调用 {@link #set(PlaylistProvider)} 注册；
 * - 列表页销毁时调用 {@link #clear(PlaylistProvider)}（带身份校验，避免误清其他页注册的来源）；
 * - 播放器侧所有读取均需判空，provider 缺失时按“无连播列表”降级走 DB 路径。
 */
public final class VideoPlaylistStore {

    /**
     * 播放列表来源。Reader 场景提供阅读目录的媒体子文件与宿主 Comic（子进度键）；
     * Collection 场景提供当前显示的漫画列表与进度回写。
     */
    public interface PlaylistProvider {

        /** Reader 场景：当前阅读目录的媒体子文件（用于视频连播），无则返回 null */
        @Nullable
        List<ReadItem> getSubFiles();

        /** Reader 场景：宿主阅读器当前的 Comic（用于 SubProgress 子进度键），无则返回 null */
        @Nullable
        Comic getReaderComic();

        /** Collection 场景：当前显示的漫画列表（连播/播放列表），无则返回空列表 */
        List<Comic> getDisplayComics();

        /** Collection 场景：回写指定漫画的视频进度（找不到对应项时静默忽略） */
        void updateVideoPosition(long comicId, long position);
    }

    private static volatile PlaylistProvider provider;

    public static void set(PlaylistProvider source) {
        provider = source;
    }

    /** 带身份校验的注销：仅当当前注册者与调用者一致时才清除 */
    public static void clear(PlaylistProvider source) {
        if (provider == source) {
            provider = null;
        }
    }

    /** 获取当前来源，可能为 null（接收模式或宿主已销毁），调用方必须判空 */
    @Nullable
    public static PlaylistProvider get() {
        return provider;
    }

    private VideoPlaylistStore() {
    }

    /** 空实现：供仅支持单一场景的宿主复用 */
    public static PlaylistProvider empty() {
        return new PlaylistProvider() {
            @Nullable
            @Override
            public List<ReadItem> getSubFiles() {
                return null;
            }

            @Nullable
            @Override
            public Comic getReaderComic() {
                return null;
            }

            @Override
            public List<Comic> getDisplayComics() {
                return Collections.emptyList();
            }

            @Override
            public void updateVideoPosition(long comicId, long position) {
            }
        };
    }
}
