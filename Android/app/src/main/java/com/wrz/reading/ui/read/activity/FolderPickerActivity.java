package com.wrz.reading.ui.read.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.model.Comic;
import com.wrz.reading.model.FolderBean;
import com.wrz.reading.ui.read.adapter.FolderAdapter;
import com.wrz.reading.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FolderPickerActivity extends BaseActivity implements View.OnClickListener {
    private final String TAG = "FolderPickerActivity";
    public static final String EXTRA_SELECTED_FOLDERS = "selected_folders";

    private final List<String> selectedFolders = new ArrayList<>();
    private final List<FolderBean> currentList = new ArrayList<>();

    private FolderAdapter folderAdapter;

    private LinearLayout emptyStateLayout;

    private TextView tvSelectedCount;
    private TextView currentPathTextView;

    private File currentDir;

    private String search = "";

    private MaterialButton btnSelectAll;


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

        btnSelectAll = findViewById(R.id.btn_select_all);

        /*-----------------------------搜索-----------------------------*/

        searchEditText = findViewById(R.id.search_edit_text);

        searchEditText.addTextChangedListener(watcher);

        searchEditText.setOnEditorActionListener(editorActionListener);

        /*-----------------------------空白缺省-----------------------------*/

        emptyStateLayout = findViewById(R.id.empty_state_layout);
        MaterialButton btn_go_back = findViewById(R.id.btn_go_back);
        btn_go_back.setOnClickListener(this);

        /*-----------------------------中间-----------------------------*/

        RecyclerView folderRecyclerView = findViewById(R.id.folder_recycler_view);

        // 创建适配器
        folderAdapter = new FolderAdapter(R.layout.item_file, currentList);

        folderRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        folderRecyclerView.setAdapter(folderAdapter);

        // 设置列表点击事件
        folderAdapter.addChildClickViewIds(R.id.ll_item, R.id.select_indicator);
        folderAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            FolderBean item = currentList.get(position);

            int viewId = view.getId();
            if (viewId == R.id.ll_item) {
                if (currentList.get(position).getFile().isDirectory()) {
                    currentDir = currentList.get(position).getFile().getAbsoluteFile();
                    updateFolderList();
                } else {
                    setSelect(item, position);
                }
            } else if (viewId == R.id.select_indicator) {
                setSelect(item, position);
            }
        });


        /*-----------------------------底部-----------------------------*/

        tvSelectedCount = findViewById(R.id.tv_selected_count);

        MaterialButton confirmButton = findViewById(R.id.btn_confirm);
        MaterialButton cancelButton = findViewById(R.id.btn_cancel);

        // 设置按钮点击事件
        confirmButton.setOnClickListener(this);

        cancelButton.setOnClickListener(this);

        btnSelectAll.setOnClickListener(this);
    }

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
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private void setButtonText() {
        if (isAllSelected()) {
            btnSelectAll.setText(R.string.select_nothing);
        } else {
            btnSelectAll.setText(R.string.select_all);
        }
        String prefix = "已选择: (";
        String number = String.valueOf(selectedFolders.size());
        String suffix = ") 项";

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(prefix);
        builder.append(number);
        builder.append(suffix);

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
            currentDir = Environment.getExternalStorageDirectory();
            if (currentDir == null || !currentDir.exists()) {
                currentDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }
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

            runOnUiThread(() -> {
                currentList.clear();
                currentList.addAll(tempList);
                // 显示当前路径
                setButtonText();
                currentPathTextView.setText(currentDir.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), "内部存储"));
                folderAdapter.notifyDataSetChanged();
                if (currentList.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
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


    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.btn_go_back) {
            goBack();

        } else if (viewId == R.id.btn_confirm) {
            if (selectedFolders.isEmpty()) {
                Toast.makeText(this, "请至少选择一个文件夹", Toast.LENGTH_SHORT).show();
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