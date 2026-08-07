package com.wrz.reading.ui.main.fragment;

import android.widget.TextView;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseFragment;

public class SettingFragment extends BaseFragment {

    private TextView tvTitle;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_setting;
    }

    @Override
    public void initView() {
        tvTitle = parentView.findViewById(R.id.tv_title);
    }

    @Override
    public void initData() {
        // 初始化数据
    }

    @Override
    public void configViews() {
        tvTitle.setText("设置页面");
        // 可以添加更多设置项的点击事件等
    }
}
