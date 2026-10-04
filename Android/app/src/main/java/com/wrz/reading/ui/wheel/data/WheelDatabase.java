package com.wrz.reading.ui.wheel.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.wheel.model.Wheel;

@Database(entities = {Wheel.class}, version = 2, exportSchema = false)
public abstract class WheelDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "wheel_database";

    private static WheelDatabase instance;

    public abstract WheelDao wheelDao();

    /**
     * v1 -> v2：wheels 表新增 explanation 列
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE wheels ADD COLUMN explanation TEXT");
        }
    };

    public static synchronized WheelDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            WheelDatabase.class, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // 数据库首次创建时，将默认 Wheel 写入数据库
                            new Thread(() -> {
                                WheelDao dao = instance.wheelDao();
                                Wheel defaultWheel = new Wheel(
                                        "-1",
                                        MyApplication.app.getString(R.string.default_list),
                                        true,
                                        MyApplication.template.getDefaultLists()
                                );
                                defaultWheel.setTemplate(false);
                                dao.insertOrReplace(defaultWheel);
                            }).start();
                        }
                    })
                    .build();
        }
        return instance;
    }
}
