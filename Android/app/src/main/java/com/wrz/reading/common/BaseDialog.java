package com.wrz.reading.common;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.wrz.reading.R;

public abstract class BaseDialog extends Dialog {

    public View parentView;

    private ShowOrDismissListener listener;

    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogTheme);
    }

    public BaseDialog(@NonNull Context context, @StyleRes int themeResId) {
        super(context, themeResId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置窗口特性
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 加载布局
        parentView = LayoutInflater.from(getContext()).inflate(getLayoutId(), null);
        setContentView(parentView);

        // 设置对话框大小（默认 WRAP_CONTENT，子类可覆写调整）
        setDialogSize();

        initView();
    }

    public abstract int getLayoutId();
    public abstract void initView();

    /** 子类可覆写以自定义窗口尺寸，默认 WRAP_CONTENT×WRAP_CONTENT */
    public void setDialogSize() {
        applySize(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** show 后回调，子类按需覆写 */
    public void onShow() {
    }

    /** dismiss 后回调，子类按需覆写 */
    public void onDismiss() {
    }

    /** 设置窗口尺寸（内部对 getWindow() 判空） */
    protected void applySize(int width, int height) {
        Window window = getWindow();
        if (window != null) {
            window.setLayout(width, height);
        }
    }


    @Override
    public void show() {
        try {
            super.show();
        } catch (Exception e) {
            // 宿主已销毁等情况，放弃显示
            return;
        }
        if (listener != null) {
            listener.show();
        }
        onShow();
    }

    @Override
    public void dismiss() {
        if (isShowing()) {
            try {
                super.dismiss();
            } catch (Exception e) {
                // 宿主已销毁时可能抛出窗口异常，忽略
            }
        }
        if (listener != null) {
            listener.dismiss();
        }
        onDismiss();
    }

    public <T extends View> T findId(@IdRes int id) {
        return parentView == null ? null : parentView.findViewById(id);
    }

    public void setText(View view, String text) {
        if (view != null && text != null) {
            if (view instanceof TextView) {
                ((TextView) view).setText(text);
            }
        }
    }

    public void setText(@IdRes int id, String text) {
        View view = findId(id);
        if (view != null && text != null) {
            if (view instanceof TextView) {
                ((TextView) view).setText(text);
            }
        }
    }

    public void setClick(View v, View.OnClickListener l) {
        if (v != null) {
            v.setOnClickListener(l);
        }
    }

    public void setClick(@IdRes int id, View.OnClickListener l) {
        View v = findId(id);
        if (v != null) {
            v.setOnClickListener(l);
        }
    }

    public void setClick(View.OnClickListener l, final View...views) {
        if (views != null) {
            for (View view : views) {
                if (view != null) {
                    view.setOnClickListener(l);
                }
            }
        }
    }

    public interface ShowOrDismissListener {
        void show();

        void dismiss();
    }

    public void setListener(ShowOrDismissListener listener) {
        this.listener = listener;
    }
}
