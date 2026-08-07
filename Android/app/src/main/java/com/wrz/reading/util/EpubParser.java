package com.wrz.reading.util;

import android.util.Log;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpubParser {
    private static final String TAG = "EpubParser";
    private static final int MAX_WORDS_PER_PAGE = 5000; // 每页最大字数限制

    public static class EpubBook {
        public String title;          // 书名
        public List<String> pages;    // 分页后的内容

        public EpubBook(String title, List<String> pages) {
            this.title = title;
            this.pages = pages;
        }
    }

    public static EpubBook parseEpub(File epubFile) throws Exception {
        String bookTitle = "未知书名";
        List<String> allPages = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(epubFile)) {
            List<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries());

            // 1. 找到OPF文件并解析书名
            String opfPath = findOpfPath(entries);
            if (opfPath == null) {
                throw new Exception("未找到OPF文件");
            }

            // 解析书名
            bookTitle = parseBookTitle(zipFile, opfPath);

            // 2. 解析章节顺序
            List<String> chapterPaths = parseOpfContentOrder(zipFile);

            // 3. 读取并分页每个章节
            for (String chapterPath : chapterPaths) {
                ZipArchiveEntry entry = zipFile.getEntry(chapterPath);
                if (entry != null) {
                    String html = readStream(zipFile.getInputStream(entry));

                    // 分页处理：每页*字，传入书名
                    List<String> pages = splitIntoPages(html, chapterPath, bookTitle);
                    allPages.addAll(pages);
                }
            }
        }
        return new EpubBook(bookTitle, allPages);
    }

    // 从OPF文件中解析书名
    private static String parseBookTitle(ZipFile zipFile, String opfPath) throws Exception {
        ZipArchiveEntry opfEntry = zipFile.getEntry(opfPath);
        if (opfEntry == null) return "未知书名";

        String opfContent = readStream(zipFile.getInputStream(opfEntry));

        // 使用正则提取<title>标签内容
        Pattern titlePattern = Pattern.compile("<dc:title[^>]*>(.*?)</dc:title>", Pattern.DOTALL);
        Matcher matcher = titlePattern.matcher(opfContent);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果dc:title不存在，尝试提取<metadata>中的title
        Pattern metaTitlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL);
        matcher = metaTitlePattern.matcher(opfContent);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "未知书名";
    }

    // 将HTML内容按字数分页（保留书名）
    private static List<String> splitIntoPages(String html, String chapterName, String bookTitle) {
        List<String> pages = new ArrayList<>();

        // 正则表达式模式
        Pattern pattern = Pattern.compile("<h1\\s+class=\"title\"\\s*>(.*?)</h1>");
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            chapterName = (chapterName
                    .replaceAll(".xhtml", "")
                    .replaceAll(".html", "")
                    + ": " + matcher.group(1));
        }

        String content = extractTextFromHtml(html);

        int totalChars = content.length();
        int start = 0;
        int pageCount = 1;

        while (start < totalChars) {
            int end = Math.min(start + MAX_WORDS_PER_PAGE, totalChars);

            // 智能断句
            if (end < totalChars) {
                int lastBreak = content.lastIndexOf("。", end);
                int lastNewline = content.lastIndexOf("\n", end);

                if (lastBreak > start && lastBreak > end - 50) {
                    end = lastBreak + 1;
                } else if (lastNewline > start && lastNewline > end - 50) {
                    end = lastNewline + 1;
                }
            }

            String pageText = content.substring(start, end).trim();

            // 传入书名和章节名
            String pageHtml = wrapAsHtmlPage(pageText, chapterName, bookTitle, pageCount);
            pages.add(pageHtml);

            start = end;
            pageCount++;
        }

        return pages;
    }

    // 将文本包装成HTML页面（显示书名）
    private static String wrapAsHtmlPage(String text, String chapterName, String bookTitle, int pageCount) {
        String css = "<style>" +
                "body { " +
                "   font-family: 'serif'; " +
                "   font-size: 17px; " +
                "   line-height: 1.9; " +
                "   margin: 32px 40px; " +
                "   background: #FAFAFA; " +
                "   color: #333; " +
                "}" +
                ".book-title { " +
                "   font-size: 16px; " +
                "   font-weight: bold; " +
                "   color: #666; " +
                "   margin-bottom: 8px; " +
                "   text-align: center; " +
                "   font-family: sans-serif; " +
                "}" +
                ".chapter-title { " +
                "   font-size: 19px; " +
                "   font-weight: bold; " +
                "   margin-bottom: 16px; " +
                "   color: #6200EE; " +
                "   border-bottom: 2px solid #6200EE; " +
                "   padding-bottom: 6px; " +
                "}" +
                ".content { " +
                "   text-align: justify; " +
                "   margin: 12px 0; " +
                "}" +
                ".page-info { " +
                "   position: fixed; " +
                "   bottom: 20px; " +
                "   right: 20px; " +
                "   color: #999; " +
                "   font-size: 13px; " +
                "   font-family: sans-serif; " +
                "}" +
                "</style>";


        // 提取简单章节名
        String simpleChapterName = chapterName
                .replaceAll(".*\\/", "")
                .replaceAll("\\.(html|xhtml)$", "")
                .replaceAll("_", " ");


        return "<html><head>" + css + "</head><body>" +
                "<div class='book-title'>" + bookTitle + "</div>" +
                "<div class='chapter-title'>" + simpleChapterName + "</div>" +
                "<div class='content'>" + text + "</div>" +
                "<div class='page-info'>第 " + pageCount + " 页</div>" +
                "</body></html>";
    }

    // 从HTML中提取文本内容
    private static String extractTextFromHtml(String html) {

//        String cleaned = html
//                .replaceAll("<script[^>]*>.*?</script>", "")
//                .replaceAll("<style[^>]*>.*?</style>", "")
//                .replaceAll("<[^>]+>", " ")
//                .replaceAll("&nbsp;", " ")
//                .replaceAll("\\s+", " ")
//                .trim();
        return html;
    }

    // 其余辅助方法保持不变
    private static String findOpfPath(List<ZipArchiveEntry> entries) {
        for (ZipArchiveEntry entry : entries) {
            if (entry.getName().endsWith(".opf")) {
                return entry.getName();
            }
        }
        return null;
    }

    private static List<String> parseOpfContentOrder(ZipFile zipFile) {
        List<String> htmlFiles = new ArrayList<>();
        for (ZipArchiveEntry entry : Collections.list(zipFile.getEntries())) {
            String name = entry.getName();

            if (name.matches(".*\\.(html|xhtml)$") &&
                    !name.contains("nav") && !name.contains("cover")) {
                htmlFiles.add(name);
            }
        }
        Collections.sort(htmlFiles);
        return htmlFiles;
    }

    public static String extractTitlePrecise(String hrefLine) {
        // 匹配 xhtml" title=" 后面的内容
        String regex = "xhtml\" title=\"([^\"]*)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(hrefLine);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static String readStream(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        return content.toString();
    }
}
