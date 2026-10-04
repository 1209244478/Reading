package com.wrz.reading.ui.read.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;

/**
 * SubProgress 子进度模型单元测试（纯 JVM）。
 */
public class SubProgressTest {

    @Test
    public void getSubProgressIndex_foundAndNotFound() {
        ArrayList<SubProgress> list = new ArrayList<>();
        list.add(new SubProgress("a.mp4", 100L));
        list.add(new SubProgress("b.mp4", 200L));

        assertEquals(0L, SubProgress.getSubProgressIndex("a.mp4", list));
        assertEquals(1L, SubProgress.getSubProgressIndex("b.mp4", list));
        assertEquals(-1L, SubProgress.getSubProgressIndex("missing.mp4", list));
    }

    @Test
    public void getSubProgress_missingReturnsNegativeOne() {
        ArrayList<SubProgress> list = new ArrayList<>();
        list.add(new SubProgress("a.mp4", 100L));

        assertEquals(100L, SubProgress.getSubProgress("a.mp4", list));
        assertEquals(-1L, SubProgress.getSubProgress("missing.mp4", list));
    }

    @Test
    public void updateSubProgress_insertsWhenAbsent() {
        ArrayList<SubProgress> list = new ArrayList<>();
        SubProgress.updateSubProgress("a.mp4", 42L, list);

        assertEquals(1, list.size());
        assertEquals("a.mp4", list.get(0).getTitle());
        assertEquals(42L, list.get(0).getProgress());
    }

    @Test
    public void updateSubProgress_updatesExistingWithoutDuplicate() {
        ArrayList<SubProgress> list = new ArrayList<>();
        SubProgress.updateSubProgress("a.mp4", 42L, list);
        SubProgress.updateSubProgress("a.mp4", 99L, list);

        assertEquals(1, list.size());
        assertEquals(99L, list.get(0).getProgress());
    }

    @Test
    public void getSubProgress_totalSuffixKey() {
        // 项目中 "_total" 后缀键用于记录总时长，验证同名前缀键互不干扰
        ArrayList<SubProgress> list = new ArrayList<>();
        SubProgress.updateSubProgress("a.mp4", 10L, list);
        SubProgress.updateSubProgress("a.mp4_total", 6000L, list);

        assertEquals(10L, SubProgress.getSubProgress("a.mp4", list));
        assertEquals(6000L, SubProgress.getSubProgress("a.mp4_total", list));
    }

    @Test
    public void sameTitleMatchesByEquals() {
        // getSubProgressIndex 使用 Objects.equals，相同内容标题应匹配
        ArrayList<SubProgress> list = new ArrayList<>();
        list.add(new SubProgress(new String("a.mp4"), 1L));
        assertSame(list.get(0), list.get(0));
        assertEquals(0L, SubProgress.getSubProgressIndex("a.mp4", list));
    }
}
