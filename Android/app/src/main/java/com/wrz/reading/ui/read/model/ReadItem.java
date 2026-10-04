package com.wrz.reading.ui.read.model;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.File;

/**
 * 阅读器内单个媒体文件的包装：图片/视频/音乐按扩展名归类为 multi-item 类型。
 */
public class ReadItem implements MultiItemEntity {

    private File file;

    public final static int TYPE_IMAGE = 0;

    public final static int TYPE_VIDEO = 1;

    public final static int TYPE_MUSIC = 2;

    public ReadItem(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public int getItemType() {
        if (FileUtils.isImageFile(file.getName())) {
            return TYPE_IMAGE;
        } else if (FileUtils.isVideoFile(file.getName())) {
            return TYPE_VIDEO;
        } else {
            return TYPE_MUSIC;
        }
    }
}
