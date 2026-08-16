package com.wrz.reading.ui.read.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件排序工具类
 * 支持多种文件名格式的自然排序
 */
public class FileSorter {

    // 匹配文件名中的数字部分
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    /**
     * 按文件名自然排序（升序）
     * 支持格式：001.jpg, page1.jpg, chapter_1.jpg, 1.jpg 等
     */
    public static List<File> sort(List<File> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return imageFiles;
        }

        List<File> sorted = new ArrayList<>(imageFiles);
        sorted.sort(createNaturalComparator(false));
        return sorted;
    }

    /**
     * 按文件名自然排序（降序）
     */
    public static List<File> sortReverse(List<File> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return imageFiles;
        }

        List<File> sorted = new ArrayList<>(imageFiles);
        sorted.sort(createNaturalComparator(true));
        return sorted;
    }

    /**
     * 按完整路径自然排序（用于包含子目录的情况）
     */
    public static List<File> sortByPath(List<File> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return imageFiles;
        }

        List<File> sorted = new ArrayList<>(imageFiles);
        sorted.sort(createPathComparator());
        return sorted;
    }

    /**
     * 创建自然排序比较器
     * 自然排序：1, 2, 10, 20 而不是 1, 10, 2, 20
     */
    private static Comparator<File> createNaturalComparator(boolean reverse) {
        return (f1, f2) -> {
            int result = naturalCompare(f1.getName(), f2.getName());
            return reverse ? -result : result;
        };
    }

    /**
     * 创建路径比较器（先按目录分组，再按文件名排序）
     */
    private static Comparator<File> createPathComparator() {
        return (f1, f2) -> {
            String path1 = f1.getAbsolutePath();
            String path2 = f2.getAbsolutePath();

            // 获取父目录路径
            String parent1 = f1.getParent() != null ? f1.getParent() : "";
            String parent2 = f2.getParent() != null ? f2.getParent() : "";

            // 先比较父目录
            int dirCompare = parent1.compareToIgnoreCase(parent2);
            if (dirCompare != 0) {
                return dirCompare;
            }

            // 同一目录下按文件名自然排序
            return naturalCompare(f1.getName(), f2.getName());
        };
    }

    /**
     * 自然排序比较
     * 将字符串拆分为文本段和数字段进行比较
     */
    private static int naturalCompare(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == null ? (s2 == null ? 0 : -1) : 1;
        }

        // 去掉扩展名进行比较（保留扩展名作为次要排序）
        String name1 = removeExtension(s1);
        String name2 = removeExtension(s2);
        String ext1 = getExtension(s1);
        String ext2 = getExtension(s2);

        // 分段比较
        List<String> segments1 = splitIntoSegments(name1);
        List<String> segments2 = splitIntoSegments(name2);

        int minSize = Math.min(segments1.size(), segments2.size());
        for (int i = 0; i < minSize; i++) {
            String seg1 = segments1.get(i);
            String seg2 = segments2.get(i);

            boolean isNum1 = isNumeric(seg1);
            boolean isNum2 = isNumeric(seg2);

            if (isNum1 && isNum2) {
                // 两个都是数字，按数值比较
                try {
                    long num1 = Long.parseLong(seg1);
                    long num2 = Long.parseLong(seg2);
                    int numCompare = Long.compare(num1, num2);
                    if (numCompare != 0) {
                        return numCompare;
                    }
                } catch (NumberFormatException e) {
                    // 解析失败，按字符串比较
                    int strCompare = seg1.compareToIgnoreCase(seg2);
                    if (strCompare != 0) {
                        return strCompare;
                    }
                }
            } else if (isNum1) {
                // 数字排在文字前面
                return -1;
            } else if (isNum2) {
                // 文字排在数字后面
                return 1;
            } else {
                // 两个都是文字，按字符串比较
                int strCompare = seg1.compareToIgnoreCase(seg2);
                if (strCompare != 0) {
                    return strCompare;
                }
            }
        }

        // 前面都相同，比较长度
        int lengthCompare = Integer.compare(segments1.size(), segments2.size());
        if (lengthCompare != 0) {
            return lengthCompare;
        }

        // 长度也相同，比较扩展名
        return ext1.compareToIgnoreCase(ext2);
    }

    /**
     * 将字符串拆分为文本段和数字段
     * 例如："page001_extra" -> ["page", "001", "_extra"]
     */
    private static List<String> splitIntoSegments(String input) {
        List<String> segments = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return segments;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            // 添加数字前的文本段
            if (matcher.start() > lastEnd) {
                segments.add(input.substring(lastEnd, matcher.start()));
            }
            // 添加数字段
            segments.add(matcher.group());
            lastEnd = matcher.end();
        }

        // 添加最后的文本段
        if (lastEnd < input.length()) {
            segments.add(input.substring(lastEnd));
        }

        return segments;
    }

    /**
     * 判断字符串是否为数字
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 去掉文件扩展名
     */
    private static String removeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * 获取文件扩展名（包含点号）
     */
    private static String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }
}
