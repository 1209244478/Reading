package com.wrz.reading.ui.read.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.github.chrisbanes.photoview.PhotoView;

/**
 * 阅读器专用的 PhotoView 子类。
 * <p>解决问题：
 * <ul>
 *   <li>缩放失效：{@link #dispatchTouchEvent} 中多指时阻止 ViewPager2 拦截。</li>
 *   <li>非缩放模式下 tap 失效：{@link #onTouchEvent} 中用 GestureDetector 自行检测 tap。</li>
 *   <li>首次进入缩放比例异常：attacher 在 View 尺寸为 0 时计算出错误的 baseMatrix，
 *       且 setZoomable(false) 后 onDrawableChanged 不重新计算。
 *       通过 {@link #applyFitMatrix} 在非缩放模式下手动计算 fit matrix，
 *       在 {@link #onLayout} 和 {@link #setImageDrawable} 中调用以覆盖 attacher 的错误 matrix。</li>
 * </ul>
 */
public class ReaderPhotoView extends PhotoView {

    public interface OnTapListener {
        void onTap(float xPercent);
    }

    public interface OnMoveListener {
        void onMove();
    }

    private OnTapListener tapListener;
    private OnMoveListener moveListener;
    private GestureDetector gestureDetector;
    private boolean needsMatrixReset = false;

    public ReaderPhotoView(Context context) {
        super(context);
        init(context);
    }

    public ReaderPhotoView(Context context, AttributeSet attr) {
        super(context, attr);
        init(context);
    }

    public ReaderPhotoView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (tapListener != null && getWidth() > 0) {
                    float xPercent = e.getX() / (float) getWidth();
                    tapListener.onTap(xPercent);
                }
                return true;
            }
        });
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.moveListener = listener;
    }

    /**
     * 非缩放模式下手动计算 FIT_CENTER matrix，覆盖 attacher 的错误 matrix。
     */
    private void applyFitMatrix() {
        Drawable d = getDrawable();
        if (d == null) return;

        float viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int drawableWidth = d.getIntrinsicWidth();
        int drawableHeight = d.getIntrinsicHeight();

        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        float scale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        float dx = (viewWidth - drawableWidth * scale) / 2f + getPaddingLeft();
        float dy = (viewHeight - drawableHeight * scale) / 2f + getPaddingTop();

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            if (!isZoomable() && getWidth() > 0 && getHeight() > 0) {
                applyFitMatrix();
            } else {
                needsMatrixReset = true;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (needsMatrixReset && getWidth() > 0 && getHeight() > 0) {
            needsMatrixReset = false;
            if (!isZoomable()) {
                applyFitMatrix();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isZoomable()) {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_MOVE && moveListener != null) {
                moveListener.onMove();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
