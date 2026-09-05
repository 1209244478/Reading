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
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.CollectionItem;
import com.wrz.reading.ui.read.model.Comic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RenameComicAdapter extends BaseQuickAdapter<Comic, BaseViewHolder> {

    private final List<Comic> edited = new ArrayList<>();

    public List<Comic> getEdited() {
        return edited;
    }

    public RenameComicAdapter(@Nullable List<Comic> data) {
        super(R.layout.item_list_input, Comic.deepCopy(data));
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Comic comic) {
        holder.setText(R.id.et_collection_name, comic.getTitle());

        TextInputEditText nameEdit = holder.getView(R.id.et_collection_name);
        TextInputLayout nameLayout = holder.getView(R.id.collection_name_layout);

        nameEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                requestIme(v);
            }
        });
        nameEdit.setOnClickListener(RenameComicAdapter::requestIme);

        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                // 从现有列表中移除同一 Collection 的旧记录
                edited.removeIf(e -> e.getId() == comic.getId());

                String newName = nameEdit.getText() != null ? nameEdit.getText().toString() : "";
                if (!newName.isEmpty() && !Objects.equals(newName, comic.getTitle())) {
                    Comic c = Comic.deepCopy(comic);
                    c.setTitle(Objects.requireNonNull(nameEdit.getText()).toString());
                    edited.add(c);
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
                } else {
                    nameLayout.setErrorEnabled(false);
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
