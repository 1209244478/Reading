package com.wrz.reading.ui.read.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wrz.reading.ui.read.model.ReadItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FileUtils 目录遍历核心（walkFileTree 统一 collect 实现）单元测试（纯 JVM）。
 */
public class FileUtilsTest {

    private File root;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("fileutils_test").toFile();
    }

    @After
    public void tearDown() {
        deleteRecursively(root);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private File newFile(String relativePath) throws IOException {
        File f = new File(root, relativePath);
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    private static List<String> names(List<File> files) {
        List<String> result = new ArrayList<>();
        for (File f : files) {
            result.add(f.getName());
        }
        return result;
    }

    @Test
    public void getImageFiles_recursive_collectsNestedAndIgnoresOthers() throws IOException {
        newFile("a.jpg");
        newFile("sub/b.png");
        newFile("sub/deep/c.webp");
        newFile("note.txt");

        List<String> names = names(FileUtils.getImageFilesRecursive(root));
        assertEquals(3, names.size());
        assertTrue(names.containsAll(Arrays.asList("a.jpg", "b.png", "c.webp")));
    }

    @Test
    public void getMediaFiles_recursive_includesImageVideoMusic() throws IOException {
        newFile("p1.jpg");
        newFile("v1.mp4");
        newFile("m1.mp3");
        newFile("skip.pdf");

        List<ReadItem> media = FileUtils.getMediaFilesRecursive(root);
        assertEquals(3, media.size());
    }

    @Test
    public void haveImageFiles_recursive_trueWhenOnlyInSubdir() throws IOException {
        newFile("sub/deep/only.png");
        assertTrue(FileUtils.haveImageFilesRecursive(root));
        assertFalse(FileUtils.haveImageFilesRecursive(new File(root, "not_exist")));
    }

    @Test
    public void getSubdirectoriesWithImages_topLevelAncestorsSorted() throws IOException {
        newFile("bb/2.jpg");
        newFile("aa/1.jpg");
        newFile("cc/note.txt");
        newFile("top.jpg"); // 根目录下的图片不计入任何子目录

        List<File> subdirs = FileUtils.getSubdirectoriesWithImages(root);
        assertEquals(Arrays.asList("aa", "bb"), names(subdirs));
    }

    @Test
    public void getSubdirectoriesWithImages_nestedImagesMapToTopAncestor() throws IOException {
        newFile("aa/x/deep/1.jpg");

        List<File> subdirs = FileUtils.getSubdirectoriesWithImages(root);
        assertEquals(1, subdirs.size());
        assertEquals("aa", subdirs.get(0).getName());
    }

    @Test
    public void getImageFiles_nonRecursive_topLevelOnly() throws IOException {
        newFile("2.jpg");
        newFile("10.jpg");
        newFile("1.jpg");
        newFile("sub/3.jpg");

        List<String> names = names(FileUtils.getImageFiles(root, true));
        // 按 compareToIgnoreCase 字典序（与原实现一致，非自然排序）
        assertEquals(Arrays.asList("1.jpg", "10.jpg", "2.jpg"), names);
    }

    @Test
    public void haveImageFiles_nonRecursive_falseWhenOnlyInSubdir() throws IOException {
        newFile("sub/only.png");
        assertFalse(FileUtils.haveImageFiles(root));
        assertTrue(FileUtils.haveImageFilesRecursive(root));
    }

    @Test
    public void getVideoFiles_recursiveSortedByName() throws IOException {
        newFile("b.mp4");
        newFile("sub/a.mkv");

        List<String> names = names(FileUtils.getVideoFiles(root));
        assertEquals(Arrays.asList("a.mkv", "b.mp4"), names);
    }
}
