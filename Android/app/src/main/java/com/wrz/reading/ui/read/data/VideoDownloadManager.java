package com.wrz.reading.ui.read.data;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.fragment.CollectionFragment;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.DownloadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 视频下载管理器（应用级单例）。
 * <p>
 * 功能：接收投屏模式下把发送端的 HTTP 视频地址下载到应用专属目录，
 * 支持暂停、恢复与断点续传：
 * <ul>
 *   <li>下载过程中数据写入 {@code filePath.part}，完成后重命名为正式文件；</li>
 *   <li>续传时以 .part 的实际长度作为 HTTP Range 起点，服务器不支持 Range
 *       （返回 200）时自动从头重新下载；</li>
 *   <li>任务状态持久化到 Room（download_tasks 表），进程重启后未完成任务
 *       统一重置为暂停，用户可在下载列表继续。</li>
 * </ul>
 * <p>
 * 线程模型：网络读写与 DB 操作均在后台执行器；{@link Listener} 回调与
 * {@link EnqueueCallback}/{@link LoadCallback} 均切回主线程。
 */
public class VideoDownloadManager {

    private static final String TAG = "VideoDownloadManager";

    /**
     * 新任务已创建并开始下载
     */
    public static final int ENQUEUE_ADDED = 0;
    /**
     * 同一地址已有任务（不重复创建）
     */
    public static final int ENQUEUE_EXISTS = 1;
    /**
     * 创建任务失败
     */
    public static final int ENQUEUE_ERROR = 2;

    /**
     * 进度写库节流间隔，避免频繁 IO
     */
    private static final long PROGRESS_PERSIST_INTERVAL = 1000L;
    /**
     * 进度回调节流间隔，避免列表频繁刷新
     */
    private static final long PROGRESS_NOTIFY_INTERVAL = 500L;

    private static volatile VideoDownloadManager instance;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 每个下载任务一个线程（暂停即退出线程，不长期占用）
     */
    private final ExecutorService downloadExecutor = Executors.newCachedThreadPool();
    /**
     * 下载任务 DB 读写串行执行
     */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private final Map<Long, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private Context appContext;
    private DownloadDao dao;

    private VideoDownloadManager() {
    }

    public static VideoDownloadManager getInstance() {
        if (instance == null) {
            synchronized (VideoDownloadManager.class) {
                if (instance == null) {
                    instance = new VideoDownloadManager();
                }
            }
        }
        return instance;
    }

    /**
     * 下载状态变化监听（主线程回调）
     */
    public interface Listener {
        void onDownloadChanged(DownloadTask task);

        void onDownloadRemoved(long taskId);
    }

    public interface EnqueueCallback {
        /**
         * @param result {@link #ENQUEUE_ADDED}/{@link #ENQUEUE_EXISTS}/{@link #ENQUEUE_ERROR}
         * @param task   任务快照，ERROR 时为 null
         */
        void onResult(int result, DownloadTask task);
    }

    public interface LoadCallback {
        void onLoaded(List<DownloadTask> tasks);
    }

    /**
     * Application 启动时初始化：重置进程被杀遗留的运行中状态（保留断点文件）
     */
    public void init(Context context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        appContext = context.getApplicationContext();
        dao = MyApplication.comicDatabase.downloadDao();
        dbExecutor.execute(() -> {
            try {
                dao.updateStatus(DownloadTask.STATUS_DOWNLOADING, DownloadTask.STATUS_PAUSED);
                dao.updateStatus(DownloadTask.STATUS_PENDING, DownloadTask.STATUS_PAUSED);
            } catch (Exception e) {
                LogUtil.e(TAG, "重置未完成下载状态失败", e);
            }
        });
    }

    private void ensureReady() {
        if (appContext == null) {
            init(MyApplication.app);
        }
    }

    // ========================= 对外操作 =========================

