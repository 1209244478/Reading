package com.wrz.reading.common;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.wrz.reading.R;
import com.wrz.reading.util.ExecutorManager;

import java.util.concurrent.ExecutorService;

public abstract class BaseActivity extends AppCompatActivity {
    public final String TAG = getClass().getSimpleName();
    public ExecutorService singleThread;
    public ExecutorService threadPool;

    public Toolbar mCommonToolbar;
    private ToolbarHelper toolbarHelper;

    private static final int REQUEST_PERMISSION = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用共享的线程池，避免每个Activity都创建新的线程池
        ExecutorManager executorManager = ExecutorManager.getInstance();
        singleThread = executorManager.getSingleThreadExecutor();
        threadPool = executorManager.getThreadPool();
        executorManager.addReference();

        setContentView(getLayoutId());

        getIntentData();

        initBackCallback();

        mCommonToolbar = findViewById(R.id.toolbar);
        if (mCommonToolbar != null) {
            toolbarHelper = new ToolbarHelper(mCommonToolbar, this::getWindow);
            initToolBar();
            setSupportActionBar(mCommonToolbar);

            mCommonToolbar.setNavigationOnClickListener(view -> goBack());
        }

        initView();

        if (checkPermissions()) {
            initData();
            configView();
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 减少引用计数（不直接关闭共享线程池）
        ExecutorManager.getInstance().removeReference();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                startActivityForResult(intent, REQUEST_PERMISSION);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_PERMISSION);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

    public abstract void getIntentData();
    public abstract int getLayoutId();

    public abstract void initToolBar();

    public abstract void initView();

    public abstract void initData();
    public abstract void configView();


    public abstract void goBack();

    @Override
    protected void onResume() {
        super.onResume();
    }


    private void initBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 注册预测性返回回调
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    this::goBack
            );
        } else {
            // 兼容旧版本的返回处理
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    goBack();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        handlePermissionResult(requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        handlePermissionResult(requestCode);
    }

    private void handlePermissionResult(int requestCode) {
        if (requestCode != REQUEST_PERMISSION) {
            return;
        }
        if (checkPermissions()) {
            initData();
            configView();
        } else {
            Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


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

}
