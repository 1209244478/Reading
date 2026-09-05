package com.wrz.reading.ui.read.utils;

import android.util.Log;

import com.wrz.reading.ui.main.Log.LogUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ZipUtils {
    private static final String TAG = "ZipUtils";
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    /**
     * 从ZIP文件中提取所有图片文件，保留目录结构
     *
     * @param zipFilePath ZIP文件路径
     * @param extractDir  解压目录
     * @return 提取的图片文件列表（按路径排序）
     */
    public static List<File> extractImagesFromZip(String zipFilePath, File extractDir) {
        List<File> imageFiles = new ArrayList<>();

        if (!extractDir.exists() && !extractDir.mkdirs()) {
            LogUtil.e(TAG, "Failed to create extract directory: " + extractDir.getPath());
            return imageFiles;
        }

        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipArchiveInputStream zais = new ZipArchiveInputStream(
                     new BufferedInputStream(fis), "UTF-8", true, true)) {

            ZipArchiveEntry entry;
            while ((entry = zais.getNextZipEntry()) != null) {
                String entryName = entry.getName();

                // 过滤掉目录和非法路径
                if (entry.isDirectory() || containsPathTraversal(entryName)) {
                    continue;
                }

                // 获取纯文件名
                String fileName = new File(entryName).getName();
                if (!FileUtils.isImageFile(fileName)) {
                    continue;
                }

                // 保留目录结构：使用原始路径创建文件
                File outputFile = new File(extractDir, entryName);

                // 确保父目录存在
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    Log.w(TAG, "Failed to create parent directory: " + parentDir.getPath());
                    continue;
                }

                // 写入文件
                try (BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(outputFile), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = zais.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    imageFiles.add(outputFile);
                } catch (IOException e) {
                    LogUtil.e(TAG, "Error extracting entry: " + entryName, e);
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                }
            }

            // 按路径排序，确保目录结构下的文件顺序正确
            imageFiles = FileSorter.sortByPath(imageFiles);
            return imageFiles;

        } catch (IOException e) {
            LogUtil.e(TAG, "Error extracting zip file: " + zipFilePath, e);
            return imageFiles;
        }
    }

    /**
     * 从ZIP文件中提取所有图片文件（扁平化，兼容旧版本）
     *
     * @param zipFilePath ZIP文件路径
     * @param extractDir  解压目录
     * @return 提取的图片文件列表
     */
    public static List<File> extractImagesFromZipFlat(String zipFilePath, File extractDir) {
        List<File> imageFiles = new ArrayList<>();

        if (!extractDir.exists() && !extractDir.mkdirs()) {
            LogUtil.e(TAG, "Failed to create extract directory: " + extractDir.getPath());
            return imageFiles;
        }

        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipArchiveInputStream zais = new ZipArchiveInputStream(
                     new BufferedInputStream(fis), "UTF-8", true, true)) {

            ZipArchiveEntry entry;
            while ((entry = zais.getNextZipEntry()) != null) {
                String entryName = entry.getName();

                // 过滤掉目录和非法路径
                if (entry.isDirectory() || containsPathTraversal(entryName)) {
                    continue;
                }

                // 获取纯文件名
                String fileName = new File(entryName).getName();
                if (!FileUtils.isImageFile(fileName)) {
                    continue;
                }

                File outputFile = new File(extractDir, fileName);

                // 确保父目录存在
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    continue;
                }

                // 写入文件
                try (BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(outputFile), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = zais.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    imageFiles.add(outputFile);
                } catch (IOException e) {
                    LogUtil.e(TAG, "Error extracting entry: " + entryName, e);
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                }
            }

            imageFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            return imageFiles;

        } catch (IOException e) {
            LogUtil.e(TAG, "Error extracting zip file: " + zipFilePath, e);
            return imageFiles;
        }
    }

    /**
     * 检查是否包含路径遍历
     */
    private static boolean containsPathTraversal(String entryName) {
        return entryName.contains("../") ||
                entryName.startsWith("/") ||
                entryName.contains("..\\") ||
                entryName.startsWith("\\");
    }

    /**
     * 获取ZIP文件的临时解压目录
     *
     * @param zipFilePath ZIP文件路径
     * @return 临时解压目录
     */
    public static File getTempExtractDir(String zipFilePath) {
        File zipFile = new File(zipFilePath);
        String zipFileName = zipFile.getName();
        String tempDirName = "comic_temp_" + zipFileName.substring(0, zipFileName.lastIndexOf('.'));

        // 使用应用缓存目录
        File cacheDir = new File(System.getProperty("java.io.tmpdir"));
        return new File(cacheDir, tempDirName);
    }

    /**
     * 清理临时解压目录
     *
     * @param tempDir 临时目录
     */
    public static void cleanupTempDir(File tempDir) {
        if (tempDir == null || !tempDir.exists()) {
            return;
        }

        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanupTempDir(file);
                } else {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete file: " + file.getPath());
                    }
                }
            }
        }

        if (!tempDir.delete()) {
            Log.w(TAG, "Failed to delete directory: " + tempDir.getPath());
        } else {
            LogUtil.d(TAG, "Cleaned up temp directory: " + tempDir.getPath());
        }
    }
}