    /**
     * 新建下载任务；同一 URL 已有任务时不重复添加。
     */
    public void enqueue(String url, String title, EnqueueCallback callback) {
        ensureReady();
        if (TextUtils.isEmpty(url)) {
            postEnqueueResult(callback, ENQUEUE_ERROR, null);
            return;
        }
        dbExecutor.execute(() -> {
            DownloadTask task;
            try {
                DownloadTask existing = dao.findByUrl(url);
                if (existing != null) {
                    postEnqueueResult(callback, ENQUEUE_EXISTS, existing.copy());
                    return;
                }
                long now = System.currentTimeMillis();
                task = new DownloadTask();
                task.setUrl(url);
                task.setTitle(TextUtils.isEmpty(title) ? "video_" + now : title);
                task.setTotalSize(-1);
                task.setDownloadedSize(0);
                task.setStatus(DownloadTask.STATUS_PENDING);
                task.setError("");
                task.setCreatedAt(now);
                task.setUpdatedAt(now);
                long id = dao.insert(task);
                task.setId(id);
                task.setFilePath(buildFilePath(task.getTitle(), url, id));
                dao.update(task);
            } catch (Exception e) {
                LogUtil.e(TAG, "创建下载任务失败", e);
                postEnqueueResult(callback, ENQUEUE_ERROR, null);
                return;
            }
            postEnqueueResult(callback, ENQUEUE_ADDED, task.copy());
            start(task);
        });
    }

    /**
     * 读取全部下载任务（按创建时间倒序），并校准 .part 文件的实际已下载大小
     */
    public void loadTasks(LoadCallback callback) {
        ensureReady();
        dbExecutor.execute(() -> {
            List<DownloadTask> tasks = new ArrayList<>();
            try {
                tasks = dao.getAll();
                for (DownloadTask task : tasks) {
                    if (task.getStatus() == DownloadTask.STATUS_COMPLETED
                            || TextUtils.isEmpty(task.getFilePath())) {
                        continue;
                    }
                    File part = new File(task.getFilePath() + ".part");
                    if (part.exists()) {
                        task.setDownloadedSize(part.length());
                    }
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "读取下载任务失败", e);
            }
            final List<DownloadTask> result = tasks;
            mainHandler.post(() -> callback.onLoaded(result));
        });
    }

