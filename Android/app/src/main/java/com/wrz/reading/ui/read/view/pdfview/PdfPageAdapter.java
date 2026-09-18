package com.wrz.reading.ui.read.view.pdfview;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;

/**
 * ViewPager2 使用的 PDF 页适配器：
 * - 页面缩放/拖拽交给第三方库 PhotoView（双击缩放、双指缩放、平移均已内置）
 * - 已渲染页面用 LruCache 缓存（按字节计费，上限约堆内存 1/4，最大 64MB）
 * - 渲染由 {@link PdfDocument} 在后台串行完成，回调后校验复用状态再上屏，避免串页
 */
public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageHolder> {

    public interface OnPageInteractionListener {
        /** 单击页面，xRatio 为点击位置相对页面宽度的比例（0~1） */
        void onPageTap(float xRatio);

        /** 页面被拖动/缩放 */
        void onPan();
    }

    private final Context context;
    private final PdfDocument document;
    private final OnPageInteractionListener listener;
    private final LruCache<Integer, Bitmap> cache;
    private volatile boolean released = false;

    public PdfPageAdapter(@NonNull Context context, @NonNull PdfDocument document,
                          @NonNull OnPageInteractionListener listener) {
        this.context = context;
        this.document = document;
        this.listener = listener;

        long maxMemory = Runtime.getRuntime().maxMemory();
        int cacheSize = (int) Math.min(maxMemory / 4, 64L * 1024 * 1024);
        this.cache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public void release() {
        released = true;
        cache.evictAll();
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PhotoView photoView = new PhotoView(context);
        photoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new PageHolder(photoView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return document.getPageCount();
    }

    class PageHolder extends RecyclerView.ViewHolder {
        final PhotoView photoView;
        int boundPosition = -1;

        PageHolder(@NonNull PhotoView photoView) {
            super(photoView);
            this.photoView = photoView;

            photoView.setOnViewTapListener((view, x, y) ->
                    listener.onPageTap(x / Math.max(1, view.getWidth())));
            photoView.setOnMatrixChangeListener(rect -> listener.onPan());
        }

        void bind(int position) {
            boundPosition = position;
            Bitmap cached = cache.get(position);
            if (cached != null) {
                photoView.setImageBitmap(cached);
                return;
            }
            photoView.setImageDrawable(null);
            document.renderPage(position, (pos, bitmap) -> {
                if (released || bitmap == null) {
                    return;
                }
                cache.put(pos, bitmap);
                // 渲染期间该 ViewHolder 可能已被复用到其他页，只有仍在展示本页时才上屏
                if (boundPosition == pos) {
                    photoView.setImageBitmap(bitmap);
                }
            });
        }
    }
}
