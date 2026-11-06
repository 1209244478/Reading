package com.wrz.reading.ui.wheel.fragment;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.wheel.activity.CreateEditActivity;
import com.wrz.reading.ui.wheel.activity.WheelListActivity;
import com.wrz.reading.ui.wheel.model.Request;
import com.wrz.reading.ui.wheel.model.Wheel;
import com.wrz.reading.ui.wheel.view.wheelview.WheelView;

public class WheelFragment extends BaseFragment {
    WheelView wheelView;
    MaterialButton startBtn;
    MaterialButton editBtn;
    ImageButton btn_menu;
    TextView resultText;
    TextView titleText;

    CardView mainCard;
    private Wheel wheel;
    private boolean isSpinning = false;

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
        resultText = findId(R.id.tv_result);
        titleText = findId(R.id.tv_title);

        mainCard = findId(R.id.mainCard);
        btn_menu = findId(R.id.btn_menu);

        setClick(titleText, v -> showExplanation());
    }

    BaseDialog explanationDialog;

    private void showExplanation() {
        if (!TextUtils.isEmpty(wheel.getExplanation())) {

            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

            String title = getString(R.string.wheel_explanation);
            String message = wheel.getExplanation();


            if (explanationDialog == null) {
                explanationDialog = new BaseDialog(activity) {
                    @Override
                    public int getLayoutId() {return R.layout.dialog_comfirm;}

                    @Override
                    public void initView() {
                        this.setText(R.id.dialog_title, title);
                        this.setText(R.id.dialog_subtitle, message);
                        this.setText(R.id.btn_confirm, getContext().getString(R.string.btn_confirm));

                        this.setClick(R.id.btn_cancel, v -> this.dismiss());

                        this.setClick(R.id.btn_confirm, v -> {this.dismiss();});
                    }
                };
            }

            explanationDialog.show();

            explanationDialog.setText(R.id.dialog_title, title);
        }
    }

    @Override
    public void initData() {
        singleThread.execute(() -> {
            Wheel w = MyApplication.wheelRepository.getLastWheel();
            if (w == null) return;
            // 复用 BaseFragment 的 activity 字段与 runOnUiIfAlive，
            // 内部已做 isAdded() 双重校验，避免回调执行时 Fragment 已 detach
            if (!Wheel.isSame(w, wheel)) {
                runOnUiIfAlive(() -> {
                    wheel = w;
                    configViews();
                });
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void configViews() {
        setUpWheelView();

        setUpButtonClick();

        if (wheel != null) {
            wheelView.setItems(wheel);

            setSubTitle();

            if (wheel.getEmoji() != null && !wheel.getEmoji().isBlank()) {
                titleText.setText(wheel.getEmoji() + " " + wheel.getTitle());
            } else {
                titleText.setText(wheel.getTitle());
            }
        }
    }

    private void setSubTitle() {
        setText((wheelView.current + 1) + "/" + wheel.getList().size(), R.id.tv_sub_title);
    }

    private void setUpButtonClick() {
        setClick(R.id.startBtn, v -> startSpinning());
        setClick(R.id.editBtn, v -> showEditPage());
        setClick(R.id.mainCard, v -> {
            if (isSpinning) return;
            resetWheel();
        });

        setClick(R.id.btn_menu, v -> showList());
        setClick(R.id.pointer, v -> startSpinning());

        setClick(R.id.nextBtn, v -> {
            wheelView.setNext();
            setSubTitle();
        });
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
                startBtn.setEnabled(true);
                btn_menu.setEnabled(true);

                Toast.makeText(parentView.getContext(), R.string.notification_all_selected, Toast.LENGTH_SHORT).show();

                resultText.setText(getString(R.string.notification_all_selected));
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
        if (wheelView.isListEmpty()) {
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

        Toast.makeText(parentView.getContext(), getString(R.string.msg_wheel_choice, option), Toast.LENGTH_SHORT).show();
    }

    private void resetWheel() {
        isSpinning = false;
        startBtn.setEnabled(true);
        wheelView.reset();
        resultText.setText(null);
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

        singleThread.execute(() -> {
            MyApplication.wheelRepository.updateWheel(wheel);
        });
    }

}
