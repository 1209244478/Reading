package com.wrz.reading.ui.read.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.utils.ExecutorManager;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.DownloadTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Database(entities = {Comic.class, Collection.class, DownloadTask.class}, version = 5)
public abstract class ComicDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "comic_database";
    private static ComicDatabase instance;

    public abstract ComicDao comicDao();

    public abstract CollectionDao collectionDao();

    public abstract DownloadDao downloadDao();

    /**
     * v1 -> v2：comics 表新增 isClean 列
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comics ADD COLUMN isClean INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * v2 -> v3：comics 表新增 cacheSize 列，collections 表新增 cacheSize 列，
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comics ADD COLUMN cacheSize INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE collections ADD COLUMN cacheSize INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * v3 -> v4：collections 表新增 fileSize 列
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE collections ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * v4 -> v5：新增 download_tasks 表（视频下载任务，支持暂停/断点续传）
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`url` TEXT, "
                    + "`title` TEXT, "
                    + "`filePath` TEXT, "
                    + "`totalSize` INTEGER NOT NULL, "
                    + "`downloadedSize` INTEGER NOT NULL, "
                    + "`status` INTEGER NOT NULL, "
                    + "`error` TEXT, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL)");
        }
    };

    public static synchronized ComicDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            ComicDatabase.class, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
        }
        return instance;
    }

    public static long UPDATE_LIMIT = 2000;

    /**
     * 是否有 DB 写入正在进行（供外部轮询等待写入完成，如列表返回刷新场景）。
     */
    public static volatile boolean isUpdating = false;

    private static final AtomicLong lastUpdateTime = new AtomicLong(0);
    private static final AtomicBoolean writeInFlight = new AtomicBoolean(false);

    /**
     * 节流更新单个 Comic：UPDATE_LIMIT 毫秒内最多一次，DB 写入切换到后台单线程执行。
     * 使用 CAS 保证并发调用下既不重复节流放行、也不会并发写库。
     */
    public static void update(Comic comic) {
        long current = System.currentTimeMillis();
        long last = lastUpdateTime.get();
        if (current - last <= UPDATE_LIMIT) {
            return;
        }
        if (!lastUpdateTime.compareAndSet(last, current)) {
            return;
        }
        if (!writeInFlight.compareAndSet(false, true)) {
            return;
        }
        isUpdating = true;
        ExecutorManager.getInstance().getSingleThreadExecutor().execute(() -> {
            try {
                MyApplication.comicDatabase.comicDao().updateComic(comic);
            } finally {
                writeInFlight.set(false);
                isUpdating = false;
            }
        });
    }

    /**
     * 批量更新：单事务写入，失败整体回滚，避免逐条提交的性能与一致性问题。
     * 调用方需在后台线程调用（与 Room 主线程检查约定一致）。
     */
    public static void updateList(List<Comic> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        MyApplication.comicDatabase.runInTransaction(() -> {
            for (Comic comic : list) {
                if (comic != null) {
                    MyApplication.comicDatabase.comicDao().updateComic(comic);
                }
            }
        });
    }
}
