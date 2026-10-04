package com.wrz.reading.ui.read.data;

import android.os.Handler;
import android.os.Looper;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.utils.ExecutorManager;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.SubProgress;

import java.util.concurrent.ExecutorService;

/**
 * 视频播放进度仓库：负责播放进度的本地持久化读写（从 VideoPlayerActivity 机械拆分，行为不变）。
 * <ul>
 *   <li>{@link #restore}：后台读 DB（Comic + 合集 SubProgress），算出恢复位置后在主线程回调 seekTo；</li>
 *   <li>{@link #save}：后台写 DB 并回写内存中的合集播放进度（投屏中用 castDurationMs，基本播完则归零）。</li>
 * </ul>
 */
public class VideoProgressRepository {

    /**
     * 后台 IO 执行器：加载/持久化播放进度。
     * 共享 ExecutorManager 单线程，与 DB 操作天然串行。
     */
    private final ExecutorService ioExecutor = ExecutorManager.getInstance().getSingleThreadExecutor();

    // 统一主线程 Handler：恢复进度时回调 seekTo
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 恢复进度回调：在主线程执行，宿主在此执行 player.seekTo
     */
    public interface SeekCallback {
        void onSeekTo(long positionMs);
    }

    /**
     * 从 DB 读取上次保存的播放位置，算出 finalPos 后在主线程回调 seekTo
     */
    public void restore(long comicId, String videoTitle, boolean isFromRead, SeekCallback onSeekTo) {
        if (comicId == -1L) return;
        ioExecutor.execute(() -> {
            Comic loaded = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (loaded == null/* || loaded.getFileType().equals(FileType.MUSIC.getCode())*/)
                return;

            long pos = -1;
            long total = -1;
            VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
            Comic hostComic = source != null ? source.getReaderComic() : null;
            if (hostComic != null) {
                pos = SubProgress.getSubProgress(videoTitle, hostComic.getSubProgress());
                total = SubProgress.getSubProgress(videoTitle + "_total", hostComic.getSubProgress());
            }


            if (!isFromRead || pos == -1 || total == -1) {
                pos = loaded.getVideoPosition();
                total = loaded.getTotal();
            }

            if (pos > 0 && pos < total - 3000L) {
                long finalPos = pos;
                mainHandler.post(() -> onSeekTo.onSeekTo(finalPos));
            }
        });
    }

    /**
     * 保存当前播放进度到 DB（投屏中不保存本地进度，用 castDurationMs 分支）
     */
    public void save(long comicId, String videoTitle, long currentPosition, long durationMs,
                     boolean isFromRead, boolean isCasting, long castDurationMs) {
        final long current = currentPosition;
        final long[] duration = {durationMs};
        ioExecutor.execute(() -> {
            Comic comic = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (comic != null) {
                long pos;
                if (isCasting) {
                    pos = castDurationMs;
                } else {
                    pos = current;
                }

                if (duration[0] <= 0) {
                    duration[0] = pos;
                }
                // 已基本播放完毕（剩余 < 3 秒）则重置为 0，避免下次恢复到结尾定格
                if (duration[0] > 0 && pos >= duration[0] - 3000L) {
                    pos = 0L;
                }

                // 回写内存中的播放进度（合集列表项），供返回列表页时差量刷新
                VideoPlaylistStore.PlaylistProvider source = VideoPlaylistStore.get();
                if (source != null) {
                    source.updateVideoPosition(comicId, pos);
                }

                if (isFromRead) {
                    Comic hostComic = source != null ? source.getReaderComic() : null;
                    if (hostComic != null) {
                        SubProgress.updateSubProgress(videoTitle, pos, hostComic.getSubProgress());
                        SubProgress.updateSubProgress(videoTitle + "_total", duration[0], hostComic.getSubProgress());
                    }
                } else {
                    final long lastRead = System.currentTimeMillis();
                    MyApplication.comicDatabase.comicDao().updateVideoProgress(comicId, pos, lastRead, duration[0]);
                }
            }
        });
    }
}
