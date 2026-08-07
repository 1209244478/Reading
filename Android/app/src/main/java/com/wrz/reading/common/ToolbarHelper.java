package com.wrz.reading.common;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

/**
 * 统一管理 Activity/Fragment 的工具栏显示/隐藏动画与状态栏联动。
 * 取代 BaseActivity 与 BaseFragment 中重复的工具栏逻辑，并内置 null 安全。
 */
public class ToolbarHelper {

    /** 提供 Window 的回调，避免 Fragment 在构造期持有尚未 attach 的 Activity */
    public interface WindowProvider {
        @Nullable
        Window getWindow();
    }

    private static final long ANIM_DURATION = 180;

    @Nullable
    private final Toolbar toolbar;
    private final WindowProvider windowProvider;
    private boolean isVisible = true;

    public ToolbarHelper(@Nullable Toolbar toolbar, WindowProvider windowProvider) {
        this.toolbar = toolbar;
        this.windowProvider = windowProvider;
        if (toolbar != null) {
            isVisible = (toolbar.getVisibility() == View.VISIBLE);
        }
    }

    public boolean isAvailable() {
        return toolbar != null;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void hideIfVisible() {
        if (isVisible) {
            hide();
        }
    }

    public void showIfNotVisible() {
        if (!isVisible) {
            show();
        }
    }

    public void toggle() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        if (toolbar == null) {
            return;
        }
        toolbar.animate()
                .translationY(-toolbar.getHeight())
                .setInterpolator(new LinearInterpolator())
                .setDuration(ANIM_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setElevation(0);
                        hideStatusBar();
                    }
                });
        isVisible = false;
    }

    private void show() {
        if (toolbar == null) {
            return;
        }
        toolbar.animate()
                .translationY(0)
                .setInterpolator(new LinearInterpolator())
                .setDuration(ANIM_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        setElevation(1);
                    }
                });
        isVisible = true;
    }

    private void setElevation(float elevation) {
        if (toolbar != null) {
            toolbar.setElevation(elevation);
        }
    }

    private void hideStatusBar() {
        Window window = windowProvider.getWindow();
        if (window != null) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            window.setAttributes(attrs);
        }
    }
}
