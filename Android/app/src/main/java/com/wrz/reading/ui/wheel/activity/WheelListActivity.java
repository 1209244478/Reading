package com.wrz.reading.ui.wheel.activity;

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
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.wheel.adapter.ListTagAdapter;
import com.wrz.reading.ui.wheel.adapter.WheelListAdapter;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.wheel.model.Tag;
import com.wrz.reading.ui.wheel.model.Wheel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WheelListActivity extends BaseActivity {

    private TextView tv_title;
    private List<Wheel> list;
    private List<Wheel> displayList;
    public WheelListAdapter wheelListAdapter;
    private ListTagAdapter listTagAdapter;
    /*private String selectedTag = null; // null 表示"全部"*/
    private RecyclerView rv_tags;

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
            Wheel wheel = displayList.get(position);
            singleThread.execute(() -> MyApplication.wheelRepository.saveLastTime(wheel));
            finish();
        });

        FloatingActionButton btn_add = findViewById(R.id.btn_add);
        btn_add.setOnClickListener(v -> showCreateOptions());

        tv_title = findViewById(R.id.tv_title);


        rv_tags = findViewById(R.id.rv_tags);
        rv_tags.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        listTagAdapter = new ListTagAdapter();

        rv_tags.setAdapter(listTagAdapter);

        listTagAdapter.setOnItemClickListener((adapter, view, position) -> {
            Tag clicked = listTagAdapter.getItem(position);
            if (clicked == null) return;

            if ("全部".equals(clicked.getTag())) {
                listTagAdapter.setAllSelected();
                tagSet.clear();
            } else {
                listTagAdapter.NoSetAll();

                clicked.setSelected(!clicked.isSelected());
                listTagAdapter.notifyItemChanged(position);

                if (clicked.isSelected()) {
                    tagSet.add(clicked.getTag());
                } else {
                    tagSet.removeIf(tag -> tag.equals(clicked.getTag()));
                }

                if (tagSet.isEmpty()) {
                    listTagAdapter.setAllSelected();
                }
            }

            // 刷新列表
            applyFilter();

        });
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
        Wheel wheel = displayList.get(position);

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
                                CreateEditActivity.start(this, wheel.getId(), -1, Request.Edit_Wheel);
                            } else if (index == 1) {
                                delWheel(wheel);
                            } else if (index == 2) {
                                makeCopy(wheel);
                            }
                        })
                .show();
    }

    private void makeCopy(Wheel wheel) {
        Wheel copy = wheel.copy();
        singleThread.execute(() -> {
            MyApplication.wheelRepository.addWheel(copy);
            runOnUiThread(this::initData);
        });
    }

    private void delWheel(Wheel toDelete) {
        DialogHelper.showConfirmDialog(this,
                getString(R.string.confirm_deletion),
                getString(R.string.message_delete_confirm, toDelete.getTitle()),
                () -> singleThread.execute(() -> {
                    MyApplication.wheelRepository.removeWheel(toDelete);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        // 依据 id 而非 position 定位，避免快速连续删除时索引错位
                        int idx = -1;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).getId().equals(toDelete.getId())) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx >= 0) {
                            list.remove(idx);
                        }
                        // 从 displayList 中移除并通知适配器
                        int displayIdx = displayList.indexOf(toDelete);
                        if (displayIdx >= 0) {
                            displayList.remove(displayIdx);
                            wheelListAdapter.notifyItemRemoved(displayIdx);
                        }
                    });
                }));
    }

    Set<String> tagSet = new LinkedHashSet<>();

    @Override
    public void initData() {
        singleThread.execute(() -> {
            List<Wheel> wheels = MyApplication.wheelRepository.getWheelList();
            runOnUiThread(() -> {
                list = wheels;

                // 从所有 wheel 中提取不重复的 tag，保持插入顺序
                initTags();

                applyFilter();
            });
        });
    }

    private void initTags() {
        tagSet.clear();
        Set<String> tempSet = new LinkedHashSet<>();
        for (Wheel w : list) {
            if (w.getTags() != null) {
                for (Tag t : w.getTags()) {
                    if (t.getTag() != null && !t.getTag().isEmpty()) {
                        tempSet.add(t.getTag());
                    }
                }
            }
        }

        List<Tag> tagList = new ArrayList<>();
        // "全部" 作为第一个 tag
        Tag allTag = new Tag("全部");
        allTag.setSelected(true);
        tagList.add(allTag);

        for (String ts : tempSet) {
            Tag t = new Tag(ts);
            t.setSelected(false);
            tagList.add(t);
        }
        listTagAdapter.setNewInstance(tagList);
        if (tagList.size() > 1) {
            rv_tags.setVisibility(View.VISIBLE);
        } else {
            rv_tags.setVisibility(View.GONE);
        }
    }

    /** 根据 selectedTag 过滤 wheel 列表并刷新适配器 */
    private void applyFilter() {
        if (list == null) return;
        boolean all = tagSet.isEmpty();
        if (all) {
            displayList = list;
        } else {
            displayList = new ArrayList<>();
            for (Wheel w : list) {
                if (w.getTags() != null) {
                    for (Tag t : w.getTags()) {
                        if (tagSet.contains(t.getTag())) {
                            LogUtil.e(TAG, "applyFilter: " + w.getTitle());
                            displayList.add(w);
                            break;
                        }
                    }
                }
            }
        }
        wheelListAdapter.setNewInstance(displayList);
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
