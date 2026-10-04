package com.wrz.reading.ui.read.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.activity.EpubReaderActivity;
import com.wrz.reading.ui.read.activity.FolderPickerActivity;
import com.wrz.reading.ui.read.activity.PdfReaderActivity;
import com.wrz.reading.ui.read.activity.ReaderActivity;
import com.wrz.reading.ui.read.activity.VideoPlayerActivity;
import com.wrz.reading.ui.read.adapter.ComicAdapter;
import com.wrz.reading.ui.read.adapter.MoveTargetAdapter;
import com.wrz.reading.ui.read.data.ComicDatabase;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FileType;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.utils.DiffCallBack;
import com.wrz.reading.ui.read.utils.FileSorter;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.VideoUtils;
import com.wrz.reading.ui.read.utils.ZipUtils;
import com.wrz.reading.ui.read.view.dialog.MoveDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 集合详情页：展示某个集合（或未分组）内的漫画列表。
 * <p>
 * 通过 {@link CollectionFragment#ARG_COLLECTION_ID} 和 {@link CollectionFragment#ARG_COLLECTION_NAME} 传参。
 * 当 collectionId == {@link CollectionFragment#UNCATEGORIZED_ID} 时表示「未分组」。
 * <p>
 * 功能：
 * - 点击漫画打开对应阅读器
 * - 长按进入选择模式，toolbar 出现「移动」按钮，可将选中漫画移动到指定集合
 * - 搜索过滤、排序切换
 * - FAB：始终从本地导入漫画（导入到当前集合；未分组则不指定集合）
 * - 重命名集合（仅命名集合）
 */
public class CollectionDetailFragment extends BaseFragment {

    private long collectionId;

    private final List<Comic> allComics = new ArrayList<>();
    public static final List<Comic> displayComics = new ArrayList<>();

    private RecyclerView comicRecyclerView;
    private ComicAdapter comicAdapter;
    private View emptyStateText;
    private FloatingActionButton addFab;
    private ImageButton btnSearch;

    private ImageButton btnSelectMode;
    private ImageButton btnEditName;
    private TextInputLayout searchLayout;
    private TextInputEditText searchEditText;

    private int currentSort = Collection.SORT_DEFAULT;
    private String searchKeyword = "";
    private NavController navController;

    /**
     * 从 FolderPickerActivity 返回的文件夹导入漫画
     */
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            return;
                        }
                        ArrayList<String> folders = result.getData()
                                .getStringArrayListExtra(FolderPickerActivity.EXTRA_SELECTED_FOLDERS);
                        if (folders == null || folders.isEmpty()) {
                            return;
                        }
                        importComicsFromFolders(folders);
                    });

    /**
     * 系统文件夹选择器（SAF）：
     * 部分机型（如鸿蒙）即使授权后也无法用 File API 读取文件，此时改用系统选择器兜底。
     */
    private final ActivityResultLauncher<Uri> safFolderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri != null) {
                            importComicsFromSafTree(uri);
                        }
                    });

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_collection_detail;
    }

    @Override
    public void initView() {
        comicRecyclerView = findId(R.id.comic_recycler_view);
        emptyStateText = findId(R.id.empty_state_text);
        addFab = findId(R.id.add_comic_fab);
        btnSearch = findId(R.id.btn_search);


        btnSelectMode = findId(R.id.btn_select_mode);

        btnEditName = findId(R.id.btn_edit_name);
        searchLayout = findId(R.id.search_layout);
        searchEditText = findId(R.id.search_edit_text);
    }

    @Override
    public void initData() {
        Bundle args = getArguments();
        String collectionName = "";
        if (args != null) {
            collectionId = args.getLong(CollectionFragment.ARG_COLLECTION_ID, CollectionFragment.UNCATEGORIZED_ID);
            collectionName = args.getString(CollectionFragment.ARG_COLLECTION_NAME, "");
        } else {
            collectionId = CollectionFragment.UNCATEGORIZED_ID;
        }

        navController = NavHostFragment.findNavController(this);

        // 设置 Toolbar 标题与返回按钮
        if (mCommonToolbar != null) {
            mCommonToolbar.setTitle(collectionName.isEmpty() ? "未分组" : collectionName);
            mCommonToolbar.setNavigationOnClickListener(v -> navController.navigateUp());
            // 挂载溢出菜单并处理点击
            mCommonToolbar.inflateMenu(R.menu.collection_detail_menu);
            mCommonToolbar.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_sort_name_asc) {
                    currentSort = Collection.SORT_BY_NAME;
                    applySortAndFilter();
                    syncSortMenuCheckState();
                } else if (itemId == R.id.action_sort_name_desc) {
                    currentSort = Collection.SORT_BY_NAME_REVERSE;
                    applySortAndFilter();
                    syncSortMenuCheckState();
                } else if (itemId == R.id.action_sort_by_size) {
                    currentSort = Collection.SORT_BY_SIZE;
                    applySortAndFilter();
                    syncSortMenuCheckState();
                } else if (itemId == R.id.action_sort_by_progress) {
                    currentSort = Collection.SORT_BY_PROGRESS;
                    applySortAndFilter();
                    syncSortMenuCheckState();
                } else if (itemId == R.id.action_sort_by_last_read_time) {
                    currentSort = Collection.SORT_BY_LAST_READ_TIME;
                    applySortAndFilter();
                    syncSortMenuCheckState();

                } else if (itemId == R.id.action_move) {
                    onMoveClick();

                } else if (itemId == R.id.action_splash) {
                    loadComics();

                } else if (itemId == R.id.action_share) {
                    onShareClick();

                }
                return true;
            });
            // 菜单勾选状态与当前排序同步
            syncSortMenuCheckState();
        }

        // 重命名按钮只在编辑模式下显示，默认隐藏
        btnEditName.setVisibility(View.GONE);

        loadComics();
    }

    @Override
    public void configViews() {
        comicRecyclerView.setLayoutManager(new GridLayoutManager(activity, (MyApplication.isTablet || isLand()) ? 4 : 3));
        comicAdapter = new ComicAdapter(R.layout.item_comic, displayComics);
        comicRecyclerView.setAdapter(comicAdapter);

        // FAB 始终用于从本地导入漫画
        addFab.setOnClickListener(v -> launchFolderPicker());

        btnSearch.setOnClickListener(v -> toggleSearch());

        btnSelectMode.setOnClickListener(v -> toggleSelectMode());

        btnEditName.setOnClickListener(v -> {
            List<Comic> Comics = new ArrayList<>();
            for (Comic comic : displayComics) {
                if (comic.isSelected()) {
                    Comics.add(comic);
                }
            }
            if (!Comics.isEmpty()) {
                showRenameDialog(Comics);
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchKeyword = s.toString().trim().toLowerCase();
                applySortAndFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        comicAdapter.setOnItemClickListener((adapter, view, position) -> {
            Comic comic = displayComics.get(position);
            if (comicAdapter.isSelectMode()) {
                comic.setSelected(!comic.isSelected());
                comicAdapter.notifyItemChanged(position);
            } else {
                openComic(comic);
            }
        });

        comicAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            if (!comicAdapter.isSelectMode()) {
                toggleSelectMode();
            }
            Comic comic = displayComics.get(position);
            comic.setSelected(true);
            comicAdapter.notifyItemChanged(position);
            return true;
        });
    }

    @Override
    public void goBack() {

    }

    public boolean isFirst = true;

    @Override
    public void onResume() {
        super.onResume();
        // 从阅读器/播放器返回时差量刷新：仅更新有变化的项（如进度），避免整列表闪烁与滚动跳动
        if (isFirst) {
            isFirst = false;
        } else {
            mSplash.postDelayed(mSplashRunnable, 1000);
        }
    }

    // 显式绑定主线程 Looper，避免无参构造 Handler() 的废弃告警
    final Handler mSplash = new Handler(Looper.getMainLooper());
    final Runnable mSplashRunnable = new Runnable() {
        @Override
        public void run() {
            if (ComicDatabase.isUpdating) {
                mSplash.postDelayed(mSplashRunnable, 1000);
            } else {
                loadComics(true);
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSplash.removeCallbacks(mSplashRunnable);
    }
// ==================== 数据加载 ====================

    private void loadComics() {
        loadComics(false);
    }

    /**
     * 加载当前集合下的漫画。
     *
     * @param useDiff true 时采用 DiffUtil 差量刷新（仅更新有变化的项，保留滚动位置），
     *                用于 onResume 返回列表场景；false 时整表刷新，用于导入/删除/移动等显式操作。
     */
    private void loadComics(boolean useDiff) {
        singleThread.execute(() -> {
            List<Comic> comics;
            if (collectionId == CollectionFragment.UNCATEGORIZED_ID) {
                comics = MyApplication.comicDatabase.comicDao().getUncategorizedComics();
            } else {
                comics = MyApplication.comicDatabase.comicDao().getComicsByCollection(collectionId);
            }

            final long[] totalSize = getComicSize(comics);

            setTotalSize(totalSize[0], totalSize[1]);

            // 在后台线程构建临时列表，避免与主线程遍历 allComics 竞态
            List<Comic> tempList = new ArrayList<>(comics);

            /*comics.forEach(c -> c.setSelected(false));*/

            runOnUiIfAlive(() -> {
                if (useDiff) {
                    applyComicsDiff(tempList);
                } else {
                    allComics.clear();
                    allComics.addAll(tempList);
                    applySortAndFilter();
                    updateEmptyState();
                }
            });
        });
    }

    private long[] getComicSize(List<Comic> comics) {
        final long[] totalSize = {0, 0};
        comics.forEach(comic -> {
            /*FileType fileType = comic.getFileTypeEnum();*/

            File file = new File(comic.getPath());
            if (file.exists()) {
                totalSize[1] += file.length();
            }

            long size = 0;
            File tempExtractDir = ZipUtils.getTempExtractDir(comic.getPath());
            if (tempExtractDir != null && tempExtractDir.isDirectory()) {
                size = FileUtils.getFolderSize(tempExtractDir.getAbsolutePath());
            }
            totalSize[0] += size;

            comic.setCacheSize(size);

        });
        return totalSize;
    }

    private void setTotalSize(long totalSize, long fileSize) {
        singleThread.execute(() -> {
            Collection collection = MyApplication.comicDatabase.collectionDao().getCollectionById(collectionId);

            if (collection != null) {
                collection.setCacheSize(totalSize);
                collection.setFileSize(fileSize);
                MyApplication.comicDatabase.collectionDao().updateCollection(collection);
            }
        });
    }

    /**
     * 差量刷新：对比当前显示列表与最新数据，只更新有变化的位置，
     * 避免整表 notifyDataSetChanged 导致的闪烁与滚动跳动。
     * Comic 未重写 equals，故按 id 判同一项，按字段对比内容是否变化。
     */
    private void applyComicsDiff(List<Comic> freshList) {
        // 旧列表快照（在重建 displayComics 之前抓取）
        List<Comic> oldList = new ArrayList<>(displayComics);
        // 更新数据源
        allComics.clear();
        allComics.addAll(freshList);
        // 重建显示列表（不触发 notify）
        rebuildDisplayList();
        // 计算差量并分发到 adapter，保留滚动位置
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new DiffCallBack.ComicDiffCallback(oldList, displayComics), false);
        diff.dispatchUpdatesTo(comicAdapter);
        updateEmptyState();
    }


    private void updateEmptyState() {
        if (allComics.isEmpty()) {
            comicRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            comicRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
        }
    }

    // ==================== 打开漫画 ====================

    private void openComic(Comic comic) {
        if (MyApplication.checkPermissions(activity)) {
            FileType type = comic.getFileTypeEnum();
            if (type == FileType.PDF) {
                PdfReaderActivity.start(activity, comic);
            } else if (type == FileType.EPUB) {
                EpubReaderActivity.start(activity, comic);
            } else if (type == FileType.VIDEO || type == FileType.MUSIC) {
                openVideoWithExternalPlayer(comic);
            } else {
                ReaderActivity.start(activity, comic);
            }
        } else {
            MyApplication.requestPermissions(activity);
        }
    }

    /**
     * 使用应用内 ExoPlayer 播放视频文件
     */
    private void openVideoWithExternalPlayer(Comic comic) {
        File file = new File(comic.getPath());
        if (!file.exists()) {
            Toast.makeText(activity, "视频文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果 path 是目录（压缩包场景），从中查找第一个视频文件
        String videoPath = comic.getPath();
        if (file.isDirectory()) {
            List<File> videos = FileUtils.getVideoFiles(file);
            if (videos.isEmpty()) {
                Toast.makeText(activity, "目录中没有视频文件", Toast.LENGTH_SHORT).show();
                return;
            }
            videoPath = videos.get(0).getAbsolutePath();
        }

        VideoPlayerActivity.start(activity, comic.getId(),
                videoPath, comic.getTitle(),
                false, 1000);
    }

    // ==================== 搜索 ====================

    private void toggleSearch() {
        if (searchLayout.getVisibility() == View.VISIBLE) {
            searchLayout.setVisibility(View.GONE);
            searchKeyword = "";
            searchEditText.setText("");
            applySortAndFilter();
        } else {
            searchLayout.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
        }
    }

    // ==================== 选择模式 ====================

    @SuppressLint("NotifyDataSetChanged")
    private void toggleSelectMode() {
        comicAdapter.toggleSelectMode();
        if (comicAdapter.isSelectMode()) {
            btnSelectMode.setImageResource(R.drawable.ic_close);
            // 选择模式下：FAB 变为删除按钮，toolbar 显示移动按钮
            addFab.setImageResource(R.drawable.delete_24px);
            addFab.setOnClickListener(v -> onDeleteFabClick());

            btnEditName.setVisibility(View.VISIBLE);

            syncSortMenuCheckState();

        } else {
            btnSelectMode.setImageResource(R.drawable.ic_photo_library);
            // 退出选择模式：FAB 恢复为添加按钮，隐藏移动和重命名按钮
            addFab.setImageResource(R.drawable.add_24px);
            addFab.setOnClickListener(v -> launchFolderPicker());

            btnEditName.setVisibility(View.GONE);

            clearSelections();

            syncSortMenuCheckState();
        }
        comicAdapter.notifyDataSetChanged();
    }

    private void clearSelections() {
        for (Comic c : allComics) {
            c.setSelected(false);
        }
    }

    private List<Comic> getSelectedComics() {
        List<Comic> selected = new ArrayList<>();
        for (Comic c : displayComics) {
            if (c.isSelected()) {
                selected.add(c);
            }
        }
        return selected;
    }

    /**
     * 选择模式下 FAB（删除按钮）点击：删除选中的漫画。
     */
    private void onDeleteFabClick() {
        List<Comic> selected = getSelectedComics();
        if (selected.isEmpty()) {
            Toast.makeText(activity, "请先选择漫画", Toast.LENGTH_SHORT).show();
            return;
        }
        DialogHelper.showDeleteConfirmDialog(activity,
                "删除漫画",
                "确定删除选中的 " + selected.size() + " 本漫画？此操作不可撤销。",
                (del) -> deleteComics(selected, del));
    }

    private void deleteComics(List<Comic> comics, boolean del) {
        singleThread.execute(() -> {
            for (Comic c : comics) {
                MyApplication.comicDatabase.comicDao().deleteComic(c);
                if (del) {
                    new File(c.getPath()).delete();
                }
            }
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, "已删除 " + comics.size() + " 本", Toast.LENGTH_SHORT).show();
                toggleSelectMode();
                loadComics();
            });
        });
    }

    // ==================== 移动到集合 ====================

    /**
     * 点击 toolbar 移动按钮：将选中的漫画移动到指定集合（含未分组）。
     */
    private void onMoveClick() {
        List<Comic> selected = getSelectedComics();
        if (selected.isEmpty()) {
            Toast.makeText(activity, "请先选择漫画", Toast.LENGTH_SHORT).show();
            return;
        }
        showMoveDialog(selected);
    }

    private void showMoveDialog(List<Comic> comics) {
        singleThread.execute(() -> {
            List<Collection> collections = MyApplication.comicDatabase.collectionDao().getAllCollections();
            // 在后台线程构建目标列表（含数量查询），避免主线程访问数据库
            List<MoveTargetAdapter.MoveTarget> targets = new ArrayList<>();
            if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                targets.add(new MoveTargetAdapter.MoveTarget(CollectionFragment.UNCATEGORIZED_ID, "未分组", "未归入任何集合"));
            }
            for (Collection c : collections) {
                if (c.getId() != collectionId) {
                    int count = MyApplication.comicDatabase.collectionDao().getComicCountInCollection(c.getId());
                    targets.add(new MoveTargetAdapter.MoveTarget(c.getId(), c.getName(), count + " 本"));
                }
            }
            runOnUiIfAlive(() -> {
                if (targets.isEmpty()) {
                    Toast.makeText(activity, "没有可移动的目标集合", Toast.LENGTH_SHORT).show();
                    return;
                }

                MoveDialog dialog = new MoveDialog(activity, targets);
                dialog.setListener(new MoveDialog.onClickListener() {
                    @Override
                    public void onItemClick(MoveTargetAdapter.MoveTarget target) {
                        moveComics(comics, target.id);
                    }

                    @Override
                    public void onCreate() {
                        showCreateCollectionDialog(name -> createCollectionAndResumeMove(comics, name));
                    }
                });
                dialog.show();
                dialog.setMoveTitle("移动 " + comics.size() + " 本到");
                /*showMoveTargetDialog(comics, targets);*/
            });
        });
    }

    /**
     * 创建集合后重新展示移动目标对话框（自动选中刚创建的集合）。
     */
    private void createCollectionAndResumeMove(List<Comic> comics, String name) {
        singleThread.execute(() -> {
            long newId = MyApplication.comicDatabase.collectionDao().insertCollection(Collection.create(name));
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, "已创建集合「" + name + "」", Toast.LENGTH_SHORT).show();
                // 直接移动到新集合
                moveComics(comics, newId);
            });
        });
    }

    /**
     * 创建集合输入对话框（复用 dialog_create_collection 布局）。
     */
    private void showCreateCollectionDialog(OnCollectionCreatedListener listener) {
        DialogHelper.showTextInputDialog(activity, null, null, null, null,
                listener::onCreated);
    }

    interface OnCollectionCreatedListener {
        void onCreated(String name);
    }

    private void moveComics(List<Comic> comics, long targetCollectionId) {
        List<Long> ids = new ArrayList<>();
        for (Comic c : comics) {
            ids.add(c.getId());
        }
        singleThread.execute(() -> {
            if (targetCollectionId == CollectionFragment.UNCATEGORIZED_ID) {
                MyApplication.comicDatabase.comicDao().moveOutOfCollection(ids);
            } else {
                MyApplication.comicDatabase.comicDao().moveToCollection(targetCollectionId, ids);
            }
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, "已移动 " + ids.size() + " 本", Toast.LENGTH_SHORT).show();
                toggleSelectMode();
                loadComics();
            });
        });
    }

    // ==================== 分享文件 ====================

    /**
     * 点击 toolbar 分享按钮：将选中的可分享文件（zip/wrz/pdf/epub/视频/音乐等实际文件）
     * 通过系统分享面板发送。图片目录类型（IMAGE）不可分享。
     */
    private void onShareClick() {
        List<Comic> selected = getSelectedComics();
        List<File> shareableFiles = new ArrayList<>();
        for (Comic comic : selected) {
            FileType fileType = comic.getFileTypeEnum();
            File file = new File(comic.getPath());
            // 仅实际文件类型可分享；IMAGE 为目录，排除
            if (fileType != FileType.IMAGE && file.exists() && file.isFile()) {
                shareableFiles.add(file);
            }
        }
        if (shareableFiles.isEmpty()) {
            Toast.makeText(activity, "选中的项目中没有可分享的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        FileUtils.shareFiles(activity, shareableFiles);
    }


    // ==================== 从本地导入漫画 ====================

    private void launchFolderPicker() {
        if (MyApplication.checkPermissions(activity)) {
            if (isStorageDirectReadable()) {
                Intent intent = new Intent(activity, FolderPickerActivity.class);
                folderPickerLauncher.launch(intent);
            } else {
                // 授权后仍读取不到文件（如鸿蒙限制），改用系统文件夹选择器
                safFolderPickerLauncher.launch(null);
            }
        } else {
            MyApplication.requestPermissions(activity);
        }
    }

    /**
     * 直接路径是否可读：授权后根目录仍列不出内容，说明该机型限制了 File API 访问
     */
    private boolean isStorageDirectReadable() {
        File root = Environment.getExternalStorageDirectory();
        File[] files = root == null ? null : root.listFiles();
        return files != null && files.length > 0;
    }

    // ==================== SAF 导入（受限机型兜底） ====================

    /**
     * 处理系统文件夹选择器返回的目录树：
     * 1. 优先把 tree URI 解析为真实路径，可读则直接导入（避免复制大文件）；
     * 2. 解析失败或不可读时，把目录内容复制到应用私有目录再导入。
     */
    private void importComicsFromSafTree(Uri treeUri) {
        singleThread.execute(() -> {
            runOnUiIfAlive(this::showLoading);
            File dir = resolveSafTreeDir(treeUri);
            if (dir == null) {
                dir = copySafTreeToLocal(treeUri);
            }
            final File importDir = dir;
            runOnUiIfAlive(() -> {
                dismissLoading();
                if (importDir == null) {
                    Toast.makeText(activity, "无法读取所选文件夹", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> paths = collectSafImportPaths(importDir);
                if (paths.isEmpty()) {
                    Toast.makeText(activity, "所选文件夹中没有可导入的内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                importComicsFromFolders(paths);
            });
        });
    }

    /**
     * 把 SAF tree URI 解析为真实路径（仅支持主存储），并确认 File API 可读。
     */
    private File resolveSafTreeDir(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            String[] split = docId.split(":");
            if (!"primary".equalsIgnoreCase(split[0])) return null;
            File dir = new File(Environment.getExternalStorageDirectory(),
                    split.length > 1 ? split[1] : "");
            File[] files = dir.listFiles();
            if (dir.isDirectory() && files != null) return dir;
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "resolveSafTreeDir failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * SAF 目录的导入策略与 FolderPickerActivity 对齐：
     * 顶层有子文件夹或支持的文件时逐个导入；否则把所选目录整体作为一部漫画导入。
     */
    private List<String> collectSafImportPaths(File dir) {
        List<String> paths = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()
                        || !FileUtils.isSupportedExtFormat(child.getName()).isEmpty()
                        || FileUtils.isVideoFile(child.getName())
                        || FileUtils.isMusicFile(child.getName())) {
                    paths.add(child.getAbsolutePath());
                }
            }
        }
        if (paths.isEmpty()) {
            paths.add(dir.getAbsolutePath());
        }
        return paths;
    }

    /**
     * 真实路径不可读时，把 SAF 目录树中可导入的文件复制到应用私有目录。
     * 返回复制后的本地目录，失败返回 null。
     */
    private File copySafTreeToLocal(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if ("primary".equalsIgnoreCase(docId) || "primary:".equals(docId)) {
                return null; // 不整盘复制存储根目录
            }
            Context context = activity.getApplicationContext();
            String displayName = queryDocumentName(context,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId));
            String folderName = sanitizeFileName(displayName);
            if (folderName.isEmpty()) folderName = "saf_import";
            File destRoot = new File(context.getExternalFilesDir(null), "saf_import/" + folderName);
            File[] copied = destRoot.listFiles();
            if (copied != null && copied.length > 0) {
                return destRoot; // 已复制过，直接复用
            }
            if (!destRoot.isDirectory() && !destRoot.mkdirs()) {
                return null;
            }
            copyDocumentChildren(context, treeUri, docId, destRoot);
            copied = destRoot.listFiles();
            return (copied != null && copied.length > 0) ? destRoot : null;
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "copySafTreeToLocal failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 递归遍历目录树：子目录继续深入，可导入的文件复制到 destDir。
     */
    private void copyDocumentChildren(Context context, Uri treeUri, String parentDocId, File destDir) {
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        try (Cursor cursor = resolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                String docId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (docId == null || name == null) continue;
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    File subDir = new File(destDir, sanitizeFileName(name));
                    if (subDir.isDirectory() || subDir.mkdirs()) {
                        copyDocumentChildren(context, treeUri, docId, subDir);
                    }
                } else if (isImportableDocument(name)) {
                    copyDocumentFile(resolver, docUri, new File(destDir, sanitizeFileName(name)));
                }
            }
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "copyDocumentChildren failed: " + e.getMessage());
        }
    }

    private boolean isImportableDocument(String name) {
        return FileUtils.isImageFile(name)
                || !FileUtils.isSupportedExtFormat(name).isEmpty()
                || FileUtils.isVideoFile(name)
                || FileUtils.isMusicFile(name);
    }

    private void copyDocumentFile(ContentResolver resolver, Uri docUri, File dest) {
        try (InputStream in = resolver.openInputStream(docUri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            LogUtil.e("CollectionDetailFragment", "copyDocumentFile failed: " + e.getMessage());
        }
    }

    private String queryDocumentName(Context context, Uri docUri) {
        try (Cursor cursor = context.getContentResolver().query(docUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String sanitizeFileName(String name) {
        return name == null ? "" : name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    /**
     * 从文件夹导入漫画。
     * 若当前在命名集合页，导入的漫画直接归属该集合；未分组页则不指定集合。
     */
    private void importComicsFromFolders(List<String> folderPaths) {
        singleThread.execute(() -> {
            runOnUiIfAlive(this::showLoading);
            int imported = 0;
            for (String path : folderPaths) {
                File file = new File(path);
                if (!file.exists()) continue;

                Comic comic = buildComicFromFile(file);
                if (comic != null && !isComicExists(comic.getOriginalPath())) {
                    // 命名集合页导入的漫画直接归属当前集合
                    if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                        comic.setCollectionId(collectionId);
                    }
                    MyApplication.comicDatabase.comicDao().insertComic(comic);
                    imported++;
                }
            }
            final int count = imported;
            runOnUiIfAlive(() -> {
                dismissLoading();
                Toast.makeText(activity, "已导入 " + count + " 本漫画", Toast.LENGTH_SHORT).show();
                loadComics();
            });
        });
    }

    private boolean isComicExists(String originalPath) {
        List<Comic> all = MyApplication.comicDatabase.comicDao().getAllComics();
        for (Comic c : all) {
            if (originalPath.equals(c.getOriginalPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据文件类型构建 Comic 对象。
     * 支持图片目录、zip、pdf、epub。
     */
    private Comic buildComicFromFile(File file) {
        String name = file.getName();
        String ext = FileUtils.isSupportedExtFormat(name);

        if (file.isDirectory()) {
            // 文件夹：统计图片+视频作为漫画内容
            List<ReadItem> media = FileUtils.getMediaFilesRecursive(file);
            if (media.isEmpty()) return null;
            File cover = FileUtils.getFirstImageFile(file);
            String coverPath = cover != null ? cover.getAbsolutePath() : "";
            return Comic.uncompressed(name, file.getAbsolutePath(), coverPath, media.size(), "image");
        }

        if (FileUtils.SPECIAL_FORMATS.equals(ext)) {
            return Comic.uncompressed(name, file.getAbsolutePath(), "", 0, FileType.ZIP.getCode());
        }
        if (FileUtils.PDF_FORMATS.equals(ext)) {
            return Comic.uncompressed(name, file.getAbsolutePath(), "", 0, FileType.PDF.getCode());
        }
        if (FileUtils.EPUB_FORMATS.equals(ext)) {
            return Comic.uncompressed(name, file.getAbsolutePath(), "", 0, FileType.EPUB.getCode());
        }
        // 视频文件：交由播放器打开
        if (FileUtils.isVideoFile(name)) {
            // 生成非首帧缩略图作为封面，避免纯黑封面
            String cover = VideoUtils.generateThumbnailFile(file, name);
            return Comic.uncompressed(name, file.getAbsolutePath(), cover, 1, FileType.VIDEO.getCode());
        }
        // 音乐文件：交由播放器打开
        if (FileUtils.isMusicFile(name)) {
            return Comic.uncompressed(name, file.getAbsolutePath(), "", 1, FileType.MUSIC.getCode());
        }
        return null;
    }

    // ==================== 重命名漫画 ====================

    private void showRenameDialog(List<Comic> list) {
        DialogHelper.showRenameComicsDialog(activity,
                "重命名", "输入新的名称", "保存", list,
                this::renameComics);
    }

    private void renameComics(List<Comic> list) {
        singleThread.execute(() -> {
            ComicDatabase.updateList(list);

            runOnUiIfAlive(() -> {
                loadComics(true);
                Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ==================== 排序搜索功能 ====================

    @SuppressLint("NotifyDataSetChanged")
    private void applySortAndFilter() {
        rebuildDisplayList();
        comicAdapter.notifyDataSetChanged();
    }


    /**
     * 根据 allComics + 当前搜索关键字 + 当前排序方式重建 displayComics。
     * 不触发 adapter 通知，调用方自行决定 notifyDataSetChanged 或 DiffUtil 差量分发。
     */
    private void rebuildDisplayList() {
        displayComics.clear();

        for (Comic c : allComics) {
            if (searchKeyword.isEmpty() || c.getTitle().toLowerCase().contains(searchKeyword)) {
                displayComics.add(c);
            }
        }

        // 目录始终排在最前面的比较器
        Comparator<Comic> dirFirst = (a, b) -> {
            boolean aDir = new java.io.File(a.getPath()).isDirectory();
            boolean bDir = new java.io.File(b.getPath()).isDirectory();
            return Boolean.compare(!aDir, !bDir); // false(目录) 排在 true(文件) 前面
        };

        switch (currentSort) {
            case Collection.SORT_BY_NAME:
                FileSorter.sortComics(dirFirst, displayComics, false);

                break;
            case Collection.SORT_BY_NAME_REVERSE:
                FileSorter.sortComics(dirFirst, displayComics, true);

                break;
            case Collection.SORT_BY_SIZE:
                // 目录始终排在最前面的比较器
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(new File(b.getPath()).length(), new File(a.getPath()).length())));

                break;
            case Collection.SORT_BY_PROGRESS:
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(b.getReadProgress(), a.getReadProgress())));
                break;
            case Collection.SORT_BY_LAST_READ_TIME:
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(b.getLastRead(), a.getLastRead())));
                break;
        }
    }

    // ==================== 排序菜单 ====================

    /**
     * 将溢出菜单中排序项的勾选状态与 currentSort 同步。
     */
    private void syncSortMenuCheckState() {
        if (mCommonToolbar == null) return;
        MenuItem sortNameAsc = mCommonToolbar.getMenu().findItem(R.id.action_sort_name_asc);
        MenuItem sortNameDesc = mCommonToolbar.getMenu().findItem(R.id.action_sort_name_desc);
        MenuItem sortBySize = mCommonToolbar.getMenu().findItem(R.id.action_sort_by_size);
        MenuItem sortByTime = mCommonToolbar.getMenu().findItem(R.id.action_sort_by_progress);
        MenuItem sortByReadTime = mCommonToolbar.getMenu().findItem(R.id.action_sort_by_last_read_time);
        MenuItem moveItem = mCommonToolbar.getMenu().findItem(R.id.action_move);
        MenuItem shareItem = mCommonToolbar.getMenu().findItem(R.id.action_share);
        if (sortNameAsc == null || sortNameDesc == null || sortByTime == null || sortByReadTime == null || moveItem == null)
            return;

        if (comicAdapter != null) {
            moveItem.setVisible(comicAdapter.isSelectMode());
            shareItem.setVisible(comicAdapter.isSelectMode());
        } else {
            moveItem.setVisible(false);
            shareItem.setVisible(false);
        }

        switch (currentSort) {
            case Collection.SORT_BY_NAME:
                sortNameAsc.setChecked(true);
                break;
            case Collection.SORT_BY_NAME_REVERSE:
                sortNameDesc.setChecked(true);
                break;
            case Collection.SORT_BY_SIZE:
                sortBySize.setChecked(true);
                break;
            case Collection.SORT_BY_PROGRESS:
                sortByTime.setChecked(true);
                break;
            case Collection.SORT_BY_LAST_READ_TIME:
                sortByReadTime.setChecked(true);
                break;
        }

    }

}
