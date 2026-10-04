package com.wrz.reading.ui.wheel.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Tag;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * WheelConverter（Room TypeConverter）序列化往返测试（纯 JVM）。
 * 覆盖历史教训：Gson 将 JSON 数字反序列化为 Integer 导致 Long 转换异常——
 * 本类字段均为 int/String，需保证往返后类型与值一致。
 */
public class WheelConverterTest {

    @Test
    public void optionList_nullReturnsNull() {
        assertNull(WheelConverter.fromOptionListToString(null));
    }

    @Test
    public void optionList_emptyOrNullJsonReturnsEmptyList() {
        assertTrue(WheelConverter.fromStringToOptionList(null).isEmpty());
        assertTrue(WheelConverter.fromStringToOptionList("").isEmpty());
    }

    @Test
    public void optionList_roundTrip() {
        List<List<Option>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(Arrays.asList(
                new Option("红色", 0xFFFF0000, 1),
                new Option("蓝色", 0xFF0000FF, 3))));
        groups.add(new ArrayList<>(Arrays.asList(
                new Option("绿色", 0xFF00FF00, 2))));

        String json = WheelConverter.fromOptionListToString(groups);
        List<List<Option>> decoded = WheelConverter.fromStringToOptionList(json);

        assertNotNull(decoded);
        assertEquals(2, decoded.size());
        assertEquals(2, decoded.get(0).size());
        assertEquals(1, decoded.get(1).size());

        Option first = decoded.get(0).get(0);
        assertEquals("红色", first.getOption());
        assertEquals(0xFFFF0000, first.getColor());
        assertEquals(1, first.getWeight());
    }

    @Test
    public void optionList_largeWeightValue_roundTrip() {
        // 大整数值（超过 short 范围）往返不失真
        List<List<Option>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(Arrays.asList(
                new Option("大权重", 0xFF123456, Integer.MAX_VALUE))));
        String json = WheelConverter.fromOptionListToString(groups);
        List<List<Option>> decoded = WheelConverter.fromStringToOptionList(json);
        assertEquals(Integer.MAX_VALUE, decoded.get(0).get(0).getWeight());
    }

    @Test
    public void tagList_roundTrip() {
        ArrayList<Tag> tags = new ArrayList<>(Arrays.asList(
                new Tag("标签A", 1),
                new Tag("标签B", 0)));

        String json = WheelConverter.fromTagListToString(tags);
        ArrayList<Tag> decoded = WheelConverter.fromStringToTagList(json);

        assertEquals(2, decoded.size());
        assertEquals("标签A", decoded.get(0).getTag());
        assertEquals(1, decoded.get(0).getOpen());
        assertEquals("标签B", decoded.get(1).getTag());
        assertEquals(0, decoded.get(1).getOpen());
    }

    @Test
    public void tagList_nullReturnsNullAndEmptyStringReturnsEmpty() {
        assertNull(WheelConverter.fromTagListToString(null));
        assertTrue(WheelConverter.fromStringToTagList(null).isEmpty());
        assertTrue(WheelConverter.fromStringToTagList("").isEmpty());
    }
}
