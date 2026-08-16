package com.wrz.reading.ui.wheel.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.wrz.reading.ui.wheel.utils.WheelConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(tableName = "wheels")
@TypeConverters(WheelConverter.class)
public class Wheel implements MultiItemEntity {

    public static final int TYPE_TITLE = 0;
    public static final int TYPE_CONTENT = 1;

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String subtitle = "";
    private String emoji = "\uD83D\uDE00";
    private List<Option> list;
    private boolean isLastTime = false;
    private boolean allowDuplicates = true;
    private boolean hideWeight = false;
    private boolean useWeight = true;
    private ArrayList<Tag> tags = new ArrayList<>();
    /** 分类名称（热门、美食、运动等），仅用于模板 */
    private String category = "";
    /** 是否为模板，true=模板，false=用户转盘 */
    private boolean isTemplate = false;
    /** item type 仅用于适配器区分分类标题和内容，不持久化 */
    @Ignore
    private int type = 1;
    /** 转盘开始旋转到结束的时间(单位：秒) */
    private int time = 3;

    public Wheel() {
    }

    @Ignore
    public Wheel(String id, String emoji, String title, String subtitle, List<Option> list) {
        this.id = id;
        this.emoji = emoji;
        this.title = title;
        this.subtitle = subtitle;
        this.list = list;
    }

    @Ignore
    public Wheel(String title, int type) {
        this.id = "title_" + System.nanoTime();
        this.title = title;
        this.type = type;
    }

    @Ignore
    public Wheel(String id, String title, boolean isLastTime, List<Option> list) {
        this.id = id;
        this.title = title;
        this.isLastTime = isLastTime;
        this.list = list;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Option> getList() {
        return list;
    }

    public void setList(List<Option> list) {
        this.list = list;
    }

    public boolean isLastTime() {
        return isLastTime;
    }

    public void setLastTime(boolean lastTime) {
        isLastTime = lastTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }

    public void setAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    @Override
    public int getItemType() {
        return getType();
    }


    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public boolean isHideWeight() {
        return hideWeight;
    }

    public void setHideWeight(boolean hideWeight) {
        this.hideWeight = hideWeight;
    }

    public boolean isUseWeight() {
        return useWeight;
    }

    public void setUseWeight(boolean useWeight) {
        this.useWeight = useWeight;
    }

    public ArrayList<Tag> getTags() {
        return tags;
    }

    public void setTags(ArrayList<Tag> tags) {
        this.tags = tags;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isTemplate() {
        return isTemplate;
    }

    public void setTemplate(boolean template) {
        isTemplate = template;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    /**
     * 复制一份新的 Wheel（新 id），用于从模板创建转盘。
     * 深拷贝 list 与 tags，避免修改副本时污染原始数据。
     */
    public Wheel copy() {
        Wheel w = new Wheel();
        w.setId(UUID.randomUUID().toString());
        w.setTitle(this.title);
        w.setSubtitle(this.subtitle);
        w.setEmoji(this.emoji);
        w.setList(deepCopyOptions(this.list));
        w.setLastTime(false);
        w.setAllowDuplicates(this.allowDuplicates);
        w.setHideWeight(this.hideWeight);
        w.setTags(deepCopyTags(this.tags));
        w.setCategory(this.category);
        w.setTemplate(false);
        return w;
    }

    private static List<Option> deepCopyOptions(List<Option> source) {
        if (source == null) return null;
        List<Option> copy = new ArrayList<>(source.size());
        for (Option o : source) {
            Option c = new Option(o.getItemType(), o.getOption());
            c.setColor(o.getColor());
            c.setWeight(o.getWeight());
            c.setPercent(o.getPercent());
            c.setSelected(o.isSelected());
            copy.add(c);
        }
        return copy;
    }

    private static ArrayList<Tag> deepCopyTags(ArrayList<Tag> source) {
        if (source == null) return null;
        ArrayList<Tag> copy = new ArrayList<>(source.size());
        for (Tag t : source) {
            copy.add(new Tag(t.getTag(), t.getOpen()));
        }
        return copy;
    }
}
