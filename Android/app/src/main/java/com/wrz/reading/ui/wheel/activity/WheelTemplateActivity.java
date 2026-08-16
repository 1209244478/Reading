package com.wrz.reading.ui.wheel.activity;

import static com.wrz.reading.ui.wheel.model.Wheel.TYPE_CONTENT;
import static com.wrz.reading.ui.wheel.model.Wheel.TYPE_TITLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.util.XPopupUtils;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.wheel.model.Wheel;
import com.wrz.reading.ui.wheel.adapter.WheelTemplateListAdapter;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.main.utils.DialogHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WheelTemplateActivity extends BaseActivity {

    public static int REQUEST_CODE_ADD = 1000;
    public static int REQUEST_CODE_CREATE_TEMPLATE = 1001;
    public static int REQUEST_CODE_EDIT_TEMPLATE = 1002;

    private TextView tv_title;
    private TextView tv_save;

    public static void start(Context context) {
        context.startActivity(new Intent(context, WheelTemplateActivity.class));
    }

    private final ArrayList<Wheel> list = new ArrayList<>();
    public WheelTemplateListAdapter templateListAdapter;

    @Override
    public void getIntentData() {
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_wheel_template;
    }

    @Override
    public void initToolBar() {
    }

    @Override
    public void initView() {
        RecyclerView recyclerView = findViewById(R.id.rv_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        templateListAdapter = new WheelTemplateListAdapter();
        templateListAdapter.setNewInstance(list);
        recyclerView.setAdapter(templateListAdapter);

        // 点击模板：复制为用户转盘并打开编辑页
        templateListAdapter.setOnItemClickListener((adapter, view, position) -> {
            Wheel item = list.get(position);
            if (item.getType() == TYPE_CONTENT) {
                CreateEditActivity.start(this, item.getId(), REQUEST_CODE_ADD, Request.Create_Wheel);
            }
        });

        templateListAdapter.addChildClickViewIds(R.id.iv_more);
        templateListAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_more) {
                showMore(position);
            }
        });

        tv_title = findViewById(R.id.tv_title);
        tv_save = findViewById(R.id.tv_save);
        tv_save.setOnClickListener(view -> goBack());

        FloatingActionButton btn_add = findViewById(R.id.btn_add);
        btn_add.setOnClickListener(v ->
                CreateEditActivity.start(this, REQUEST_CODE_CREATE_TEMPLATE, Request.Create_Template));
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void initData() {
        singleThread.execute(() -> {
            List<Wheel> templates = MyApplication.wheelRepository.getTemplateList();
            List<Wheel> grouped = buildGroupedList(templates);
            runOnUiThread(() -> {
                // adapter 在 initView 中已通过 setNewInstance(list) 持有 list 引用
                // 直接原地修改并 notifyDataSetChanged，无需引用比较
                list.clear();
                list.addAll(grouped);
                templateListAdapter.notifyDataSetChanged();
            });
        });
    }

    /**
     * 将模板列表按 category 分组，每组前插入 TYPE_TITLE 项
     */
    private List<Wheel> buildGroupedList(List<Wheel> templates) {
        Map<String, List<Wheel>> grouped = new LinkedHashMap<>();
        for (Wheel wheel : templates) {
            String category = wheel.getCategory();
            if (category == null || category.isEmpty()) {
                category = getString(R.string.cpv_custom);
            }
            wheel.setType(TYPE_CONTENT);
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(wheel);
        }

        List<Wheel> result = new ArrayList<>();
        for (Map.Entry<String, List<Wheel>> entry : grouped.entrySet()) {
            result.add(new Wheel(entry.getKey(), TYPE_TITLE));
            result.addAll(entry.getValue());
        }
        return result;
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
                                CreateEditActivity.start(this, list.get(position).getId(), REQUEST_CODE_EDIT_TEMPLATE, Request.Edit_Template);
                            } else if (index == 1) {
                                delTemplate(position);
                            } else if (index == 2) {
                                makeCopyTemplate(position);
                            }
                        })
                .show();
    }

    private void makeCopyTemplate(int position) {
        Wheel copy = list.get(position).copy();
        singleThread.execute(() -> {
            MyApplication.wheelRepository.addTemplate(copy);
            runOnUiThread(this::initData);
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void delTemplate(int position) {
        Wheel item = list.get(position);
        // 标题项不应被删除（安全防护）
        if (item.getType() == TYPE_TITLE) return;
        DialogHelper.showDeleteConfirm(this,
                getString(R.string.confirm_deletion),
                getString(R.string.message_delete_confirm, item.getTitle()),
                () -> singleThread.execute(() -> {
                    MyApplication.wheelRepository.delTemplate(item);
                    // 删除后重新加载分组，确保空分类标题被移除
                    runOnUiThread(this::initData);
                }));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_CODE_ADD) {
            // 从模板创建用户转盘：OptionListActivity 已保存，直接返回上一级
            finish();
        } else if (requestCode == REQUEST_CODE_EDIT_TEMPLATE || requestCode == REQUEST_CODE_CREATE_TEMPLATE) {
            // 编辑/新建模板完成：刷新列表
            initData();
        }
    }

    @Override
    public void configView() {
        tv_title.setText(R.string.wheel_template);
        tv_save.setText(R.string.close);
        tv_save.setVisibility(View.VISIBLE);
    }

    @Override
    public void goBack() {
        finish();
    }
}
