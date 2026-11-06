package com.wrz.reading.ui.read.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.common.TvInputHelper;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.read.adapter.FolderAdapter;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FolderBean;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FolderPickerActivity extends BaseActivity implements View.OnClickListener {
    public static final String EXTRA_SELECTED_FOLDERS = "selected_folders";

    private final List<String> selectedFolders = new ArrayList<>();
    private final List<FolderBean> currentList = new ArrayList<>();

    private FolderAdapter folderAdapter;

    private TextView tvSelectedCount;
    private TextView currentPathTextView;

    private File currentDir;

    private String search = "";


    private TextInputEditText searchEditText;

    @Override
    public void getIntentData() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_file_selector;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        /*-----------------------------头部-----------------------------*/

        currentPathTextView = findViewById(R.id.tv_current_path);

        /*-----------------------------搜索-----------------------------*/

        searchEditText = findViewById(R.id.search_edit_text);

        // TV：聚焦时只显示焦点，按 OK 才弹出输入法
        TvInputHelper.bind(searchEditText);

        searchEditText.addTextChangedListener(watcher);

        searchEditText.setOnEditorActionListener(editorActionListener);

        /*-----------------------------空白缺省-----------------------------*/

        setClick(R.id.btn_go_back, this);

        /*-----------------------------中间-----------------------------*/

        RecyclerView folderRecyclerView = findViewById(R.id.folder_recycler_view);

        // 创建适配器
        folderAdapter = new FolderAdapter(R.layout.item_file, currentList);

        folderRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        folderRecyclerView.setAdapter(folderAdapter);

        setAdapterClick();

        /*-----------------------------底部-----------------------------*/

        tvSelectedCount = findViewById(R.id.tv_selected_count);

        setClick(R.id.btn_confirm, this);
        setClick(R.id.btn_cancel, this);
        setClick(R.id.btn_select_all, this);

        setClick(R.id.btn_view_mode, v -> {
            if (linear) {
                folderRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
                folderAdapter = new FolderAdapter(R.layout.item_file_grid, currentList);
            } else {
                folderRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                folderAdapter = new FolderAdapter(R.layout.item_file, currentList);
            }
            folderRecyclerView.setAdapter(folderAdapter);
            setAdapterClick();
            linear = !linear;
        });
    }

    private void setAdapterClick() {
        // TV 遥控器：焦点落在 item 根布局上，OK 键通过 item 点击事件处理
        folderAdapter.setOnItemClickListener((adapter, view, position) -> handleFolderClick(position));
        // 触摸端的勾选指示器保留独立点击
        folderAdapter.addChildClickViewIds(R.id.select_indicator);
        folderAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.select_indicator) {
                setSelect(currentList.get(position), position);
            }
        });
    }

    /**
     * 目录进入下一级；文件切换选中状态（遥控器 OK / 触摸点击 item 均走这里）。
     */
    private void handleFolderClick(int position) {
        if (position < 0 || position >= currentList.size()) {
            return;
        }
        FolderBean item = currentList.get(position);
        if (item.getFile().isDirectory()) {
            currentDir = item.getFile().getAbsoluteFile();
            updateFolderList();
        } else {
            setSelect(item, position);
        }
    }

    boolean linear = true;

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable editable) {

        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            search = String.valueOf(charSequence);
            updateFolderList();
        }
    };

    TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                // 关闭键盘
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                // 清除焦点（可选）
                searchEditText.clearFocus();

                return true;
            }
            return false;
        }
    };

    private void setSelect(FolderBean item, int position) {
        if (!item.isExist()) {
            if (item.isSelected()) {
                for (int i = 0; i < selectedFolders.size(); i++) {
                    String str = selectedFolders.get(i);
                    if (str.equals(item.getFile().getAbsolutePath())) {
                        selectedFolders.remove(i);
                        break;
                    }
                }
            } else {
                selectedFolders.add(item.getFile().getAbsolutePath());
            }

            item.setSelected(!item.isSelected());
            // 更新选择按钮文本
            setButtonText();

            folderAdapter.notifyItemChanged(position);
        }
    }

    private boolean isAllSelected() {
        for (FolderBean folder : currentList) {
            if (!folder.isSelected() && !folder.isExist()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void goBack() {
        if (!currentDir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            File parent = currentDir.getParentFile();
            if (parent != null) {
                currentDir = parent;
                updateFolderList();
            }
        }
        super.goBack();
    }

    private void setButtonText() {
        if (isAllSelected()) {
            setText(R.string.select_nothing, R.id.btn_select_all);
        } else {
            setText(R.string.select_all, R.id.btn_select_all);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(getString(R.string.msg_selected_count_paren, selectedFolders.size()));

        /*// 设置数字部分为红色
        builder.setSpan(
                new ForegroundColorSpan(Color.RED),
                prefix.length(),
                prefix.length() + number.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // 设置其他部分为白色
        builder.setSpan(
                new ForegroundColorSpan(Color.BLACK),
                0,
                prefix.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        builder.setSpan(
                new ForegroundColorSpan(Color.BLACK),
                prefix.length() + number.length(),
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );*/

        tvSelectedCount.setText(builder);
    }

    @Override
    public void initData() {
        // 从根目录或从上次导入的目录开始
        String lastAddPath = MyApplication.manager.getLastAdd();
        File lastAddDir = new File(lastAddPath);

        if (MyApplication.manager.getLastAdd().isEmpty() || !lastAddDir.exists()) {
            if (isStorageDirectReadable()) {
                currentDir = Environment.getExternalStorageDirectory();
            } else {
                currentDir = this.getExternalFilesDir("");
            }
            if (currentDir == null || !currentDir.exists()) {
                currentDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }
            LogUtil.e(TAG, "initData: currentDir=" + currentDir.getName());
        } else {
            currentDir = lastAddDir;
        }

        updateFolderList();
    }

    @Override
    public void configView() {

    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateFolderList() {
        singleThread.submit(() -> {
            // 在后台线程构建临时列表，避免与主线程 Adapter 访问竞态
            List<FolderBean> tempList = new ArrayList<>();

            List<File> folderList = new ArrayList<>();

            // 获取当前目录下的所有文件
            File[] files = currentDir.listFiles();

            // 处理文件夹
            if (files != null) {
                LogUtil.e(TAG, "updateFolderList: files=" + Arrays.stream(files));
                for (File file : files) {
                    if (!file.getName().startsWith(".") && !file.isHidden()) {
                        if (file.isDirectory()) {
                            folderList.add(file);
                        }
                    }
                }
            }

            // 按名称排序
            folderList.sort(Comparator.comparing(File::getName));

            List<File> filesList = new ArrayList<>();

            // 处理文件
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().startsWith(".") && !file.isHidden()) {
                        if (!FileUtils.isSupportedExtFormat(file.getName()).isEmpty() || FileUtils.isVideoFile(file.getName())
                                || FileUtils.isMusicFile(file.getName())) {
                            filesList.add(file);
                        }
                    }
                }
            }

            filesList.sort(Comparator.comparing(File::getName));

            folderList.addAll(filesList);

            // 添加文件夹名称到列表
            for (File file : folderList) {
                if (!search.isEmpty()) {
                    if (file.getName().toLowerCase().contains(search.toLowerCase())) {
                        if (containsFiles(file.getAbsolutePath())) {
                            tempList.add(new FolderBean(file, true));
                        } else {
                            tempList.add(new FolderBean(file, false));
                        }
                    }
                } else {
                    if (containsFiles(file.getAbsolutePath())) {
                        tempList.add(new FolderBean(file, true));
                    } else {
                        tempList.add(new FolderBean(file, false));
                    }
                }
            }

            List<Comic> comics = MyApplication.comicDatabase.comicDao().getAllComicsSortedByName();

            for (Comic comic : comics) {
                for (FolderBean folder : tempList) {
                    if (comic.getOriginalPath().equals(folder.getFile().getAbsolutePath())
                            || comic.getPath().equals(folder.getFile().getAbsolutePath())) {
                        folder.setExist(true);
                    }
                }
            }

            tempList.removeIf(FolderBean::isExist);

            runOnUiThread(() -> {
                currentList.clear();
                currentList.addAll(tempList);
                // 显示当前路径
                setButtonText();
                currentPathTextView.setText(currentDir.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), getString(R.string.path_internal_storage)));
                folderAdapter.notifyDataSetChanged();
                if (currentList.isEmpty()) {
                    gone(R.id.btn_select_all);
                    visible(R.id.empty_state_layout);
                } else {
                    visible(R.id.btn_select_all);
                    gone(R.id.empty_state_layout);
                }
            });
        });

    }

    private boolean containsFiles(String directory) {
        for (String str : selectedFolders) {
            if (str.equals(directory)) {
                return true;
            }
        }
        return false;
    }


    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.btn_go_back) {
            goBack();

        } else if (viewId == R.id.btn_confirm) {
            if (selectedFolders.isEmpty()) {
                Toast.makeText(this, R.string.msg_select_at_least_one, Toast.LENGTH_SHORT).show();
            } else {
                MyApplication.manager.saveLastAdd(currentDir.getAbsolutePath());

                Intent resultIntent = new Intent();
                resultIntent.putStringArrayListExtra(EXTRA_SELECTED_FOLDERS, new ArrayList<>(selectedFolders));
                setResult(RESULT_OK, resultIntent);
                finish();
            }

        } else if (viewId == R.id.btn_cancel) {
            finish();

        } else if (viewId == R.id.btn_select_all) {
            if (isAllSelected()) {
                for (FolderBean folder : currentList) {
                    if (!folder.isExist()) {
                        folder.setSelected(false);

                        for (int i = 0; i < selectedFolders.size(); i++) {
                            String str = selectedFolders.get(i);
                            if (str.equals(folder.getFile().getAbsolutePath())) {
                                selectedFolders.remove(i);
                                break;
                            }
                        }
                    }
                }
            } else {
                for (FolderBean folder : currentList) {
                    if (!folder.isExist()) {
                        folder.setSelected(true);
                        selectedFolders.add(folder.getFile().getAbsolutePath());
                    }
                }
            }
            setButtonText();
            folderAdapter.notifyDataSetChanged();
        }
    }

}