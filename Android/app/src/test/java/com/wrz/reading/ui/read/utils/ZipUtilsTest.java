package com.wrz.reading.ui.read.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZipUtils 解压单元测试（纯 JVM，使用真实 zip 包验证）。
 * 覆盖：目录结构保留、非图片过滤、Zip-Slip 路径穿越防护、异常输入容错。
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
}
