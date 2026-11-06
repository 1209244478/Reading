package com.wrz.reading.ui.read.transfer;

import android.content.Context;
import android.text.TextUtils;

import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

/**
 * 局域网导入服务：设备端启动一个极简 HTTP 服务器，电脑/手机浏览器扫码打开上传页后，
 * 以原始请求体（Content-Length）把文件直接写入目标目录，避免大视频在内存或临时目录中二次拷贝。
 * <p>
 * URL 中携带随机 token，只有扫描设备屏幕二维码（或手动输入完整地址）才能访问上传页。
 * 页面本身为静态资源 {@code res/raw/import_page.html}，通过相对路径 {@code upload} 提交文件。
 */
public class LocalImportServer extends NanoHTTPD {

    /** 默认端口，被占用时自动递增重试 */
    public static final int DEFAULT_PORT = 8899;
    private static final int PORT_RETRY_COUNT = 20;

    /** 单文件上限（8GB），避免异常请求撑爆磁盘 */
    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024 * 1024;
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final long PROGRESS_INTERVAL_MS = 400;
    /** 大文件上传时两次读取之间可能间隔较久，放宽 socket 读超时（5 分钟） */
    private static final int SOCKET_TIMEOUT = 5 * 60 * 1000;

    private final Context context;
    private final String token;
    private final File destDir;
    private final Listener listener;

    /** 串行化「选最终文件名 + 重命名」，避免多设备同时上传同名文件互相覆盖 */
    private final Object uploadLock = new Object();
    private static final AtomicInteger PART_FILE_SEQ = new AtomicInteger();

    private String pageHtml;

    public LocalImportServer(Context context, int port, String token, File destDir,
                             Listener listener) {
        super(port);
        this.context = context.getApplicationContext();
        this.token = token;
        this.destDir = destDir;
        this.listener = listener;
    }

    /**
     * 启动服务，端口从 {@link #DEFAULT_PORT} 开始递增重试。
     *
     * @return 启动成功的服务实例；全部端口被占用时返回 null
     */
    public static LocalImportServer startServer(Context context, String token, File destDir,
                                                Listener listener) {
        for (int i = 0; i < PORT_RETRY_COUNT; i++) {
            LocalImportServer server =
                    new LocalImportServer(context, DEFAULT_PORT + i, token, destDir, listener);
            try {
                // daemon 线程：页面关闭后即使 stop 失败也不会阻止进程退出
                server.start(SOCKET_TIMEOUT);
                return server;
            } catch (Exception e) {
                LogUtil.e("LocalImportServer", "端口 " + (DEFAULT_PORT + i) + " 启动失败: "
                        + e.getMessage());
                try {
                    server.stop();
                } catch (Exception ignored) {
                    // 端口冲突时无需处理
                }
            }
        }
        return null;
    }

