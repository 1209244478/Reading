package com.wrz.reading.ui.main.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.wrz.reading.ui.wheel.data.WheelRepository;

/**
 * 应用偏好设置管理：仅负责 SharedPreferences 读写。
 * Wheel/Template 数据库操作已迁移至 {@link WheelRepository}。
 */
public class PrefManager {

    private static volatile PrefManager INSTANCE;
    private final SharedPreferences preferences;

    public PrefManager(Context context) {
        preferences = context.getSharedPreferences(
                Prefs.PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public static PrefManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PrefManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PrefManager(context);
                }
            }
        }
        return INSTANCE;
    }


    // ==================== 随机数设置 ====================

    public int getRandomStart() {
        return preferences.getInt(Prefs.KEY_RANDOM_START, 1);
    }

    public void saveRandomStart(int start) {
        preferences.edit().putInt(Prefs.KEY_RANDOM_START, start).apply();
    }

    public int getRandomEnd() {
        return preferences.getInt(Prefs.KEY_RANDOM_END, 10);
    }

    public void saveRandomEnd(int end) {
        preferences.edit().putInt(Prefs.KEY_RANDOM_END, end).apply();
    }

    public int getRandomQuantity() {
        return preferences.getInt(Prefs.KEY_RANDOM_QUANTITY, 1);
    }

    public void saveRandomQuantity(int quantity) {
        preferences.edit().putInt(Prefs.KEY_RANDOM_QUANTITY, quantity).apply();
    }

    public boolean getRandomAllowDuplicates() {
        return preferences.getBoolean(Prefs.KEY_RANDOM_ALLOW_DUPLICATES, false);
    }

    public void saveRandomAllowDuplicates(boolean allow_duplicates) {
        preferences.edit().putBoolean(Prefs.KEY_RANDOM_ALLOW_DUPLICATES, allow_duplicates).apply();
    }

    // ==================== 其他偏好 ====================

    /**
     * 保存上次导入漫画的目录地址
     */
    public void saveLastAdd(String lastAdd) {
        preferences.edit().putString(Prefs.KEY_LAST_ADD, lastAdd).apply();
    }

    /**
     * 获取上次导入漫画的目录地址
     */
    public String getLastAdd() {
        return preferences.getString(Prefs.KEY_LAST_ADD, "");
    }



    /**
     * 保存上次导入漫画的目录地址
     */
    public void saveCastName(String lastAdd) {
        preferences.edit().putString(Prefs.KEY_CAST_NAME, lastAdd).apply();
    }


    /**
     * 获取上次导入漫画的目录地址
     */
    public String getCastName() {
        return preferences.getString(Prefs.KEY_CAST_NAME, "");
    }



    /**
     * 保存是否自动清除缓存
     */
    public void saveClearCache(boolean clear) {
        preferences.edit().putBoolean(Prefs.KEY_CLEAR_CACHE, clear).apply();
    }


    /**
     * 获取是否自动清除缓存
     */
    public boolean getClearCache() {
        return preferences.getBoolean(Prefs.KEY_CLEAR_CACHE, false);
    }
}
