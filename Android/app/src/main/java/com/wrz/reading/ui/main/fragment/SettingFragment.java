package com.wrz.reading.ui.main.fragment;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.main.utils.DialogHelper;

public class SettingFragment extends BaseFragment {

    private SwitchCompat switchReceiveCast;
    private LinearLayout ll_name;
    private TextView tv_name;

    private String cast_name;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_setting;
    }

    @Override
    public void initView() {
        switchReceiveCast = parentView.findViewById(R.id.switch_receive_cast);

        ll_name = parentView.findViewById(R.id.ll_name);
        tv_name = parentView.findViewById(R.id.tv_name);
    }

    @Override
    public void initData() {
        // 初始化数据
        cast_name = MyApplication.manager.getCastName();
    }

    @Override
    public void configViews() {

        final Context ctx = requireContext();
        boolean enabled = DlnaRendererManager.isReceiverEnabled(ctx);
        // 先恢复开关状态再设置监听，避免 setChecked 触发监听重复启动
        switchReceiveCast.setChecked(enabled);

        switchReceiveCast.setOnCheckedChangeListener((button, isChecked) -> {
            DlnaRendererManager.setReceiverEnabled(ctx, isChecked);
            if (isChecked) {
                DlnaRendererManager.getInstance().start(ctx);
            } else {
                DlnaRendererManager.getInstance().stop();
            }
        });

        ll_name.setOnClickListener(v -> {
            DialogHelper.showTextInputDialog(getActivity(), "设置接收投屏名称", "", "保存", cast_name,
                    text -> {
                        cast_name = text;
                        tv_name.setText(cast_name);
                        MyApplication.manager.saveCastName(text);
                        if (switchReceiveCast.isChecked()) {
                            DlnaRendererManager.getInstance().reStart(MyApplication.app);
                        }
                    });
        });

        tv_name.setText(cast_name);
    }

    @Override
    public void goBack() {

    }
}
