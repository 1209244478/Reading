package com.wrz.reading.ui.wheel.activity;

import static com.wrz.reading.ui.wheel.activity.BatchAddActivity.RESULT_BATCH_ADD;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.lxj.xpopup.XPopup;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.main.popupView.EmojiPopup;
import com.wrz.reading.ui.main.popupView.WeightChangePopup;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.wheel.adapter.OptionAdapter;
import com.wrz.reading.ui.wheel.adapter.TagAdapter;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.wheel.model.Tag;
import com.wrz.reading.ui.wheel.model.Wheel;
import com.wrz.reading.ui.wheel.view.dialog.ColorPickerDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CreateEditActivity extends BaseActivity {

    public static final String EXTRA_WHEEL_ID = "com.wrz.make_decision.EXTRA_WHEEL_ID";
    public static final String EXTRA_REQUEST_ID = "com.wrz.make_decision.EXTRA_REQUEST_ID";

    public static final int REQUEST_COE_BATCH_ADD = 10001;


    private SwitchCompat switch_allow_duplicates;
    private SwitchCompat switch_use_weight;
    private SwitchCompat switch_hide_weight;

    private LinearLayout ll_hide_weight;
    private TextView tv_title;
    private TextView tv_emoji;
    private TextInputEditText tv_question;
    private TextInputEditText et_category;
    private NestedScrollView sv_content;
    private TextView tv_time;
    private String requestId;
    private RecyclerView rv_tags;
    private TagAdapter tagAdapter;
    private ImageView iv_add_tag;


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

    private Wheel wheel;
    private List<Option> list;
    private OptionAdapter adapter;

    private String id;


    @Override
    public void getIntentData() {
        id = getIntent().getStringExtra(EXTRA_WHEEL_ID);
        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_option_list;
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

        sv_content = findViewById(R.id.sv_content);
        RecyclerView rv_list = findViewById(R.id.rv_list);
        rv_list.setLayoutManager(new LinearLayoutManager(this));

        adapter = new OptionAdapter(new OptionAdapter.ChangeListener() {
            @Override
            public void onChange(String newContent, int position) {
                if (!list.get(position).getOption().equals(newContent)) {
                    list.get(position).setOption(newContent);
                }
            }

            @Override
            public void onFocusChange(String newContent, int position, boolean hasFocus) {
            }
        });
        rv_list.setAdapter(adapter);

        adapter.addChildLongClickViewIds(R.id.color_view, R.id.tv_weight, R.id.iv_delete, R.id.iv_add, R.id.tv_add, R.id.iv_batch_add, R.id.tv_batch_add);
        adapter.addChildClickViewIds(R.id.color_view, R.id.tv_weight, R.id.iv_delete, R.id.iv_add, R.id.tv_add, R.id.iv_batch_add, R.id.tv_batch_add);

        adapter.setOnItemChildLongClickListener((adapter, view1, position) -> {
            view1.setTag(position);
            handleItemClick(view1.getId(), position);
            return true;
        });
        adapter.setOnItemChildClickListener((adapter, view1, position) -> {
            view1.setTag(position);
            handleItemClick(view1.getId(), position);
        });

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
            switch_hide_weight.setClickable(isChecked);
            ll_hide_weight.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        iv_add_tag = findViewById(R.id.iv_add_tag);
        iv_add_tag.setOnClickListener(v -> {
            showAddTag();
        });

        rv_tags = findViewById(R.id.rv_tags);

        tagAdapter = new TagAdapter(R.layout.item_tag);

        rv_tags.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv_tags.setAdapter(tagAdapter);

        tagAdapter.addChildClickViewIds(R.id.iv_delete);
        tagAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            delTag(position);
        });
    }

    private void showAddTag() {
        DialogHelper.showTextInputDialog(this, "添加标签",
                "", "添加", "",
                text -> {
                    wheel.getTags().add(new Tag(text));
                    tagAdapter.notifyDataSetChanged();
                });
    }

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

    private void batchAdd() {
        BatchAddActivity.start(this, REQUEST_COE_BATCH_ADD);
    }

    /**
     * 处理子 View 的点击事件
     */
    private void handleItemClick(int viewId, int position) {
        if (viewId == R.id.color_view) {
            showColorPicker(position);
        } else if (viewId == R.id.tv_weight) {
            showChangeWeight(position);
        } else if (viewId == R.id.iv_delete) {
            delOption(position);
        } else if (viewId == R.id.iv_add || viewId == R.id.tv_add) {
            addOption("");
        } else if (viewId == R.id.iv_batch_add || viewId == R.id.tv_batch_add) {
            batchAdd();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_COE_BATCH_ADD && resultCode == RESULT_OK && data != null) {
            String result = data.getStringExtra(RESULT_BATCH_ADD);
            if (result != null) {
                for (String op : result.split("\n")) {
                    addOption(op);
                }
            }
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

    private void showChangeWeight(int position) {
        /*EditConfirmDialog dialog = new EditConfirmDialog(this, getString(R.string.adjust_weights),
                "", String.valueOf(list.get(position).getWeight()), "1");
        dialog.setConfirmListener(text -> {
            int weight;
            if (text.isEmpty() || text.equals("0")) {
                weight = 1;
            } else {
                try {
                    weight = Integer.parseInt(text);
                    if (weight <= 0) weight = 1;
                } catch (NumberFormatException e) {
                    weight = 1;
                }
            }
            list.get(position).setWeight(weight);
            adapter.notifyItemChanged(position);
            calculatePercent(true);
        });
        dialog.show();*/

        boolean isNight = this.getResources().getBoolean(R.bool.is_night);
        new XPopup.Builder(this)
                .hasShadowBg(false)
                .isViewMode(true)
                .isDarkTheme(isNight)
                .autoOpenSoftInput(false)
                .isDestroyOnDismiss(false)
                .asCustom(new WeightChangePopup(this, 0, getString(R.string.adjust_weights), "1", String.valueOf(list.get(position).getWeight()), text -> {
                    int weight;
                    if (text.isEmpty() || text.equals("0")) {
                        weight = 1;
                    } else {
                        try {
                            weight = Integer.parseInt(text);
                            if (weight <= 0) weight = 1;
                        } catch (NumberFormatException e) {
                            weight = 1;
                        }
                    }
                    list.get(position).setWeight(weight);
                    adapter.notifyItemChanged(position);
                    calculatePercent(true);
                }))
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

    private void showColorPicker(int position) {
        List<Integer> colors = MyApplication.template.getColors();
        int currentColor = list.get(position).getColor();

        ColorPickerDialog dialog = new ColorPickerDialog(this, colors, currentColor);
        dialog.setOnClickListener(color -> {
            list.get(position).setColor(color);
            this.adapter.notifyItemChanged(position);
        });
        dialog.show();
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
        list = wheel.getList();
        if (list == null) {
            list = new ArrayList<>();
            wheel.setList(list);
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

        configView();
    }

    /**
     * 保存：根据模式写入对应表
     */
    private void save() {
        list.removeIf(option -> option.getOption().isEmpty());

        if (tv_question.getText() != null) {
            wheel.setTitle(tv_question.getText().toString().trim());
        }
        wheel.setList(list);

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

    @Override
    public void configView() {
        if (list == null) return;
        if (list.isEmpty()) {
            list.add(new Option(""));
        }
        if (!haveItemType(1)) {
            list.add(new Option(1, ""));
        }
        if (!haveItemType(2)) {
            list.add(new Option(2, ""));
        }

        if (wheel.getEmoji() != null && !wheel.getEmoji().isEmpty()) {
            tv_emoji.setText(wheel.getEmoji());
        }

        calculatePercent(false);
        tv_question.setText(wheel.getTitle());
        adapter.setNewInstance(list);

        tv_time.setText(String.valueOf(wheel.getTime()));

        tagAdapter.setNewInstance(wheel.getTags());
    }

    /**
     * 最大余数法计算百分比，确保总和正好为 100
     */
    @SuppressLint("NotifyDataSetChanged")
    public void calculatePercent(boolean notify) {
        int totalWeight = 0;
        for (Option option : list) {
            if (option.getItemType() == 0) {
                totalWeight += option.getWeight();
            }
        }

        if (totalWeight <= 0) {
            for (int i = 0; i < list.size(); i++) {
                Option option = list.get(i);
                if (option.getItemType() == 0) {
                    option.setPercent(0);
                    if (notify) adapter.notifyItemChanged(i);
                }
            }
            return;
        }

        List<Option> realOptions = new ArrayList<>();
        int[] remainders = new int[list.size()];
        int baseSum = 0;

        for (int i = 0; i < list.size(); i++) {
            Option option = list.get(i);
            if (option.getItemType() != 0) continue;
            realOptions.add(option);
            int exact = option.getWeight() * 100;
            int base = exact / totalWeight;
            remainders[realOptions.size() - 1] = exact % totalWeight;
            option.setPercent(base);
            baseSum += base;
        }

        int gap = 100 - baseSum;
        if (gap > 0 && !realOptions.isEmpty()) {
            Integer[] indices = new Integer[realOptions.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }
            java.util.Arrays.sort(indices, (a, b) -> Integer.compare(remainders[b], remainders[a]));
            for (int i = 0; i < gap && i < indices.length; i++) {
                realOptions.get(indices[i]).setPercent(realOptions.get(indices[i]).getPercent() + 1);
            }
        }

        if (notify) {
            adapter.notifyDataSetChanged();
        }
    }

    private boolean haveItemType(int itemType) {
        for (Option option : list) {
            if (option.getItemType() == itemType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void goBack() {
        finish();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void delOption(int position) {
        String option = list.get(position).getOption();
        DialogHelper.showConfirmDialog(this,
                getString(R.string.confirm_deletion),
                getString(R.string.message_delete_confirm, option),
                () -> {
                    list.remove(position);
                    calculatePercent(false);
                    adapter.notifyDataSetChanged();
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void addOption(String option) {
        Option newOption = new Option(option, getColor(), 1);

        if (list.size() >= 2) {
            // 插入到末尾两个特殊项（itemType 1/2）之前
            list.add(list.size() - 2, newOption);
        } else {
            list.add(newOption);
        }

        calculatePercent(false);
        adapter.notifyDataSetChanged();
        sv_content.fullScroll(View.FOCUS_DOWN);
    }

    private int getColor() {
        Set<Integer> usedColors = new HashSet<>();
        for (Option option : list) {
            usedColors.add(option.getColor());
        }
        for (Integer color : MyApplication.template.getColors()) {
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        return ContextCompat.getColor(this, R.color.ios_blue);
    }
}
