package com.wrz.reading.ui.main.adapter;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.DownloadTask;

import java.util.Locale;

/**
 * 下载列表适配器：展示标题、状态、进度，并根据状态显示"暂停/继续"操作。
 * 仅使用任务实体中的字段，不在绑定过程中做磁盘 stat。
 */
public class DownloadListAdapter extends BaseQuickAdapter<DownloadTask, BaseViewHolder> {

    public DownloadListAdapter(int layoutResId) {
        super(layoutResId);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, DownloadTask task) {
        Context ctx = getContext();
        holder.setText(R.id.tv_download_title, task.getTitle());

        TextView statusView = holder.getView(R.id.tv_download_status);
        statusView.setText(statusText(ctx, task.getStatus()));
        statusView.setTextColor(ContextCompat.getColor(ctx, statusColor(task.getStatus())));

        ProgressBar progressBar = holder.getView(R.id.pb_download);
        TextView percentView = holder.getView(R.id.tv_download_percent);
        long total = task.getTotalSize();
        long done = task.getDownloadedSize();
        boolean running = task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                || task.getStatus() == DownloadTask.STATUS_PENDING;

        if (task.getStatus() == DownloadTask.STATUS_COMPLETED) {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(100);
            percentView.setText(ctx.getString(R.string.download_percent, 100));
            holder.setText(R.id.tv_download_progress,
                    ctx.getString(R.string.download_progress_sizes, formatSize(done), formatSize(done)));
        } else if (total > 0) {
            int percent = (int) Math.min(100, done * 100 / total);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(percent);
            percentView.setText(ctx.getString(R.string.download_percent, percent));
            holder.setText(R.id.tv_download_progress,
                    ctx.getString(R.string.download_progress_sizes, formatSize(done), formatSize(total)));
        } else {
            progressBar.setIndeterminate(running);
            if (!running) {
                progressBar.setProgress(0);
            }
            percentView.setText("");
            holder.setText(R.id.tv_download_progress,
                    ctx.getString(R.string.download_progress_unknown, formatSize(done)));
        }

        TextView action = holder.getView(R.id.tv_download_action);
        if (running) {
            action.setVisibility(View.VISIBLE);
            action.setText(R.string.btn_download_pause);
        } else if (task.getStatus() == DownloadTask.STATUS_PAUSED
                || task.getStatus() == DownloadTask.STATUS_FAILED) {
            action.setVisibility(View.VISIBLE);
            action.setText(R.string.btn_download_resume);
        } else {
            action.setVisibility(View.GONE);
        }


        if (holder.getView(R.id.tv_download_action).getVisibility() == View.VISIBLE) {
            holder.itemView.setNextFocusRightId(R.id.tv_download_action);
            holder.getView(R.id.tv_download_action).setNextFocusRightId(R.id.tv_download_delete);
            holder.getView(R.id.tv_download_delete).setNextFocusLeftId(R.id.tv_download_action);

        } else {
            holder.itemView.setNextFocusRightId(R.id.tv_download_delete);
            holder.getView(R.id.tv_download_delete).setNextFocusLeftId(R.id.item_view);
        }
    }

    private static String statusText(Context ctx, int status) {
        switch (status) {
            case DownloadTask.STATUS_PENDING:
                return ctx.getString(R.string.download_status_pending);
            case DownloadTask.STATUS_DOWNLOADING:
                return ctx.getString(R.string.download_status_downloading);
            case DownloadTask.STATUS_PAUSED:
                return ctx.getString(R.string.download_status_paused);
            case DownloadTask.STATUS_COMPLETED:
                return ctx.getString(R.string.download_status_completed);
            default:
                return ctx.getString(R.string.download_status_failed);
        }
    }

    private static int statusColor(int status) {
        if (status == DownloadTask.STATUS_COMPLETED) {
            return R.color.ios_blue;
        }
        if (status == DownloadTask.STATUS_FAILED) {
            return R.color.ios_red;
        }
        return R.color.ios_text_secondary;
    }

    /** 字节数格式化为 B/KB/MB/GB/TB */
    public static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) {
            return String.format(Locale.US, "%d %s", bytes, units[unit]);
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
