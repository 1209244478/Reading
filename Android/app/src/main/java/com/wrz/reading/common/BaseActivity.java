package com.wrz.reading.common;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.dialog.LoadingDialog;
import com.wrz.reading.ui.main.utils.ExecutorManager;

import java.util.concurrent.ExecutorService;

/**
 * Activity 基类：统一模板流程与共享资源。
 * <ul>
 *   <li>线程：从 {@link ExecutorManager} 取共享 singleThread/threadPool 并引用计数，
 *       子类勿自行 new 线程池；onDestroy 释放引用</li>
 *   <li>模板方法：{@link #getLayoutId()} 必须实现；
 *       getIntentData → initToolBar → initView → initData → configView 按序回调，goBack() 处理返回</li>
 *   <li>Toolbar：布局内 id 为 toolbar 的控件自动绑定为 mCommonToolbar 并 setSupportActionBar</li>
 * </ul>
 */
public abstract class BaseActivity extends AppCompatActivity {
    public final String TAG = getClass().getSimpleName();
    public ExecutorService singleThread;
    public ExecutorService threadPool;
    private LoadingDialog loadingDialog;
    public Toolbar mCommonToolbar;
    private ToolbarHelper toolbarHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用共享的线程池，避免每个Activity都创建新的线程池
        ExecutorManager executorManager = ExecutorManager.getInstance();
        singleThread = executorManager.getSingleThreadExecutor();
        threadPool = executorManager.getThreadPool();
        executorManager.addReference();

        setContentView(getLayoutId());
        LogUtil.e(TAG, "setContentView.");

        getIntentData();
        LogUtil.e(TAG, "getIntentData.");


        /*initBackCallback();*/

        mCommonToolbar = findViewById(R.id.toolbar);
        if (mCommonToolbar != null) {
            toolbarHelper = new ToolbarHelper(mCommonToolbar, this::getWindow);
            initToolBar();
            setSupportActionBar(mCommonToolbar);

            mCommonToolbar.setNavigationOnClickListener(view -> goBack());
        }

        LogUtil.e(TAG, "setSupportActionBar.");

        initView();

        initData();
        LogUtil.e(TAG, "initData.");
        configView();
        LogUtil.e(TAG, "configView.");

        // 横屏时隐藏状态栏（通知栏），竖屏恢复显示
        applyLandscapeSystemBar();

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 覆盖声明了 configChanges 旋转不重建的 Activity（如 VideoPlayerActivity）
        applyLandscapeSystemBar();
    }

    /**
     * 横屏时隐藏状态栏（通知栏），下滑可临时呼出；竖屏恢复显示。
     * 仅隐藏 statusBars，不影响导航栏 inset（悬浮导航栏依赖底部 inset）。
     */
    private void applyLandscapeSystemBar() {
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars());
        }
    }

    @Override
    protected void onDestroy() {
        // 防止窗口泄漏：销毁前关闭加载弹窗
        dismissLoading();

        super.onDestroy();

        // 减少引用计数（不直接关闭共享线程池）
        ExecutorManager.getInstance().removeReference();
    }

    public abstract void getIntentData();
    public abstract int getLayoutId();

    public abstract void initToolBar();

    public abstract void initView();

    public abstract void initData();
    public abstract void configView();


    public abstract void goBack();


    public boolean mIsToolBarVisible = true;

    public void hideToolBarIfVisible() {
        if (toolbarHelper != null) {
            toolbarHelper.hideIfVisible();
            mIsToolBarVisible = toolbarHelper.isVisible();
        }
    }

    public void showToolBarIfNotVisible() {
        if (toolbarHelper != null) {
            toolbarHelper.showIfNotVisible();
            mIsToolBarVisible = toolbarHelper.isVisible();
        }
    }

    public void toggleToolBarVisibleOrGone() {
        if (toolbarHelper != null) {
            toolbarHelper.toggle();
            mIsToolBarVisible = toolbarHelper.isVisible();
        }
    }

    public void showToolbar() {
        if (toolbarHelper != null) {
            toolbarHelper.showIfNotVisible();
            mIsToolBarVisible = toolbarHelper.isVisible();
        }
    }

    // 隐藏状态栏与导航栏
    public void hideToolbar() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    protected void setClick(View v, View.OnClickListener l) {
        if (v != null) {
            v.setOnClickListener(l);
        }
    }

    protected void setClick(int vId, View.OnClickListener l) {
        View v = findViewById(vId);
        if (v != null) {
            v.setOnClickListener(l);
        }
    }

    protected void setClick(View.OnClickListener l, final View...views) {
        if (views != null) {
            for (View view : views) {
                if (view != null) {
                    view.setOnClickListener(l);
                }
            }
        }
    }

    protected void setText(String text, final View...views) {
        if (views != null) {
            for (View view : views) {
                if (view != null) {
                    ((TextView) view).setText(text);
                }
            }
        }
    }

    protected void setText(String text, final int...views) {
        if (views != null) {
            for (Integer viewId : views) {
                View view = findViewById(viewId);
                if (view != null) {
                    ((TextView) view).setText(text);
                }
            }
        }
    }

    protected void gone(final View... views) {
        if (views != null) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }

    protected void visible(final View... views) {
        if (views != null) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * 安全地在主线程执行 UI 更新： Activity 未销毁时才执行。
     */
    protected void runOnUiIfAlive(Runnable action) {
        if (!this.isDestroyed() && !this.isFinishing()) {
            this.runOnUiThread(action);
        }
    }

    public LoadingDialog getLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialog.instance(this);
            loadingDialog.setCancelable(false);
        }
        return loadingDialog;
    }

    public void hideLoading() {
        if (loadingDialog != null)
            loadingDialog.hide();
    }

    public void showLoading() {
        if (isDestroyed() || isFinishing()) {
            return;
        }
        getLoadingDialog().show();
    }

    public void dismissLoading() {
        if (loadingDialog != null) {
            try {
                loadingDialog.dismiss();
            } catch (Exception e) {
                // 异步回调时宿主可能已销毁，忽略窗口异常
            }
            loadingDialog = null;
        }
    }

    public boolean isLand() {
        return (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

}
