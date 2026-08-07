package com.wrz.reading.ui.main.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.wrz.reading.R;
import com.wrz.reading.common.BaseActivity;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private BottomNavigationView bottomNavigationView;

    @Override
    public void getIntentData() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    public void initToolBar() {
        // 如果需要 Toolbar，可以在这里设置
    }

    @Override
    public void initView() {
        // 初始化 Bottom Navigation View
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // 悬浮导航栏在 edge-to-edge（targetSdk 36 在 API 35+ 强制）下需避开系统手势条：
        // 将系统底部 inset 叠加到容器的 bottomMargin 上，保证玻璃栏完整浮在手势条之上。
        View navContainer = findViewById(R.id.bottom_nav_container);
        ViewCompat.setOnApplyWindowInsetsListener(navContainer, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            // 16dp 为 XML 中声明的基础悬浮间距，再叠加系统 inset
            float density = v.getResources().getDisplayMetrics().density;
            lp.bottomMargin = (int) (16 * density) + bottomInset;
            return insets;
        });
    }

    @Override
    public void initData() {
        // 初始化数据
    }

    @Override
    public void configView() {

    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // 在 onPostCreate 中获取 NavController
        setupNavigation();
    }

    private void setupNavigation() {
        try {
            // 方式1：使用 NavHostFragment
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);

            if (navHostFragment != null) {
                navController = navHostFragment.getNavController();
                NavigationUI.setupWithNavController(bottomNavigationView, navController);

                // 设置导航监听器
                navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                    Log.d(TAG, "Navigating to: " + destination.getLabel());

                    // 可以在这里处理不同页面的特殊逻辑
                    int vId = destination.getId();
                    if (vId == R.id.mainFragment) {

                    } else if (vId == R.id.wheelFragment) {

                    }
                });
            } else {
                Log.e(TAG, "NavHostFragment not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation: ", e);
        }
    }

    @Override
    public void goBack() {
        // 优先由 NavController 弹出回退栈（如从集合详情返回集合列表），
        // 只有处于根目的地时才结束 Activity
        if (navController != null && navController.navigateUp()) {
            return;
        }
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // 处理 Toolbar 菜单点击
        if (item.getItemId() == android.R.id.home) {
            goBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 获取 NavController 的公共方法，供 Fragment 使用
    public NavController getNavController() {
        return navController;
    }
}
