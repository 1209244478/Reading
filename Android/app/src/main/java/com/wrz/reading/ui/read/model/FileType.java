package com.wrz.reading.ui.read.model;

import androidx.annotation.NonNull;

/**
 * 漫画文件类型枚举。
 * {@link #code} 与 Comic.fileType 持久化字段保持一致，兼容已有 Room 数据库。
 */
public enum FileType {
    /** 普通图片目录 */
    IMAGE("image"),
    /** zip 压缩包 */
    ZIP(".zip"),
    /** 应用压缩格式 */
    WRZ(".wrz"),
    /** PDF 文档 */
    PDF(".pdf"),
    /** EPUB 电子书 */
    EPUB(".epub"),
    /** 视频文件（交由播放器打开） */
    VIDEO("video"),
    /** 音乐文件（交由播放器打开） */
    MUSIC("music");

    private final String code;

    FileType(@NonNull String code) {
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    /**
     * 是否基于图片（用 ReaderActivity 浏览）。
     * 包含纯图片目录、zip 压缩包、应用压缩格式。
     */
    public boolean isImageBased() {
        return this == IMAGE || this == ZIP || this == WRZ;
    }

    /**
     * 根据持久化的 code 解析为枚举，未知值归为 {@link #IMAGE}。
     */
    @NonNull
    public static FileType fromCode(String code) {
        if (code == null) {
            return IMAGE;
        }
        for (FileType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return IMAGE;
    }
}
