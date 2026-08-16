package com.wrz.reading.ui.read.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;

@Database(entities = {Comic.class, Collection.class}, version = 3)
public abstract class ComicDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "comic_database";
    private static ComicDatabase instance;

    public abstract ComicDao comicDao();

    public abstract CollectionDao collectionDao();

    /**
     * v1 -> v2：创建 collections 表，并为 comics 表添加 collectionId 列
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `collections` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`name` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)");
            database.execSQL("ALTER TABLE comics ADD COLUMN collectionId INTEGER");
        }
    };

    /**
     * v2 -> v3：comics 表新增 videoPosition 列，用于保存视频播放进度（毫秒）
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comics ADD COLUMN videoPosition INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static synchronized ComicDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            ComicDatabase.class, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
        }
        return instance;
    }
}
