package com.wrz.reading.ui.main.fragment;

import android.content.Context;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.main.activity.DownloadListActivity;
import com.wrz.reading.ui.main.utils.DialogHelper;

public class SettingFragment extends BaseFragment {

    private SwitchCompat switchReceiveCast;
    private LinearLayout ll_name;
    private TextView tv_name;
    private LinearLayout ll_receive_mode;
    private TextView tv_receive_mode;
    private LinearLayout ll_download_list;

    private String cast_name;
    private SwitchCompat switchClearCache;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_setting;
    }

    @Override
    public void initView() {
        switchReceiveCast = findId(R.id.switch_receive_cast);

        ll_name = findId(R.id.ll_name);
        tv_name = findId(R.id.tv_name);

        ll_receive_mode = findId(R.id.ll_receive_mode);
        tv_receive_mode = findId(R.id.tv_receive_mode);

        ll_download_list = findId(R.id.ll_download_list);

        switchClearCache = findId(R.id.switch_clear_cache);
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

        tv_receive_mode.setText(getReceiveModeLabel(DlnaRendererManager.getReceiveMode(ctx)));
        ll_receive_mode.setOnClickListener(v -> showReceiveModeDialog(ctx));

        ll_name.setOnClickListener(v -> {
            DialogHelper.showTextInputDialog(getActivity(), getString(R.string.title_set_cast_name), "", getString(R.string.btn_save), cast_name,
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

        setClick(ll_download_list, v -> DownloadListActivity.start(ctx));

        switchClearCache.setChecked(MyApplication.manager.getClearCache());
        switchClearCache.setOnCheckedChangeListener((buttonView, isChecked) ->
                MyApplication.manager.saveClearCache(isChecked));
    }

    /** 弹出接收方式单选弹窗：DLNA / WrzCast / AirPlay / Chromecast / 全部。 */
    private void showReceiveModeDialog(Context ctx) {
        final DlnaRendererManager.ReceiveMode[] modes = {
                DlnaRendererManager.ReceiveMode.DLNA,
                DlnaRendererManager.ReceiveMode.WRZCAST,
                DlnaRendererManager.ReceiveMode.AIRPLAY,
                DlnaRendererManager.ReceiveMode.CHROMECAST,
                DlnaRendererManager.ReceiveMode.ALL
        };
        String[] labels = new String[modes.length];
        DlnaRendererManager.ReceiveMode current = DlnaRendererManager.getReceiveMode(ctx);
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = getReceiveModeLabel(modes[i]);
            if (modes[i] == current) {
                checked = i;
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.title_receive_mode)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    DlnaRendererManager.ReceiveMode mode = modes[which];
                    if (mode == DlnaRendererManager.getReceiveMode(ctx)) {
                        return;
                    }
                    boolean chromecastSupported = DlnaRendererManager.isChromecastReceiverSupported(ctx);
                    boolean airPlaySupported = DlnaRendererManager.isAirPlayReceiverSupported(ctx);
                    // 手机端不支持 Chromecast / AirPlay 接收，不允许切换到仅这些协议
                    if (mode == DlnaRendererManager.ReceiveMode.CHROMECAST && !chromecastSupported) {
                        Toast.makeText(ctx, R.string.msg_chromecast_not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (mode == DlnaRendererManager.ReceiveMode.AIRPLAY && !airPlaySupported) {
                        Toast.makeText(ctx, R.string.msg_airplay_not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DlnaRendererManager.setReceiveMode(ctx, mode);
                    tv_receive_mode.setText(getReceiveModeLabel(mode));
                    if ((mode.includesChromecast() && !chromecastSupported)
                            || (mode.includesAirPlay() && !airPlaySupported)) {
                        Toast.makeText(ctx, R.string.msg_receive_mode_unsupported_protocol,
                                Toast.LENGTH_SHORT).show();
                    }
                    // 接收已开启时重启接收端，使新接收方式立即生效
                    if (switchReceiveCast.isChecked()) {
                        DlnaRendererManager.getInstance().reStart(MyApplication.app);
                    }
                })
                .show();
    }

    /** 接收方式的展示名称。 */
    private String getReceiveModeLabel(DlnaRendererManager.ReceiveMode mode) {
        switch (mode) {
            case WRZCAST:
                return getString(R.string.receive_mode_wrzcast);
            case AIRPLAY:
                return getString(R.string.receive_mode_airplay);
            case CHROMECAST:
                return getString(R.string.receive_mode_chromecast);
            case ALL:
                return getString(R.string.receive_mode_all);
            default:
                return getString(R.string.receive_mode_dlna);
        }
    }

    @Override
    public void goBack() {

    }
}
