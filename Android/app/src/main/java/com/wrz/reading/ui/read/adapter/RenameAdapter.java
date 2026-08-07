package com.wrz.reading.ui.read.adapter;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wrz.reading.R;
import com.wrz.reading.model.Comic;

import java.util.List;
import java.util.Objects;

public class RenameAdapter extends BaseQuickAdapter<Comic, BaseViewHolder> {
    public RenameAdapter(@Nullable List<Comic> data) {
        super(R.layout.item_list_input, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Comic comic) {
        holder.setText(R.id.collection_name_edit, comic.getTitle());

        TextInputEditText nameEdit = holder.getView(R.id.collection_name_edit);
        TextInputLayout nameLayout = holder.getView(R.id.collection_name_layout);

        nameEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                requestIme(v);
            }
        });
        nameEdit.setOnClickListener(RenameAdapter::requestIme);

        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (nameEdit.getText() != null && !nameEdit.getText().toString().isEmpty()) {
                    comic.setTitle(Objects.requireNonNull(nameEdit.getText()).toString());
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String text = charSequence.toString();
                if (text.isEmpty()) {
                    nameLayout.setError(holder.itemView.getContext().getString(R.string.name_should_not_empty));
                }
            }
        });

    }

    private static void requestIme(View view) {
        view.post(() -> {
            view.requestFocus();
            InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
            }
        });
    }
}
