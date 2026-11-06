package com.wrz.reading.ui.read.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.Constant;
import com.wrz.reading.ui.read.model.ReadItem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 文件操作工具集（纯静态）：
 * <ul>
 *   <li>目录遍历：所有遍历统一走私有 {@code collect(File, FilePredicate, boolean)}，
 *       基于 File.listFiles 递归实现（通过 canonical path 去重避免符号链接循环），
 *       兼容 Android 6（不依赖 java.nio.file / java.util.function）；
 *       getMediaFilesRecursive 等公开方法均为其不同谓词/深度组合</li>
 *   <li>媒体类型识别：图片/视频/音乐/压缩包/文档按扩展名判断</li>
 *   <li>封面：getAndSetCoverImagePath 生成"."开头的隐藏封面图并缓存路径</li>
 *   <li>分享：通过 FileProvider 以 content:// URI 调起系统分享面板</li>
 * </ul>
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    public static final String SPECIAL_FORMATS = ".zip";
    public static final String PDF_FORMATS = ".pdf";
    public static final String EPUB_FORMATS = ".epub";
    public static final String VIDEO_FORMATS = "video";

    private static final String[] SUPPORTED_IMG_FORMAT = {
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    };
    private static final String[] SUPPORTED_EXT_FORMAT = {
            ".zip", ".pdf", ".epub"
    };
    private static final String[] SUPPORTED_VIDEO_FORMAT = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".ts", ".webm", ".3gp", ".m4v", ".wmv", ".rmvb", ".rm"
    };
    private static final String[] SUPPORTED_MUSIC_FORMAT = {
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".wma"
    };

    public static List<File> getImageFiles(File directory, boolean sortByName) {
        List<File> imageFiles = collect(directory, f -> isImageFile(f.getName()), false);
        if (sortByName) {
            imageFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        }
        return imageFiles;
    }

    public static boolean haveImageFiles(File directory) {
        return !collect(directory, f -> isImageFile(f.getName()), false).isEmpty();
    }

    /**
     * 文件过滤条件（自定义接口，避免 java.util.function.Predicate 对 Android 6 的依赖）。
     */
    private interface FilePredicate {
        boolean accept(File file);
    }

    /**
     * 单趟遍历核心：深度优先遍历目录树，收集满足条件的文件。
     * 无法访问的条目直接跳过（与 listFiles 返回 null 的容错语义一致），
     * 并通过 canonical path 去重，避免符号链接造成循环递归。
     *
     * @param root      起始目录
     * @param accept    文件过滤条件
     * @param recursive 是否递归遍历子目录
     * @return 按遍历顺序（目录内条目顺序）排列的文件列表
     */
    private static List<File> collect(File root, FilePredicate accept, boolean recursive) {
        List<File> result = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return result;
        }
        collectInto(root, accept, recursive, result, new HashSet<>());
        return result;
    }

    private static void collectInto(File dir, FilePredicate accept, boolean recursive,
                                    List<File> result, Set<String> visited) {
        String canonicalPath;
        try {
            canonicalPath = dir.getCanonicalPath();
        } catch (IOException e) {
            canonicalPath = dir.getAbsolutePath();
        }
        // 符号链接可能造成环，按真实路径去重
        if (!visited.add(canonicalPath)) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (recursive) {
                    collectInto(child, accept, recursive, result, visited);
                }
            } else if (accept.accept(child)) {
                result.add(child);
            }
        }
    }

    /**
     * 递归获取目录下所有图片文件（包括子目录）
     * 用于支持压缩包解压后保留目录结构的情况
     */
    public static List<File> getImageFilesRecursive(File directory) {
        return collect(directory, f -> isImageFile(f.getName()), true);
    }

    /**
     * 递归获取目录下所有媒体文件（图片+视频+音乐），包括子目录。
     * 用于文件夹漫画中混合图片与视频的场景。
     */
    public static List<ReadItem> getMediaFilesRecursive(File directory) {
        List<ReadItem> mediaFiles = new ArrayList<>();
        for (File file : collect(directory,
                f -> isImageFile(f.getName()) || isVideoFile(f.getName()) || isMusicFile(f.getName()),
                true)) {
            mediaFiles.add(new ReadItem(file));
        }
        return mediaFiles;
    }

    /**
     * 检查目录（包括子目录）是否包含图片文件
     */
    public static boolean haveImageFilesRecursive(File directory) {
        return !collect(directory, f -> isImageFile(f.getName()), true).isEmpty();
    }

    /**
     * 获取目录下包含图片的子目录列表（单趟遍历实现）
     * 用于在阅读器中提供文件夹选择功能
     */
    public static List<File> getSubdirectoriesWithImages(File directory) {
        List<File> subdirs = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            return subdirs;
        }
        String rootPath = directory.getAbsolutePath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        Set<String> seen = new HashSet<>();
        for (File image : collect(directory, f -> isImageFile(f.getName()), true)) {
            String absPath = image.getAbsolutePath();
            if (!absPath.startsWith(prefix)) {
                continue;
            }
            String relative = absPath.substring(prefix.length());
            int separator = relative.indexOf(File.separatorChar);
            if (separator < 0) {
                continue; // 图片直接位于根目录下，不属于任何子目录
            }
            File top = new File(directory, relative.substring(0, separator));
            if (seen.add(top.getAbsolutePath())) {
                subdirs.add(top);
            }
        }
        // 按目录名排序
        subdirs.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        return subdirs;
    }

    public static String handleDirectoryUri(Uri treeUri) {
        String uriString = treeUri.toString();

        // 检查是否是 external storage 的 tree URI
        if (uriString.startsWith("content://com.android.externalstorage.documents/tree/")) {
            try {
                // 解析路径
                String path = uriString.replace("content://com.android.externalstorage.documents/tree/", "");

                // 解码 URL 编码
                path = URLDecoder.decode(path, "UTF-8");

                // 处理 primary: 前缀
                if (path.startsWith("primary:")) {
                    path = path.replace("primary:", "");
                    return Environment.getExternalStorageDirectory() + "/" + path;
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static boolean isImageFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String extension : SUPPORTED_IMG_FORMAT) {
            if (lowerFileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** 判断是否为支持的视频文件 */
    public static boolean isVideoFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String extension : SUPPORTED_VIDEO_FORMAT) {
            if (lowerFileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** 判断是否为支持的音乐文件 */
    public static boolean isMusicFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String extension : SUPPORTED_MUSIC_FORMAT) {
            if (lowerFileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
    /** 判断是否为支持的音乐文件 */
    public static boolean isExtFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String extension : SUPPORTED_EXT_FORMAT) {
            if (lowerFileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String isWhatFile(String fileName) {
        if (isVideoFile(fileName)) {
            return "v";
        } else if (isMusicFile(fileName)) {
            return "m";
        } else /*if (isImageFile(fileName))*/ {
            return "i";
        }
    }

    public static String isSupportedExtFormat(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String extension : SUPPORTED_EXT_FORMAT) {
            if (lowerFileName.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }

    public static File getFirstImageFile(File directory) {
        List<File> imageFiles = getImageFiles(directory, true);
        return imageFiles.isEmpty() ? null : imageFiles.get(0);
    }

    /** 递归获取目录下所有视频文件，按文件名排序 */
    public static List<File> getVideoFiles(File dir) {
        List<File> result = collect(dir, f -> isVideoFile(f.getName()), true);
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    public static long getFolderSize(File file) {
        long size = 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        size += getFolderSize(f); // 递归子文件夹
                    } else {
                        size += f.length();
                    }
                }
            }
        } else {
            size = file.length();
        }
        return size;
    }


    public static boolean copyFile(File sourceFile, File destFile) {
        if (!sourceFile.exists()) {
            return false;
        }

        // 确保目标文件的目录存在
        File destDir = destFile.getParentFile();
        if (destDir != null && !destDir.exists()) {
            destDir.mkdirs();
        }
        if (destFile.exists()) {
            destFile.delete();
        }

        // 使用try-with-resources自动关闭资源
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            LogUtil.e("FileUtils", "copyFile: ", e);
            return false;
        }
    }

    public static String getAndSetCoverImagePath(File coverImage, String title) {
        File newCoverFile = new File(CompressionUtil.getComicsDirectory(), "." + title);

        if (coverImage.isFile() && FileUtils.isVideoFile(coverImage.getName())) {
            return VideoUtils.generateThumbnailFile(coverImage, title);
        }

        if (FileUtils.copyFile(coverImage, newCoverFile)) {
            return newCoverFile.getAbsolutePath();
        }
        return coverImage.getAbsolutePath();
    }


    public static String getChapterPath(String bookId, int chapter) {
        return Constant.getPathTxt() + bookId + File.separator + chapter + ".txt";
    }

    public static File getChapterFile(String bookId, int chapter) {
        File file = new File(getChapterPath(bookId, chapter));
        if (!file.exists())
            createFile(file);
        return file;
    }

    public static File getBookDir(String bookId) {
        return new File(Constant.getPathTxt() + bookId);
    }

    public static String getMerginBook(String bookName) {
        return Constant.getPathTxt() + bookName;
    }

    public static File createWifiTempFile() {
        String src = Constant.getPathData() + "/" + System.currentTimeMillis();
        File file = new File(src);
        if (!file.exists())
            createFile(file);
        return file;
    }

    public static File[] getBookDirFiles(String bookId) {
        File dir = getBookDir(bookId);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files == null) return null;
            try {
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File lhs, File rhs) {
                        Integer lPage = Integer.parseInt(getFileNameNotType(lhs));
                        int rPage = Integer.parseInt(getFileNameNotType(rhs));
                        return Integer.compare(lPage, rPage);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            return files;
        }
        return null;
    }

    public static String getFileNameNotType(File file) {
        String path = file.getPath();
        int separatorIndex = path.lastIndexOf(File.separator);
        int lastTypeIndex = path.lastIndexOf(".");
        return (separatorIndex < 0) ? path.substring(0,  lastTypeIndex) : path.substring(separatorIndex + 1, lastTypeIndex);
    }


    /**
     * 获取Wifi传书保存文件
     *
     * @param fileName
     * @return
     */
    public static File createWifiTranfesFile(String fileName) {
        LogUtil.i(TAG, "wifi trans save " + fileName);
        // 取文件名作为文件夹（bookid）
        String absPath = Constant.getPathTxt() + "/" + fileName + "/1.txt";

        File file = new File(absPath);
        if (!file.exists())
            createFile(file);
        return file;
    }

    public static String getEpubFolderPath(String epubFileName) {
        return Constant.getPathEpub() + "/" + epubFileName;
    }

    /**
     * 从 EPUB 的 META-INF/container.xml 中解析 OPF 的 full-path 属性值。
     * 失败或不存在时返回空字符串。
     */
    private static String parseOpfPath(String unzipDir) {
        String opfPath = "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(unzipDir
                + "/META-INF/container.xml"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("full-path")) {
                    int start = line.indexOf("full-path");
                    int start2 = line.indexOf('\"', start);
                    int stop2 = line.indexOf('\"', start2 + 1);
                    if (start2 > -1 && stop2 > start2) {
                        opfPath = line.substring(start2 + 1, stop2).trim();
                        break;
                    }
                }
            }
        } catch (NullPointerException | IOException e) {
            LogUtil.e(TAG, e.toString());
        }
        return opfPath;
    }

    public static String getPathOPF(String unzipDir) {
        String mPathOPF = parseOpfPath(unzipDir);
        if (!mPathOPF.contains("/")) {
            return null;
        }
        int last = mPathOPF.lastIndexOf('/');
        if (last > -1) {
            mPathOPF = mPathOPF.substring(0, last);
        }
        return mPathOPF;
    }

    public static boolean checkOPFInRootDirectory(String unzipDir) {
        String mPathOPF = parseOpfPath(unzipDir);
        return !mPathOPF.contains("/");
    }

    public static void unzipFile(String inputZip, String destinationDirectory) throws IOException {

        int buffer = 2048;
        List<String> zipFiles = new ArrayList<>();
        File sourceZipFile = new File(inputZip);
        File unzipDirectory = new File(destinationDirectory);

        createDir(unzipDirectory.getAbsolutePath());

        try (ZipFile zipFile = new ZipFile(sourceZipFile, ZipFile.OPEN_READ)) {
            Enumeration zipFileEntries = zipFile.entries();

            while (zipFileEntries.hasMoreElements()) {

                ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
                String currentEntry = entry.getName();
                File destFile = new File(unzipDirectory, currentEntry);

                if (currentEntry.endsWith(Constant.SUFFIX_ZIP)) {
                    zipFiles.add(destFile.getAbsolutePath());
                }

                File destinationParent = destFile.getParentFile();
                createDir(destinationParent.getAbsolutePath());

                if (!entry.isDirectory()) {

                    if (destFile != null && destFile.exists()) {
                        LogUtil.i(TAG, destFile + "已存在");
                        continue;
                    }

                    try (BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
                         FileOutputStream fos = new FileOutputStream(destFile);
                         BufferedOutputStream dest = new BufferedOutputStream(fos, buffer)) {
                        int currentByte;
                        byte[] data = new byte[buffer];
                        while ((currentByte = is.read(data, 0, buffer)) != -1) {
                            dest.write(data, 0, currentByte);
                        }
                        dest.flush();
                    }
                }
            }
        }

        for (Iterator iter = zipFiles.iterator(); iter.hasNext(); ) {
            String zipName = (String) iter.next();
            unzipFile(zipName, destinationDirectory + File.separatorChar
                    + zipName.substring(0, zipName.lastIndexOf(Constant.SUFFIX_ZIP)));
        }
    }

    /**
     * 读取Assets文件
     *
     * @param fileName
     * @return
     */
    public static byte[] readAssets(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        try (InputStream fin = MyApplication.app.getAssets().open("uploader" + fileName)) {
            int length = fin.available();
            byte[] buffer = new byte[length];
            fin.read(buffer);
            return buffer;
        } catch (Exception e) {
            LogUtil.e(TAG, "readAssets: ", e);
            return null;
        }
    }

    /**
     * 创建根缓存目录
     *
     * @return
     */
    public static String createRootPath(Context context) {
        String cacheRootPath = "";
        if (isSdCardAvailable()) {
            // /sdcard/Android/data/<application package>/cache
            cacheRootPath = context.getExternalCacheDir().getPath();
        } else {
            // /data/data/<application package>/cache
            cacheRootPath = context.getCacheDir().getPath();
        }
        return cacheRootPath;
    }

    public static boolean isSdCardAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 递归创建文件夹
     *
     * @param dirPath
     * @return 创建失败返回""
     */
    public static String createDir(String dirPath) {
        try {
            File file = new File(dirPath);
            if (file.getParentFile().exists()) {
                LogUtil.i(TAG, "----- 创建文件夹" + file.getAbsolutePath());
                file.mkdir();
                return file.getAbsolutePath();
            } else {
                createDir(file.getParentFile().getAbsolutePath());
                LogUtil.i(TAG, "----- 创建文件夹" + file.getAbsolutePath());
                file.mkdir();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dirPath;
    }

    /**
     * 递归创建文件夹
     *
     * @param file
     * @return 创建失败返回""
     */
    public static String createFile(File file) {
        try {
            if (file.getParentFile().exists()) {
                LogUtil.i(TAG, "----- 创建文件" + file.getAbsolutePath());
                file.createNewFile();
                return file.getAbsolutePath();
            } else {
                createDir(file.getParentFile().getAbsolutePath());
                file.createNewFile();
                LogUtil.i(TAG, "----- 创建文件" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 将内容写入文件
     *
     * @param filePath eg:/mnt/sdcard/demo.txt
     * @param content  内容
     * @param isAppend 是否追加
     */
    public static void writeFile(String filePath, String content, boolean isAppend) {
        LogUtil.i(TAG, "save:" + filePath);
        try {
            FileOutputStream fout = new FileOutputStream(filePath, isAppend);
            byte[] bytes = content.getBytes();
            fout.write(bytes);
            fout.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeFile(String filePathAndName, String fileContent) {
        try {
            OutputStream outstream = new FileOutputStream(filePathAndName);
            OutputStreamWriter out = new OutputStreamWriter(outstream);
            out.write(fileContent);
            out.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取Raw下的文件内容
     *
     * @param context
     * @param resId
     * @return 文件内容
     */
    public static String getFileFromRaw(Context context, int resId) {
        if (context == null) {
            return null;
        }

        StringBuilder s = new StringBuilder();
        try {
            InputStreamReader in = new InputStreamReader(context.getResources().openRawResource(resId));
            BufferedReader br = new BufferedReader(in);
            String line;
            while ((line = br.readLine()) != null) {
                s.append(line);
            }
            return s.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] getBytesFromFile(File f) {
        if (f == null) {
            return null;
        }
        try (FileInputStream stream = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream(1000)) {
            byte[] b = new byte[1000];
            for (int n; (n = stream.read(b)) != -1; ) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            LogUtil.e(TAG, "getBytesFromFile: ", e);
            return null;
        }
    }

    /**
     * 文件拷贝
     *
     * @param src  源文件
     * @param desc 目的文件
     */
    public static void fileChannelCopy(File src, File desc) {
        createFile(desc);
        // 使用try-with-resources自动关闭资源
        try (FileInputStream fi = new FileInputStream(src);
             FileOutputStream fo = new FileOutputStream(desc);
             FileChannel in = fi.getChannel();
             FileChannel out = fo.getChannel()) {
            in.transferTo(0, in.size(), out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 转换文件大小
     *
     * @param fileLen 单位B
     * @return
     */
    public static String formatFileSizeToString(long fileLen) {
        DecimalFormat df = new DecimalFormat("0.00");
        String fileSizeString = "";
        if (fileLen < 1024) {
            fileSizeString = df.format((double) fileLen) + "B";
        } else if (fileLen < 1048576) {
            fileSizeString = df.format((double) fileLen / 1024) + "K";
        } else if (fileLen < 1073741824) {
            fileSizeString = df.format((double) fileLen / 1048576) + "M";
        } else {
            fileSizeString = df.format((double) fileLen / 1073741824) + "G";
        }
        return fileSizeString;
    }

    /**
     * 删除指定文件
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static boolean deleteFile(File file) throws IOException {
        return deleteFileOrDirectory(file);
    }

    /**
     * 删除指定文件，如果是文件夹，则递归删除
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static boolean deleteFileOrDirectory(File file) throws IOException {
        try {
            if (file != null && file.isFile()) {
                return file.delete();
            }
            if (file != null && file.isDirectory()) {
                File[] childFiles = file.listFiles();
                // 删除空文件夹
                if (childFiles == null || childFiles.length == 0) {
                    return file.delete();
                }
                // 递归删除文件夹下的子文件
                for (int i = 0; i < childFiles.length; i++) {
                    deleteFileOrDirectory(childFiles[i]);
                }
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取文件夹大小
     *
     */
    public static long getFolderSize(String dir) {
        File file = new File(dir);
        long size = 0;
        if (!file.isDirectory()) {
            return size;
        }
        try {
            File[] fileList = file.listFiles();
            for (int i = 0; i < fileList.length; i++) {
                // 如果下面还有文件
                if (fileList[i].isDirectory()) {
                    size = size + getFolderSize(fileList[i].getAbsolutePath());
                } else {
                    size = size + fileList[i].length();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return size;
    }

    /***
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return
     */
    public static String getExtensionName(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    /**
     * 获取文件内容
     *
     * @param path
     * @return
     */
    public static String getFileOutputString(String path, String charset) {
        try {
            File file = new File(path);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset), 8192);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append("\n").append(line);
            }
            bufferedReader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getCharset(String fileName) {
        BufferedInputStream bis = null;
        String charset = "GBK";
        byte[] first3Bytes = new byte[3];
        try {
            boolean checked = false;
            bis = new BufferedInputStream(new FileInputStream(fileName));
            bis.mark(0);
            int read = bis.read(first3Bytes, 0, 3);
            if (read == -1)
                return charset;
            if (first3Bytes[0] == (byte) 0xFF && first3Bytes[1] == (byte) 0xFE) {
                charset = "UTF-16LE";
                checked = true;
            } else if (first3Bytes[0] == (byte) 0xFE
                    && first3Bytes[1] == (byte) 0xFF) {
                charset = "UTF-16BE";
                checked = true;
            } else if (first3Bytes[0] == (byte) 0xEF
                    && first3Bytes[1] == (byte) 0xBB
                    && first3Bytes[2] == (byte) 0xBF) {
                charset = "UTF-8";
                checked = true;
            }
            bis.mark(0);
            if (!checked) {
                while ((read = bis.read()) != -1) {
                    if (read >= 0xF0)
                        break;
                    if (0x80 <= read && read <= 0xBF) // 单独出现BF以下的，也算是GBK
                        break;
                    if (0xC0 <= read && read <= 0xDF) {
                        read = bis.read();
                        if (0x80 <= read && read <= 0xBF) // 双字节 (0xC0 - 0xDF)
                            // (0x80 - 0xBF),也可能在GB编码内
                            continue;
                        else
                            break;
                    } else if (0xE0 <= read && read <= 0xEF) {// 也有可能出错，但是几率较小
                        read = bis.read();
                        if (0x80 <= read && read <= 0xBF) {
                            read = bis.read();
                            if (0x80 <= read && read <= 0xBF) {
                                charset = "UTF-8";
                                break;
                            } else
                                break;
                        } else
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return charset;
    }

    public static String getCharset1(String fileName) throws IOException {
        try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(fileName))) {
            int p = (bin.read() << 8) + bin.read();

            String code;
            switch (p) {
                case 0xefbb:
                    code = "UTF-8";
                    break;
                case 0xfffe:
                    code = "Unicode";
                    break;
                case 0xfeff:
                    code = "UTF-16BE";
                    break;
                default:
                    code = "GBK";
            }
            return code;
        }
    }

    public static void saveWifiTxt(String src, String desc) {
        byte[] LINE_END = "\n".getBytes();
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(src), getCharset(src));
             BufferedReader br = new BufferedReader(isr);
             FileOutputStream fout = new FileOutputStream(desc, true)) {
            String temp;
            while ((temp = br.readLine()) != null) {
                byte[] bytes = temp.getBytes();
                fout.write(bytes);
                fout.write(LINE_END);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 分享多个文件 或 单个文件
     * 通过 FileProvider 以 content:// URI 调起系统分享面板。
     * 单文件用 ACTION_SEND，多文件用 ACTION_SEND_MULTIPLE。
     */
    public static void shareFiles(Activity activity, List<File> files) {
        if (activity == null || files == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            uris.add(FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file));
        }

        String mime = getMimeType(files.get(0).getName());
        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            // 混合类型时统一用 */*，避免类型不匹配导致目标应用收不到
            boolean allSame = true;
            for (File file : files) {
                if (!getMimeType(file.getName()).equals(mime)) {
                    allSame = false;
                    break;
                }
            }
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType(allSame ? mime : "*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)));
    }

    /**
     * 分享 单个文件
     * 通过 FileProvider 以 content:// URI 调起系统分享面板。
     * 单文件用 ACTION_SEND
     */
    public static void shareFile(Activity activity, File file) {
        if (activity == null || file == null) return;
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", file);

        String mime = getMimeType(file.getName());
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)));
    }

    /**
     * 根据文件扩展名推断 MIME 类型，未知类型回退为通用二进制流。
     */
    private static String getMimeType(String fileName) {
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".wrz")) {
            return "application/zip";
        }
        if (lower.endsWith(".epub")) {
            return "application/epub+zip";
        }
        String ext = MimeTypeMap.getFileExtensionFromUrl(lower);
        String mime = ext != null ? map.getMimeTypeFromExtension(ext) : null;
        return mime != null ? mime : "application/octet-stream";
    }

}