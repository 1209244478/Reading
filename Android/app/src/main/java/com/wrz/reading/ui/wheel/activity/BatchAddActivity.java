package com.wrz.reading.ui.wheel.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseActivity;

public class BatchAddActivity extends BaseActivity {

    public static void start(Activity context, int request) {
        Intent intent = new Intent(context, BatchAddActivity.class);
        context.startActivityForResult(intent, request);
    }

    public static String RESULT_BATCH_ADD = "result_batch_add";

    private TextView tv_title;
    private EditText et_input;
    private TextView tv_save;

    @Override
    public void getIntentData() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_batch_add;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        tv_title = findViewById(R.id.tv_title);
        tv_save = findViewById(R.id.tv_save);
        tv_save.setVisibility(View.VISIBLE);
        et_input = findViewById(R.id.et_input);

        tv_save.setOnClickListener(v -> {
            if (et_input.getText() != null && !et_input.getText().toString().isEmpty()) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(RESULT_BATCH_ADD, et_input.getText().toString());
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                Toast.makeText(this, R.string.input_empty, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void initData() {

    }

    @Override
    public void configView() {
        tv_title.setText(R.string.add_batch_option);
    }

    @Override
    public void goBack() {
        finish();
    }
}