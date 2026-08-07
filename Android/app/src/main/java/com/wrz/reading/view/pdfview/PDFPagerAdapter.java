package com.wrz.reading.view.pdfview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.wrz.reading.R;

import java.lang.ref.WeakReference;

public class PDFPagerAdapter extends BasePDFPagerAdapter
        implements SimplePhotoAttacher.OnMatrixChangedListener {  // 使用自定义类

    private static final float DEFAULT_SCALE = 1f;

    SparseArray<WeakReference<SimplePhotoAttacher>> attachers;  // 修改类型
    PdfScale scale = new PdfScale();
    OnPageClickListener pageClickListener;

    private final OnComicClickListener listener;

    public interface OnComicClickListener {
        void onComicClick();
        void onScroll();
    }

    public PDFPagerAdapter(Context context, String pdfPath, OnComicClickListener listener) {
        super(context, pdfPath);
        this.listener = listener;
        attachers = new SparseArray<>();
    }

    @Override
    @SuppressWarnings("NewApi")
    public Object instantiateItem(ViewGroup container, int position) {
        View itemView = inflater.inflate(R.layout.view_pdf_page, container, false);
        ImageView iv_item = (ImageView) itemView.findViewById(R.id.imageView);

        if (renderer == null || getCount() < position) {
            return itemView;
        }

        PdfRenderer.Page page = getPDFPage(renderer, position);

        Bitmap bitmap = bitmapContainer.get(position);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        // 使用自定义的 SimplePhotoAttacher
        SimplePhotoAttacher attacher = new SimplePhotoAttacher(iv_item);
        attacher.setScale(scale.getScale(), scale.getCenterX(), scale.getCenterY(), true);
        attacher.setOnMatrixChangeListener(this);

        attachers.put(position, new WeakReference<>(attacher));

        iv_item.setImageBitmap(bitmap);
        attacher.setOnPhotoTapListener(new SimplePhotoAttacher.OnPhotoTapListener() {
            @Override
            public void onPhotoTap(View view, float x, float y) {
                if (pageClickListener != null) {
                    pageClickListener.onPageTap(view, x, y);
                }
            }
        });
        attacher.update();
        container.addView(itemView, 0);

        iv_item.setOnClickListener(v -> listener.onComicClick());
        iv_item.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                listener.onScroll();
                return true;
            }
            return false;
        });

        return itemView;
    }

    @Override
    public void close() {
        super.close();
        /*if (attachers != null) {
            attachers.clear();
            attachers = null;
        }*/
    }

    @Override
    public void onMatrixChanged(RectF rect) {
        if (scale.getScale() != PdfScale.DEFAULT_SCALE) {
            // 可以在这里保存缩放状态
            // scale.setCenterX(rect.centerX());
            // scale.setCenterY(rect.centerY());
        }
    }

    // Builder 类保持不变
    public static class Builder {
        Context context;
        String pdfPath = "";
        float scale = DEFAULT_SCALE;
        float centerX = 0f, centerY = 0f;
        int offScreenSize = DEFAULT_OFFSCREENSIZE;
        float renderQuality = DEFAULT_QUALITY;
        OnPageClickListener pageClickListener;
        private final OnComicClickListener listener;

        public Builder(Context context, OnComicClickListener listener) {
            this.context = context;
            this.listener = listener;
        }

        public Builder setScale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder setScale(PdfScale scale) {
            this.scale = scale.getScale();
            this.centerX = scale.getCenterX();
            this.centerY = scale.getCenterY();
            return this;
        }

        public Builder setCenterX(float centerX) {
            this.centerX = centerX;
            return this;
        }

        public Builder setCenterY(float centerY) {
            this.centerY = centerY;
            return this;
        }

        public Builder setRenderQuality(float renderQuality) {
            this.renderQuality = renderQuality;
            return this;
        }

        public Builder setOffScreenSize(int offScreenSize) {
            this.offScreenSize = offScreenSize;
            return this;
        }

        public Builder setPdfPath(String path) {
            this.pdfPath = path;
            return this;
        }

        public Builder setOnPageClickListener(OnPageClickListener listener) {
            if (listener != null) {
                pageClickListener = listener;
            }
            return this;
        }

        public PDFPagerAdapter create() {
            PDFPagerAdapter adapter = new PDFPagerAdapter(context, pdfPath, listener);
            adapter.scale.setScale(scale);
            adapter.scale.setCenterX(centerX);
            adapter.scale.setCenterY(centerY);
            adapter.offScreenSize = offScreenSize;
            adapter.renderQuality = renderQuality;
            adapter.pageClickListener = pageClickListener;
            return adapter;
        }
    }
}
