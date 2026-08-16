package com.wrz.reading.ui.read.utils;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MapConverter {

    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromMapToString(Map<Integer, Integer> map) {
        if (map == null) {
            return null;
        }
        return gson.toJson(map);
    }

    @TypeConverter
    public static Map<Integer, Integer> fromStringToMap(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new HashMap<>();
        }
        Type type = new TypeToken<HashMap<Integer, Integer>>() {}.getType();
        return gson.fromJson(jsonString, type);
    }
}
