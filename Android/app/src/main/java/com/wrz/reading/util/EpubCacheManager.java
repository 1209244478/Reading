package com.wrz.reading.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class EpubCacheManager {
    private static final String PREF_NAME = "epub_cache";
    private static final String KEY_CACHE_MAP = "cache_map";

    // 检查是否有缓存（使用文件路径）
    public static boolean hasCache(Context context, String epubPath) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cacheMap = prefs.getString(KEY_CACHE_MAP, "");
        // 使用路径的hash作为标识
        String key = String.valueOf(epubPath.hashCode());
        return cacheMap.contains(key + ";");
    }

    // 保存缓存
    public static void saveCache(Context context, String epubPath, List<String> pages) throws Exception {
        String serialized = serializePages(pages);
        File cacheFile = getCacheFile(context, epubPath);

        try (FileWriter writer = new FileWriter(cacheFile)) {
            writer.write(serialized);
        }

        // 更新索引
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = String.valueOf(epubPath.hashCode());
        String cacheMap = prefs.getString(KEY_CACHE_MAP, "");

        if (!cacheMap.contains(key)) {
            editor.putString(KEY_CACHE_MAP, cacheMap + key + ";");
        }
        editor.apply();
    }


    // 读取缓存
    public static List<String> loadCache(Context context, String epubPath) throws Exception {
        File cacheFile = getCacheFile(context, epubPath);
        if (!cacheFile.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return deserializePages(sb.toString());
        }
    }

    private static File getCacheFile(Context context, String epubPath) {
        // 使用路径hash作为文件名，避免路径中的特殊字符问题
        String fileName = String.valueOf(epubPath.hashCode());
        return new File(context.getCacheDir(), fileName + ".cache");
    }

    // 清理特定EPUB的缓存
    public static void clearCache(Context context, String epubPath) {
        File cacheFile = getCacheFile(context, epubPath);
        if (cacheFile.exists()) {
            cacheFile.delete();
        }

        // 从索引中移除
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = String.valueOf(epubPath.hashCode());
        String cacheMap = prefs.getString(KEY_CACHE_MAP, "");
        cacheMap = cacheMap.replace(key + ";", "");
        prefs.edit().putString(KEY_CACHE_MAP, cacheMap).apply();
    }

    // 清理所有缓存（保持不变）
    public static void clearAllCache(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cacheMap = prefs.getString(KEY_CACHE_MAP, "");

        if (!cacheMap.isEmpty()) {
            String[] keys = cacheMap.split(";");
            for (String key : keys) {
                if (!key.isEmpty()) {
                    File cacheFile = new File(context.getCacheDir(), key + ".cache");
                    cacheFile.delete();
                }
            }
        }

        prefs.edit().clear().apply();
    }

    // 简单序列化：用特殊分隔符连接HTML页面
    private static String serializePages(List<String> pages) {
        StringBuilder sb = new StringBuilder();
        for (String page : pages) {
            // 用Base64编码避免分隔符冲突
            String encoded = android.util.Base64.encodeToString(page.getBytes(), android.util.Base64.DEFAULT);
            sb.append(encoded).append("|PAGE|");
        }
        return sb.toString();
    }

    private static List<String> deserializePages(String data) {
        List<String> pages = new ArrayList<>();
        String[] encodedPages = data.split("\\|PAGE\\|");

        for (String encoded : encodedPages) {
            if (encoded.isEmpty()) continue;
            try {
                byte[] decoded = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                pages.add(new String(decoded));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return pages;
    }
}
