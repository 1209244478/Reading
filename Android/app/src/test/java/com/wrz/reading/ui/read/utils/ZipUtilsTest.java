package com.wrz.reading.ui.read.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZipUtils 解压单元测试（纯 JVM，使用真实 zip 包验证）。
 * 覆盖：目录结构保留、非图片过滤、Zip-Slip 路径穿越防护、绝对路径归一化、
 * UTF-8/GBK 文件名解码、异常输入容错。
 */
public class ZipUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void putEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    /** 构造测试 zip：正常图片（含子目录）、非图片、路径穿越条目、空目录 */
    private File createTestZip() throws IOException {
        File zip = tmp.newFile("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            putEntry(zos, "001.jpg", "img-001");
            putEntry(zos, "page2.jpg", "img-page2");
            putEntry(zos, "page10.jpg", "img-page10");
            putEntry(zos, "sub/002.png", "img-sub");
            putEntry(zos, "notes.txt", "not an image");
            putEntry(zos, "../evil.jpg", "traversal");
            zos.putNextEntry(new ZipEntry("empty_dir/"));
            zos.closeEntry();
        }
        return zip;
    }

    @Test
    public void extractImages_keepsStructureAndFilters() throws IOException {
        File zip = createTestZip();
        File extractDir = tmp.newFolder("extract");

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);

        // 仅 4 个图片被解出，且按路径自然排序（page2 在 page10 前，子目录最后）
        assertEquals(4, result.size());
        assertEquals("001.jpg", result.get(0).getName());
        assertEquals("page2.jpg", result.get(1).getName());
        assertEquals("page10.jpg", result.get(2).getName());
        assertEquals("002.png", result.get(3).getName());

        // 子目录结构保留
        assertTrue(result.get(3).getParentFile().getName().equals("sub"));
        // 非图片未解出
        assertFalse(new File(extractDir, "notes.txt").exists());
    }

    @Test
    public void extractImages_blocksPathTraversal() throws IOException {
        File zip = createTestZip();
        File extractDir = tmp.newFolder("extract_traversal");
        File parentDir = extractDir.getParentFile();

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);

        // 目录穿越条目（../evil.jpg）不得被写出
        for (File f : result) {
            assertTrue(f.getAbsolutePath().startsWith(extractDir.getAbsolutePath()));
        }
        assertFalse(new File(parentDir, "evil.jpg").exists());
        assertEquals(4, result.size());
    }

    @Test
    public void extractImages_contentIntact() throws IOException {
        File zip = createTestZip();
        File extractDir = tmp.newFolder("extract_content");

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);
        File subFile = null;
        for (File f : result) {
            if ("002.png".equals(f.getName())) {
                subFile = f;
            }
        }
        byte[] bytes = Files.readAllBytes(subFile.toPath());
        assertEquals("img-sub", new String(bytes, "UTF-8"));
    }

    // 注：ZipUtils 错误路径（文件不存在等）依赖 LogUtil→LogImpl，
    // 其静态初始化需要 Android Application 上下文，纯 JVM 测试无法覆盖，故不测该分支。

    @Test
    public void extractImages_emptyZipReturnsEmpty() throws IOException {
        File zip = tmp.newFile("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            // 不写入任何条目
        }
        File extractDir = tmp.newFolder("extract_empty");
        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);
        assertTrue(result.isEmpty());
    }

    /**
     * 打包工具误写入绝对路径时，应去掉前缀后安全解出，而不是丢弃整个包的图片；
     * 去掉前缀后仍含 ".." 的条目依旧必须拦截。
     */
    @Test
    public void extractImages_normalizesAbsoluteEntryPaths() throws IOException {
        File zip = tmp.newFile("absolute.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            putEntry(zos, "/sub/003.jpg", "abs-unix");
            putEntry(zos, "\\win\\004.jpg", "abs-win");
            putEntry(zos, "D:/drive/005.jpg", "abs-drive");
            putEntry(zos, "/../evil2.jpg", "traversal-absolute");
        }
        File extractDir = tmp.newFolder("extract_absolute");

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);

        assertEquals(3, result.size());
        for (File f : result) {
            assertTrue(f.getAbsolutePath().startsWith(extractDir.getAbsolutePath() + File.separator));
        }
        assertTrue(new File(extractDir, "sub/003.jpg").isFile());
        assertTrue(new File(extractDir, "win/004.jpg").isFile());
        assertTrue(new File(extractDir, "drive/005.jpg").isFile());
        assertFalse(new File(extractDir.getParentFile(), "evil2.jpg").exists());
    }

    /** 带 UTF-8 标志的中文条目名应保持原样，不能被 ISO-8859-1 打开方式破坏。 */
    @Test
    public void extractImages_utf8ChineseNames() throws IOException {
        File zip = createZipWithNames(StandardCharsets.UTF_8, "漫画/第01页.jpg", "漫画/第02页.jpg");
        File extractDir = tmp.newFolder("extract_utf8_names");

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);

        assertEquals(2, result.size());
        File firstPage = new File(extractDir, "漫画/第01页.jpg");
        assertTrue(firstPage.isFile());
        assertTrue(new File(extractDir, "漫画/第02页.jpg").isFile());
        assertEquals("content-漫画/第01页.jpg",
                new String(Files.readAllBytes(firstPage.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * Windows 工具生成的 GBK 文件名（未设置 UTF-8 标志）在系统默认 UTF-8 严格解码下
     * 会抛 MALFORMED；应自动回退为 GBK/GB18030 并正确还原文件名。
     */
    @Test
    public void extractImages_gbkChineseNames() throws IOException {
        File zip = createZipWithNames(Charset.forName("GBK"), "中文/第一页.jpg", "中文/第二页.jpg");
        File extractDir = tmp.newFolder("extract_gbk_names");

        List<File> result = ZipUtils.extractImagesFromZip(zip.getAbsolutePath(), extractDir);

        assertEquals(2, result.size());
        File firstPage = new File(extractDir, "中文/第一页.jpg");
        assertTrue(firstPage.isFile());
        assertTrue(new File(extractDir, "中文/第二页.jpg").isFile());
        assertEquals("content-中文/第一页.jpg",
                new String(Files.readAllBytes(firstPage.toPath()), StandardCharsets.UTF_8));
    }

    private File createZipWithNames(Charset charset, String... names) throws IOException {
        File zip = tmp.newFile("names-" + charset.name() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip), charset)) {
            for (String name : names) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(("content-" + name).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }
}
