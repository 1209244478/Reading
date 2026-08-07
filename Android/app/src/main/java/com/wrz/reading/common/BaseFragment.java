/**
 * Copyright 2016 JustWayward Team
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wrz.reading.common;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.wrz.reading.R;
import com.wrz.reading.util.ExecutorManager;
import com.wrz.reading.view.loadding.CustomDialog;

import java.util.concurrent.ExecutorService;

public abstract class BaseFragment extends Fragment {
    public final String TAG = this.getClass().getSimpleName();
    protected View parentView;
    protected FragmentActivity activity;
    protected LayoutInflater inflater;
    public ExecutorService singleThread;
    public ExecutorService threadPool;
    protected Context mContext;

    private CustomDialog dialog;

    public Toolbar mCommonToolbar;
    private ToolbarHelper toolbarHelper;

    public boolean splashOnResume = false;

    public abstract
    @LayoutRes
    int getLayoutResId();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        parentView = inflater.inflate(getLayoutResId(), container, false);
        activity = getSupportActivity();
        mContext = activity;
        this.inflater = inflater;

        // 使用共享的线程池，避免每个Fragment都创建新的线程池
        ExecutorManager executorManager = ExecutorManager.getInstance();
        singleThread = executorManager.getSingleThreadExecutor();
        threadPool = executorManager.getThreadPool();
        executorManager.addReference();

        mCommonToolbar = parentView.findViewById(R.id.toolbar);
        if (mCommonToolbar != null) {
            toolbarHelper = new ToolbarHelper(mCommonToolbar, () -> activity == null ? null : activity.getWindow());
        }
        return parentView;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initView();

        initData();
        configViews();
    }

    public abstract void initView();

    public abstract void initData();

    @Override
    public void onResume() {
        super.onResume();

        if (splashOnResume) {
            initData();
            configViews();
        }
    }

    /**
     * 对各种控件进行设置、适配、填充数据
     */
    public abstract void configViews();

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (FragmentActivity) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    /**
     * 安全地在主线程执行 UI 更新：Fragment 仍在宿主 Activity 上且未销毁时才执行。
     * 在 UI 线程再次检查 isAdded()，确保回调执行时 activity 字段非空。
     */
    protected void runOnUiIfAlive(Runnable action) {
        FragmentActivity act = activity;
        if (act != null && isAdded()) {
            act.runOnUiThread(() -> {
                if (isAdded()) {
                    action.run();
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 减少引用计数（不直接关闭共享线程池）
        ExecutorManager.getInstance().removeReference();
    }

    public <T extends View> T findId(@IdRes int id) {
        return parentView.findViewById(id);
    }

    public FragmentActivity getSupportActivity() {
        return super.getActivity();
    }

    public Context getApplicationContext() {
        return this.activity == null ? (getActivity() == null ? null : getActivity()
                .getApplicationContext()) : this.activity.getApplicationContext();
    }

    protected View getParentView() {
        return parentView;
    }

    public CustomDialog getDialog() {
        if (dialog == null) {
            dialog = CustomDialog.instance(getActivity());
            dialog.setCancelable(false);
        }
        return dialog;
    }

    public void hideDialog() {
        if (dialog != null)
            dialog.hide();
    }

    public void showDialog() {
        getDialog().show();
    }

    public void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    protected void gone(final View... views) {
        if (views != null && views.length > 0) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }

    protected void visible(final View... views) {
        if (views != null && views.length > 0) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.VISIBLE);
                }
            }
        }

    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
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

}
