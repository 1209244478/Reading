package com.wrz.reading.common;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.wrz.reading.R;

public abstract class BaseDialog extends Dialog {

    public View parentView;

    private ShowOrDismissListener listener;

    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogTheme);
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

        // 设置对话框大小
        setDialogSize();

        initView();
    }

    public abstract int getLayoutId();
    public abstract void initView();

    public abstract void setDialogSize();

    @Override
    public void show() {
        super.show();
        if (listener != null) {
            listener.show();
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (listener != null) {
            listener.dismiss();
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
