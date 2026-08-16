package com.wrz.reading.ui.wheel.view.dialog;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.ui.wheel.adapter.ColorGridAdapter;

import java.util.ArrayList;
import java.util.List;

public class ColorPickerDialog extends BaseDialog {

    ArrayList<Integer> colors = new ArrayList<>();
    int selectedColor;

    public ColorPickerDialog(@NonNull Context context, List<Integer> colors, int selectedColor) {
        super(context);

        this.colors.addAll(colors);
        this.selectedColor = selectedColor;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_color_picker;
    }

    @Override
    public void initView() {
        View previewBlock = parentView.findViewById(R.id.color_preview);
        TextView hexText = parentView.findViewById(R.id.color_hex);
        RecyclerView colorGrid = parentView.findViewById(R.id.color_grid);


        GradientDrawable previewDrawable = new GradientDrawable();
        previewDrawable.setCornerRadius(8f);
        previewDrawable.setColor(selectedColor);
        previewBlock.setBackground(previewDrawable);
        hexText.setText(String.format("#%06X", 0xFFFFFF & selectedColor));

        colorGrid.setLayoutManager(new GridLayoutManager(parentView.getContext(), 6));
        ColorGridAdapter adapter = new ColorGridAdapter(colors, selectedColor);
        colorGrid.setAdapter(adapter);


        this.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        adapter.setOnColorClickListener(color -> {
            selectedColor = color;
            previewDrawable.setColor(color);
            hexText.setText(String.format("#%06X", 0xFFFFFF & color));
            adapter.setSelectedColor(color);
        });

        parentView.findViewById(R.id.btn_cancel).setOnClickListener(v -> this.dismiss());
        parentView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            onClickListener.onClicked(selectedColor);
            this.dismiss();
        });
    }

    onClickListener onClickListener;

    public interface onClickListener {
        void onClicked(int color);
    }

    public void setOnClickListener(onClickListener onClickListener) {
        this.onClickListener = onClickListener;
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
