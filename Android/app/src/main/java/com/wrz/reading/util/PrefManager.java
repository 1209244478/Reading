package com.wrz.reading.util;

import static com.wrz.reading.model.ComicSortPrefs.KEY_EVENT_COUNT;
import static com.wrz.reading.model.ComicSortPrefs.KEY_EVENT_LIST;
import static com.wrz.reading.model.Event.REPEAT_NONE;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wrz.reading.model.ComicSortPrefs;
import com.wrz.reading.model.Event;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用偏好设置管理：仅负责 SharedPreferences 读写。
 * Wheel/Template 数据库操作已迁移至 {@link com.wrz.reading.data.WheelRepository}。
 */
public class PrefManager {

    private static volatile PrefManager INSTANCE;
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    /**
     * 在列表中查找指定 id 的 Event 索引，未找到返回 -1
     */
    private static int findIndexById(List<Event> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    public PrefManager(Context context) {
        preferences = context.getSharedPreferences(
                ComicSortPrefs.PREF_NAME,
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

    // ==================== Event 列表 ====================

    /**
     * 存储 Event 列表（会覆盖现有的list）
     */
    public void saveEventList(List<Event> events) {
        SharedPreferences.Editor editor = preferences.edit();
        String optionListJson = gson.toJson(events);
        editor.putString(KEY_EVENT_LIST, optionListJson);
        editor.putInt(KEY_EVENT_COUNT, events.size());
        editor.apply();
    }

    /**
     * 读取 Event 列表
     */
    public List<Event> getEventList() {
        String optionListJson = preferences.getString(KEY_EVENT_LIST, null);
        if (optionListJson != null) {
            try {
                Type type = new TypeToken<List<Event>>() {
                }.getType();
                List<Event> result = gson.fromJson(optionListJson, type);
                return result != null ? result : new ArrayList<>();
            } catch (Exception e) {
                return new ArrayList<>();
            }
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 添加单个 Event 到列表
     */
    public void addEvent(Event event) {
        List<Event> mutableList = new ArrayList<>(getEventList());
        int existingIndex = findIndexById(mutableList, event.getId());
        if (existingIndex != -1) {
            mutableList.set(existingIndex, event);
        } else {
            mutableList.add(event);
        }
        saveEventList(mutableList);
    }

    /**
     * 获取 Event By id
     */
    public Event getEventById(long id) {
        List<Event> list = getEventList();
        if (list.isEmpty()) {
            return createNewEvent();
        } else {
            for (Event event : list) {
                if (id == event.getId()) {
                    return event;
                }
            }
            return createNewEvent();
        }
    }

    /**
     * 创建新 Event
     */
    public Event createNewEvent() {
        return new Event(
                System.currentTimeMillis(),
                "新列表",
                LocalDateTime.of(2011, 2, 28, 0, 0, 0),
                "",
                "-1",
                REPEAT_NONE
        );
    }

    // ==================== 随机数设置 ====================

    public int getRandomStart() {
        return preferences.getInt(ComicSortPrefs.KEY_RANDOM_START, 1);
    }

    public void saveRandomStart(int start) {
        preferences.edit().putInt(ComicSortPrefs.KEY_RANDOM_START, start).apply();
    }

    public int getRandomEnd() {
        return preferences.getInt(ComicSortPrefs.KEY_RANDOM_END, 10);
    }

    public void saveRandomEnd(int end) {
        preferences.edit().putInt(ComicSortPrefs.KEY_RANDOM_END, end).apply();
    }

    public int getRandomQuantity() {
        return preferences.getInt(ComicSortPrefs.KEY_RANDOM_QUANTITY, 1);
    }

    public void saveRandomQuantity(int quantity) {
        preferences.edit().putInt(ComicSortPrefs.KEY_RANDOM_QUANTITY, quantity).apply();
    }

    public boolean getRandomAllowDuplicates() {
        return preferences.getBoolean(ComicSortPrefs.KEY_RANDOM_ALLOW_DUPLICATES, false);
    }

    public void saveRandomAllowDuplicates(boolean allow_duplicates) {
        preferences.edit().putBoolean(ComicSortPrefs.KEY_RANDOM_ALLOW_DUPLICATES, allow_duplicates).apply();
    }

    // ==================== 排序设置 ====================

    /**
     * 保存排序方式
     * 0：按名称
     * 1：按上次阅读时间
     */
    public void saveSortType(int sortBy) {
        preferences.edit().putInt(ComicSortPrefs.KEY_SORT_BY, sortBy).apply();
    }

    public int getSortType() {
        return preferences.getInt(ComicSortPrefs.KEY_SORT_BY, ComicSortPrefs.SORT_BY_NAME);
    }

    public void saveSortOrder(int sortOrder) {
        preferences.edit().putInt(ComicSortPrefs.KEY_SORT_ORDER, sortOrder).apply();
    }

    public int getSortOrder() {
        return preferences.getInt(ComicSortPrefs.KEY_SORT_ORDER, ComicSortPrefs.ORDER_ASCENDING);
    }

    public void toggleSortOrder() {
        int currentOrder = getSortOrder();
        int newOrder = (currentOrder == ComicSortPrefs.ORDER_ASCENDING)
                ? ComicSortPrefs.ORDER_DESCENDING
                : ComicSortPrefs.ORDER_ASCENDING;
        saveSortOrder(newOrder);
    }

    // ==================== 其他偏好 ====================

    /**
     * 保存上次导入漫画的目录地址
     */
    public void saveLastAdd(String lastAdd) {
        preferences.edit().putString(ComicSortPrefs.KEY_LAST_ADD, lastAdd).apply();
    }

    /**
     * 获取上次导入漫画的目录地址
     */
    public String getLastAdd() {
        return preferences.getString(ComicSortPrefs.KEY_LAST_ADD, "");
    }



    /**
     * 保存上次导入漫画的目录地址
     */
    public void saveCastName(String lastAdd) {
        preferences.edit().putString(ComicSortPrefs.KEY_CAST_NAME, lastAdd).apply();
    }



    /**
     * 获取上次导入漫画的目录地址
     */
    public String getCastName() {
        return preferences.getString(ComicSortPrefs.KEY_CAST_NAME, "随便什么名字");
    }


}
