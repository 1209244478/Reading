package com.wrz.reading.view.popupView;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.lxj.xpopup.core.BottomPopupView;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.util.PrefManager;

public class RandomSettingPopup extends BottomPopupView {
    TextInputEditText tv_start;
    TextInputEditText tv_end;
    TextView tv_cancel;
    TextView tv_save;
    TextView tv_quantity;

    private Switch switchAllowDuplicates;

    /** 保存成功后的回调，用于通知调用方刷新数据 */
    public interface OnSaveListener {
        void onSaved();
    }

    private OnSaveListener onSaveListener;

    public void setOnSaveListener(OnSaveListener listener) {
        this.onSaveListener = listener;
    }

    public RandomSettingPopup(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.popup_custom_bottom;
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        tv_start = findViewById(R.id.tv_start);
        tv_end = findViewById(R.id.tv_end);
        tv_quantity = findViewById(R.id.tv_quantity);
        tv_cancel = findViewById(R.id.tv_cancel);
        tv_save = findViewById(R.id.tv_save);

        switchAllowDuplicates = findViewById(R.id.switch_allow_duplicates);

        tv_start.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String str_start = String.valueOf(s);
                if (str_start.isBlank()) {
                    tv_start.setHint("0");
                }
            }
        });

        tv_end.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String str_end = String.valueOf(s);
                if (str_end.isBlank()) {
                    tv_end.setHint("10");
                }
            }
        });
        tv_quantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String str_quantity = String.valueOf(s);
                if (str_quantity.isBlank()) {
                    tv_quantity.setHint("1");
                }
            }
        });

        tv_cancel.setOnClickListener(v -> this.dismiss());

        tv_save.setOnClickListener(v -> {
            if (MyApplication.manager != null) {
                if (tv_start.getText() != null) {
                    MyApplication.manager.saveRandomStart(Integer.parseInt(tv_start.getText().toString()));
                }
                if (tv_end.getText() != null) {
                    MyApplication.manager.saveRandomEnd(Integer.parseInt(tv_end.getText().toString()));
                }
                if (tv_quantity.getText() != null) {
                    MyApplication.manager.saveRandomQuantity(Integer.parseInt(tv_quantity.getText().toString()));
                }
                MyApplication.manager.saveRandomAllowDuplicates(isChecked);
                if (onSaveListener != null) {
                    onSaveListener.onSaved();
                }
                this.dismiss();
            }
        });

        switchAllowDuplicates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.isChecked = isChecked;
        });

        tv_start.setText(String.valueOf(MyApplication.manager.getRandomStart()));
        tv_end.setText(String.valueOf(MyApplication.manager.getRandomEnd()));
        tv_quantity.setText(String.valueOf(MyApplication.manager.getRandomQuantity()));

        isChecked = MyApplication.manager.getRandomAllowDuplicates();

        switchAllowDuplicates.setChecked(isChecked);
    }

    boolean isChecked;

    @Override
    protected void onShow() {
        super.onShow();

    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

}