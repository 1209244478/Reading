package com.wrz.reading.ui.wheel.activity;

import static com.wrz.reading.ui.wheel.activity.BatchAddActivity.RESULT_BATCH_ADD;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.lxj.xpopup.XPopup;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.wheel.adapter.ColorGridAdapter;
import com.wrz.reading.util.DialogHelper;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.model.Option;
import com.wrz.reading.model.Wheel;
import com.wrz.reading.ui.wheel.adapter.OptionAdapter;
import com.wrz.reading.view.popupView.EmojiPopup;
import com.wrz.reading.view.popupView.WeightChangePopup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OptionListActivity extends BaseActivity {

    public static final String EXTRA_WHEEL_ID = "com.wrz.make_decision.EXTRA_WHEEL_ID";
    public static final String EXTRA_IS_CREATE_TEMPLATE = "com.wrz.make_decision.EXTRA_IS_CREATE_TEMPLATE";
    public static final String EXTRA_IS_EDIT_TEMPLATE = "com.wrz.make_decision.EXTRA_IS_EDIT_TEMPLATE";
    public static final String EXTRA_IS_COPY_FROM_TEMPLATE = "com.wrz.make_decision.EXTRA_IS_COPY_FROM_TEMPLATE";

    public static final int REQUEST_COE_BATCH_ADD = 10001;

    private Switch switch_allow_duplicates;
    private Switch switch_use_weight;
    private Switch switch_hide_weight;

    private LinearLayout ll_hide_weight;
    private TextView tv_title;
    private TextView tv_emoji;
    private TextInputEditText tv_question;
    private TextInputEditText et_category;
    private NestedScrollView sv_content;
    private TextView tv_time;


    /** 编辑用户转盘 */
    public static void start(Context context, String id) {
        Intent intent = new Intent(context, OptionListActivity.class);
        intent.putExtra(EXTRA_WHEEL_ID, id);
        context.startActivity(intent);
    }

    /** 创建新模板 */
    public static void startCreateTemplate(Activity context, int requestCode) {
        Intent intent = new Intent(context, OptionListActivity.class);
        intent.putExtra(EXTRA_IS_CREATE_TEMPLATE, true);
        context.startActivityForResult(intent, requestCode);
    }

    /** 编辑现有模板 */
    public static void startEditTemplate(Activity context, String templateId, int requestCode) {
        Intent intent = new Intent(context, OptionListActivity.class);
        intent.putExtra(EXTRA_WHEEL_ID, templateId);
        intent.putExtra(EXTRA_IS_EDIT_TEMPLATE, true);
        context.startActivityForResult(intent, requestCode);
    }

    /** 从模板复制创建用户转盘 */
    public static void startFromTemplate(Activity context, String templateId, int requestCode) {
        Intent intent = new Intent(context, OptionListActivity.class);
        intent.putExtra(EXTRA_WHEEL_ID, templateId);
        intent.putExtra(EXTRA_IS_COPY_FROM_TEMPLATE, true);
        context.startActivityForResult(intent, requestCode);
    }

    private Wheel wheel;
    private List<Option> list;
    private OptionAdapter adapter;

    private String id;
    private boolean isCreateTemplate;
    private boolean isEditTemplate;
    private boolean isCopyFromTemplate;

    @Override
    public void getIntentData() {
        id = getIntent().getStringExtra(EXTRA_WHEEL_ID);
        isCreateTemplate = getIntent().getBooleanExtra(EXTRA_IS_CREATE_TEMPLATE, false);
        isEditTemplate = getIntent().getBooleanExtra(EXTRA_IS_EDIT_TEMPLATE, false);
        isCopyFromTemplate = getIntent().getBooleanExtra(EXTRA_IS_COPY_FROM_TEMPLATE, false);
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
        tv_time.setOnClickListener(view -> {
            showChangeTime();
        });

        switch_allow_duplicates = findViewById(R.id.switch_allow_duplicates);
        switch_allow_duplicates.setOnCheckedChangeListener((buttonView, isChecked) ->
                wheel.setAllowDuplicates(isChecked));


        ll_hide_weight = findViewById(R.id.ll_hide_weight);

        switch_hide_weight = findViewById(R.id.switch_hide_weight);
        switch_hide_weight.setOnCheckedChangeListener((buttonView, isChecked) ->
                wheel.setHideWeight(isChecked));

        switch_use_weight = findViewById(R.id.switch_use_weight);
        switch_use_weight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wheel.setUseWeight(isChecked);
            switch_hide_weight.setClickable(isChecked);
            ll_hide_weight.setVisibility(isChecked ? View.VISIBLE : View.GONE);
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

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);
        View previewBlock = dialogView.findViewById(R.id.color_preview);
        TextView hexText = dialogView.findViewById(R.id.color_hex);
        RecyclerView colorGrid = dialogView.findViewById(R.id.color_grid);

        GradientDrawable previewDrawable = new GradientDrawable();
        previewDrawable.setCornerRadius(8f);
        previewDrawable.setColor(currentColor);
        previewBlock.setBackground(previewDrawable);
        hexText.setText(String.format("#%06X", 0xFFFFFF & currentColor));

        colorGrid.setLayoutManager(new GridLayoutManager(this, 6));
        ColorGridAdapter adapter = new ColorGridAdapter(colors, currentColor);
        colorGrid.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final int[] selected = {currentColor};
        adapter.setOnColorClickListener(color -> {
            selected[0] = color;
            previewDrawable.setColor(color);
            hexText.setText(String.format("#%06X", 0xFFFFFF & color));
            adapter.setSelectedColor(color);
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            list.get(position).setColor(selected[0]);
            this.adapter.notifyItemChanged(position);
            dialog.dismiss();
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
        if (isEditTemplate) {
            return MyApplication.wheelRepository.getTemplateById(id);
        }
        if (isCopyFromTemplate) {
            Wheel template = MyApplication.wheelRepository.getTemplateById(id);
            return template != null ? template.copy() : null;
        }
        if (isCreateTemplate) {
            Wheel w = MyApplication.wheelRepository.getBlankWheel();
            w.setId(String.valueOf(System.currentTimeMillis()));
            return w;
        }
        if (id == null || id.equals("-1")) {
            Wheel w = MyApplication.wheelRepository.getDefaultWheel();
            w.setId(String.valueOf(System.currentTimeMillis()));
            return w;
        }
        return MyApplication.wheelRepository.getWheelById(id);
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

        boolean showCategory = isEditTemplate || isCreateTemplate;
        findViewById(R.id.label_category).setVisibility(showCategory ? View.VISIBLE : View.GONE);
        findViewById(R.id.card_category).setVisibility(showCategory ? View.VISIBLE : View.GONE);

        if (isEditTemplate) {
            tv_title.setText(R.string.edit_template);
            if (wheel.getCategory() != null) {
                et_category.setText(wheel.getCategory());
            }
        } else if (isCreateTemplate) {
            tv_title.setText(R.string.create_template);
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

        if (isEditTemplate || isCreateTemplate) {
            String category = et_category.getText() != null
                    ? et_category.getText().toString().trim() : "";
            if (category.isEmpty()) {
                category = getString(R.string.cpv_custom);
            }
            wheel.setCategory(category);
        }

        singleThread.execute(() -> {
            if (isEditTemplate) {
                MyApplication.wheelRepository.updateTemplate(wheel);
            } else if (isCreateTemplate) {
                MyApplication.wheelRepository.addTemplate(wheel);
            } else if (isCopyFromTemplate) {
                // 从模板复制创建的新转盘：插入并设为最后使用，使其立即激活
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
    }

    /**
     * 最大余数法计算百分比，确保总和正好为 100
     */
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
        DialogHelper.showDeleteConfirm(this,
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
