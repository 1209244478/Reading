package com.wrz.reading.ui.wheel.activity;

import static com.wrz.reading.ui.wheel.activity.BatchAddActivity.RESULT_BATCH_ADD;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.textfield.TextInputEditText;
import com.lxj.xpopup.XPopup;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.main.popupView.EmojiPopup;
import com.wrz.reading.ui.main.popupView.WeightChangePopup;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.wheel.adapter.TagAdapter;
import com.wrz.reading.ui.wheel.adapter.WheelAdapter;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.wheel.model.Tag;
import com.wrz.reading.ui.wheel.model.Wheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CreateEditActivity extends BaseActivity {

    private String id;

    private Wheel wheel;

    private List<List<Option>> lists;

    private String requestId;

    public static final String EXTRA_WHEEL_ID = "com.wrz.make_decision.EXTRA_WHEEL_ID";
    public static final String EXTRA_REQUEST_ID = "com.wrz.make_decision.EXTRA_REQUEST_ID";
    private WheelAdapter adapter;
    private TextView tv_emoji;
    private TextView tv_time;
    private SwitchCompat switch_allow_duplicates;
    private TagAdapter tagAdapter;
    private SwitchCompat switch_hide_weight;
    private SwitchCompat switch_use_weight;
    private LinearLayout ll_hide_weight;
    private TextView tv_title;
    private TextInputEditText et_category;
    private TextView tv_question;
    private ViewPager2 vp_list;

    public static void start(Activity context, int requestCode, Request requestId) {
        Intent intent = new Intent(context, CreateEditActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId.getCode());
        context.startActivityForResult(intent, requestCode);
    }

    public static void start(Activity context, String id, int requestCode, Request requestId) {
        Intent intent = new Intent(context, CreateEditActivity.class);
        intent.putExtra(EXTRA_WHEEL_ID, id);
        intent.putExtra(EXTRA_REQUEST_ID, requestId.getCode());
        context.startActivityForResult(intent, requestCode);
    }

    @Override
    public void getIntentData() {
        id = getIntent().getStringExtra(EXTRA_WHEEL_ID);
        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_edit;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        tv_title = findViewById(R.id.tv_title);
        et_category = findViewById(R.id.et_category);

        TextView tv_save = findViewById(R.id.tv_save);
        tv_save.setVisibility(View.VISIBLE);
        tv_save.setOnClickListener(v -> save());

        tv_emoji = findViewById(R.id.tv_emoji);
        tv_emoji.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            showEmojiPopup();
        });

        tv_question = findViewById(R.id.tv_question);

        vp_list = findViewById(R.id.vp_list);

        adapter = new WheelAdapter(this);

        vp_list.setAdapter(adapter);


        tv_time = findViewById(R.id.tv_time);
        tv_time.setOnClickListener(view -> showChangeTime());


        switch_allow_duplicates = findViewById(R.id.switch_allow_duplicates);
        switch_allow_duplicates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (wheel != null) {
                wheel.setAllowDuplicates(isChecked);
            }
        });


        ll_hide_weight = findViewById(R.id.ll_hide_weight);

        switch_hide_weight = findViewById(R.id.switch_hide_weight);
        switch_hide_weight.setOnCheckedChangeListener((buttonView, isChecked) ->
                wheel.setHideWeight(isChecked));

        switch_use_weight = findViewById(R.id.switch_use_weight);
        switch_use_weight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (wheel != null) {
                wheel.setUseWeight(isChecked);
            }
            adapter.setUseWeight(isChecked);
            switch_hide_weight.setClickable(isChecked);
            ll_hide_weight.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        ImageView iv_add_tag = findViewById(R.id.iv_add_tag);
        iv_add_tag.setOnClickListener(v -> showAddTag());

        RecyclerView rv_tags = findViewById(R.id.rv_tags);

        tagAdapter = new TagAdapter(R.layout.item_tag);

        rv_tags.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv_tags.setAdapter(tagAdapter);

        tagAdapter.addChildClickViewIds(R.id.iv_delete);
        tagAdapter.setOnItemChildClickListener((adapter, view, position) -> delTag(position));

        setClick(R.id.btn_go_previous, v -> {
            if (vp_list.getCurrentItem() != 0) {
                vp_list.setCurrentItem(vp_list.getCurrentItem() - 1);
            }
        });
        setClick(R.id.btn_go_next, v -> {
            if (vp_list.getCurrentItem() != (lists.size() - 1)) {
                vp_list.setCurrentItem(vp_list.getCurrentItem() + 1);
            }
        });

        setClick(R.id.btn_del_list, v -> {
            adapter.removeIndex(vp_list.getCurrentItem());
            setCount();
        });
        setClick(R.id.btn_add_list, v -> {
            wheel.getList().add(new ArrayList<>());
            adapter.notifyDataSetChanged();
            setCount();
        });
        setClick(R.id.ll_explanation, v -> showExplanation());

        vp_list.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setCount();
            }
        });
    }

    private void setCount() {
        setText((vp_list.getCurrentItem() + 1) + "/" + lists.size(), R.id.tv_count);
    }

    private void showExplanation() {
        DialogHelper.showTextInputDialog(this, getString(R.string.wheel_explanation), "", getString(R.string.btn_confirm),
                (wheel.getExplanation() != null) ? wheel.getExplanation() : "", text -> {
                    if (!TextUtils.isEmpty(text)) {
                        wheel.setExplanation(text);
                    } else {
                        wheel.setExplanation("");
                    }
                });
    }


    @Override
    public void initData() {
        singleThread.execute(() -> {
            Wheel w = loadWheel();
            if (w == null) {
                runOnUiThread(this::finish);
                return;
            }
            runOnUiThread(() -> applyWheel(w));
        });
    }

    /**
     * 根据启动模式加载 Wheel，统一在后台线程执行。
     * 返回 null 表示模板/转盘不存在，调用方应 finish。
     */
    private Wheel loadWheel() {
        // 创建转盘（从模板）
        if (Objects.equals(requestId, Request.Create_Wheel.getCode())) {
            Wheel template = MyApplication.wheelRepository.getTemplateById(id);
            return template != null ? template.copy() : null;

            // 编辑转盘
        } else if (Objects.equals(requestId, Request.Edit_Wheel.getCode())) {
            return MyApplication.wheelRepository.getWheelById(id);

            // 创建模板
        } else if (Objects.equals(requestId, Request.Create_Template.getCode())) {
            Wheel w = MyApplication.wheelRepository.getBlankWheel();
            w.setId(String.valueOf(System.currentTimeMillis()));
            return w;

            // 编辑模板
        } else if (Objects.equals(requestId, Request.Edit_Template.getCode())) {
            return MyApplication.wheelRepository.getTemplateById(id);

            // 从空白转盘创建转盘
        } else if (Objects.equals(requestId, Request.Create_Wheel_Fron_Blank.getCode())) {

            return MyApplication.wheelRepository.getBlankWheel();
            // 使用默认转盘
        } else {
            Wheel w = MyApplication.wheelRepository.getDefaultWheel();
            w.setId(String.valueOf(System.currentTimeMillis()));
            return w;
        }
    }


    /**
     * 应用加载到的 Wheel 到 UI
     */
    private void applyWheel(Wheel w) {
        wheel = w;
        lists = wheel.getList();
        if (lists == null) {
            lists = new ArrayList<>();
            wheel.setList(lists);
        }

        switch_allow_duplicates.setChecked(wheel.isAllowDuplicates());
        switch_hide_weight.setClickable(wheel.isUseWeight());
        switch_use_weight.setChecked(wheel.isUseWeight());

        ll_hide_weight.setVisibility(wheel.isUseWeight() ? View.VISIBLE : View.GONE);

        switch_hide_weight.setChecked(wheel.isHideWeight());

        boolean showCategory = Objects.equals(requestId, Request.Create_Template.getCode())
                || Objects.equals(requestId, Request.Edit_Template.getCode());

        findViewById(R.id.label_category).setVisibility(showCategory ? View.VISIBLE : View.GONE);
        findViewById(R.id.card_category).setVisibility(showCategory ? View.VISIBLE : View.GONE);

        setTitle();

        configView();
    }

    private void setTitle() {
        if (Objects.equals(requestId, Request.Create_Wheel.getCode())) {
            tv_title.setText(R.string.create_wheel);

            // 编辑转盘
        } else if (Objects.equals(requestId, Request.Edit_Wheel.getCode())) {
            tv_title.setText(R.string.edit_wheel);

            // 创建模板
        } else if (Objects.equals(requestId, Request.Create_Template.getCode())) {
            tv_title.setText(R.string.create_template);

            // 编辑模板
        } else if (Objects.equals(requestId, Request.Edit_Template.getCode())) {
            tv_title.setText(R.string.edit_template);
            if (wheel.getCategory() != null) {
                et_category.setText(wheel.getCategory());
            }

            // 从空白转盘创建转盘
        } else if (Objects.equals(requestId, Request.Create_Wheel_Fron_Blank.getCode())) {
            tv_title.setText(R.string.create_wheel);

            // 使用默认转盘
        } else {
            tv_title.setText(R.string.edit_wheel);
        }
    }

    private void showEmojiPopup() {
        EmojiPopup emojiPopup = new EmojiPopup(this, wheel.getEmoji(), emoji -> {
            Log.i(TAG, "showEmojiPopup: emoji:" + emoji);
            tv_emoji.setText(emoji);
            wheel.setEmoji(emoji);
        });
        new XPopup.Builder(this)
                .hasShadowBg(false)
                .isViewMode(true)
                .autoOpenSoftInput(false)
                .isDestroyOnDismiss(false)
                .asCustom(emojiPopup)
                .show();
    }

    private void showChangeTime() {
        boolean isNight = this.getResources().getBoolean(R.bool.is_night);
        new XPopup.Builder(this)
                .hasShadowBg(false)
                .isViewMode(true)
                .isDarkTheme(isNight)
                .autoOpenSoftInput(false)
                .isDestroyOnDismiss(false)
                .asCustom(new WeightChangePopup(this, 0, getString(R.string.adjust_time), "3", String.valueOf(wheel.getTime()), text -> {
                    int time;
                    if (text.isEmpty() || text.equals("0")) {
                        time = 1;
                    } else {
                        try {
                            time = Integer.parseInt(text);
                            if (time <= 0) time = 1;
                        } catch (NumberFormatException e) {
                            time = 1;
                        }
                    }
                    wheel.setTime(time);
                    tv_time.setText(text);
                }))
                .show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showAddTag() {
        DialogHelper.showTextInputDialog(this, "添加标签",
                "", "添加", "",
                text -> {
                    wheel.getTags().add(new Tag(text));
                    tagAdapter.notifyDataSetChanged();
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void delTag(int position) {
        String tag = wheel.getTags().get(position).getTag();
        DialogHelper.showConfirmDialog(this,
                getString(R.string.confirm_deletion),
                getString(R.string.message_delete_confirm, tag),
                () -> {
                    wheel.getTags().remove(position);
                    tagAdapter.notifyDataSetChanged();
                });

    }

    @Override
    public void configView() {
        if (lists == null) return;

        if (wheel.getEmoji() != null && !wheel.getEmoji().isEmpty()) {
            tv_emoji.setText(wheel.getEmoji());
        }
        tv_question.setText(wheel.getTitle());
        tv_time.setText(String.valueOf(wheel.getTime()));

        adapter.setNewInstance(lists);
        tagAdapter.setNewInstance(wheel.getTags());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            adapter.setUseWeight(wheel.isUseWeight());
        }, 100);
    }

    @Override
    public void goBack() {

    }

    /**
     * 保存：根据模式写入对应表
     */
    private void save() {
        for (List<Option> list : lists) {
            list.removeIf(option -> option.getOption().isEmpty());
        }

        if (tv_question.getText() != null) {
            wheel.setTitle(tv_question.getText().toString().trim());
        }
        wheel.setList(lists);

        if (Objects.equals(requestId, Request.Create_Template.getCode())
                || Objects.equals(requestId, Request.Edit_Template.getCode())) {
            String category = et_category.getText() != null
                    ? et_category.getText().toString().trim() : "";
            if (category.isEmpty()) {
                category = getString(R.string.cpv_custom);
            }
            wheel.setCategory(category);
        }

        singleThread.execute(() -> {
            if (Objects.equals(requestId, Request.Edit_Template.getCode())) {
                MyApplication.wheelRepository.updateTemplate(wheel);

            } else if (Objects.equals(requestId, Request.Create_Template.getCode())) {
                MyApplication.wheelRepository.addTemplate(wheel);

            } else if (Objects.equals(requestId, Request.Create_Wheel.getCode())
                    || Objects.equals(requestId, Request.Create_Wheel_Fron_Blank.getCode())) {
                // 创建的新转盘：插入并设为最后使用，使其立即激活
                MyApplication.wheelRepository.saveLastTime(wheel);

            } else {
                MyApplication.wheelRepository.updateWheel(wheel);

            }
            runOnUiThread(() -> {
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    public static int REQUEST_COE_BATCH_ADD = 1000;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COE_BATCH_ADD && resultCode == RESULT_OK && data != null) {
            String result = data.getStringExtra(RESULT_BATCH_ADD);
            if (result != null) {
                for (String op : result.split("\n")) {
                    int position = vp_list.getCurrentItem();
                    Option newOption = new Option(op, adapter.getColor(position), 1);

                    adapter.addOptionToList(newOption, lists.get(position));
                }
                adapter.notifyItemChanged(vp_list.getCurrentItem());
            }
        }
    }

}