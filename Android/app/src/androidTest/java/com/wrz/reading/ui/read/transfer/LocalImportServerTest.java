package com.wrz.reading.ui.read.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.wrz.reading.ui.read.utils.QRCodeUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 局域网导入服务的端到端测试：启动服务后通过 HTTP 上传文件，
 * 校验页面、原始请求体落盘、回调、重名处理与非法类型拦截。
 */
@RunWith(AndroidJUnit4.class)
public class LocalImportServerTest {

    private static final String TOKEN = "testtoken1234";

    private Context context;
    private File destDir;
    private LocalImportServer server;
    private final List<String> savedNames = new CopyOnWriteArrayList<>();
    private volatile String savedContent;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        destDir = new File(context.getCacheDir(), "import_server_test");
        deleteRecursively(destDir);
        assertTrue(destDir.mkdirs());
        server = LocalImportServer.startServer(context, TOKEN, destDir, new LocalImportServer.Listener() {
            @Override
            public void onUploadStarted(String name, long totalBytes) {
            }

            @Override
            public void onUploadProgress(String name, long received, long totalBytes) {
            }

            @Override
            public void onFileSaved(File file) {
                savedNames.add(file.getName());
                try {
                    savedContent = readAll(new FileInputStream(file));
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onUploadFailed(String name, String message) {
            }
        });
        assertNotNull("服务应在可用端口上启动", server);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
        deleteRecursively(destDir);
    }

    @Test
    public void servesUploadPage() throws Exception {
        HttpURLConnection connection = open("/t/" + TOKEN + "/");
        assertEquals(200, connection.getResponseCode());
        String html = readAll(connection.getInputStream());
        assertTrue(html.contains("局域网传书"));
        assertTrue(html.contains("upload?name="));
    }

    @Test
    public void rejectsUnknownToken() throws Exception {
        HttpURLConnection connection = open("/t/wrongtoken/");
        assertEquals(404, connection.getResponseCode());
    }

    @Test
    public void uploadsFileAndHandlesDuplicateName() throws Exception {
        byte[] payload = "fake-zip-content".getBytes("UTF-8");
        assertEquals(200, upload("漫画.zip", payload));
        assertEquals(200, upload("漫画.zip", payload));

        File first = new File(destDir, "漫画.zip");
        assertTrue(first.isFile());
        assertEquals(payload.length, first.length());
        // 重名时自动追加序号，避免覆盖已有文件
        assertTrue(new File(destDir, "漫画(1).zip").isFile());
        assertEquals("fake-zip-content", savedContent);
        assertEquals(2, savedNames.size());
    }

    @Test
    public void sanitizesPathTraversalName() throws Exception {
        assertEquals(200, upload("../evil.zip", "x".getBytes("UTF-8")));
        assertTrue(new File(destDir, "evil.zip").isFile());
        assertFalse(new File(destDir.getParentFile(), "evil.zip").exists());
    }

    @Test
    public void rejectsUnsupportedFileType() throws Exception {
        assertEquals(415, upload("notes.txt", "hello".getBytes("UTF-8")));
        assertFalse(new File(destDir, "notes.txt").exists());
    }

    @Test
    public void generatesQrCodeBitmap() {
        Bitmap bitmap = QRCodeUtil.generate(
                "http://192.168.1.10:8899/t/" + TOKEN + "/", 320);
        assertNotNull(bitmap);
        assertEquals(320, bitmap.getWidth());
        assertEquals(320, bitmap.getHeight());
    }

    private HttpURLConnection open(String path) throws Exception {
        URL url = new URL("http://127.0.0.1:" + server.getListeningPort() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        return connection;
    }

    private int upload(String name, byte[] payload) throws Exception {
        String encoded = URLEncoder.encode(name, "UTF-8");
        HttpURLConnection connection =
                open("/t/" + TOKEN + "/upload?name=" + encoded);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(payload.length);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        try (java.io.OutputStream out = connection.getOutputStream()) {
            out.write(payload);
            out.flush();
        }
        int code = connection.getResponseCode();
        connection.disconnect();
        return code;
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
