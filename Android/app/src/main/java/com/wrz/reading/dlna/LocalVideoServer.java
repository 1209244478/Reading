package com.wrz.reading.dlna;

import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本地 HTTP 服务器，用于将手机上的视频文件以 HTTP 流的形式提供给电视拉取播放。
 * 支持 HTTP Range 请求（电视拖动进度时使用），返回 206 Partial Content。
 */
public class LocalVideoServer extends NanoHTTPD {

    private final File videoFile;

    public LocalVideoServer(int port, File videoFile) {
        super(port);
        this.videoFile = videoFile;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        // NanoHTTPD 的 header key 均为小写
        String range = headers.get("range");
        long fileLength = videoFile.length();
        String mime = getMimeType(videoFile.getName());

        // 没有 Range 请求，返回全部内容（200）
        if (TextUtils.isEmpty(range)) {
            try {
                FileInputStream fis = new FileInputStream(videoFile);
                Response response = newFixedLengthResponse(Response.Status.OK, mime, fis, fileLength);
                response.addHeader("Accept-Ranges", "bytes");
                return response;
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "文件读取失败");
            }
        }

        // 解析 Range: bytes=start-end（end 可省略）
        long start = 0;
        long end = fileLength - 1;
        String rangeValue = range.substring(range.indexOf("=") + 1);
        if (rangeValue.contains("-")) {
            String[] parts = rangeValue.split("-");
            try {
                if (!parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                }
            } catch (NumberFormatException e) {
                start = 0;
            }
            if (parts.length > 1 && !parts[1].isEmpty()) {
                try {
                    end = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    end = fileLength - 1;
                }
            }
        }

        // 范围不合法
        if (start >= fileLength || start > end) {
            Response resp = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "");
            resp.addHeader("Content-Range", "bytes */" + fileLength);
            return resp;
        }
        if (end >= fileLength) {
            end = fileLength - 1;
        }
        long contentLength = end - start + 1;

        try {
            FileInputStream fis = new FileInputStream(videoFile);
            // 跳到起始字节（skip 可能一次跳不完，循环跳）
            long skipped = 0;
            while (skipped < start) {
                long s = fis.skip(start - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            // newFixedLengthResponse 会读取 contentLength 字节后关闭流
            Response response = newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT, mime, fis, contentLength);
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            return response;
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "文件读取失败");
        }
    }

    /**
     * 根据文件扩展名返回 MIME 类型。
     */
    public static String getMimeType(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        if (lower.endsWith(".wmv")) return "video/x-ms-wmv";
        if (lower.endsWith(".rmvb") || lower.endsWith(".rm")) {
            return "application/vnd.rn-realmedia-vbr";
        }
        return "application/octet-stream";
    }
}
