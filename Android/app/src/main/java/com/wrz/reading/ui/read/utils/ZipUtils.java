package com.wrz.reading.ui.read.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.model.ReadItem;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import dalvik.system.ZipPathValidator;

/**
 * ZIP 解压工具。
 * <p>
 * 注意：不能使用 Apache Commons Compress 的 {@code ZipArchiveInputStream}——
 * 它内部引用 {@code java.nio.file.LinkOption}（API 26+），在 Android 6/7 电视上会抛
 * {@link NoClassDefFoundError} 导致阅读器进程崩溃。这里统一使用 Android 自带的
 * {@link java.util.zip.ZipFile}，兼容 API 23，且能正确读取 ZIP 中央目录与 UTF-8 文件名。
 * <p>
 * Android 14（targetSdk 34+）起系统会校验 ZIP 条目路径，只要包内存在含 ".." 或以 "/"
 * 开头的条目，打开时就会抛 {@code ZipException: restricted zip entry name}。正常漫画包
 * 也可能因打包工具写入异常路径而中招，因此打开前会临时关闭该校验（见 {@link #openZipFile}），
 * 解压时仍由 {@link #containsPathTraversal} 逐条过滤。
 * <p>
 * 旧版 Windows 打包工具常用 GBK 编码文件名且不写 UTF-8 标志，系统默认按 UTF-8 严格解码会抛
 * {@code IllegalArgumentException: MALFORMED}。这里统一用 ISO-8859-1 打开 ZIP，再由
 * {@link #detectEntryNameCharset} / {@link #decodeEntryName} 还原 UTF-8 或 GBK 文件名。
 */
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
        extractZipEntries(zipFilePath, extractDir, false, imageFiles::add);
        // 按路径排序，确保目录结构下的文件顺序正确
        return FileSorter.sortByPath(imageFiles);
    }

    /**
     * 从ZIP文件中提取所有图片文件，保留目录结构
     *
     * @param zipFilePath ZIP文件路径
     * @param extractDir  解压目录
     * @return 提取的图片条目列表（按路径排序）
     */
    public static List<ReadItem> extractReadItemsFromZip(String zipFilePath, File extractDir) {
        List<ReadItem> items = new ArrayList<>();
        extractZipEntries(zipFilePath, extractDir, false, file -> items.add(new ReadItem(file)));
        return FileSorter.sortReadItemsByPath(items);
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
        extractZipEntries(zipFilePath, extractDir, true, imageFiles::add);
        imageFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        return imageFiles;
    }

    private interface EntryCallback {
        void onExtracted(File file);
    }

    /**
     * 统一的 ZIP 遍历逻辑：过滤目录/非图片/路径穿越条目，按需保留目录结构，
     * 边读取边写入解压目录。任一条目失败只跳过该条目，不影响其余图片。
     */
    private static void extractZipEntries(String zipFilePath, File extractDir,
                                          boolean flat, EntryCallback callback) {
        if (extractDir == null) {
            return;
        }
        if (!extractDir.exists() && !extractDir.mkdirs()) {
            LogUtil.e(TAG, "Failed to create extract directory: " + extractDir.getPath());
            return;
        }

        // 用 ISO-8859-1 打开：每个字节会原样映射成一个字符，可绕开系统按 UTF-8
        // 严格解码旧 ZIP 文件名时抛出的 MALFORMED；真正的编码在 decodeEntryName 中判断。
        try (ZipFile zipFile = openZipFile(zipFilePath, StandardCharsets.ISO_8859_1)) {
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement());
            }
            Charset nameCharset = detectEntryNameCharset(entries);
            for (ZipEntry entry : entries) {
                String entryName = decodeEntryName(entry, nameCharset);
                if (entryName == null || entry.isDirectory()) {
                    continue;
                }
                // 统一分隔符，并去掉打包工具误写入的绝对路径前缀，避免整包图片被跳过
                String normalizedName = normalizeEntryName(entryName);
                if (normalizedName.isEmpty() || containsPathTraversal(normalizedName)) {
                    Log.w(TAG, "Skip unsafe zip entry: " + entryName);
                    continue;
                }

                String fileName = new File(normalizedName).getName();
                if (!FileUtils.isImageFile(fileName)) {
                    continue;
                }

                File outputFile = flat
                        ? new File(extractDir, fileName)
                        : new File(extractDir, normalizedName);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    Log.w(TAG, "Failed to create parent directory: " + parentDir.getPath());
                    continue;
                }

                try (InputStream in = zipFile.getInputStream(entry);
                     BufferedOutputStream bos = new BufferedOutputStream(
                             new FileOutputStream(outputFile), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                } catch (Exception e) {
                    LogUtil.e(TAG, "Error extracting entry: " + entryName, e);
                    if (outputFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        outputFile.delete();
                    }
                    continue;
                }
                callback.onExtracted(outputFile);
            }
        } catch (Exception e) {
            // 损坏/加密/非标准 ZIP：返回已成功解出的部分，由上层决定是否提示
            LogUtil.e(TAG, "Error extracting zip file: " + zipFilePath, e);
        }
    }

    /**
     * 打开 ZIP。Android 14（targetSdk 34+）会对整个压缩包做路径校验，包内只要有一个
     * 条目名含 ".." 或以 "/" 开头，{@link ZipFile} 就会在 native open 阶段抛
     * {@code ZipException: restricted zip entry name}，导致正常图片也无法解出。
     * <p>
     * 这里仅在打开瞬间临时关闭系统校验，随后立即恢复同等严格的校验；
     * 解压过程由 {@link #containsPathTraversal(String)} 自行过滤危险条目，
     * 因此不会写出解压目录。
     * <p>
     * charset 使用 ISO-8859-1，避免旧 ZIP 中的 GBK 文件名触发 MALFORMED；
     * 条目名的真实编码由 {@link #decodeEntryName(ZipEntry, Charset)} 判断。
     */
    private static ZipFile openZipFile(String zipFilePath, Charset charset) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ZipFileOpenerApi34.open(zipFilePath, charset);
        }
        return newZipFile(zipFilePath, charset);
    }

    /**
     * 按指定编码构造 {@link ZipFile}。
     * <p>
     * 带 Charset 的构造器在 Android 上是 API 24 才加入的（Java 7 有，但 Android 6 没有）。
     * 这里用反射调用：API 24+ 以及 JVM 单测能拿到该构造器；API 23 会退回默认构造器，
     * 避免在旧电视上抛 {@link NoSuchMethodError}，同时也不会让 lint 误报 NewApi。
     */
    private static ZipFile newZipFile(String zipFilePath, Charset charset) throws IOException {
        try {
            Constructor<ZipFile> constructor =
                    ZipFile.class.getConstructor(String.class, Charset.class);
            return constructor.newInstance(zipFilePath, charset);
        } catch (NoSuchMethodException e) {
            // Android 6（API 23）：没有带编码的构造器，只能按系统默认 UTF-8 打开
            return new ZipFile(zipFilePath);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException("Failed to open zip: " + zipFilePath, cause);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to open zip: " + zipFilePath, e);
        }
    }

    /** 仅在 Android 14+ 加载，避免旧系统解析不存在的 {@link ZipPathValidator}。 */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final class ZipFileOpenerApi34 {
        /** 与系统默认校验保持一致，打开完成后恢复，避免影响其它 ZIP 解压逻辑。 */
        private static final ZipPathValidator.Callback STRICT_CALLBACK =
                new ZipPathValidator.Callback() {
                    @Override
                    public void onZipEntryAccess(String path) throws ZipException {
                        if (path.startsWith("/") || path.equals("..") || path.contains("../")) {
                            throw new ZipException("restricted zip entry name");
                        }
                    }
                };

        static ZipFile open(String zipFilePath, Charset charset) throws IOException {
            ZipPathValidator.clearCallback();
            try {
                return newZipFile(zipFilePath, charset);
            } finally {
                ZipPathValidator.setCallback(STRICT_CALLBACK);
            }
        }

        private ZipFileOpenerApi34() {
        }
    }

    /**
     * 判断整包条目名的编码，尽量让同一个 ZIP 内的目录名保持一致：
     * <ul>
     *   <li>全部能按 UTF-8 严格解码 → UTF-8（ASCII 名字也走这里）；</li>
     *   <li>UTF-8 失败、全部能按 GB18030 解码 → GB18030（Windows 中文 ZIP 常见）；</li>
     *   <li>两种编码都有非法条目 → 返回 null，由 {@link #decodeEntryName} 逐条回退。</li>
     * </ul>
     * 两种都能解码时比较解出的 CJK 字符数，避免把 GBK 文件名误判成 UTF-8。
     */
    private static Charset detectEntryNameCharset(List<ZipEntry> entries) {
        Charset legacy = legacyCharset();
        boolean hasNonAscii = false;
        boolean allUtf8 = true;
        boolean allLegacy = true;
        int utf8Cjk = 0;
        int legacyCjk = 0;

        for (ZipEntry entry : entries) {
            byte[] raw = rawEntryName(entry);
            if (raw == null) {
                continue;
            }
            if (!isAscii(raw)) {
                hasNonAscii = true;
            }
            String utf8 = decodeStrictly(raw, StandardCharsets.UTF_8);
            String legacyName = decodeStrictly(raw, legacy);
            if (utf8 == null) {
                allUtf8 = false;
            }
            if (legacyName == null) {
                allLegacy = false;
            }
            if (utf8 != null) {
                utf8Cjk += countCjk(utf8);
            }
            if (legacyName != null) {
                legacyCjk += countCjk(legacyName);
            }
            if (!allUtf8 && !allLegacy) {
                break;
            }
        }

        if (!hasNonAscii) {
            return StandardCharsets.UTF_8;
        }
        if (allUtf8 && allLegacy) {
            return legacyCjk > utf8Cjk ? legacy : StandardCharsets.UTF_8;
        }
        if (allUtf8) {
            return StandardCharsets.UTF_8;
        }
        if (allLegacy) {
            return legacy;
        }
        return null;
    }

    /**
     * 还原 ZIP 条目名的真实编码。
     * <p>
     * {@link ZipFile} 默认按 UTF-8 严格解码条目名，很多 Windows 打包工具生成的 GBK
     * 文件名会直接抛 {@code IllegalArgumentException: MALFORMED}，导致整包无法读取。
     * 这里改为用 ISO-8859-1 打开（字节与字符一一对应），再按以下顺序处理：
     * <ol>
     *   <li>名字中已含无法映射回 ISO-8859-1 的字符（如带 UTF-8 标志的条目）→ 已被
     *       系统正确解码，直接使用；</li>
     *   <li>按 {@link #detectEntryNameCharset} 选出的整包编码解码；</li>
     *   <li>整包编码无法判定时，逐条尝试 UTF-8，再尝试 GB18030/GBK。</li>
     * </ol>
     */
    private static String decodeEntryName(ZipEntry entry, Charset charset) {
        String decoded = entry.getName();
        if (decoded == null) {
            return null;
        }
        byte[] raw = rawEntryName(entry);
        if (raw == null) {
            // 系统已按 UTF-8 正确解码（通常条目带 UTF-8 标志）
            return decoded;
        }
        if (charset != null) {
            String name = decodeStrictly(raw, charset);
            if (name != null) {
                return name;
            }
        }
        String utf8 = decodeStrictly(raw, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String legacy = decodeStrictly(raw, legacyCharset());
        return legacy != null ? legacy : decoded;
    }

    /**
     * 用 ISO-8859-1 从条目名还原原始字节；若名字已被系统按 UTF-8 正确解码，
     * 则无法无损还原，返回 null。
     */
    private static byte[] rawEntryName(ZipEntry entry) {
        String name = entry.getName();
        if (name == null) {
            return null;
        }
        byte[] raw = name.getBytes(StandardCharsets.ISO_8859_1);
        return name.equals(new String(raw, StandardCharsets.ISO_8859_1)) ? raw : null;
    }

    private static boolean isAscii(byte[] bytes) {
        for (byte b : bytes) {
            if ((b & 0x80) != 0) {
                return false;
            }
        }
        return true;
    }

    /** 粗略统计 CJK 字符数，用于区分“真的中文”和 GBK/UTF-8 互解的乱码。 */
    private static int countCjk(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x3400 && c <= 0x4DBF)      // CJK 扩展 A
                    || (c >= 0x4E00 && c <= 0x9FFF)   // CJK 基本区
                    || (c >= 0x3040 && c <= 0x30FF)   // 日文假名
                    || (c >= 0xAC00 && c <= 0xD7AF)) { // 韩文
                count++;
            }
        }
        return count;
    }

    /** 按指定编码严格解码；字节非法时返回 null，交由下一种编码尝试。 */
    private static String decodeStrictly(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 中文旧 ZIP 的常见编码。Android 均支持 GBK；优先 GB18030 以覆盖更多生僻字，
     * 若个别精简系统缺失则回退 GBK。
     */
    private static Charset legacyCharset() {
        try {
            return Charset.forName("GB18030");
        } catch (Exception e) {
            return Charset.forName("GBK");
        }
    }

    /**
     * 统一使用 "/" 分隔符，并去掉 Unix 绝对路径（/foo）、Windows 盘符（C:/foo）
     * 等前缀，把条目名转成可安全拼接到解压目录的相对路径。
     * 转换后仍需经 {@link #containsPathTraversal(String)} 检查 ".."。
     */
    private static String normalizeEntryName(String entryName) {
        String name = entryName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) {
            name = name.substring(2);
            while (name.startsWith("/")) {
                name = name.substring(1);
            }
        }
        return name;
    }

    /**
     * 检查是否包含路径遍历。按路径段判断，兼容 "/" 与 "\" 分隔符。
     */
    private static boolean containsPathTraversal(String entryName) {
        String name = entryName.replace('\\', '/');
        if (name.isEmpty() || name.startsWith("/")) {
            return true;
        }
        // 绝对 Windows 路径（C:/...）同样拒绝
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) {
            return true;
        }
        for (String segment : name.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
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
        if (!FileUtils.isExtFile(zipFileName)) {
            return null;
        }
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