    /**
     * 获取用于局域网访问的本机 IPv4 地址（优先 wlan/eth，排除蜂窝网络）。
     */
    public static String findLanIpAddress() {
        String fallback = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en != null && en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp() || intf.isLoopback()) {
                    continue;
                }
                String name = intf.getName() == null ? ""
                        : intf.getName().toLowerCase(Locale.ROOT);
                boolean preferred = name.startsWith("wlan")
                        || name.startsWith("ap") || name.startsWith("eth");
                for (Enumeration<InetAddress> addresses = intf.getInetAddresses();
                     addresses.hasMoreElements(); ) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)
                            || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (preferred) {
                        return ip;
                    }
                    if (fallback == null && !isCellularInterface(name)) {
                        fallback = ip;
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.e("LocalImportServer", "findLanIpAddress: " + e.getMessage());
        }
        return fallback;
    }

    private static boolean isCellularInterface(String name) {
        return name.startsWith("rmnet") || name.startsWith("ccmni")
                || name.startsWith("pdp") || name.startsWith("ppp")
                || name.startsWith("tun") || name.startsWith("clat");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String base = "/t/" + token;

        // 手动输入根地址时给出提示，不泄露 token
        if ("/".equals(uri) || "/index.html".equals(uri)) {
            return text(Response.Status.OK,
                    "请在手机「妙妙小工具」中打开远程导入，并使用浏览器扫描二维码。");
        }
        if (!uri.equals(base) && !uri.equals(base + "/") && !uri.equals(base + "/upload")) {
            return text(Response.Status.NOT_FOUND, "页面不存在");
        }
        if (uri.equals(base + "/upload")) {
            Method method = session.getMethod();
            if (method != Method.POST && method != Method.PUT) {
                return text(Response.Status.METHOD_NOT_ALLOWED, "请使用 POST 上传");
            }
            return handleUpload(session);
        }

        Response response = newFixedLengthResponse(Response.Status.OK,
                "text/html; charset=utf-8", getPageHtml());
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    private Response handleUpload(IHTTPSession session) {
        String requestedName = firstParam(session.getParameters(), "name");
        if (TextUtils.isEmpty(requestedName)) {
            requestedName = "upload_" + System.currentTimeMillis();
        }
        String displayName = sanitizeName(requestedName);

        if (!isSupportedFile(displayName)) {
            notifyFailed(displayName, "不支持的文件类型");
            return text(Response.Status.UNSUPPORTED_MEDIA_TYPE,
                    "不支持的文件类型：" + displayName + "\n仅支持 zip、pdf、epub、视频、音乐");
        }

        long contentLength = parseContentLength(session.getHeaders().get("content-length"));
        if (contentLength <= 0) {
            notifyFailed(displayName, "缺少文件大小");
            return text(Response.Status.LENGTH_REQUIRED, "缺少 Content-Length");
        }
        if (contentLength > MAX_UPLOAD_BYTES) {
            notifyFailed(displayName, "文件超过 8GB");
            return text(Response.Status.PAYLOAD_TOO_LARGE, "文件超过 8GB，无法接收");
        }
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            notifyFailed(displayName, "无法创建接收目录");
            return text(Response.Status.INTERNAL_ERROR, "无法创建接收目录");
        }

        File partFile = new File(destDir, ".upload_" + System.currentTimeMillis()
                + "_" + PART_FILE_SEQ.incrementAndGet() + ".part");
        notifyStarted(displayName, contentLength);

        long received = 0;
        long lastProgressAt = 0;
        // 注意：不能关闭 session.getInputStream()，NanoHTTPD 关闭输入流会一并关闭 socket，
        // 导致响应（以及浏览器端的成功回调）无法送达。
        InputStream in = session.getInputStream();
        try (FileOutputStream out = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (received < contentLength) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, contentLength - received));
                if (read <= 0) {
                    break;
                }
                out.write(buffer, 0, read);
                received += read;
                long now = System.currentTimeMillis();
                if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                    lastProgressAt = now;
                    notifyProgress(displayName, received, contentLength);
                }
            }
            out.flush();
        } catch (IOException e) {
            deleteQuietly(partFile);
            notifyFailed(displayName, "接收失败：" + e.getMessage());
            return text(Response.Status.INTERNAL_ERROR, "接收失败，请重试");
        }

        if (received != contentLength) {
            deleteQuietly(partFile);
            notifyFailed(displayName, "传输中断");
            return text(Response.Status.BAD_REQUEST, "传输中断，请重试");
        }

        File target;
        synchronized (uploadLock) {
            target = uniqueFile(destDir, displayName);
            if (!partFile.renameTo(target)) {
                if (!copyFile(partFile, target)) {
                    deleteQuietly(partFile);
                    notifyFailed(displayName, "保存文件失败");
                    return text(Response.Status.INTERNAL_ERROR, "保存文件失败");
                }
                deleteQuietly(partFile);
            }
        }

        notifyProgress(target.getName(), contentLength, contentLength);
        notifySaved(target);

        String json = "{\"ok\":true,\"name\":\"" + escapeJson(target.getName())
                + "\",\"size\":" + contentLength + "}";
        return newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", json);
    }

    private String getPageHtml() {
        if (pageHtml != null) {
            return pageHtml;
        }
        try (InputStream in = context.getResources().openRawResource(R.raw.import_page)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            pageHtml = new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            LogUtil.e("LocalImportServer", "读取上传页面失败: " + e.getMessage());
            pageHtml = "<html><body><p>上传页面加载失败，请重新打开远程导入。</p></body></html>";
        }
        return pageHtml;
    }

    private static Response text(Response.IStatus status, String message) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", message);
    }

    private static String firstParam(Map<String, List<String>> params, String key) {
        if (params == null) {
            return null;
        }
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static long parseContentLength(String value) {
        if (TextUtils.isEmpty(value)) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isSupportedFile(String name) {
        return !FileUtils.isSupportedExtFormat(name).isEmpty()
                || FileUtils.isVideoFile(name)
                || FileUtils.isMusicFile(name);
    }

    /**
     * 去掉路径与非法字符，避免通过浏览器传入 "../" 等名称写出目标目录。
     * 超长文件名保留扩展名并截断主体。
     */
    private static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t\\x00-\\x1f:*?\"<>|]", "_").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "upload";
        }
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 120 - ext.length()) + ext;
        }
        return name;
    }

    private static File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            file = new File(dir, base + "(" + i + ")" + ext);
            if (!file.exists()) {
                return file;
            }
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private static boolean copyFile(File source, File target) {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            deleteQuietly(target);
            return false;
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void notifyStarted(String name, long totalBytes) {
        if (listener != null) {
            try {
                listener.onUploadStarted(name, totalBytes);
            } catch (Exception e) {
                LogUtil.e("LocalImportServer", "onUploadStarted: " + e.getMessage());
            }
        }
    }

    private void notifyProgress(String name, long received, long totalBytes) {
        if (listener != null) {
            try {
                listener.onUploadProgress(name, received, totalBytes);
            } catch (Exception e) {
                LogUtil.e("LocalImportServer", "onUploadProgress: " + e.getMessage());
            }
        }
    }

    private void notifySaved(File file) {
        if (listener != null) {
            try {
                listener.onFileSaved(file);
            } catch (Exception e) {
                LogUtil.e("LocalImportServer", "onFileSaved: " + e.getMessage());
            }
        }
    }

    private void notifyFailed(String name, String message) {
        if (listener != null) {
            try {
                listener.onUploadFailed(name, message);
            } catch (Exception e) {
                LogUtil.e("LocalImportServer", "onUploadFailed: " + e.getMessage());
            }
        }
    }

    /**
     * 上传事件回调，均在 NanoHTTPD 工作线程触发，调用方需自行切回主线程更新 UI。
     */
    public interface Listener {

        void onUploadStarted(String name, long totalBytes);

        void onUploadProgress(String name, long received, long totalBytes);

        void onFileSaved(File file);

        void onUploadFailed(String name, String message);
    }
}
