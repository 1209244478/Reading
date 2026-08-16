package com.wrz.reading.ui.read.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FileType;
import com.wrz.reading.ui.read.activity.EpubReaderActivity;
import com.wrz.reading.ui.read.activity.FolderPickerActivity;
import com.wrz.reading.ui.read.activity.PdfReaderActivity;
import com.wrz.reading.ui.read.activity.ReaderActivity;
import com.wrz.reading.ui.read.activity.VideoPlayerActivity;
import com.wrz.reading.ui.read.adapter.ComicAdapter;
import com.wrz.reading.ui.read.adapter.MoveTargetAdapter;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.VideoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    private static final int SORT_NAME = 0;
    private static final int SORT_LAST_READ = 1;
    private static final int SORT_ADDED = 2;
    private static final int SORT_PROGRESS = 3;

    private long collectionId;
    private String collectionName;

    private final List<Comic> allComics = new ArrayList<>();
    public static final List<Comic> displayComics = new ArrayList<>();

    private RecyclerView comicRecyclerView;
    private ComicAdapter comicAdapter;
    private View emptyStateText;
    private FloatingActionButton addFab;
    private ImageButton btnSearch;
    private ImageButton btnSplash;
    private ImageButton btnChangeSort;
    private ImageButton btnSelectMode;
    private ImageButton btnMove;
    private ImageButton btnEditName;
    private TextInputLayout searchLayout;
    private TextInputEditText searchEditText;

    private int currentSort = SORT_NAME;
    private String searchKeyword = "";
    private NavController navController;

    /**
     * 从 FolderPickerActivity 返回的文件夹导入漫画
     */
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                                return;
                            }
                            ArrayList<String> folders = result.getData()
                                    .getStringArrayListExtra(FolderPickerActivity.EXTRA_SELECTED_FOLDERS);
                            if (folders == null || folders.isEmpty()) {
                                return;
                            }
                            importComicsFromFolders(folders);
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
        btnSplash = findId(R.id.btn_splash);
        btnChangeSort = findId(R.id.btn_change_sort);
        btnSelectMode = findId(R.id.btn_select_mode);
        btnMove = findId(R.id.btn_move);
        btnEditName = findId(R.id.btn_edit_name);
        searchLayout = findId(R.id.search_layout);
        searchEditText = findId(R.id.search_edit_text);
    }

    @Override
    public void initData() {
        Bundle args = getArguments();
        if (args != null) {
            collectionId = args.getLong(CollectionFragment.ARG_COLLECTION_ID, CollectionFragment.UNCATEGORIZED_ID);
            collectionName = args.getString(CollectionFragment.ARG_COLLECTION_NAME, "");
        } else {
            collectionId = CollectionFragment.UNCATEGORIZED_ID;
            collectionName = "";
        }

        navController = NavHostFragment.findNavController(this);

        // 设置 Toolbar 标题与返回按钮
        if (mCommonToolbar != null) {
            mCommonToolbar.setTitle(collectionName.isEmpty() ? "未分组" : collectionName);
            mCommonToolbar.setNavigationOnClickListener(v -> navController.navigateUp());
        }

        // 重命名按钮只在编辑模式下显示，默认隐藏
        btnEditName.setVisibility(View.GONE);

        loadComics();
    }

    @Override
    public void configViews() {
        comicRecyclerView.setLayoutManager(new GridLayoutManager(activity, 3));
        comicAdapter = new ComicAdapter(R.layout.item_comic, displayComics);
        comicRecyclerView.setAdapter(comicAdapter);

        // FAB 始终用于从本地导入漫画
        addFab.setOnClickListener(v -> launchFolderPicker());

        btnSearch.setOnClickListener(v -> toggleSearch());
        btnSplash.setOnClickListener(v -> loadComics());

        btnChangeSort.setOnClickListener(v -> {
            currentSort = (currentSort + 1) % 4;
            applySortAndFilter();
            Toast.makeText(activity, sortName(currentSort), Toast.LENGTH_SHORT).show();
        });
        btnSelectMode.setOnClickListener(v -> toggleSelectMode());
        btnMove.setOnClickListener(v -> onMoveClick());
        btnEditName.setOnClickListener(v -> {
            List<Comic> Comics = new ArrayList<>();
            for (Comic comic : displayComics) {
                if (comic.isSelected()) {
                    Comics.add(comic);
                }
            }
            showRenameDialog(Comics);
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

    @Override
    public void onResume() {
        super.onResume();
        // 从阅读器/播放器返回时差量刷新：仅更新有变化的项（如进度），避免整列表闪烁与滚动跳动
        loadComics(true);
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

            // 在后台线程构建临时列表，避免与主线程遍历 allComics 竞态
            List<Comic> tempList = new ArrayList<>(comics);

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
                new ComicDiffCallback(oldList, displayComics), false);
        diff.dispatchUpdatesTo(comicAdapter);
        updateEmptyState();
    }

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

        switch (currentSort) {
            case SORT_NAME:
                Collections.sort(displayComics, Comparator.comparing(Comic::getTitle, String.CASE_INSENSITIVE_ORDER));
                break;
            case SORT_LAST_READ:
                Collections.sort(displayComics, (a, b) -> Long.compare(b.getLastRead(), a.getLastRead()));
                break;
            case SORT_ADDED:
                Collections.sort(displayComics, (a, b) -> Long.compare(b.getId(), a.getId()));
                break;
            case SORT_PROGRESS:
                Collections.sort(displayComics, (a, b) -> Integer.compare((int) b.getReadProgress(), (int) a.getReadProgress()));
                break;
        }
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
        Intent intent = new Intent(activity, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, comic.getPath());
        intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, comic.getTitle());
        intent.putExtra(VideoPlayerActivity.EXTRA_COMIC_ID, comic.getId());
        startActivity(intent);
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

    private void toggleSelectMode() {
        comicAdapter.toggleSelectMode();
        if (comicAdapter.isSelectMode()) {
            btnSelectMode.setImageResource(R.drawable.ic_close);
            // 选择模式下：FAB 变为删除按钮，toolbar 显示移动按钮
            addFab.setImageResource(R.drawable.delete_24px);
            addFab.setOnClickListener(v -> onDeleteFabClick());
            btnMove.setVisibility(View.VISIBLE);
            btnEditName.setVisibility(View.VISIBLE);

            btnSplash.setVisibility(View.GONE);
            btnChangeSort.setVisibility(View.GONE);

        } else {
            btnSelectMode.setImageResource(R.drawable.ic_photo_library);
            // 退出选择模式：FAB 恢复为添加按钮，隐藏移动和重命名按钮
            addFab.setImageResource(R.drawable.add_24px);
            addFab.setOnClickListener(v -> launchFolderPicker());
            btnMove.setVisibility(View.GONE);
            btnEditName.setVisibility(View.GONE);

            btnSplash.setVisibility(View.VISIBLE);
            btnChangeSort.setVisibility(View.VISIBLE);

            clearSelections();
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
        DialogHelper.showDeleteConfirm(activity,
                "删除漫画",
                "确定删除选中的 " + selected.size() + " 本漫画？此操作不可撤销。",
                () -> deleteComics(selected));
    }

    private void deleteComics(List<Comic> comics) {
        singleThread.execute(() -> {
            for (Comic c : comics) {
                MyApplication.comicDatabase.comicDao().deleteComic(c);
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
        showMoveToCollectionDialog(selected);
    }

    private void showMoveToCollectionDialog(List<Comic> comics) {
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
                showMoveTargetDialog(comics, targets);
            });
        });
    }

    private void showMoveTargetDialog(List<Comic> comics, List<MoveTargetAdapter.MoveTarget> targets) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_move_to_collection, null);
        TextView titleView = dialogView.findViewById(R.id.move_dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.move_dialog_subtitle);
        RecyclerView listView = dialogView.findViewById(R.id.move_target_list);

        titleView.setText("移动 " + comics.size() + " 本到");
        subtitleView.setText("选择目标集合");
        listView.setLayoutManager(new LinearLayoutManager(activity));
        MoveTargetAdapter adapter = new MoveTargetAdapter(targets);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        adapter.setOnItemClickListener((target -> {
            moveComics(comics, target.id);
            dialog.dismiss();
        }));
        dialogView.findViewById(R.id.move_btn_cancel).setOnClickListener(v -> dialog.dismiss());
        // 创建新集合：创建后刷新目标列表
        dialogView.findViewById(R.id.move_btn_create).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateCollectionDialog(name -> createCollectionAndResumeMove(comics, name));
        });

        dialog.show();
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

    // ==================== 从本地导入漫画 ====================

    private void launchFolderPicker() {
        Intent intent = new Intent(activity, FolderPickerActivity.class);
        folderPickerLauncher.launch(intent);
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
            List<File> media = FileUtils.getMediaFilesRecursive(file);
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
        DialogHelper.showListInputDialog(activity,
                "重命名", "输入新的名称", "保存", list,
                this::renameCollections);
    }

    private void renameCollection(long comicId, String newName) {
        singleThread.execute(() -> {
            Comic comic = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (comic != null) {
                comic.setTitle(newName);
                MyApplication.comicDatabase.comicDao().updateComic(comic);
            }
            runOnUiIfAlive(() -> Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show());
        });
    }

    private void renameCollections(List<Comic> list) {
        singleThread.execute(() -> {
            for (Comic comic : list) {
                if (comic != null) {
                    MyApplication.comicDatabase.comicDao().updateComic(comic);
                }
            }

            runOnUiIfAlive(() -> Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show());
        });
    }

    // ==================== 工具方法 ====================

    private String sortName(int sort) {
        switch (sort) {
            case SORT_NAME:
                return "按名称排序";
            case SORT_LAST_READ:
                return "按最近阅读排序";
            case SORT_ADDED:
                return "按添加时间排序";
            case SORT_PROGRESS:
                return "按阅读进度排序";
            default:
                return "";
        }
    }

    /**
     * DiffUtil 回调：按 id 判定同一项，按可视字段判定内容是否变化。
     * Comic 未重写 equals，不能依赖 List.indexOf/contains。
     */
    private static class ComicDiffCallback extends DiffUtil.Callback {
        private final List<Comic> oldList;
        private final List<Comic> newList;

        ComicDiffCallback(List<Comic> oldList, List<Comic> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Comic a = oldList.get(oldItemPosition);
            Comic b = newList.get(newItemPosition);
            return a.getId() == b.getId()
                    && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getCoverPath(), b.getCoverPath())
                    && Objects.equals(a.getFileType(), b.getFileType())
                    && a.getReadProgress() == b.getReadProgress()
                    && a.getVideoPosition() == b.getVideoPosition()
                    && a.getTotal() == b.getTotal()
                    && a.getLastRead() == b.getLastRead()
                    && a.isSelected() == b.isSelected()
                    && a.isHorizontal() == b.isHorizontal();
        }
    }
}
