package com.wrz.reading.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.wrz.reading.Log.LogUtil;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.Constant;

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
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        List<File> imageFiles = new ArrayList<>();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isImageFile(file.getName())) {
                    imageFiles.add(file);
                }
            }

            if (sortByName) {
                imageFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            }
        }

        return imageFiles;
    }

    public static boolean haveImageFiles(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isImageFile(file.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 递归获取目录下所有图片文件（包括子目录）
     * 用于支持压缩包解压后保留目录结构的情况
     */
    public static List<File> getImageFilesRecursive(File directory) {
        List<File> imageFiles = new ArrayList<>();
        if (directory == null || !directory.exists()) {
            return imageFiles;
        }
        collectImageFilesRecursive(directory, imageFiles);
        return imageFiles;
    }

    /**
     * 递归收集图片文件
     */
    private static void collectImageFilesRecursive(File directory, List<File> imageFiles) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归搜索子目录
                collectImageFilesRecursive(file, imageFiles);
            } else if (file.isFile() && isImageFile(file.getName())) {
                imageFiles.add(file);
            }
        }
    }

    /**
     * 递归获取目录下所有媒体文件（图片+视频），包括子目录。
     * 用于文件夹漫画中混合图片与视频的场景。
     */
    public static List<File> getMediaFilesRecursive(File directory) {
        List<File> mediaFiles = new ArrayList<>();
        if (directory == null || !directory.exists()) {
            return mediaFiles;
        }
        collectMediaFilesRecursive(directory, mediaFiles);
        return mediaFiles;
    }

    private static void collectMediaFilesRecursive(File directory, List<File> mediaFiles) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectMediaFilesRecursive(file, mediaFiles);
            } else if (file.isFile() && (isImageFile(file.getName()) || isVideoFile(file.getName())|| isMusicFile(file.getName()))) {
                mediaFiles.add(file);
            }
        }
    }

    /**
     * 检查目录（包括子目录）是否包含图片文件
     */
    public static boolean haveImageFilesRecursive(File directory) {
        if (directory == null || !directory.exists()) {
            return false;
        }
        return checkImageFilesRecursive(directory);
    }

    /**
     * 递归检查是否有图片文件
     */
    private static boolean checkImageFilesRecursive(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (checkImageFilesRecursive(file)) {
                    return true;
                }
            } else if (file.isFile() && isImageFile(file.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取目录下包含图片的子目录列表
     * 用于在阅读器中提供文件夹选择功能
     */
    public static List<File> getSubdirectoriesWithImages(File directory) {
        List<File> subdirs = new ArrayList<>();
        if (directory == null || !directory.exists()) {
            return subdirs;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return subdirs;
        }

        for (File file : files) {
            if (file.isDirectory() && haveImageFilesRecursive(file)) {
                subdirs.add(file);
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
            Log.e("FileUtils", "copyFile: ", e);
            return false;
        }
    }

    public static String getAndSetCoverImagePath(File coverImage, String title) {
        File newCoverFile = new File(CompressionUtil.getComicsDirectory(), "." + title);

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
            Log.e(TAG, "readAssets: ", e);
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
            Log.e(TAG, "getBytesFromFile: ", e);
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
     * @return
     * @throws Exception
     */
    public static long getFolderSize(String dir) throws Exception {
        File file = new File(dir);
        long size = 0;
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

    /**
     * 递归获取所有文件
     *
     * @param root
     * @param ext  指定扩展名
     */
    private synchronized void getAllFiles(File root, String ext) {
        List<File> list = new ArrayList<>();
        File files[] = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    getAllFiles(f, ext);
                } else {
                    if (f.getName().endsWith(ext) && f.length() > 50)
                        list.add(f);
                }
            }
        }
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

}