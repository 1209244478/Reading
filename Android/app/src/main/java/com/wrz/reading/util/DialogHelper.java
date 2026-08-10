package com.wrz.reading.util;

import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wrz.reading.R;
import com.wrz.reading.model.Comic;
import com.wrz.reading.ui.read.adapter.RenameAdapter;
import com.wrz.reading.view.dialog.TextInputDialog;

import java.util.List;

/**
 * 对话框统一工具类。
 * <p>聚合项目中重复出现的 iOS 风格对话框：删除确认、单文本输入（创建/重命名）、页码跳转。
 * 各调用点改为静态方法调用，避免在 Activity/Fragment 中散落相同的布局 inflate 与监听注册代码。
 */
public final class DialogHelper {

    private DialogHelper() {
    }

    /**
     * 单文本输入确认回调：返回 trim 后的文本
     */
    public interface TextInputCallback {
        void onConfirm(String text);
    }

    /**
     * 单文本输入确认回调：返回 trim 后的文本
     */
    public interface ListInputCallback {
        void onConfirm(List<Comic> list);
    }

    /**
     * 页码跳转回调：返回目标页（0-based）
     */
    public interface PageJumpCallback {
        void onJumpTo(int page);
    }

    /**
     * 通用的删除确认对话框（2 按钮：确认 / 取消）。
     *
     * @param activity  宿主 Activity
     * @param title     对话框标题
     * @param message   对话框正文
     * @param onConfirm 点击确认后回调（在主线程）
     */
    public static void showDeleteConfirm(Activity activity, String title, String message,
                                         final Runnable onConfirm) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * 单文本输入对话框，复用 dialog_create_collection 布局（iOS 风格圆角卡片）。
     * 适用于创建集合、重命名集合、重命名漫画等场景。
     *
     * @param activity    宿主 Activity
     * @param title       标题
     * @param subtitle    副标题
     * @param confirmText 确认按钮文案（如 "创建" / "保存"）
     * @param initialText 输入框初始内容（可为空）
     * @param callback    确认回调（已校验非空）
     */
    public static void showTextInputDialog(Activity activity, String title, String subtitle,
                                           String confirmText, String initialText,
                                           final TextInputCallback callback) {

        new TextInputDialog(activity, title, subtitle, confirmText, initialText, callback).show();

        /*if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_create_collection, null);
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_subtitle);
        MaterialButton confirmBtn = dialogView.findViewById(R.id.btn_confirm);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.collection_name_layout);
        TextInputEditText nameEdit = dialogView.findViewById(R.id.et_collection_name);

        if (title != null) titleView.setText(title);
        if (subtitle != null) subtitleView.setText(subtitle);
        if (confirmText != null) confirmBtn.setText(confirmText);
        if (initialText != null && !initialText.isEmpty()) {
            nameEdit.setText(initialText);
            nameEdit.setSelection(initialText.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            String text = nameEdit.getText() != null ? nameEdit.getText().toString().trim() : "";
            if (text.isEmpty()) {
                nameLayout.setError(activity.getString(R.string.name_should_not_empty));
                return;
            }
            if (callback != null) callback.onConfirm(text);
            dialog.dismiss();
        });

        dialog.show();*/
    }

    /**
     * 多文本输入对话框，复用 dialog_list_input 布局（iOS 风格圆角卡片）。
     * 适用于重命名漫画场景。
     *
     * @param activity    宿主 Activity
     * @param title       标题
     * @param subtitle    副标题
     * @param confirmText 确认按钮文案（如 "创建" / "保存"）
     * @param Comics      漫画列表（可为空）
         * @param callback    确认回调（已校验非空）
     */
    public static void showListInputDialog(Activity activity, String title, String subtitle,
                                           String confirmText, List<Comic> Comics,
                                           final ListInputCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_list_input, null);
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_subtitle);
        MaterialButton confirmBtn = dialogView.findViewById(R.id.btn_confirm);
        RecyclerView recyclerView = dialogView.findViewById(R.id.collection_recycler_view);

        RenameAdapter renameAdapter = new RenameAdapter(Comics);

        recyclerView.setAdapter(renameAdapter);
        LinearLayoutManager manager = new LinearLayoutManager(activity);
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(manager);

        if (title != null) titleView.setText(title);
        if (subtitle != null) subtitleView.setText(subtitle);
        if (confirmText != null) confirmBtn.setText(confirmText);

        // 改用普通 Dialog（非 AlertDialog）：AlertDialog 的 window 配置存在 IME 抑制缺陷，
        // dialog 内 EditText 永远 "is not served"，IME 弹到 Activity 的 served view 上。
        // 普通 Dialog 的 window 能正确成为 IME target。
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(dialogView);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        // 必须在 show() 之前设置，否则 IMM 已记录默认 hidden 状态，后续 showSoftInput 被抑制。
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            if (callback != null) callback.onConfirm(renameAdapter.getData());
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 页码跳转对话框（SeekBar 选择）。
     *
     * @param activity 宿主 Activity
     * @param current  当前页（0-based）
     * @param total    总页数
     * @param callback 跳转回调
     */
    public static void showPageJumpDialog(Activity activity, int current, int total,
                                          final PageJumpCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (total <= 0) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_page_jump, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.jump_to_page));
        builder.setView(dialogView);

        SeekBar pageSeekBar = dialogView.findViewById(R.id.page_seek_bar);
        TextView currentPageText = dialogView.findViewById(R.id.current_page_text);
        TextView totalPageText = dialogView.findViewById(R.id.total_page_text);

        totalPageText.setText(String.valueOf(total));
        pageSeekBar.setMax(total - 1);
        pageSeekBar.setProgress(current);
        currentPageText.setText(String.valueOf(current + 1));

        pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentPageText.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        builder.setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
            if (callback != null) callback.onJumpTo(pageSeekBar.getProgress());
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }
}
