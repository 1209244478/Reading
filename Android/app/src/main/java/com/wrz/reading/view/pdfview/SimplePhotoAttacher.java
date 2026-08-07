package com.wrz.reading.view.pdfview;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.GestureDetector;
import android.widget.ImageView;

public class SimplePhotoAttacher {
    private ImageView imageView;
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();

    private float scale = 1.0f;
    private float centerX = 0f;
    private float centerY = 0f;

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    private float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private OnMatrixChangedListener matrixChangedListener;
    private OnPhotoTapListener photoTapListener;

    public interface OnMatrixChangedListener {
        void onMatrixChanged(RectF rect);
    }

    public interface OnPhotoTapListener {
        void onPhotoTap(View view, float x, float y);
    }

    public SimplePhotoAttacher(ImageView imageView) {
        this.imageView = imageView;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);

        // 双击放大
        gestureDetector = new GestureDetector(imageView.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (scale == 1.0f) {
                    scale = 2.0f;
                    centerX = e.getX();
                    centerY = e.getY();
                } else {
                    scale = 1.0f;
                    centerX = 0;
                    centerY = 0;
                }
                updateMatrix();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (photoTapListener != null) {
                    photoTapListener.onPhotoTap(imageView, e.getX() / imageView.getWidth(), e.getY() / imageView.getHeight());
                }
                return true;
            }
        });

        // 缩放和拖拽
        scaleDetector = new ScaleGestureDetector(imageView.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float initialScale = 1.0f;

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                initialScale = scale;
                mode = ZOOM;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                scale = initialScale * scaleFactor;
                scale = Math.max(1.0f, Math.min(scale, 5.0f)); // 限制缩放范围

                centerX = detector.getFocusX();
                centerY = detector.getFocusY();

                updateMatrix();
                return true;
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            /*if (event.getPointerCount() > 1) {
                scaleDetector.onTouchEvent(event);
            } else {
                gestureDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        savedMatrix.set(matrix);
                        mode = DRAG;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float dx = event.getX() - centerX;
                            float dy = event.getY() - centerY;

                            matrix.set(savedMatrix);
                            matrix.postTranslate(dx, dy);
                            imageView.setImageMatrix(matrix);

                            if (matrixChangedListener != null) {
                                RectF rect = new RectF();
                                imageView.getDrawable().getBounds();
                                matrix.mapRect(rect);
                                matrixChangedListener.onMatrixChanged(rect);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mode = NONE;
                        break;
                }
            }*/
            return true;
        });
    }

    public void setScale(float scale, float centerX, float centerY, boolean animate) {
        this.scale = scale;
        this.centerX = centerX;
        this.centerY = centerY;
        updateMatrix();
    }

    public void setOnMatrixChangeListener(OnMatrixChangedListener listener) {
        this.matrixChangedListener = listener;
    }

    public void setOnPhotoTapListener(OnPhotoTapListener listener) {
        this.photoTapListener = listener;
    }

    public void update() {
        updateMatrix();
    }

    private void updateMatrix() {
        if (imageView.getDrawable() == null) return;

        RectF rect = new RectF();
        imageView.getDrawable().getBounds();

        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();

        if (viewWidth == 0 || viewHeight == 0 || rect.width() == 0 || rect.height() == 0) {
            return;
        }

        // 重置矩阵
        matrix.reset();

        // 缩放
        float scaleX = viewWidth / rect.width();
        float scaleY = viewHeight / rect.height();
        float initScale = Math.min(scaleX, scaleY) * 0.9f; // 留一点边距

        // 应用用户缩放
        float finalScale = initScale * scale;

        // 计算居中位置
        float dx = viewWidth / 2f - (rect.width() * finalScale) / 2f;
        float dy = viewHeight / 2f - (rect.height() * finalScale) / 2f;

        // 应用变换
        matrix.postScale(finalScale, finalScale, viewWidth / 2f, viewHeight / 2f);
        matrix.postTranslate(dx, dy);

        // 应用用户平移（如果有）
        if (centerX != 0 && centerY != 0) {
            matrix.postTranslate(centerX - viewWidth / 2f, centerY - viewHeight / 2f);
        }

        imageView.setImageMatrix(matrix);

        if (matrixChangedListener != null) {
            RectF mappedRect = new RectF(rect);
            matrix.mapRect(mappedRect);
            matrixChangedListener.onMatrixChanged(mappedRect);
        }
    }
}
