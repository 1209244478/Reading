package com.wrz.reading.common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.lxj.xpopup.core.BottomPopupView;

public abstract class BaseBottomPopupView extends BottomPopupView {
    public BaseBottomPopupView(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return getLayoutId();
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        initView();
    }

    @Override
    protected void onShow() {
        super.onShow();

        OnShow();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();

        OnDismiss();
    }


    public abstract int getLayoutId();

    public abstract void initView();

    public abstract void OnShow();

    public abstract void OnDismiss();


}
