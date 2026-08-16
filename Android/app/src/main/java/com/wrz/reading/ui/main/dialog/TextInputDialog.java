package com.wrz.reading.ui.main.dialog;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.ui.main.utils.DialogHelper;

public class TextInputDialog extends BaseDialog {

    String title;
    String subtitle;
    String confirmText;
    String initialText;
    DialogHelper.TextInputCallback callback;

    public TextInputDialog(@NonNull Context context, String title, String subtitle,
                           String confirmText, String initialText,
                           final DialogHelper.TextInputCallback callback) {
        super(context);

        this.title = title;
        this.subtitle = subtitle;
        this.confirmText = confirmText;
        this.initialText = initialText;
        this.callback = callback;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_create_collection;
    }

    @Override
    public void initView() {
        TextView dialog_title = parentView.findViewById(R.id.dialog_title);
        TextView dialog_subtitle = parentView.findViewById(R.id.dialog_subtitle);

        MaterialButton btn_confirm = parentView.findViewById(R.id.btn_confirm);
        parentView.findViewById(R.id.btn_cancel).setOnClickListener(v -> this.dismiss());

        TextInputLayout collection_name_layout = parentView.findViewById(R.id.collection_name_layout);
        TextInputEditText et_collection_name = parentView.findViewById(R.id.et_collection_name);


        if (title != null) dialog_title.setText(title);
        if (subtitle != null) dialog_subtitle.setText(subtitle);
        if (confirmText != null) btn_confirm.setText(confirmText);
        if (initialText != null && !initialText.isEmpty()) {
            et_collection_name.setText(initialText);
            et_collection_name.setSelection(initialText.length());
        }

        btn_confirm.setOnClickListener(v -> {
            String text = et_collection_name.getText() != null ? et_collection_name.getText().toString().trim() : "";
            if (text.isEmpty()) {
                collection_name_layout.setError(parentView.getContext().getString(R.string.name_should_not_empty));
                return;
            }
            if (callback != null) callback.onConfirm(text);
            this.dismiss();
        });
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
