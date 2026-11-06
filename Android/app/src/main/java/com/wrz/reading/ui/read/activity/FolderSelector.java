package com.wrz.reading.ui.read.activity;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;

/**
 * 漫画内文件夹下拉选择器：
 * 当漫画目录包含多个含图子文件夹时，通过 Spinner 在「全部文件夹」与各子文件夹之间切换。
 * <p>
 * 只负责 Spinner 生命周期与所选文件夹的文件收集；
 * 排序、写入数据源与视图刷新通过 {@link Host#onFolderSelected(int, List)} 交回宿主处理
 * （与原 ReaderActivity.onFolderSelected 顺序一致：先回调位置再处理文件，空列表也回调）。
 */
public class FolderSelector {

    /**
     * 宿主回调：position==0 表示「全部文件夹」；files 为所选文件夹收集到的原始列表（可能为空）
     */
    public interface Host {
        void onFolderSelected(int position, List<ReadItem> files);
    }

    private final Activity activity;
    private final Spinner spinner;
    private final Host host;

    @Nullable
    private List<File> subdirectories;
    private boolean active = false;

    public FolderSelector(Activity activity, Spinner spinner, Host host) {
        this.activity = activity;
        this.spinner = spinner;
        this.host = host;
    }

    /**
     * 扫描 rootDir 下的含图子文件夹并决定是否展示 Spinner（多于 1 个才显示）。
     * 可在 IO 线程调用，内部切换到主线程更新 UI。
     */
    public void bind(File rootDir) {
        List<File> folders = FileUtils.getSubdirectoriesWithImages(rootDir);
        if (folders.size() > 1) {
            activity.runOnUiThread(() -> showSpinner(folders));
        } else {
            activity.runOnUiThread(this::hide);
        }
    }

    private void showSpinner(List<File> folders) {
        if (spinner == null) {
            return;
        }

        active = true;
        subdirectories = folders;

        // 创建文件夹名称列表，添加"全部"选项
        List<String> folderNames = new ArrayList<>();
        folderNames.add(activity.getString(R.string.all_folders));
        for (File folder : folders) {
            folderNames.add(folder.getName());
        }

        // 使用自定义布局确保文字颜色正确显示
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.spinner_item, folderNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setVisibility(View.VISIBLE);

        // 设置选择监听器
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                select(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 隐藏 Spinner 并清空选择状态
     */
    public void hide() {
        if (spinner != null) {
            spinner.setVisibility(View.GONE);
            spinner.setOnItemSelectedListener(null);
        }
        active = false;
        subdirectories = null;
    }

    private void select(int position) {
        if (!active || subdirectories == null) {
            return;
        }

        List<ReadItem> files;
        if (position == 0) {
            // 选择"全部文件夹"
            files = new ArrayList<>();
            for (File folder : subdirectories) {
                files.addAll(FileUtils.getMediaFilesRecursive(folder));
            }
        } else {
            // 选择特定文件夹
            if (position - 1 >= subdirectories.size()) {
                return;
            }
            files = FileUtils.getMediaFilesRecursive(subdirectories.get(position - 1));
        }

        host.onFolderSelected(position, files);
    }
}
