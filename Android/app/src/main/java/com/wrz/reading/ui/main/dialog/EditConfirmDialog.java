package com.wrz.reading.ui.main.dialog;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;

public class EditConfirmDialog extends BaseDialog {

    String title;
    String subTitle;
    String content;
    String hint;

    public EditConfirmDialog(@NonNull Context context, String title, String subTitle, String content, String hint) {
        super(context);

        this.title = title;
        this.subTitle = subTitle;
        this.content = content;
        this.hint = hint;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_edit_text_confirm;
    }

    @Override
    public void initView() {
        TextView tv_title = findId(R.id.tv_title);
        setText(tv_title, title);
        TextView tv_sub_title = findId(R.id.tv_sub_title);
        setText(tv_sub_title, subTitle);


        EditText et_input = findId(R.id.et_input);
        et_input.setHint(hint);
        et_input.setText(content);

        TextView tv_cancel = findId(R.id.tv_cancel);
        TextView tv_confirm = findId(R.id.tv_confirm);

        tv_cancel.setOnClickListener(view -> {
            if (cancelListener != null) {
                cancelListener.onCancel();
            } else {
                dismiss();
            }
        });

        tv_confirm.setOnClickListener(v -> {
            if (confirmListener != null) {
                confirmListener.onConfirm(et_input.getText().toString());
                dismiss();
            } else {
                dismiss();
            }
        });
    }

    onCancelListener cancelListener;

    public void setOnCancelListener(@Nullable onCancelListener listener) {
        this.cancelListener = listener;
    }

    public interface onCancelListener {
        void onCancel();
    }

    onConfirmListener confirmListener;

    public void setConfirmListener(onConfirmListener listener) {
        this.confirmListener = listener;
    }

    public interface onConfirmListener {
        void onConfirm(String text);
    }

    @Override
    public void setDialogSize() {
        if (this.getWindow() != null) {
            this.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
