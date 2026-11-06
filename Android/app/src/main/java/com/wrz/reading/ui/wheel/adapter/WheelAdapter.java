package com.wrz.reading.ui.wheel.adapter;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.lxj.xpopup.XPopup;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.main.popupView.WeightChangePopup;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.wheel.activity.BatchAddActivity;
import com.wrz.reading.ui.wheel.activity.CreateEditActivity;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.view.dialog.ColorPickerDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WheelAdapter extends BaseQuickAdapter<List<Option>, BaseViewHolder> {

    final Map<Integer, OptionAdapter> adapterMap = new HashMap<>();

    Activity activity;

    public WheelAdapter(Activity activity) {
        super(R.layout.item_rv);

        this.activity = activity;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, List<Option> options) {
        RecyclerView rv_list = holder.getView(R.id.rv_list);

        if (isLand() && !isTv()) {
            rv_list.setLayoutManager(new GridLayoutManager(holder.itemView.getContext(), 2));
        } else {
            rv_list.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        }

        OptionAdapter adapter = new OptionAdapter(new OptionAdapter.ChangeListener() {
            @Override
            public void onChange(String newContent, int position) {
                if (!getData().get(holder.getLayoutPosition()).get(position).getOption().equals(newContent)) {
                    getData().get(holder.getLayoutPosition()).get(position).setOption(newContent);
                }
            }

            @Override
            public void onFocusChange(String newContent, int position, boolean hasFocus) {

            }
        });

        rv_list.setAdapter(adapter);

        adapterMap.put(holder.getLayoutPosition(), adapter);

        adapter.addChildLongClickViewIds(R.id.color_view, R.id.tv_weight, R.id.iv_delete, R.id.item_add, R.id.item_batch_add);
        adapter.addChildClickViewIds(R.id.color_view, R.id.tv_weight, R.id.iv_delete, R.id.item_add, R.id.item_batch_add);

        adapter.setOnItemChildLongClickListener((adapter1, view1, position) -> {
            view1.setTag(position);
            handleItemClick(view1.getId(), position, holder.getLayoutPosition());
            return true;
        });
        adapter.setOnItemChildClickListener((adapter1, view1, position) -> {
            view1.setTag(position);
            handleItemClick(view1.getId(), position, holder.getLayoutPosition());
        });

        if (options.isEmpty()) {
            options.add(new Option(""));
        }
        if (!haveItemType(1, options)) {
            options.add(new Option(1, ""));
        }
        if (!haveItemType(2, options)) {
            options.add(new Option(2, ""));
        }

        adapter.setNewInstance(options);

        calculatePercent(true, holder.getLayoutPosition());
    }

    @SuppressLint("NotifyDataSetChanged")
    public void removeIndex(int index) {
        this.getData().remove(index);
        adapterMap.clear();
        this.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setUseWeight(boolean useWeight) {
        for (Map.Entry<Integer, OptionAdapter> map : adapterMap.entrySet()) {
            OptionAdapter adapter = map.getValue();
            if (adapter != null) {
                adapter.setUseWeight(useWeight);
                adapter.notifyDataSetChanged();
            }
        }
    }

    private boolean haveItemType(int itemType, List<Option> list) {
        for (Option option : list) {
            if (option.getItemType() == itemType) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理子 View 的点击事件
     */
    private void handleItemClick(int viewId, int position, int vp_position) {
        if (viewId == R.id.color_view) {
            showColorPicker(position, vp_position);
        } else if (viewId == R.id.tv_weight) {
            showChangeWeight(position, vp_position);
        } else if (viewId == R.id.iv_delete) {
            delOption(position, vp_position);
        } else if (viewId == R.id.item_add) {
            addOption("", vp_position);
        } else if (viewId == R.id.item_batch_add) {
            batchAdd(vp_position);
        }
    }

    private void showColorPicker(int position, int vp_position) {
        List<Integer> colors = MyApplication.template.getColors();
        int currentColor = this.getData().get(vp_position).get(position).getColor();

        ColorPickerDialog dialog = new ColorPickerDialog(this.getContext(), colors, currentColor);
        dialog.setOnClickListener(color -> {
            this.getData().get(vp_position).get(position).setColor(color);
            this.adapterMap.get(vp_position).notifyItemChanged(position);
        });
        dialog.show();
    }

    private void showChangeWeight(int position, int vp_position) {
        boolean isNight = this.getContext().getResources().getBoolean(R.bool.is_night);
        new XPopup.Builder(this.getContext())
                .hasShadowBg(false)
                .isViewMode(true)
                .isDarkTheme(isNight)
                .autoOpenSoftInput(false)
                .isDestroyOnDismiss(false)
                .asCustom(new WeightChangePopup(this.getContext(), 0, this.getContext().getString(R.string.adjust_weights), "1", String.valueOf(this.getData().get(vp_position).get(position).getWeight()), text -> {
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
                    this.getData().get(vp_position).get(position).setWeight(weight);
                    this.adapterMap.get(vp_position).notifyItemChanged(position);
                    calculatePercent(true, vp_position);
                }))
                .show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void delOption(int position, int vp_position) {
        String option = getData().get(vp_position).get(position).getOption();
        DialogHelper.showConfirmDialog(this.getContext(),
                getContext().getString(R.string.confirm_deletion),
                getContext().getString(R.string.message_delete_confirm, option),
                () -> {
                    getData().get(vp_position).remove(position);
                    calculatePercent(false, vp_position);
                    this.adapterMap.get(vp_position).notifyDataSetChanged();
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void addOption(String option, int vp_position) {
        Option newOption = new Option(option, getColor(vp_position), 1);

        addOptionToList(newOption, getData().get(vp_position));

        calculatePercent(false, vp_position);
        this.adapterMap.get(vp_position).notifyDataSetChanged();
    }

    public void addOptionToList(Option option, List<Option> list) {
        if (list.size() >= 2) {
            // 插入到末尾两个特殊项（itemType 1/2）之前
            list.add(list.size() - 2, option);
        } else {
            list.add(option);
        }
    }

    public int getColor(int vp_position) {
        Set<Integer> usedColors = new HashSet<>();
        for (Option option : getData().get(vp_position)) {
            usedColors.add(option.getColor());
        }
        for (Integer color : MyApplication.template.getColors()) {
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        return ContextCompat.getColor(this.getContext(), R.color.ios_blue);
    }

    /**
     * 最大余数法计算百分比，确保总和正好为 100
     */
    @SuppressLint("NotifyDataSetChanged")
    public void calculatePercent(boolean notify, int vp_position) {
        if (this.adapterMap.get(vp_position) == null) return;
        List<Option> list = this.adapterMap.get(vp_position).getData();
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
                    if (notify) this.adapterMap.get(vp_position).notifyItemChanged(i);
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
            this.adapterMap.get(vp_position).notifyDataSetChanged();
        }
    }

    private void batchAdd(int vp_position) {
        BatchAddActivity.start(activity, vp_position, CreateEditActivity.REQUEST_COE_BATCH_ADD);
    }

    public boolean isLand() {
        return (MyApplication.app.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    public boolean isTv() {
        return (MyApplication.app.getResources().getBoolean(R.bool.is_tv));
    }

}
