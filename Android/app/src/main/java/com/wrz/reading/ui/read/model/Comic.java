package com.wrz.reading.ui.read.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.wrz.reading.ui.read.utils.MapConverter;

import java.util.HashMap;
import java.util.Map;

@Entity(tableName = "comics")
public class Comic {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String title;
    private String path; // 压缩后的文件路径
    private String originalPath; // 原始目录路径
    private String coverPath;
    private long lastRead;
    private long total;
    private int readProgress;
    /** 视频播放进度（毫秒），仅用于 fileType=video；0 表示未播放过 */
    private long videoPosition;

    @TypeConverters(MapConverter.class)
    Map<Integer, Integer> percentMap = new HashMap<>();

    private boolean horizontal;
    private boolean isCompressed = false; // 是否已压缩
    private boolean isSelected = false; // 是否被选中

    private String fileType = "image"; // 文件类型：image, pdf, epub, mobi

    // 所属集合 ID，null 表示未分组
    private Long collectionId;
    public Comic() {
    }

    /**
     * 创建未压缩的 Comic（path 与 originalPath 相同）。
     * 推荐使用此工厂方法替代直接调用构造器。
     */
    public static Comic uncompressed(String title, String path, String coverPath, long total, String fileType) {
        return new Comic(title, path, path, coverPath, total, fileType, false);
    }

    /**
     * 创建已压缩的 Comic（path 为压缩包路径，originalPath 为原始目录）。
     */
    public static Comic compressed(String title, String compressedPath, String originalPath, String coverPath, long total, String fileType) {
        return new Comic(title, compressedPath, originalPath, coverPath, total, fileType, true);
    }

    private Comic(String title, String path, String originalPath, String coverPath, long total, String fileType, boolean isCompressed) {
        this.title = title;
        this.path = path;
        this.originalPath = originalPath;
        this.coverPath = coverPath;
        this.lastRead = System.currentTimeMillis();
        this.readProgress = 0;
        this.horizontal = true;
        this.isCompressed = isCompressed;
        this.total = total;
        this.fileType = fileType;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public long getLastRead() {
        return lastRead;
    }

    public void setLastRead(long lastRead) {
        this.lastRead = lastRead;
    }

    public int getReadProgress() {
        return readProgress;
    }

    public void setReadProgress(int readProgress) {
        this.readProgress = readProgress;
    }

    public long getVideoPosition() {
        return videoPosition;
    }

    public void setVideoPosition(long videoPosition) {
        this.videoPosition = videoPosition;
    }

    public boolean isHorizontal() {
        return horizontal;
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public boolean isCompressed() {
        return isCompressed;
    }

    public void setCompressed(boolean compressed) {
        isCompressed = compressed;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /**
     * 获取文件类型枚举，便于 switch 分发，替代字符串 if-else。
     */
    public FileType getFileTypeEnum() {
        return FileType.fromCode(fileType);
    }

    public Map<Integer, Integer> getPercentMap() {
        return percentMap;
    }

    public void setPercentMap(Map<Integer, Integer> percentMap) {
        this.percentMap = percentMap;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }
}