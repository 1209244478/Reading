package com.wrz.reading.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.wrz.reading.model.Wheel;

@Database(entities = {Wheel.class}, version = 3, exportSchema = false)
public abstract class WheelDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "wheel_database";

    private static WheelDatabase instance;

    public abstract WheelDao wheelDao();

    public static synchronized WheelDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            WheelDatabase.class, DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
