package com.wrz.reading.ui.read.view.dialog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;

/**
 * 导入方式选择对话框（由集合详情页 FAB 调起）。
 * <p>
 * 本地导入：关闭对话框后沿用原有的文件夹选择流程。
 * 远程导入：切换为二维码面板，展示局域网地址与接收进度；关闭对话框会停止本地服务。
 * 电视端：D-pad 焦点限制在弹窗内的可操作项之间，确认键即点击，选项带自定义焦点高亮。
 */
public class ImportSourceDialog extends BaseDialog {

    public interface Listener {

        /** 用户选择本地导入 */
        void onLocalImport();

        /** 用户选择远程导入，调用方负责启动局域网服务并回调 {@link #showRemoteInfo} */
        void onRemoteImport();

        /** 远程接收结束（点击停止或关闭对话框），调用方负责停止局域网服务 */
        void onStopRemoteImport();

        /** 对话框已关闭 */
        void onDismissed();
    }

    private Listener listener;

    /** 当前是否处于远程导入模式：关闭对话框时需要通知调用方停止服务 */
    private boolean remoteMode;

    /** 焦点请求代际号：面板切换后丢弃旧的延迟聚焦任务 */
    private int focusToken;

    public ImportSourceDialog(@NonNull Context context) {
        super(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_import_source;
    }

    @Override
    public void initView() {
        setText(R.id.import_dialog_title, getContext().getString(R.string.import_dialog_title));
        setText(R.id.import_dialog_subtitle, getContext().getString(R.string.import_dialog_subtitle));

        setClick(R.id.import_remote_option, v -> {
            remoteMode = true;
            showRemoteStarting();
            if (listener != null) listener.onRemoteImport();
        });

        setClick(R.id.import_local_option, v -> {
            if (listener != null) listener.onLocalImport();
            dismiss();
        });

        setClick(R.id.import_btn_stop_remote, v -> {
            if (listener != null) listener.onStopRemoteImport();
            remoteMode = false;
            resetToOptions();
        });

        setClick(R.id.import_btn_cancel, v -> dismiss());
        // 初始为导入方式选择：电视布局的停止按钮在固定底栏，需要显式隐藏
        setVisible(R.id.import_btn_stop_remote, false);

        // 电视遥控器：焦点只在弹窗内的可操作项之间移动
        applyFocusLinks(false);
        // 地址文本可选中有助于手机复制；电视端必须移出焦点链，否则会多出一个不可点击的焦点项
        TextView urlView = findId(R.id.import_remote_url);
        if (urlView != null && isTv()) {
            urlView.setTextIsSelectable(false);
            urlView.setFocusable(false);
            urlView.setFocusableInTouchMode(false);
            urlView.setClickable(false);
            urlView.setLongClickable(false);
        }
    }

    /**
     * 弹窗显示后主动把焦点交给第一个选项，避免遥控器首次按键无响应。
     */
    @Override
    public void onShow() {
        focusAfterLayout(R.id.import_remote_option);
    }

    @Override
    public void setDialogSize() {
        applySize(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onDismiss() {
        if (remoteMode) {
            remoteMode = false;
            if (listener != null) listener.onStopRemoteImport();
        }
        if (listener != null) listener.onDismissed();
    }

    // ==================== 远程导入面板 ====================

    /**
     * 切换到远程导入面板，等待服务启动结果。
     */
    public void showRemoteStarting() {
        remoteMode = true;
        setVisible(R.id.import_options, false);
        setVisible(R.id.import_remote_panel, true);
        setVisible(R.id.import_qr_frame, false);
        setVisible(R.id.import_remote_url, false);
        setVisible(R.id.import_remote_count, false);
        setVisible(R.id.import_btn_stop_remote, true);
        ProgressBar progress = findId(R.id.import_remote_progress);
        if (progress != null) {
            progress.setIndeterminate(true);
            progress.setVisibility(View.VISIBLE);
        }
        setText(R.id.import_remote_status, getContext().getString(R.string.import_remote_starting));
        setText(R.id.import_btn_cancel, getContext().getString(R.string.import_remote_close));
        applySize(dialogWidth(), remoteDialogHeight());
        applyFocusLinks(true);
        focusAfterLayout(R.id.import_btn_stop_remote);
    }

    /**
     * 服务启动成功：展示二维码与可手动输入的地址。
     */
    public void showRemoteInfo(String url, Bitmap qrBitmap) {
        ProgressBar progress = findId(R.id.import_remote_progress);
        if (progress != null) {
            progress.setIndeterminate(false);
            progress.setProgress(0);
            progress.setVisibility(View.GONE);
        }
        setText(R.id.import_remote_status, getContext().getString(R.string.import_remote_waiting));

        ImageView qrImage = findId(R.id.import_qr_image);
        if (qrImage != null) {
            qrImage.setImageBitmap(qrBitmap);
        }
        setVisible(R.id.import_qr_frame, qrBitmap != null);

        TextView urlView = findId(R.id.import_remote_url);
        if (urlView != null) {
            urlView.setText(getContext().getString(R.string.import_remote_url, url));
            urlView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 服务启动失败（无 Wi-Fi、端口占用等）。
     */
    public void showRemoteError(String message) {
        ProgressBar progress = findId(R.id.import_remote_progress);
        if (progress != null) {
            progress.setVisibility(View.GONE);
        }
        setVisible(R.id.import_qr_frame, false);
        setVisible(R.id.import_remote_url, false);
        setText(R.id.import_remote_status, message);
        setText(R.id.import_btn_cancel, getContext().getString(R.string.import_remote_close));
        focusAfterLayout(R.id.import_btn_stop_remote);
    }

    /** 正在接收文件并显示百分比 */
    public void showReceiving(String name, int percent) {
        setText(R.id.import_remote_status,
                getContext().getString(R.string.import_remote_receiving, name, Math.max(percent, 0)));
        ProgressBar progress = findId(R.id.import_remote_progress);
        if (progress != null) {
            progress.setIndeterminate(percent < 0);
            if (percent >= 0) {
                progress.setProgress(percent);
            }
            progress.setVisibility(View.VISIBLE);
        }
    }

    /** 文件已完整接收，等待导入数据库 */
    public void showReceived(String name) {
        hideProgress();
        setText(R.id.import_remote_status,
                getContext().getString(R.string.import_remote_received, name));
    }

    /** 文件已导入书架 */
    public void showImported(String name, int importedCount) {
        hideProgress();
        setText(R.id.import_remote_status,
                getContext().getString(R.string.import_remote_imported, name));
        TextView countView = findId(R.id.import_remote_count);
        if (countView != null) {
            countView.setText(getContext().getString(R.string.import_remote_imported_count, importedCount));
            countView.setVisibility(View.VISIBLE);
        }
    }

    /** 文件导入失败 */
    public void showImportFailed(String name, String reason) {
        hideProgress();
        setText(R.id.import_remote_status,
                getContext().getString(R.string.import_remote_import_failed, name)
                        + (reason == null || reason.isEmpty() ? "" : "：" + reason));
    }

    /** 上传过程失败 */
    public void showUploadFailed(String name, String message) {
        hideProgress();
        setText(R.id.import_remote_status,
                getContext().getString(R.string.import_remote_upload_failed, name)
                        + (message == null || message.isEmpty() ? "" : "（" + message + "）"));
    }

    private void hideProgress() {
        ProgressBar progress = findId(R.id.import_remote_progress);
        if (progress != null) {
            progress.setVisibility(View.GONE);
        }
    }

    /**
     * 停止接收后回到导入方式选择，便于再次选择本地或重新开启远程导入。
     */
    private void resetToOptions() {
        setVisible(R.id.import_options, true);
        setVisible(R.id.import_remote_panel, false);
        setVisible(R.id.import_qr_frame, false);
        setVisible(R.id.import_remote_url, false);
        setVisible(R.id.import_remote_count, false);
        setVisible(R.id.import_btn_stop_remote, false);
        hideProgress();
        setText(R.id.import_remote_status, getContext().getString(R.string.import_remote_starting));
        setText(R.id.import_btn_cancel, getContext().getString(R.string.btn_cancel));
        applySize(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
        applyFocusLinks(false);
        focusAfterLayout(R.id.import_remote_option);
    }

    private void setVisible(int id, boolean visible) {
        View view = findId(id);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 电视遥控器焦点链路：左右方向锁在弹窗内，上下按当前面板在可操作项之间移动，
     * 避免焦点跑到底层的集合列表或弹窗外。
     */
    private void applyFocusLinks(boolean remotePanel) {
        View remoteOption = findId(R.id.import_remote_option);
        View localOption = findId(R.id.import_local_option);
        View stop = findId(R.id.import_btn_stop_remote);
        View cancel = findId(R.id.import_btn_cancel);

        if (remoteOption != null) {
            remoteOption.setNextFocusUpId(remoteOption.getId());
            remoteOption.setNextFocusLeftId(remoteOption.getId());
            remoteOption.setNextFocusRightId(remoteOption.getId());
            remoteOption.setNextFocusDownId(remotePanel
                    ? remoteOption.getId() : R.id.import_local_option);
        }
        if (localOption != null) {
            localOption.setNextFocusUpId(remotePanel
                    ? localOption.getId() : R.id.import_remote_option);
            localOption.setNextFocusLeftId(localOption.getId());
            localOption.setNextFocusRightId(localOption.getId());
            localOption.setNextFocusDownId(remotePanel
                    ? localOption.getId() : R.id.import_btn_cancel);
        }
        if (stop != null) {
            stop.setNextFocusUpId(remotePanel
                    ? stop.getId() : R.id.import_remote_option);
            stop.setNextFocusLeftId(stop.getId());
            stop.setNextFocusRightId(stop.getId());
            stop.setNextFocusDownId(R.id.import_btn_cancel);
        }
        if (cancel != null) {
            cancel.setNextFocusUpId(remotePanel
                    ? R.id.import_btn_stop_remote : R.id.import_local_option);
            cancel.setNextFocusLeftId(cancel.getId());
            cancel.setNextFocusRightId(cancel.getId());
            cancel.setNextFocusDownId(cancel.getId());
        }
    }

    /**
     * 布局完成后再请求焦点：面板切换后立即 requestFocus 会因视图尚未布局而失败。
     * 仅电视端主动聚焦，手机/平板上保持系统默认的触摸行为。
     */
    private void focusAfterLayout(int id) {
        if (!isTv()) {
            return;
        }
        final int token = ++focusToken;
        View view = findId(id);
        if (view != null) {
            requestFocusWithRetry(view, token, 0);
        }
    }

    /**
     * 弹窗刚 show 时窗口可能还没有输入焦点，requestFocus 会直接返回 false。
     * 用短间隔重试，既覆盖布局未完成，也覆盖窗口焦点尚未建立的情况。
     */
    private void requestFocusWithRetry(View view, int token, int attempt) {
        view.post(() -> {
            if (token != focusToken) {
                return;
            }
            if (view.requestFocus()) {
                return;
            }
            if (attempt < 8) {
                view.postDelayed(() -> requestFocusWithRetry(view, token, attempt + 1), 120);
            }
        });
    }

    private boolean isTv() {
        Configuration configuration = getContext().getResources().getConfiguration();
        if ((configuration.uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        return getContext().getResources().getBoolean(R.bool.is_tv);
    }

    private int dialogWidth() {
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int maxWidth = isTv() ? dp(600) : dp(420);
        return Math.min((int) (screenWidth * 0.9f), maxWidth);
    }

    private int remoteDialogHeight() {
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int maxHeight = isTv() ? dp(760) : dp(620);
        return Math.min((int) (screenHeight * 0.9f), maxHeight);
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
