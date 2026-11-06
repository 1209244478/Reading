package com.wrz.reading.ui.read.data;

import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.ZipUtils;

import java.io.File;
import java.util.List;

/**
 * 漫画体积计算器：统计漫画文件本体大小与解压缓存大小。
 * 每个解压目录仅做一趟递归遍历；计算涉及磁盘 IO，应在后台线程执行。
 */
public final class ComicSizeCalculator {

    private ComicSizeCalculator() {
    }

    /**
     * 统计列表中所有漫画的体积，并把每个漫画的缓存大小写入其 cacheSize 字段；
     * 同时预计算文件本体大小（fileSize）与目录标志（isDirectory），
     * 供列表绑定与排序使用，避免主线程做磁盘 stat。
     *
     * @param comics 待统计的漫画列表
     * @return [0] 解压缓存总大小；[1] 文件本体总大小
     */
    public static long[] calculate(List<Comic> comics) {
        final long[] totalSize = {0, 0};
        for (Comic comic : comics) {
            File file = new File(comic.getPath());
            boolean isDir = file.isDirectory();
            comic.setDirectory(isDir);
            long fileLength = 0;
            if (file.exists()) {
                fileLength = file.length();
                totalSize[1] += fileLength;
            }
            comic.setFileSize(fileLength);

            long size = 0;
            File tempExtractDir = ZipUtils.getTempExtractDir(comic.getPath());
            if (tempExtractDir != null && tempExtractDir.isDirectory()) {
                size = FileUtils.getFolderSize(tempExtractDir.getAbsolutePath());
            }
            totalSize[0] += size;

            comic.setCacheSize(size);
        }
        return totalSize;
    }
}
