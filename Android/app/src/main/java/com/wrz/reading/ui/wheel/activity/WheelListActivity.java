package com.wrz.reading.ui.wheel.activity;

import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.util.XPopupUtils;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.wheel.model.Wheel;
import com.wrz.reading.ui.wheel.adapter.WheelListAdapter;

import java.util.List;

public class WheelListActivity extends BaseActivity {

    private TextView tv_title;
    private List<Wheel> list;
    public WheelListAdapter wheelListAdapter;

    public static void start(Context context) {
        context.startActivity(new Intent(context, WheelListActivity.class));
    }

    @Override
    public void getIntentData() {
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_wheel_list;
    }

    @Override
    public void initToolBar() {
    }

    @Override
    public void initView() {
        RecyclerView recyclerView = findViewById(R.id.rv_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        wheelListAdapter = new WheelListAdapter(R.layout.item_wheel);
        recyclerView.setAdapter(wheelListAdapter);

        wheelListAdapter.addChildClickViewIds(R.id.iv_more);
        wheelListAdapter.addChildLongClickViewIds(R.id.iv_more);
        wheelListAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_more) {
                showMore(position);
            }
        });
        wheelListAdapter.setOnItemChildLongClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_more) {
                showMore(position);
            }
            return true;
        });

        wheelListAdapter.setOnItemClickListener((adapter, view, position) -> {
            Wheel wheel = list.get(position);
            singleThread.execute(() -> MyApplication.wheelRepository.saveLastTime(wheel));
            finish();
        });

        FloatingActionButton btn_add = findViewById(R.id.btn_add);
        btn_add.setOnClickListener(v -> showCreateOptions());

        tv_title = findViewById(R.id.tv_title);
    }

    private void showCreateOptions() {
        boolean isNight = this.getResources().getBoolean(R.bool.is_night);
        new XPopup.Builder(this)
                .isDarkTheme(isNight)
                .hasShadowBg(false)
                .borderRadius(XPopupUtils.dp2px(this, 15))
                .asBottomList(getString(R.string.choose_one_option),
                        new String[]{getString(R.string.create_from_template), getString(R.string.create_from_blank)},
                        (index, text) -> {
                            if (index == 0) {
                                WheelTemplateActivity.start(this);
                            } else if (index == 1) {
                                // id 传 -1：OptionListActivity 会基于默认选项创建新转盘
                                CreateEditActivity.start(this, -1, Request.Create_Wheel_Fron_Blank);
                            }
                        })
                .show();
    }

    private void showMore(int position) {
        boolean isNight = this.getResources().getBoolean(R.bool.is_night);

        new XPopup.Builder(this)
                .isDarkTheme(isNight)
                .hasShadowBg(false)
                .customHostLifecycle(getLifecycle())
                .moveUpToKeyboard(false)
                .isDestroyOnDismiss(false)
                .borderRadius(XPopupUtils.dp2px(this, 15))
                .asBottomList(getString(R.string.choose_one_option),
                        new String[]{getString(R.string.btn_edit), getString(R.string.delete), getString(R.string.make_a_copy)},
                        (index, text) -> {
                            if (index == 0) {
                                CreateEditActivity.start(this, list.get(position).getId(), -1, Request.Edit_Wheel);
                            } else if (index == 1) {
                                delWheel(position);
                            } else if (index == 2) {
                                makeCopy(position);
                            }
                        })
                .show();
    }

    private void makeCopy(int position) {
        Wheel copy = list.get(position).copy();
        singleThread.execute(() -> {
            MyApplication.wheelRepository.addWheel(copy);
            runOnUiThread(this::initData);
        });
    }

    private void delWheel(int position) {
        if (position < 0 || position >= list.size()) return;
        Wheel toDelete = list.get(position);
        DialogHelper.showDeleteConfirm(this,
                getString(R.string.confirm_deletion),
                getString(R.string.message_delete_confirm, toDelete.getTitle()),
                () -> singleThread.execute(() -> {
                    MyApplication.wheelRepository.removeWheel(toDelete);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        // 依据 id 而非 position 定位，避免快速连续删除时索引错位
                        int idx = -1;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).getId() != null
                                    && list.get(i).getId().equals(toDelete.getId())) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx >= 0) {
                            list.remove(idx);
                            wheelListAdapter.notifyItemRemoved(idx);
                        }
                    });
                }));
    }

    @Override
    public void initData() {
        singleThread.execute(() -> {
            List<Wheel> wheels = MyApplication.wheelRepository.getWheelList();
            runOnUiThread(() -> {
                list = wheels;
                wheelListAdapter.setNewInstance(list);
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        initData();
    }

    @Override
    public void configView() {
        tv_title.setText(R.string.my_wheel);
    }

    @Override
    public void goBack() {
        finish();
    }
}
