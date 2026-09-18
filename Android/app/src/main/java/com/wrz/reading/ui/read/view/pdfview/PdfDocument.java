package com.wrz.reading.ui.read.view.pdfview;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * 系统 PdfRenderer 的线程安全封装：
 * - PdfRenderer 同一时刻只允许打开一页且非线程安全，所有渲染任务串行提交到单一 Executor
 * - 渲染结果统一回调到主线程，调用方无需关心线程
 */
public class PdfDocument {

    public interface RenderCallback {
        /** 渲染完成回调（主线程）；失败或文档已关闭时 bitmap 为 null */
        void onRendered(int position, @Nullable Bitmap bitmap);
    }

    /** 位图至少为页面原始尺寸的倍数（保证放大后清晰） */
    private static final float MIN_QUALITY = 2.0f;
    /** 在铺满屏幕宽度的基础上预留的放大余量 */
    private static final float ZOOM_HEADROOM = 1.2f;
    /** 位图单边上限，防止 300DPI 超清扫描件撑爆内存 */
    private static final int MAX_DIMENSION = 2560;

    private final PdfRenderer renderer;
    private final Executor executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int screenWidth;
    private volatile boolean closed = false;

    /**
     * @param path     本地 PDF 文件路径
     * @param executor 串行 Executor（所有页面渲染将在此排队执行）
     * @throws IOException       文件不存在或已损坏
     * @throws SecurityException PDF 设有密码
     */
    public PdfDocument(@NonNull String path, @NonNull Executor executor) throws IOException {
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
        this.renderer = new PdfRenderer(fd);
        this.executor = executor;
        // 取屏幕短边作为适配基准，横竖屏切换时位图尺寸保持稳定
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        this.screenWidth = Math.min(dm.widthPixels, dm.heightPixels);
    }

    public int getPageCount() {
        return closed ? 0 : renderer.getPageCount();
    }

    /** 异步渲染指定页，bitmap 尺寸按屏幕宽度适配并预留放大余量，结果回调在主线程 */
    public void renderPage(int position, @NonNull RenderCallback callback) {
        executor.execute(() -> {
            if (closed) {
                deliver(position, null, callback);
                return;
            }
            Bitmap bitmap = null;
            try {
                PdfRenderer.Page page = renderer.openPage(position);
                try {
                    int pageW = page.getWidth();
                    int pageH = page.getHeight();

                    float fit = screenWidth / (float) pageW;
                    float scale = Math.max(MIN_QUALITY, fit * ZOOM_HEADROOM);
                    int width = Math.round(pageW * scale);
                    int height = Math.round(pageH * scale);
                    if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                        float k = MAX_DIMENSION / (float) Math.max(width, height);
                        width = Math.round(width * k);
                        height = Math.round(height * k);
                    }

                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    // PdfRenderer 只绘制页面内容，先把背景擦白，避免透明区域显示为黑底
                    bitmap.eraseColor(Color.WHITE);

                    Matrix matrix = new Matrix();
                    matrix.setScale(width / (float) pageW, height / (float) pageH);
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                } finally {
                    page.close();
                }
            } catch (Exception e) {
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
            }
            deliver(position, bitmap, callback);
        });
    }

    private void deliver(int position, @Nullable Bitmap bitmap, @NonNull RenderCallback callback) {
        mainHandler.post(() -> {
            if (closed) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                return;
            }
            callback.onRendered(position, bitmap);
        });
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            renderer.close();
        } catch (Exception ignored) {
        }
    }
}