    /**
     * 暂停下载：保留 .part 文件，可随时恢复
     */
    public void pause(long taskId) {
        ensureReady();
        DownloadJob job = jobs.get(taskId);
        if (job != null) {
            job.requestPause();
            return;
        }
        dbExecutor.execute(() -> {
            DownloadTask task = dao.getById(taskId);
            if (task == null) {
                return;
            }
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                    || task.getStatus() == DownloadTask.STATUS_PENDING) {
                task.setStatus(DownloadTask.STATUS_PAUSED);
                task.setUpdatedAt(System.currentTimeMillis());
                dao.update(task);
                notifyChanged(task);
            }
        });
    }

    /**
     * 继续下载：从 .part 已下载位置发起 Range 请求（断点续传）
     */
    public void resume(long taskId) {
        ensureReady();
        dbExecutor.execute(() -> {
            DownloadTask task = dao.getById(taskId);
            if (task == null || task.getStatus() == DownloadTask.STATUS_COMPLETED) {
                return;
            }
            if (jobs.containsKey(taskId)) {
                return;
            }
            task.setStatus(DownloadTask.STATUS_PENDING);
            task.setError("");
            task.setUpdatedAt(System.currentTimeMillis());
            dao.update(task);
            notifyChanged(task);
            start(task);
        });
    }

    /**
     * 删除任务及其临时/正式文件
     */
    public void delete(long taskId) {
        ensureReady();
        DownloadJob job = jobs.remove(taskId);
        if (job != null) {
            job.cancelForDelete();
        }
        dbExecutor.execute(() -> {
            try {
                DownloadTask task = dao.getById(taskId);
                if (task != null) {
                    if (!TextUtils.isEmpty(task.getFilePath())) {
                        deleteQuietly(new File(task.getFilePath() + ".part"));
                        deleteQuietly(new File(task.getFilePath()));
                    }
                    dao.deleteById(taskId);
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "删除下载任务失败", e);
            }
            mainHandler.post(() -> {
                for (Listener listener : listeners) {
                    listener.onDownloadRemoved(taskId);
                }
            });
        });
    }

    public File getDownloadDir() {
        ensureReady();
        File base = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (base == null) {
            base = appContext.getFilesDir();
        }
        return new File(base, "downloads");
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ========================= 下载实现 =========================

    private void start(DownloadTask task) {
        if (task == null || TextUtils.isEmpty(task.getFilePath())) {
            return;
        }
        DownloadJob job = new DownloadJob(task.copy());
        if (jobs.putIfAbsent(task.getId(), job) != null) {
            return;
        }
        downloadExecutor.execute(job);
    }

    private void postEnqueueResult(EnqueueCallback callback, int result, DownloadTask task) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onResult(result, task));
    }

    private void persist(DownloadTask task) {
        DownloadTask snapshot = task.copy();
        dbExecutor.execute(() -> {
            try {
                dao.update(snapshot);
            } catch (Exception e) {
                LogUtil.e(TAG, "更新下载任务失败", e);
            }
        });
    }

    private void notifyChanged(DownloadTask task) {
        final DownloadTask snapshot = task.copy();
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onDownloadChanged(snapshot);
            }
        });
    }

    /**
     * 单个下载任务的执行体
     */
    private final class DownloadJob implements Runnable {

        private final DownloadTask task;
        private volatile boolean pauseRequested;
        private volatile boolean deleted;
        private volatile Call call;
        private long lastPersistAt;
        private long lastNotifyAt;

        DownloadJob(DownloadTask task) {
            this.task = task;
        }

        void requestPause() {
            pauseRequested = true;
            cancelCall();
        }

        void cancelForDelete() {
            deleted = true;
            pauseRequested = true;
            cancelCall();
        }

        private void cancelCall() {
            Call running = call;
            if (running != null) {
                running.cancel();
            }
        }

        @Override
        public void run() {
            final File partFile = new File(task.getFilePath() + ".part");
            FileOutputStream out = null;
            try {
                if (deleted) {
                    return;
                }
                File parent = new File(task.getFilePath()).getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建下载目录");
                }

                // 以 .part 实际长度作为续传起点（进程重启后也准确）
                long existing = partFile.exists() ? partFile.length() : 0;
                task.setStatus(DownloadTask.STATUS_DOWNLOADING);
                task.setDownloadedSize(existing);
                task.setError("");
                task.setUpdatedAt(System.currentTimeMillis());
                persist(task);
                notifyChanged(task);

                Request.Builder builder = new Request.Builder()
                        .url(task.getUrl())
                        .header("Accept-Encoding", "identity");
                if (existing > 0) {
                    builder.header("Range", "bytes=" + existing + "-");
                }
                call = httpClient.newCall(builder.build());
                if (pauseRequested) {
                    finishPaused(partFile);
                    return;
                }

                try (Response response = call.execute()) {
                    if (deleted) {
                        return;
                    }
                    int code = response.code();
                    long total;
                    boolean append;
                    ResponseBody body = response.body();
                    if (code == 416) {
                        // Range 起点已到文件末尾（如完成前进程被杀）：视为已下载完成
                        long rangedTotal = parseContentRangeTotal(response.header("Content-Range"));
                        if (rangedTotal > 0 && existing >= rangedTotal) {
                            finishCompleted(partFile);
                            return;
                        }
                        throw new IOException("HTTP " + code);
                    } else if (code == 206) {
                        append = existing > 0;
                        total = parseContentRangeTotal(response.header("Content-Range"));
                        if (total <= 0 && body != null && body.contentLength() > 0) {
                            total = existing + body.contentLength();
                        }
                    } else if (code == 200) {
                        // 服务器忽略了 Range：从头下载，覆盖旧临时文件
                        append = false;
                        existing = 0;
                        total = body != null ? body.contentLength() : -1;
                    } else {
                        throw new IOException("HTTP " + code);
                    }
                    if (body == null) {
                        throw new IOException("响应内容为空");
                    }

                    task.setTotalSize(total);
                    task.setDownloadedSize(existing);
                    out = new FileOutputStream(partFile, append);
                    long downloaded = existing;
                    byte[] buffer = new byte[64 * 1024];
                    try (InputStream in = body.byteStream()) {
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            if (pauseRequested || deleted) {
                                break;
                            }
                            out.write(buffer, 0, len);
                            downloaded += len;
                            task.setDownloadedSize(downloaded);
                            maybeNotify(task);
                            maybePersist(task);
                        }
                    }
                    out.flush();
                    out.close();
                    out = null;

                    if (deleted) {
                        return;
                    }
                    if (pauseRequested) {
                        finishPaused(partFile);
                        return;
                    }
                    if (total > 0 && downloaded < total) {
                        // 连接提前结束：保留断点，标记暂停供用户继续
                        task.setStatus(DownloadTask.STATUS_PAUSED);
                        task.setDownloadedSize(downloaded);
                        task.setUpdatedAt(System.currentTimeMillis());
                        persist(task);
                        notifyChanged(task);
                        return;
                    }

                    finishCompleted(partFile);
                }
            } catch (Exception e) {
                if (deleted) {
                    return;
                }
                if (pauseRequested) {
                    finishPaused(partFile);
                    return;
                }
                LogUtil.e(TAG, "下载失败: " + task.getUrl(), e);
                task.setStatus(DownloadTask.STATUS_FAILED);
                task.setError(e.getMessage() == null ? "" : e.getMessage());
                if (partFile.exists()) {
                    task.setDownloadedSize(partFile.length());
                }
                task.setUpdatedAt(System.currentTimeMillis());
                persist(task);
                notifyChanged(task);
            } finally {
                closeQuietly(out);
                call = null;
                jobs.remove(task.getId(), this);
            }
        }

        /**
         * 下载完成：.part 重命名为正式文件并更新状态
         */
        private void finishCompleted(File partFile) throws IOException {
            if (deleted) {
                return;
            }
            File target = new File(task.getFilePath());
            if (!partFile.renameTo(target)) {
                boolean renamed = target.exists() && target.delete() && partFile.renameTo(target);
                if (!renamed) {
                    throw new IOException("保存文件失败");
                }
            }
            long finalSize = target.length();
            task.setStatus(DownloadTask.STATUS_COMPLETED);
            task.setDownloadedSize(finalSize);
            if (task.getTotalSize() <= 0) {
                task.setTotalSize(finalSize);
            }
            task.setUpdatedAt(System.currentTimeMillis());
            persist(task);
            notifyChanged(task);

            Comic comic = Comic.buildComicFromFile(target);
            if (comic != null && !Comic.isComicExists(comic.getOriginalPath())) {
                MyApplication.comicDatabase.comicDao().insertComic(comic);
            }
        }

        private void finishPaused(File partFile) {
            if (deleted) {
                return;
            }
            task.setStatus(DownloadTask.STATUS_PAUSED);
            if (partFile.exists()) {
                task.setDownloadedSize(partFile.length());
            }
            task.setUpdatedAt(System.currentTimeMillis());
            persist(task);
            notifyChanged(task);
        }

        private void maybePersist(DownloadTask task) {
            long now = System.currentTimeMillis();
            if (now - lastPersistAt >= PROGRESS_PERSIST_INTERVAL) {
                lastPersistAt = now;
                persist(task);
            }
        }

        private void maybeNotify(DownloadTask task) {
            long now = System.currentTimeMillis();
            if (now - lastNotifyAt >= PROGRESS_NOTIFY_INTERVAL) {
                lastNotifyAt = now;
                notifyChanged(task);
            }
        }
    }

    // ========================= 工具方法 =========================

    private static long parseContentRangeTotal(String contentRange) {
        if (TextUtils.isEmpty(contentRange)) {
            return -1;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash >= contentRange.length() - 1) {
            return -1;
        }
        String total = contentRange.substring(slash + 1).trim();
        if ("*".equals(total)) {
            return -1;
        }
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String buildFilePath(String title, String url, long taskId) {
        File dir = getDownloadDir();
        if (!dir.exists() && !dir.mkdirs()) {
            LogUtil.e(TAG, "创建下载目录失败: " + dir.getAbsolutePath());
        }
        String ext = resolveExtension(url, title);
        String base = sanitizeFileName(title);
        if (!ext.isEmpty() && base.toLowerCase(Locale.US).endsWith(ext)) {
            base = base.substring(0, base.length() - ext.length());
        }
        if (base.isEmpty()) {
            base = "video";
        }
        File target = new File(dir, base + ext);
        if (target.exists()) {
            // 同名文件已存在（其他任务的历史文件）：追加任务 id 区分
            target = new File(dir, base + "_" + taskId + ext);
        }
        return target.getAbsolutePath();
    }

    private static String sanitizeFileName(String title) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        String name = title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name;
    }

    private static String resolveExtension(String url, String title) {
        String ext = null;
        try {
            ext = extensionOf(new java.net.URL(url).getPath());
        } catch (Exception ignored) {
            // URL 非法时退回标题后缀
        }
        if (ext == null) {
            ext = extensionOf(title);
        }
        return ext == null ? ".mp4" : ext;
    }

    private static String extensionOf(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1);
        if (!ext.matches("[A-Za-z0-9]{2,5}")) {
            return null;
        }
        return "." + ext.toLowerCase(Locale.US);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static void closeQuietly(FileOutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }
}
