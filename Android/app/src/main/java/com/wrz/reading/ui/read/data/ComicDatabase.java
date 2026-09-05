package com.wrz.reading.ui.read.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;

import java.util.ArrayList;
import java.util.List;

@Database(entities = {Comic.class, Collection.class}, version = 2)
public abstract class ComicDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "comic_database";
    private static ComicDatabase instance;

    public abstract ComicDao comicDao();

    public abstract CollectionDao collectionDao();

    /**
     * v1 -> v2：comics 表新增 isClean 列
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comics ADD COLUMN isClean INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static synchronized ComicDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            ComicDatabase.class, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
        }
        return instance;
    }

    public static long lastUpdate = 0;
    public static long UPDATE_LIMIT = 2000;

    public static void update(Comic comic) {
        long current = System.currentTimeMillis();
        if (current - lastUpdate > UPDATE_LIMIT) {
            MyApplication.comicDatabase.comicDao().updateComic(comic);
            lastUpdate = current;
        }
    }
    public static void updateList(ArrayList<Comic> list) {
        for (Comic comic : list) {
            if (comic != null) {
                MyApplication.comicDatabase.comicDao().updateComic(comic);
            }
        }
    }

    public static void updateList(List<Comic> list) {
        for (Comic comic : list) {
            if (comic != null) {
                MyApplication.comicDatabase.comicDao().updateComic(comic);
            }
        }
    }
}
