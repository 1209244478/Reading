package com.wrz.reading.ui.wheel.utils;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Tag;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Room TypeConverters：将 Wheel 中的复杂字段序列化为 JSON 字符串存入数据库。
 */
public class WheelConverter {

    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromOptionListToString(List<Option> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<Option> fromStringToOptionList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<Option>>() {}.getType();
        return gson.fromJson(json, type);
    }

    @TypeConverter
    public static String fromTagListToString(ArrayList<Tag> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static ArrayList<Tag> fromStringToTagList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Tag>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
