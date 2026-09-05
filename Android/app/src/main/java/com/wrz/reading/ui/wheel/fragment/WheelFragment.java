package com.wrz.reading.ui.wheel.fragment;

import android.annotation.SuppressLint;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Wheel;
import com.wrz.reading.ui.wheel.activity.CreateEditActivity;
import com.wrz.reading.ui.wheel.activity.WheelListActivity;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.wheel.view.wheelview.WheelView;

import java.util.List;

public class WheelFragment extends BaseFragment {
    WheelView wheelView;
    MaterialButton startBtn;
    MaterialButton editBtn;
    ImageButton btn_menu;
    TextView resultText;
    TextView titleText;
    CardView resultCard;
    CardView mainCard;
    FrameLayout wheelContainer;
    private List<Option> list;
    private Wheel wheel;
    private boolean isSpinning = false;
    private ImageView pointer;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_wheel;
    }

    @Override
    public void initView() {
        splashOnResume = true;
        wheelView = findId(R.id.wheel);
        startBtn = findId(R.id.startBtn);
        editBtn = findId(R.id.editBtn);
        resultText = findId(R.id.resultText);
        titleText = findId(R.id.titleText);
        resultCard = findId(R.id.resultCard);


        mainCard = findId(R.id.mainCard);
        wheelContainer = findId(R.id.wheelContainer);
        btn_menu = findId(R.id.btn_menu);
        pointer = findId(R.id.pointer);
    }

    @Override
    public void initData() {
        singleThread.execute(() -> {
            Wheel w = MyApplication.wheelRepository.getLastWheel();
            if (w == null) return;
            // 复用 BaseFragment 的 activity 字段与 runOnUiIfAlive，
            // 内部已做 isAdded() 双重校验，避免回调执行时 Fragment 已 detach
            runOnUiIfAlive(() -> {
                wheel = w;
                list = wheel.getList();
                configViews();
            });
        });
    }

    @Override
    public void configViews() {
        setUpWheelView();

        setUpButtonClick();

        if (wheel != null) {
            wheelView.setItems(wheel);

            if (wheel.getEmoji() != null && !wheel.getEmoji().isBlank()) {
                titleText.setText(wheel.getEmoji() + " " + wheel.getTitle());
            } else {
                titleText.setText(wheel.getTitle());
            }
        }
    }

    private void setUpButtonClick() {
        startBtn.setOnClickListener(v -> startSpinning());
        editBtn.setOnClickListener(v -> showEditPage());
        mainCard.setOnClickListener(v -> {
            if (isSpinning) return;
            resetWheel();
        });

        resultCard.setOnClickListener(v -> {
            if (isSpinning) return;
            if (getString(R.string.hint_wheel).equals(resultText.getText().toString())) {
                startSpinning();
            } else {
                resetWheel();
            }
        });

        btn_menu.setOnClickListener(v -> showList());

        pointer.setOnClickListener(v -> startSpinning());
    }

    private void setUpWheelView() {
        wheelView.setOnItemSelectedListener(new WheelView.OnItemListener() {
            @Override
            public void onTempSelected(String item) {
                resultText.setText(item);
            }

            @Override
            public void onItemSelected(String item) {
                showResult(item);
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onAllSelected() {
                isSpinning = false;
                startBtn.setEnabled(false);
                btn_menu.setEnabled(true);

                Toast.makeText(parentView.getContext(), R.string.notification_all_selected, Toast.LENGTH_SHORT).show();

                resultText.setText(getString(R.string.notification_all_selected) + "\n"
                        + getString(R.string.click_to_reset));
            }
        });
    }

    @Override
    public void goBack() {

    }

    /**
     * 开始旋转
     */
    private void startSpinning() {
        if (isSpinning) return;
        if (list.isEmpty()) {
            Toast.makeText(parentView.getContext(), R.string.message_empty_options, Toast.LENGTH_SHORT).show();
            return;
        }

        isSpinning = true;

        startBtn.setEnabled(false);
        editBtn.setEnabled(false);
        btn_menu.setEnabled(false);

        resultText.setText(R.string.hint_spinning);
        resultText.setTextColor(ContextCompat.getColor(parentView.getContext(), R.color.apple_red));

        wheelView.startSpin();
    }

    /**
     * 显示结果
     */
    private void showResult(String option) {
        isSpinning = false;

        startBtn.setEnabled(true);
        editBtn.setEnabled(true);
        btn_menu.setEnabled(true);

        resultText.setText(MyApplication.app.getString(R.string.message_result, option));
        resultText.setTextColor(ContextCompat.getColor(parentView.getContext(), R.color.apple_red));

        android.view.animation.Animation highlightAnimation = AnimationUtils.loadAnimation(parentView.getContext(), R.anim.result_highlight);
        resultCard.startAnimation(highlightAnimation);

        Toast.makeText(parentView.getContext(), "选择了 " + option, Toast.LENGTH_SHORT).show();
    }

    private void resetWheel() {
        isSpinning = false;
        startBtn.setEnabled(true);
        wheelView.reset();
        resultText.setText(R.string.hint_wheel);
        resultText.setTextColor(ContextCompat.getColor(parentView.getContext(), R.color.ios_text_primary));
    }

    private void showEditPage() {
        CreateEditActivity.start(activity, wheel.getId(), -1, Request.Edit_Wheel);
    }

    private void showList() {
        WheelListActivity.start(activity);
    }

    @Override
    public void onPause() {
        super.onPause();
        wheelView.stopSpin();
    }

}
