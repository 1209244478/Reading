package com.wrz.reading.ui.read.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.wrz.reading.ui.read.utils.MapConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity(tableName = "comics")
@TypeConverters(MapConverter.class)
public class Comic {
    @PrimaryKey(autoGenerate = true)
    private long id;


    // 所属集合 ID，null 表示未分组
    private Long collectionId;


    /*-------------------------------基础信息------------------------------------*/

    private String title; // 标题
    private String path; // 压缩后的文件路径
    private String originalPath; // 原始目录路径
    private String coverPath; // 封面位置
    private long lastRead; // 上次阅读时间
    private String fileType = "image"; // 文件类型：image, pdf, epub, mobi

    /*-------------------------------进度相关------------------------------------*/
    private long total; // 总时长
    private int readProgress; // 阅读进度
    /** 视频播放进度（毫秒），仅用于 fileType=video；0 表示未播放过 */
    private long videoPosition; // 视频进度

    Map<String, Long> percentMap = new HashMap<>(); // 子目录进度

    private ArrayList<SubProgress> subProgress = new ArrayList<>();

    /*-------------------------------设置相关------------------------------------*/

    private boolean horizontal; // 阅读方向（true：水平，false：垂直）
    private boolean isCompressed = false; // 是否已压缩
    private boolean isSelected = false; // 是否被选中

    private boolean isClean = false; // 是否退出阅读后清理缓存


    /*-------------------------------排序相关------------------------------------*/

    /**
     * 按名称正序
      */
    public static final int SORT_BY_NAME = 0;

    /**
     * 按名称倒序
     */
    public static final int SORT_BY_NAME_REVERSE = 1;

    /**
     * 按修改时间
     */
    public static final int SORT_BY_MODIFY_TIME = 2;

    private int sort = SORT_BY_NAME;


    /*-------------------------------自动播放------------------------------------*/

    /**
     * 自动播放间隔
     */
    private long autoPlay = 3000L;


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

    public Map<String, Long> getPercentMap() {
        return percentMap;
    }

    public void setPercentMap(Map<String, Long> percentMap) {
        this.percentMap = percentMap;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public long getAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(long autoPlay) {
        this.autoPlay = autoPlay;
    }

    public ArrayList<SubProgress> getSubProgress() {
        return subProgress;
    }

    public void setSubProgress(ArrayList<SubProgress> subProgress) {
        this.subProgress = subProgress;
    }

    public boolean isClean() {
        return isClean;
    }

    public void setClean(boolean clean) {
        isClean = clean;
    }

    /** 深拷贝：仅复制标题相关字段，取消时不影响原始数据 */
    public static List<Comic> deepCopy(List<Comic> data) {
        if (data == null) return null;
        java.util.ArrayList<Comic> copy = new java.util.ArrayList<>();
        for (Comic src : data) {
            Comic c = new Comic();
            c.setId(src.getId());
            c.setTitle(src.getTitle());
            c.setPath(src.getPath());
            c.setOriginalPath(src.getOriginalPath());
            c.setCoverPath(src.getCoverPath());
            c.setTotal(src.getTotal());
            c.setFileType(src.getFileType());
            c.setHorizontal(src.isHorizontal());
            c.setReadProgress(src.getReadProgress());
            c.setVideoPosition(src.getVideoPosition());
            c.setSubProgress(deepCopySubProgress(src.getSubProgress()));
            c.setCollectionId(src.getCollectionId());
            c.setSort(src.getSort());
            c.setAutoPlay(src.getAutoPlay());
            copy.add(c);
        }
        return copy;
    }

    public static Comic deepCopy(Comic comic) {
        if (comic == null) return null;

        Comic copy = new Comic();
        copy.setId(comic.getId());
        copy.setTitle(comic.getTitle());
        copy.setPath(comic.getPath());
        copy.setOriginalPath(comic.getOriginalPath());
        copy.setCoverPath(comic.getCoverPath());
        copy.setTotal(comic.getTotal());
        copy.setFileType(comic.getFileType());
        copy.setHorizontal(comic.isHorizontal());
        copy.setReadProgress(comic.getReadProgress());
        copy.setVideoPosition(comic.getVideoPosition());
        copy.setSubProgress(deepCopySubProgress(comic.getSubProgress()));
        copy.setCollectionId(comic.getCollectionId());
        copy.setSort(comic.getSort());
        copy.setAutoPlay(comic.getAutoPlay());

        return copy;
    }

    private static ArrayList<SubProgress> deepCopySubProgress(ArrayList<SubProgress> src) {
        if (src == null) return new ArrayList<>();
        ArrayList<SubProgress> dest = new ArrayList<>(src.size());
        for (SubProgress sp : src) {
            dest.add(new SubProgress(sp.getTitle(), sp.getProgress()));
        }
        return dest;
    }

}