package com.wrz.reading.ui.random.fragment;

import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.lxj.xpopup.XPopup;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.random.adapter.RandomAdapter;
import com.wrz.reading.view.popupView.RandomSettingPopup;

import java.util.ArrayList;
import java.util.Random;

public class RandomFragment extends BaseFragment {
    ArrayList<String> list = new ArrayList<>();
    RecyclerView rv_list;
    MaterialButton btn_generate;
    MaterialButton btn_clear;
    TextView tv_start;
    TextView tv_end;
    int start;
    int end;
    int quantity;
    private RandomAdapter adapter;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_random;
    }

    @Override
    public void initView() {
        splashOnResume = true;
        rv_list = parentView.findViewById(R.id.rv_list);
        rv_list.setLayoutManager(new LinearLayoutManager(parentView.getContext()));
        adapter = new RandomAdapter(list);

        rv_list.setAdapter(adapter);

        btn_generate = parentView.findViewById(R.id.btn_generate);
        btn_clear = parentView.findViewById(R.id.btn_clear);

        ImageButton btn_menu = parentView.findViewById(R.id.btn_menu);

        btn_menu.setOnClickListener(v -> {
            RandomSettingPopup popup = new RandomSettingPopup(parentView.getContext());
            popup.setOnSaveListener(() -> {
                initData();
                configViews();
            });
            new XPopup.Builder(parentView.getContext())
                    .hasShadowBg(false)
                    .isViewMode(true)
                    .autoOpenSoftInput(false)
                    .isDestroyOnDismiss(false)
                    .asCustom(popup)
                    .show();
        });

        tv_start = parentView.findViewById(R.id.tv_start);
        tv_end = parentView.findViewById(R.id.tv_end);

        btn_generate.setOnClickListener(v -> generateUnique());

        btn_clear.setOnClickListener(v -> {
            list.clear();
            adapter.notifyDataSetChanged();
        });
    }



    private void generateUnique() {
        Random random = new Random();

        // 如果需要的数量大于范围，调整逻辑
        for (int i = 0; i < quantity; i++) {
            int randomNumber = generateRandomNumber(random);
            if (randomNumber == -1) {
                Toast.makeText(parentView.getContext(), "已达最大数", Toast.LENGTH_SHORT).show();
                break;
            }
            list.add(String.valueOf(randomNumber));
        }

        adapter.notifyDataSetChanged();
        rv_list.post(() -> rv_list.scrollToPosition(adapter.getItemCount() - 1));
    }

    private int generateRandomNumber(Random random) {
        int max = Math.max(start, end);
        int min = Math.min(start, end);

        int range = (max - min + 1);

        int num = random.nextInt(range) + min;
        if (!MyApplication.manager.getRandomAllowDuplicates()) {
            if (list.size() >= range) {
                return -1;
            }
            // 迭代重试，避免递归栈溢出
            int attempts = 0;
            while (list.contains(String.valueOf(num))) {
                num = random.nextInt(range) + min;
                if (++attempts > range * 4) break;
            }
        }
        return num;
    }

    @Override
    public void initData() {
        start = MyApplication.manager.getRandomStart();
        end = MyApplication.manager.getRandomEnd();
        quantity = MyApplication.manager.getRandomQuantity();
    }

    @Override
    public void configViews() {
        tv_start.setText(String.valueOf(start));
        tv_end.setText(String.valueOf(end));

    }

}
