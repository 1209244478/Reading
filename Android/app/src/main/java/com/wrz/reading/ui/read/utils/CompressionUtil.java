package com.wrz.reading.ui.read.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.Log.LogUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompressionUtil {
    private static final String TAG = "CompressionUtil";
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    /**
     * 将图片文件列表压缩成ZIP文件
     *
     * @param context 上下文
     * @param imageFiles 图片文件列表
     * @param comicTitle 漫画标题
     * @return 压缩后的文件路径
     */
    public static String compressImagesToZip(Context context, List<File> imageFiles, String comicTitle) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            LogUtil.e(TAG, "No images to compress");
            return null;
        }

        // 创建下载目录下的漫画文件夹
        File comicsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "漫画");
        // 使用应用私有目录下的漫画文件夹
        // File comicsDir = getComicsDirectory();

        if (!comicsDir.exists() && !comicsDir.mkdirs()) {
            LogUtil.e(TAG, "Failed to create comics directory");
            return null;
        }

        // 创建ZIP文件
        String zipFileName = comicTitle + FileUtils.SPECIAL_FORMATS;
        File zipFile = new File(comicsDir, zipFileName);

        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {

            for (int i = 0; i < imageFiles.size(); i++) {
                File imageFile = imageFiles.get(i);
                if (!imageFile.exists() || !imageFile.isFile()) {
                    Log.w(TAG, "Skipping non-existent file: " + imageFile.getPath());
                    continue;
                }

                // 创建ZIP条目
                ZipEntry zipEntry = new ZipEntry(imageFile.getName());
                zipOut.putNextEntry(zipEntry);

                // 写入文件内容
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(imageFile), BUFFER_SIZE)) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, bytesRead);
                    }
                }

                zipOut.closeEntry();
            }

            Log.i(TAG, "Successfully compressed images to: " + zipFile.getPath());
            return zipFile.getPath();
        } catch (IOException e) {
            LogUtil.e(TAG, "Error compressing images", e);
            if (zipFile.exists()) {
                zipFile.delete();
            }
            return null;
        }
    }

    /**
     * 获取漫画文件夹路径（应用私有外部存储）
     *
     * @return 漫画文件夹路径
     */
    public static File getComicsDirectory() {
        // 使用应用私有外部存储目录，无需申请存储权限，卸载自动清理
        File comicsDir = new File(MyApplication.app.getExternalFilesDir(null), "漫画");
        if (!comicsDir.exists() && !comicsDir.mkdirs()) {
            LogUtil.e(TAG, "Failed to create comics directory");
            return null;
        }
        return comicsDir;
    }

}