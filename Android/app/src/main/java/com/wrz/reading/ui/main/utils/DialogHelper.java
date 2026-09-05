package com.wrz.reading.ui.main.utils;

import android.app.Activity;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.ui.main.dialog.TextInputDialog;
import com.wrz.reading.ui.read.adapter.RenameCollectionItemAdapter;
import com.wrz.reading.ui.read.adapter.RenameComicAdapter;
import com.wrz.reading.ui.read.model.CollectionItem;
import com.wrz.reading.ui.read.model.Comic;

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
     * 通用的删除确认对话框（2 按钮：确认 / 取消）。
     *
     * @param activity  宿主 Activity
     * @param title     对话框标题
     * @param message   对话框正文
     * @param onConfirm 点击确认后回调（在主线程）
     */
    public static void showConfirmDialog(Activity activity, String title, String message,
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
     * 单文本输入确认回调：返回 trim 后的文本
     */
    public interface TextInputCallback {
        void onConfirm(String text);
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
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        new TextInputDialog(activity, title, subtitle, confirmText, initialText, callback).show();

    }

    public interface RenameComicCallback {
        void onConfirm(List<Comic> list);
    }

    /**
     * 多文本输入对话框，复用 dialog_list_input 布局（iOS 风格圆角卡片）。
     * 适用于重命名漫画场景。
     *
     * @param activity    宿主 Activity
     * @param title       标题
     * @param subtitle    副标题
     * @param confirmText 确认按钮文案（如 "创建" / "保存"）
     * @param comics      漫画列表（可为空）
         * @param callback    确认回调（已校验非空）
     */
    public static void showRenameComicsDialog(Activity activity, String title, String subtitle,
                                              String confirmText, List<Comic> comics,
                                              final RenameComicCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        new BaseDialog(activity) {
            @Override
            public int getLayoutId() {
                return R.layout.dialog_list_input;
            }

            @Override
            public void initView() {
                setText(R.id.dialog_title, title);
                setText(R.id.dialog_subtitle, subtitle);
                setText(R.id.btn_confirm, confirmText);

                RecyclerView recyclerView = findId(R.id.collection_recycler_view);

                RenameComicAdapter adapter = new RenameComicAdapter(comics);

                recyclerView.setAdapter(adapter);
                LinearLayoutManager manager = new LinearLayoutManager(getContext());
                manager.setOrientation(LinearLayoutManager.VERTICAL);
                recyclerView.setLayoutManager(manager);

                findId(R.id.btn_cancel).setOnClickListener(v -> this.dismiss());
                findId(R.id.btn_confirm).setOnClickListener(v -> {
                    if (callback != null) callback.onConfirm(adapter.getEdited());
                    this.dismiss();
                });
            }

            @Override
            public void setDialogSize() {

            }

        }.show();

    }

    /**
     * 单文本输入确认回调：返回 trim 后的文本
     */
    public interface RenameCollectionCallback {
        void onConfirm(List<CollectionItem> list);
    }

    /**
     * 修改重命名合集，复用 dialog_list_input 布局（iOS 风格圆角卡片）。
     * 适用于重命名合集场景。
     *
     * @param activity    宿主 Activity
     * @param title       标题
     * @param subtitle    副标题
     * @param confirmText 确认按钮文案（如 "创建" / "保存"）
     * @param collections      合集列表（可为空）
         * @param callback    确认回调（已校验非空）
     */
    public static void showRenameCollectionsDialog(Activity activity, String title, String subtitle,
                                           String confirmText, List<CollectionItem> collections,
                                           final RenameCollectionCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        /*RenameCollectionsDialog dialog = new RenameCollectionsDialog(activity, collections);

        dialog.show();

        dialog.setCallback(callback);

        dialog.setText(R.id.dialog_title, title);
        dialog.setText(R.id.dialog_subtitle, subtitle);
        dialog.setText(R.id.btn_confirm, confirmText);*/

        new BaseDialog(activity) {

            @Override
            public int getLayoutId() {
                return R.layout.dialog_list_input;
            }

            @Override
            public void initView() {
                setText(R.id.dialog_title, title);
                setText(R.id.dialog_subtitle, subtitle);
                setText(R.id.btn_confirm, confirmText);

                RecyclerView recyclerView = findId(R.id.collection_recycler_view);

                RenameCollectionItemAdapter renameCollectionItemAdapter = new RenameCollectionItemAdapter(collections);

                recyclerView.setAdapter(renameCollectionItemAdapter);
                LinearLayoutManager manager = new LinearLayoutManager(getContext());
                manager.setOrientation(LinearLayoutManager.VERTICAL);
                recyclerView.setLayoutManager(manager);

                findId(R.id.btn_cancel).setOnClickListener(v -> this.dismiss());
                findId(R.id.btn_confirm).setOnClickListener(v -> {
                    if (callback != null) callback.onConfirm(renameCollectionItemAdapter.getEdited());
                    this.dismiss();
                });
            }

            @Override
            public void setDialogSize() {

            }
        }.show();
    }

    /**
     * 页码跳转回调：返回目标页（0-based）
     */
    public interface PageJumpCallback {
        void onJumpTo(int page);
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

        BaseDialog dialog = new BaseDialog(activity) {
            @Override
            public int getLayoutId() {
                return R.layout.dialog_page_jump;
            }

            @Override
            public void initView() {
                setText(R.id.title, activity.getString(R.string.jump_to_page));
                setText(R.id.current_page_text, String.valueOf(current + 1));
                setText(R.id.total_page_text, String.valueOf(total));

                SeekBar pageSeekBar = findId(R.id.page_seek_bar);
                pageSeekBar.setMax(total - 1);
                pageSeekBar.setProgress(current);

                pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        setText(R.id.current_page_text, String.valueOf(progress + 1));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                findId(R.id.btn_cancel).setOnClickListener(v -> dismiss());
                findId(R.id.btn_confirm).setOnClickListener(v -> {
                    if (callback != null) callback.onJumpTo(pageSeekBar.getProgress());
                    dismiss();
                });
            }

            @Override
            public void setDialogSize() {

            }
        };

        dialog.show();
    }
}
