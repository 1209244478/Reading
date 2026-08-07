package com.wrz.reading.ui.wheel.adapter;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.google.android.material.textfield.TextInputEditText;
import com.wrz.reading.R;
import com.wrz.reading.model.Option;

public class OptionAdapter extends BaseMultiItemQuickAdapter<Option, BaseViewHolder> {

    ChangeListener listener;

    public OptionAdapter(ChangeListener listener) {
        addItemType(0, R.layout.item_option);
        addItemType(1, R.layout.item_option_add);
        addItemType(2, R.layout.item_batch_add);

        this.listener = listener;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Option option) {
        if (option.getItemType() == 0) {
            holder.setText(R.id.et_option, option.getOption());
            holder.setText(R.id.tv_weight, option.getWeight() + " | " + option.getPercent() + "%");

            CardView colorView = holder.getView(R.id.color_view);
            try {
                colorView.setCardBackgroundColor(option.getColor());
                holder.setGone(R.id.color_view, false);
            } catch (Exception e) {
            }

            TextInputEditText et_option = holder.getView(R.id.et_option);

            // 移除旧的 TextWatcher，避免 ViewHolder 复用时累积导致重复回调
            Object oldWatcher = et_option.getTag(R.id.et_option);
            if (oldWatcher instanceof TextWatcher) {
                et_option.removeTextChangedListener((TextWatcher) oldWatcher);
            }

            et_option.setOnFocusChangeListener((v, hasFocus) -> {
                /*if (listener != null && et_option.getText() != null) {
                    listener.onFocusChange(et_option.getText().toString().trim(), holder.getLayoutPosition(), hasFocus);
                }*/
            });

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {

                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String newString = String.valueOf(s);
                    if (!newString.isEmpty()) {
                        if (listener != null && et_option.getText() != null) {
                            listener.onChange(et_option.getText().toString().trim(), holder.getLayoutPosition());
                        }
                    }
                }
            };
            et_option.addTextChangedListener(textWatcher);
            et_option.setTag(R.id.et_option, textWatcher);
        } else {

        }
    }

    public interface ChangeListener {
        void onChange(String newContent, int position);

        void onFocusChange(String newContent, int position, boolean hasFocus);
    }

}
