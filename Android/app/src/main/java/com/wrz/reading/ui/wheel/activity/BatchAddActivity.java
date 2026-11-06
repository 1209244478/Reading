package com.wrz.reading.ui.wheel.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseActivity;

public class BatchAddActivity extends BaseActivity {

    public static String REQUEST_VP_POSITION = "request_vp_position";

    public static void start(Activity context, int vp_position, int request) {
        Intent intent = new Intent(context, BatchAddActivity.class);
        intent.putExtra(REQUEST_VP_POSITION, vp_position);
        context.startActivityForResult(intent, request);
    }

    public static void start(Context context, int request) {
        Intent intent = new Intent(context, BatchAddActivity.class);
        context.startActivity(intent);
    }

    public static String RESULT_BATCH_ADD = "result_batch_add";
    public static String RESULT_VP_POSITION = "result_vp_position";

    private EditText et_input;

    private int vp_position;

    @Override
    public void getIntentData() {
        vp_position = getIntent().getIntExtra(REQUEST_VP_POSITION, -1);
        if (vp_position == -1) {
            finish();
        }
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
        visible(R.id.tv_save);
        et_input = findViewById(R.id.et_input);
        et_input.setFocusable(true);

        setClick(R.id.tv_save, v -> {
            if (et_input.getText() != null && !et_input.getText().toString().isEmpty()) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(RESULT_BATCH_ADD, et_input.getText().toString());
                resultIntent.putExtra(RESULT_VP_POSITION, vp_position);
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
        setText(R.string.add_batch_option, R.id.tv_title);
    }


}