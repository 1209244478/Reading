package com.wrz.reading.ui.read.utils;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.wrz.reading.ui.read.model.SubProgress;
import com.wrz.reading.ui.wheel.model.Tag;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MapConverter {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Long.class, new LongAdapter())
            .registerTypeAdapter(long.class, new LongAdapter())
            .create();

    @TypeConverter
    public static String fromMapToString(Map<String, Long> map) {
        if (map == null) {
            return null;
        }
        return gson.toJson(map);
    }

    @TypeConverter
    public static Map<String, Long> fromStringToMap(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new HashMap<>();
        }
        Type type = new TypeToken<HashMap<String, Long>>() {}.getType();
        return gson.fromJson(jsonString, type);
    }

    @TypeConverter
    public static String fromSubprogressListToString(ArrayList<SubProgress> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static ArrayList<SubProgress> fromStringToSubProgressList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<SubProgress>>() {}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 确保 JSON 数字反序列化为 Long，而非 Integer。
     * Gson 默认对 int 范围内的数字返回 Integer，会导致 ClassCastException。
     */
    private static class LongAdapter implements JsonSerializer<Long>, JsonDeserializer<Long> {
        @Override
        public JsonElement serialize(Long src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src);
        }

        @Override
        public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return json.getAsLong();
        }
    }
}
