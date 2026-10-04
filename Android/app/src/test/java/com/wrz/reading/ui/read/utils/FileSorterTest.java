package com.wrz.reading.ui.read.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.wrz.reading.ui.read.model.ReadItem;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * FileSorter 自然排序单元测试（纯 JVM，无 Android 依赖）。
 */
public class FileSorterTest {

    private static File file(String path) {
        return new File(path);
    }

    private static List<String> names(List<File> files) {
        List<String> result = new ArrayList<>();
        for (File f : files) {
            result.add(f.getName());
        }
        return result;
    }

    @Test
    public void sort_naturalOrder_numbersNotLexicographic() {
        List<File> files = Arrays.asList(
                file("10.jpg"), file("1.jpg"), file("2.jpg"), file("20.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("1.jpg", "2.jpg", "10.jpg", "20.jpg"), names(sorted));
    }

    @Test
    public void sort_prefixNumber_page2BeforePage10() {
        List<File> files = Arrays.asList(file("page10.jpg"), file("page2.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("page2.jpg", "page10.jpg"), names(sorted));
    }

    @Test
    public void sort_numericSegmentBeforeText() {
        List<File> files = Arrays.asList(file("cover.jpg"), file("001.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("001.jpg", "cover.jpg"), names(sorted));
    }

    @Test
    public void sort_chineseFileName() {
        List<File> files = Arrays.asList(file("第2话.jpg"), file("第10话.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("第2话.jpg", "第10话.jpg"), names(sorted));
    }

    @Test
    public void sort_extensionTieBreak() {
        List<File> files = Arrays.asList(file("a.png"), file("a.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("a.jpg", "a.png"), names(sorted));
    }

    @Test
    public void sort_nullAndEmptyReturnSameInstance() {
        assertTrue(FileSorter.sort(null) == null || FileSorter.sort(null).isEmpty());
        List<File> empty = new ArrayList<>();
        assertSame(empty, FileSorter.sort(empty));
    }

    @Test
    public void sort_reverseOrder() {
        List<File> files = Arrays.asList(file("1.jpg"), file("10.jpg"), file("2.jpg"));
        List<File> sorted = FileSorter.sortReverse(files);
        assertEquals(Arrays.asList("10.jpg", "2.jpg", "1.jpg"), names(sorted));
    }

    @Test
    public void sortByPath_groupsByDirectoryThenNaturalName() {
        List<File> files = new ArrayList<>(Arrays.asList(
                file("/root/sub/2.jpg"),
                file("/root/aaa/10.jpg"),
                file("/root/sub/1.jpg"),
                file("/root/aaa/2.jpg")));
        List<File> sorted = FileSorter.sortByPath(files);
        assertEquals(Arrays.asList(
                "/root/aaa/2.jpg",
                "/root/aaa/10.jpg",
                "/root/sub/1.jpg",
                "/root/sub/2.jpg"), paths(sorted));
    }

    private static List<String> paths(List<File> files) {
        List<String> result = new ArrayList<>();
        for (File f : files) {
            result.add(f.getPath().replace('\\', '/'));
        }
        return result;
    }

    @Test
    public void sortReadItems_naturalOrder() {
        List<ReadItem> items = new ArrayList<>(Arrays.asList(
                new ReadItem(file("10.jpg")),
                new ReadItem(file("2.jpg")),
                new ReadItem(file("1.jpg"))));
        List<ReadItem> sorted = FileSorter.sortReadItems(items, false);
        assertEquals(Arrays.asList("1.jpg", "2.jpg", "10.jpg"), namesOfReadItems(sorted));

        List<ReadItem> reversed = FileSorter.sortReadItems(items, true);
        assertEquals(Arrays.asList("10.jpg", "2.jpg", "1.jpg"), namesOfReadItems(reversed));
    }

    private static List<String> namesOfReadItems(List<ReadItem> items) {
        List<String> result = new ArrayList<>();
        for (ReadItem item : items) {
            result.add(item.getFile().getName());
        }
        return result;
    }

    @Test
    public void sort_originalListNotMutated() {
        List<File> files = Arrays.asList(file("2.jpg"), file("1.jpg"));
        List<File> sorted = FileSorter.sort(files);
        assertEquals(Arrays.asList("2.jpg", "1.jpg"), names(files));
        assertEquals(Arrays.asList("1.jpg", "2.jpg"), names(sorted));
    }
}
