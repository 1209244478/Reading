package com.wrz.reading.ui.read.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.common.TvFocusHelper;
import com.wrz.reading.common.TvInputHelper;
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
import com.wrz.reading.ui.read.data.ComicSizeCalculator;
import com.wrz.reading.ui.read.data.VideoPlaylistStore;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FileType;
import com.wrz.reading.ui.read.model.ReadItem;
import com.wrz.reading.ui.read.transfer.LocalImportServer;
import com.wrz.reading.ui.read.utils.DiffCallBack;
import com.wrz.reading.ui.read.utils.FileSorter;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.utils.QRCodeUtil;
import com.wrz.reading.ui.read.view.dialog.ImportSourceDialog;
import com.wrz.reading.ui.read.view.dialog.MoveDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
 * - FAB：弹出导入方式选择，支持「本地导入」（文件夹选择）与「远程导入」
 *   （展示二维码，浏览器扫码后经局域网上传 zip / 视频等文件）
 * - 重命名集合（仅命名集合）
 */
public class CollectionDetailFragment extends BaseFragment {

    private long collectionId;

    private final List<Comic> allComics = new ArrayList<>();
    private static final List<Comic> displayComics = new ArrayList<>();

    /**
     * 加载代际号：体积统计已移至共享线程池，并发加载时以此丢弃过期的回调结果。
     */
    private final AtomicInteger loadGeneration = new AtomicInteger();

    /**
     * 视频连播状态源：注册到 VideoPlaylistStore 供 VideoPlayerActivity 读取，
     * 替代原先播放器跨组件直接访问 CollectionDetailFragment.displayComics 的静态耦合。
     */
    private final VideoPlaylistStore.PlaylistProvider collectionPlaylistProvider =
            new VideoPlaylistStore.PlaylistProvider() {
                @Nullable
                @Override
                public List<ReadItem> getSubFiles() {
                    return null;
                }

                @Nullable
                @Override
                public Comic getReaderComic() {
                    return null;
                }

                @Override
                public List<Comic> getDisplayComics() {
                    return displayComics;
                }

                @Override
                public void updateVideoPosition(long comicId, long position) {
                    for (Comic c : displayComics) {
                        if (c != null && c.getId() == comicId) {
                            c.setVideoPosition(position);
                            return;
                        }
                    }
                }
            };


    private RecyclerView comicRecyclerView;
    private ComicAdapter comicAdapter;
    private View emptyStateText;
    private View addFab;
    private ImageView addFabIcon;
    private TextView addFabLabel;
    private View btnSearch;

    /** 导入方式选择对话框（本地 / 远程） */
    private ImportSourceDialog importDialog;
    /** 远程导入的局域网接收服务 */
    private volatile LocalImportServer importServer;
    /**
     * 远程服务启动代际号：关闭对话框后立即递增，
     * 使仍在启动中的后台任务丢弃刚创建的服务，避免服务泄漏。
     */
    private final AtomicInteger remoteImportGeneration = new AtomicInteger();
    /** 本次远程导入已成功导入的文件数 */
    private int remoteImportedCount;

    private View btnSelectMode;
    private View btnEditName;
    private View searchLayout;
    private EditText searchEditText;

    /** 电视端专属标题/摘要/操作块 */
    private TextView tvDetailTitle;
    private TextView tvDetailSummary;
    private TextView tvSortChip;
    private View tvBtnMove;
    private View tvBtnShare;

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
        addFabIcon = findId(R.id.add_comic_fab_icon);
        addFabLabel = findId(R.id.add_comic_fab_label);
        btnSearch = findId(R.id.btn_search);


        btnSelectMode = findId(R.id.btn_select_mode);

        btnEditName = findId(R.id.btn_edit_name);
        searchLayout = findId(R.id.search_layout);
        searchEditText = findId(R.id.search_edit_text);

        tvDetailTitle = findId(R.id.tv_detail_title);
        tvDetailSummary = findId(R.id.tv_detail_summary);
        tvSortChip = findId(R.id.tv_btn_sort);
        tvBtnMove = findId(R.id.tv_btn_move);
        tvBtnShare = findId(R.id.tv_btn_share);
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

        if (tvDetailTitle != null) {
            tvDetailTitle.setText(collectionName.isEmpty()
                    ? getString(R.string.uncategorized) : collectionName);
        }

        // 设置 Toolbar 标题与返回按钮（手机/平板布局；电视端使用独立操作块）
        if (mCommonToolbar != null) {
            mCommonToolbar.setTitle(collectionName.isEmpty() ? getString(R.string.uncategorized) : collectionName);
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
        comicRecyclerView.setLayoutManager(new GridLayoutManager(activity, isTv() ? 6 : (MyApplication.isTablet || isLand()) ? 4 : 3));
        comicAdapter = new ComicAdapter(R.layout.item_comic, displayComics);
        comicRecyclerView.setAdapter(comicAdapter);

        comicRecyclerView.setHasFixedSize(true);

        // FAB：普通模式下选择导入方式（本地/远程），选择模式下变为删除按钮
        setClick(addFab, v -> {
            if (comicAdapter == null) return;
            if (comicAdapter.isSelectMode()) {
                onDeleteFabClick();
            } else {
                showImportDialog();
            }
        });

        setClick(btnSearch, v -> toggleSearch());

        setClick(btnSelectMode, v -> toggleSelectMode());

        setClick(btnEditName, v -> {
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

        setupTvActions();
        updateTvSummary();
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
        // 电视端从阅读器返回时，若焦点丢失则回到列表首项
        if (isTv() && getView() != null && getView().findFocus() == null) {
            TvFocusHelper.focusFirstIfNeeded(comicRecyclerView);
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
        // 关闭导入对话框并停止局域网接收服务，避免窗口/线程泄漏
        if (importDialog != null) {
            importDialog.dismiss();
            importDialog = null;
        }
        stopRemoteImport();
        VideoPlaylistStore.clear(collectionPlaylistProvider);
        mSplash.removeCallbacks(mSplashRunnable);
    }
// ==================== 数据加载 ====================

    private void loadComics() {
        loadComics(false);
    }

    /**
     * 加载当前集合下的漫画。
     *
     * @param useDiff true 时采用轻量差量刷新：仅查库比对进度等字段（从阅读器/播放器
     *                返回场景），不重新统计体积（沿用内存中的旧值），避免每次返回都做
     *                重磁盘 IO；false 时全量加载：统计体积并把集合总大小持久化到数据库，
     *                用于导入/删除/移动等显式操作。
     */
    private void loadComics(boolean useDiff) {
        final int gen = loadGeneration.incrementAndGet();
        // 只读查询与重磁盘 IO 均在共享线程池执行，不阻塞数据库单线程
        threadPool.execute(() -> {
            List<Comic> comics;
            if (collectionId == CollectionFragment.UNCATEGORIZED_ID) {
                comics = MyApplication.comicDatabase.comicDao().getUncategorizedComics();
            } else {
                comics = MyApplication.comicDatabase.comicDao().getComicsByCollection(collectionId);
            }

            // 后台预校验封面文件存在性（失效的置空），列表绑定时不再做磁盘 stat
            for (Comic c : comics) {
                String cover = c.getCoverPath();
                if (cover != null && !cover.isEmpty() && !new File(cover).exists()) {
                    c.setCoverPath("");
                }
            }

            if (!useDiff) {
                // 全量加载：统计体积（重磁盘 IO，含 fileSize/isDirectory 预计算）并持久化集合总大小
                long[] totalSize = ComicSizeCalculator.calculate(comics);
                if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                    updateCollectionSizeInDb(collectionId, totalSize[0], totalSize[1]);
                }
            }

            // 在后台线程构建临时列表，避免与主线程遍历 allComics 竞态
            List<Comic> tempList = new ArrayList<>(comics);

            runOnUiIfAlive(() -> {
                if (gen != loadGeneration.get()) {
                    return;
                }
                if (useDiff) {
                    // 轻量刷新：体积/目录字段沿用内存旧值（本次未重算）
                    Map<Long, Comic> oldById = new HashMap<>();
                    for (Comic old : allComics) {
                        oldById.put(old.getId(), old);
                    }
                    for (Comic c : tempList) {
                        Comic old = oldById.get(c.getId());
                        if (old != null) {
                            c.setCacheSize(old.getCacheSize());
                            c.setFileSize(old.getFileSize());
                            c.setDirectory(old.isDirectory());
                        }
                    }
                    applyComicsDiff(tempList);
                } else {
                    allComics.clear();
                    allComics.addAll(tempList);
                    applySortAndFilter();
                    updateEmptyState();
                }
                if (isTv()) {
                    TvFocusHelper.focusFirstIfNeeded(comicRecyclerView);
                }
            });
        });
    }

    /**
     * 将指定集合的缓存/文件总大小写入数据库（后台执行）。
     * 仅在移动漫画到其它集合后调用（源集合由全量加载自行统计持久化），
     * 体积统计（重磁盘 IO）在共享线程池执行，避免阻塞数据库单线程。
     */
    private void persistCollectionSize(long targetCollectionId) {
        if (targetCollectionId == CollectionFragment.UNCATEGORIZED_ID) {
            return; // 未分组没有集合记录，无需持久化
        }
        threadPool.execute(() -> {
            List<Comic> comics = MyApplication.comicDatabase.comicDao()
                    .getComicsByCollection(targetCollectionId);
            long[] totalSize = ComicSizeCalculator.calculate(comics);
            updateCollectionSizeInDb(targetCollectionId, totalSize[0], totalSize[1]);
        });
    }

    private static void updateCollectionSizeInDb(long collectionId, long cacheSize, long fileSize) {
        Collection collection = MyApplication.comicDatabase.collectionDao().getCollectionById(collectionId);
        if (collection != null) {
            collection.setCacheSize(cacheSize);
            collection.setFileSize(fileSize);
            MyApplication.comicDatabase.collectionDao().updateCollection(collection);
        }
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
            // 电视端空列表时把焦点交给导入按钮，避免遥控器焦点悬空
            if (isTv() && addFab != null && parentView != null
                    && parentView.findFocus() == null) {
                addFab.requestFocus();
            }
        } else {
            comicRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
        }
        updateTvSummary();
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
     * 使用应用内 ExoPlayer 播放视频文件。
     * 文件存在性检查与目录内视频查找（递归磁盘 IO）在后台线程执行，避免点击时卡顿。
     */
    private void openVideoWithExternalPlayer(Comic comic) {
        threadPool.execute(() -> {
            File file = new File(comic.getPath());
            if (!file.exists()) {
                runOnUiIfAlive(() -> Toast.makeText(activity, getString(R.string.msg_video_not_found), Toast.LENGTH_SHORT).show());
                return;
            }

            // 如果 path 是目录（压缩包场景），从中查找第一个视频文件
            String videoPath = comic.getPath();
            if (file.isDirectory()) {
                List<File> videos = FileUtils.getVideoFiles(file);
                if (videos.isEmpty()) {
                    runOnUiIfAlive(() -> Toast.makeText(activity, getString(R.string.msg_dir_no_video), Toast.LENGTH_SHORT).show());
                    return;
                }
                videoPath = videos.get(0).getAbsolutePath();
            }

            final String path = videoPath;
            runOnUiIfAlive(() -> {
                // 注册连播状态源后再启动播放器，保证播放器 onCreate 时可读取
                VideoPlaylistStore.set(collectionPlaylistProvider);
                VideoPlayerActivity.start(activity, comic.getId(),
                        path, comic.getTitle(),
                        false, 1000);
            });
        });
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
            // 电视端输入框由外层容器接收遥控器焦点，OK 后才弹出软键盘
            if (isTv()) {
                searchLayout.requestFocus();
            } else {
                searchEditText.requestFocus();
            }
        }
    }

    // ==================== 选择模式 ====================

    @SuppressLint("NotifyDataSetChanged")
    private void toggleSelectMode() {
        comicAdapter.toggleSelectMode();
        if (comicAdapter.isSelectMode()) {
            // 选择模式下：FAB 变为删除按钮，toolbar 显示移动按钮
            btnEditName.setVisibility(View.VISIBLE);
            syncSortMenuCheckState();
        } else {
            // 退出选择模式：FAB 恢复为添加按钮，隐藏移动和重命名按钮
            btnEditName.setVisibility(View.GONE);
            clearSelections();
            syncSortMenuCheckState();
        }
        updateSelectModeIcons(comicAdapter.isSelectMode());
        updateTvActionVisibility();
        comicAdapter.notifyDataSetChanged();
    }

    /**
     * 电视端操作块显示文字，手机端使用图标：统一刷新选择模式下的外观。
     */
    private void updateSelectModeIcons(boolean selectMode) {
        if (btnSelectMode instanceof ImageButton) {
            ((ImageButton) btnSelectMode).setImageResource(
                    selectMode ? R.drawable.ic_close : R.drawable.ic_photo_library);
        } else if (btnSelectMode instanceof TextView) {
            ((TextView) btnSelectMode).setText(selectMode
                    ? R.string.tv_action_exit_select : R.string.tv_action_select);
        }

        if (addFab instanceof FloatingActionButton) {
            ((FloatingActionButton) addFab).setImageResource(
                    selectMode ? R.drawable.delete_24px : R.drawable.add_24px);
        } else {
            if (addFabIcon != null) {
                addFabIcon.setImageResource(selectMode
                        ? R.drawable.delete_24px : R.drawable.add_24px);
            }
            if (addFabLabel != null) {
                addFabLabel.setText(selectMode
                        ? R.string.tv_action_delete_selected : R.string.tv_action_import);
            }
        }
    }

    /**
     * 电视端选择模式下才显示“移动/分享”，避免默认状态下无关操作干扰焦点移动。
     */
    private void updateTvActionVisibility() {
        if (!isTv()) {
            return;
        }
        boolean selectMode = comicAdapter != null && comicAdapter.isSelectMode();
        if (tvBtnMove != null) {
            tvBtnMove.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        }
        if (tvBtnShare != null) {
            tvBtnShare.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        }
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
            Toast.makeText(activity, getString(R.string.msg_select_comics_first), Toast.LENGTH_SHORT).show();
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
            // 源集合总大小由随后的全量加载（loadComics→calculate）统计并持久化，无需重复遍历
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, getString(R.string.msg_deleted_comics, comics.size()), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(activity, getString(R.string.msg_select_comics_first), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(activity, getString(R.string.msg_no_move_target), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(activity, getString(R.string.msg_collection_created, name), Toast.LENGTH_SHORT).show();
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
            // 源集合与目标集合的大小都发生变化：源集合由全量加载统计持久化，
            // 目标集合不在当前页展示，需单独持久化（未分组无记录则跳过）
            persistCollectionSize(targetCollectionId);
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, getString(R.string.msg_moved_comics, ids.size()), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(activity, getString(R.string.msg_no_shareable_file), Toast.LENGTH_SHORT).show();
            return;
        }
        FileUtils.shareFiles(activity, shareableFiles);
    }


    // ==================== 导入方式选择（本地 / 远程） ====================

    /**
     * FAB 点击：弹出导入方式选择对话框。
     * 本地导入沿用原有的文件夹选择流程；远程导入展示局域网二维码供浏览器扫码上传。
     */
    private void showImportDialog() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (importDialog != null && importDialog.isShowing()) {
            return;
        }
        remoteImportedCount = 0;
        importDialog = new ImportSourceDialog(activity);
        importDialog.setListener(new ImportSourceDialog.Listener() {
            @Override
            public void onLocalImport() {
                launchFolderPicker();
            }

            @Override
            public void onRemoteImport() {
                startRemoteImport();
            }

            @Override
            public void onStopRemoteImport() {
                stopRemoteImport();
            }

            @Override
            public void onDismissed() {
                importDialog = null;
            }
        });
        importDialog.show();
    }

    /**
     * 启动远程导入：在后台创建接收目录、获取局域网 IP、启动 HTTP 服务并生成二维码。
     * 服务启动可能耗时（端口重试），因此全部在共享线程池执行，完成后切回主线程展示。
     */
    private void startRemoteImport() {
        final ImportSourceDialog dialog = importDialog;
        if (dialog == null) {
            return;
        }
        remoteImportedCount = 0;
        final int generation = remoteImportGeneration.incrementAndGet();
        threadPool.execute(() -> {
            File destDir = resolveRemoteImportDir();
            if (destDir == null) {
                runOnUiIfAlive(() -> {
                    if (importDialog == dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_dir_failed));
                    }
                });
                return;
            }

            String ip = LocalImportServer.findLanIpAddress();
            if (ip == null || ip.isEmpty()) {
                runOnUiIfAlive(() -> {
                    if (importDialog == dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_no_wifi));
                    }
                });
                return;
            }

            // token 防止同网段其他设备随意访问上传页
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            LocalImportServer server = LocalImportServer.startServer(
                    MyApplication.app, token, destDir, createImportServerListener());
            if (server == null) {
                runOnUiIfAlive(() -> {
                    if (importDialog == dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_server_failed));
                    }
                });
                return;
            }
            // 先登记再校验代际号：若启动期间用户已关闭对话框，随后一定能把该服务停掉
            importServer = server;
            if (generation != remoteImportGeneration.get()) {
                if (importServer == server) {
                    importServer = null;
                }
                stopServerAsync(server);
                return;
            }

            final String url = "http://" + ip + ":" + server.getListeningPort()
                    + "/t/" + token + "/";
            final Bitmap qrBitmap = QRCodeUtil.generate(url, 640);
            runOnUiIfAlive(() -> {
                if (importDialog == dialog && generation == remoteImportGeneration.get()) {
                    dialog.showRemoteInfo(url, qrBitmap);
                }
            });
        });
    }

    /**
     * 停止远程接收服务（幂等）。stop 会 join 监听线程，放到独立线程执行避免卡主线程。
     */
    private void stopRemoteImport() {
        remoteImportGeneration.incrementAndGet();
        LocalImportServer server = importServer;
        importServer = null;
        if (server != null) {
            stopServerAsync(server);
        }
    }

    private void stopServerAsync(LocalImportServer server) {
        new Thread(() -> {
            try {
                server.stop();
            } catch (Exception e) {
                LogUtil.e("CollectionDetailFragment", "stop import server: " + e.getMessage());
            }
        }, "LocalImportServerStop").start();
    }

    /**
     * 远程接收目录：优先 Download/漫画（用户可见），不可写时回退到应用外部私有目录。
     */
    private File resolveRemoteImportDir() {
        File publicDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "漫画");
        if (isDirWritable(publicDir)) {
            return publicDir;
        }
        Context context = MyApplication.app;
        if (context == null) {
            return null;
        }
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        if (base == null) {
            return null;
        }
        File fallback = new File(base, "import");
        return isDirWritable(fallback) ? fallback : null;
    }

    /**
     * 目录可写性探测：mkdirs 成功不代表可写（如无全盘访问权限时公共目录已存在），
     * 故实际创建并删除一个探针文件。
     */
    private boolean isDirWritable(File dir) {
        if (dir == null) {
            return false;
        }
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".write_probe_" + System.currentTimeMillis());
            try (FileOutputStream ignored = new FileOutputStream(probe)) {
                return true;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                probe.delete();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private LocalImportServer.Listener createImportServerListener() {
        return new LocalImportServer.Listener() {
            @Override
            public void onUploadStarted(String name, long totalBytes) {
                runOnUiIfAlive(() -> {
                    if (importDialog != null) {
                        importDialog.showReceiving(name, 0);
                    }
                });
            }

            @Override
            public void onUploadProgress(String name, long received, long totalBytes) {
                final int percent = totalBytes > 0
                        ? (int) (received * 100 / totalBytes) : -1;
                runOnUiIfAlive(() -> {
                    if (importDialog != null) {
                        importDialog.showReceiving(name, percent);
                    }
                });
            }

            @Override
            public void onFileSaved(File file) {
                runOnUiIfAlive(() -> {
                    if (importDialog != null) {
                        importDialog.showReceived(file.getName());
                    }
                });
                // 导入不依赖界面存活，避免用户切走页面后文件未被加入书架
                importRemoteFile(file);
            }

            @Override
            public void onUploadFailed(String name, String message) {
                runOnUiIfAlive(() -> {
                    if (importDialog != null) {
                        importDialog.showUploadFailed(name, message);
                    }
                });
            }
        };
    }

    /**
     * 把浏览器上传完成的文件导入当前集合（单线程串行执行，避免视频缩略图生成并发抢占资源）。
     */
    private void importRemoteFile(File file) {
        singleThread.execute(() -> {
            Comic comic = null;
            String failReason = "";
            try {
                comic = Comic.buildComicFromFile(file);
            } catch (Exception e) {
                failReason = e.getMessage() == null ? "解析失败" : e.getMessage();
            }
            boolean imported = false;
            if (comic != null) {
                if (Comic.isComicExists(comic.getOriginalPath())) {
                    failReason = "文件已导入过";
                } else {
                    if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                        comic.setCollectionId(collectionId);
                    }
                    MyApplication.comicDatabase.comicDao().insertComic(comic);
                    imported = true;
                }
            } else if (failReason.isEmpty()) {
                failReason = "不支持的文件格式";
            }

            final boolean success = imported;
            final String reason = failReason;
            runOnUiIfAlive(() -> {
                if (success) {
                    remoteImportedCount++;
                    if (importDialog != null) {
                        importDialog.showImported(file.getName(), remoteImportedCount);
                    }
                    loadComics();
                } else {
                    if (importDialog != null) {
                        importDialog.showImportFailed(file.getName(), reason);
                    }
                    Toast.makeText(activity,
                            getString(R.string.import_remote_import_failed, file.getName())
                                    + "：" + reason, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ==================== 从本地导入漫画 ====================

    private void launchFolderPicker() {
        if (MyApplication.checkPermissions(activity)) {
            /*if (isStorageDirectReadable()) {*/
            Intent intent = new Intent(activity, FolderPickerActivity.class);
            folderPickerLauncher.launch(intent);
            /*} else {
                // 授权后仍读取不到文件（如鸿蒙限制），改用系统文件夹选择器
                safFolderPickerLauncher.launch(null);
            }*/
        } else {
            MyApplication.requestPermissions(activity);
        }
    }

    // ==================== SAF 导入（受限机型兜底） ====================

    /**
     * 处理系统文件夹选择器返回的目录树：
     * 1. 优先把 tree URI 解析为真实路径（支持主存储及 SD 卡/OTG 等全部外置卷），
     * 可读则直接导入（避免复制大文件，视频导入尤其关键）；
     * 2. 仅当解析失败（第三方 DocumentsProvider、云盘等真无真实路径）时，
     * 才把目录内容复制到应用私有目录再导入。
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
                    Toast.makeText(activity, getString(R.string.msg_cannot_read_folder), Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> paths = collectSafImportPaths(importDir);
                if (paths.isEmpty()) {
                    Toast.makeText(activity, getString(R.string.msg_folder_no_import_content), Toast.LENGTH_SHORT).show();
                    return;
                }
                importComicsFromFolders(paths);
            });
        });
    }

    /**
     * 把 SAF tree URI 解析为真实路径并确认 File API 可读，按优先级依次尝试：
     * 1. raw:&lt;绝对路径&gt;（DownloadStorageProvider 的 raw 格式）；
     * 2. &lt;卷名&gt;:&lt;路径&gt;（主存储 primary 与 SD 卡/OTG 等 UUID 卷）；
     * 3. msd:&lt;id&gt;（系统"下载"根目录树，MediaStore 行 ID）→ 查 MediaStore 得真实路径。
     * 仅以上全部失败（第三方 DocumentsProvider、云盘等）才回退到复制。
     */
    private File resolveSafTreeDir(Uri treeUri) {
        try {
            LogUtil.e("CollectionDetailFragment", "resolveSafTreeDir treeUri: " + treeUri);
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            LogUtil.e("CollectionDetailFragment", "resolveSafTreeDir docId: " + docId);
            // 1) raw:<绝对路径>
            if (docId.startsWith("raw:")) {
                File rawDir = new File(docId.substring(4));
                if (rawDir.isDirectory() && rawDir.listFiles() != null) return rawDir;
                return null;
            }
            // 2) <卷名>:<路径>
            String[] split = docId.split(":", 2);
            String subPath = split.length > 1 ? split[1] : "";
            File root = resolveVolumeRoot(split[0]);
            if (root != null) {
                File dir = subPath.isEmpty() ? root : new File(root, subPath);
                if (dir.isDirectory() && dir.listFiles() != null) return dir;
            }
            // 3) msd:<id>（系统"下载"目录树）→ 先查 MediaStore，失败再按名称在 Download 下匹配
            if ("msd".equalsIgnoreCase(split[0])) {
                File mediaStoreDir = resolveMediaStoreDir(docId);
                if (mediaStoreDir != null) return mediaStoreDir;
                return resolveDownloadsDirByName(treeUri, docId);
            }
            return null;
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "resolveSafTreeDir failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * 解析 msd:&lt;id&gt; 型文档 ID（用户经系统选择器选择"下载"根目录下的文件夹时，
     * DownloadStorageProvider 生成 MediaStore 行 ID）：
     * 查 MediaStore files 表该行的 DATA 列获得真实路径（目录行也有 DATA），
     * File API 可读则返回。注意：未授予"所有文件访问"时 MediaProvider 会过滤
     * 目录行导致查询为空，此路不通时由 {@link #resolveDownloadsDirByName} 兜底。
     */
    private File resolveMediaStoreDir(String docId) {
        int idx = docId.indexOf(':');
        if (idx <= 0 || !"msd".equalsIgnoreCase(docId.substring(0, idx))) {
            return null;
        }
        Context context = activity.getApplicationContext();
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[]{MediaStore.MediaColumns.DATA},
                MediaStore.MediaColumns._ID + "=?",
                new String[]{docId.substring(idx + 1)}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String data = cursor.getString(0);
                LogUtil.e("CollectionDetailFragment", "resolveMediaStoreDir data: " + data);
                if (data != null) {
                    File dir = new File(data);
                    if (dir.isDirectory() && dir.listFiles() != null) return dir;
                }
            }
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "resolveMediaStoreDir failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * msd 树兜底解析：SAF 树授权允许查询文档显示名，而下载 provider 的文档
     * 必然位于主存储 Download/ 目录下——按显示名在真实 Download 目录里定位。
     * 仅用 File API + 一次 SAF 名称查询，不依赖 MediaStore 可见性。
     */
    private File resolveDownloadsDirByName(Uri treeUri, String docId) {
        try {
            Context context = activity.getApplicationContext();
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
            String name = queryDocumentName(context, docUri);
            LogUtil.e("CollectionDetailFragment", "resolveDownloadsDirByName name: " + name);
            if (name == null || name.isEmpty()) {
                return null;
            }
            File downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.isDirectory()) {
                return null;
            }
            // 1) Download/<显示名> 直接匹配
            File direct = new File(downloadDir, name);
            if (direct.isDirectory() && direct.listFiles() != null) {
                return direct;
            }
            // 2) 选的就是 Download 根目录本身（华为等 ROM 的 provider 显示名可能被本地化为"下载"）
            if (name.equalsIgnoreCase(downloadDir.getName())
                    || "Downloads".equalsIgnoreCase(name) || "下载".equals(name) || "downloads".equals(name)) {
                return downloadDir;
            }
            // 3) 更深层级的同名目录（msd 可指向 Download 下任意层级）
            File found = findDirByName(downloadDir, name, 0);
            if (found != null) {
                LogUtil.e("CollectionDetailFragment", "resolveDownloadsDirByName found: " + found);
                return found;
            }
        } catch (Exception e) {
            LogUtil.e("CollectionDetailFragment", "resolveDownloadsDirByName failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * 在目录子树中查找名称完全匹配的可读目录（限制深度与遍历范围，避免大目录卡顿）
     */
    private File findDirByName(File dir, String name, int depth) {
        if (depth > 3) {
            return null;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (name.equals(child.getName()) && child.listFiles() != null) {
                return child;
            }
        }
        for (File child : children) {
            if (child.getName().startsWith(".")) {
                continue;
            }
            File found = findDirByName(child, name, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 卷名 → 挂载根目录。primary 为主存储；其余卷（UUID）通过
     * getExternalFilesDirs 返回的 /storage/&lt;uuid&gt;/Android/data/&lt;pkg&gt;/files
     * 向上回溯 4 级反推挂载点后匹配。匹配不到返回 null（走复制回退）。
     */
    private File resolveVolumeRoot(String volume) {
        if (volume.isEmpty() || "primary".equalsIgnoreCase(volume)) {
            return Environment.getExternalStorageDirectory();
        }
        Context context = activity.getApplicationContext();
        for (File filesDir : context.getExternalFilesDirs(null)) {
            if (filesDir == null) continue;
            File vol = filesDir;
            // files → pkg → data → Android → /storage/<uuid>
            for (int i = 0; i < 4 && vol != null; i++) {
                vol = vol.getParentFile();
            }
            if (vol != null && volume.equalsIgnoreCase(vol.getName()) && vol.isDirectory()) {
                return vol;
            }
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
            // 大文件（视频）复制：256KB 缓冲显著优于默认 8KB
            byte[] buffer = new byte[256 * 1024];
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

                Comic comic = Comic.buildComicFromFile(file);
                if (comic != null && !Comic.isComicExists(comic.getOriginalPath())) {
                    // 命名集合页导入的漫画直接归属当前集合
                    if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                        comic.setCollectionId(collectionId);
                    }
                    MyApplication.comicDatabase.comicDao().insertComic(comic);
                    imported++;
                }
            }
            // 当前集合总大小由随后的全量加载（loadComics→calculate）统计并持久化，无需重复遍历
            final int count = imported;
            runOnUiIfAlive(() -> {
                dismissLoading();
                Toast.makeText(activity, getString(R.string.msg_imported_comics, count), Toast.LENGTH_SHORT).show();
                loadComics();
            });
        });
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
                Toast.makeText(activity, getString(R.string.msg_renamed), Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ==================== 排序搜索功能 ====================

    /**
     * 重建显示列表并差量刷新。
     * 搜索输入/排序切换在主线程高频触发，用 DiffUtil 仅更新变化的项，
     * 避免 notifyDataSetChanged 整表重绑造成的卡顿与闪烁。
     */
    private void applySortAndFilter() {
        List<Comic> oldList = new ArrayList<>(displayComics);
        rebuildDisplayList();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new DiffCallBack.ComicDiffCallback(oldList, displayComics), false);
        diff.dispatchUpdatesTo(comicAdapter);
        updateTvSummary();
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

        // 目录始终排在最前面的比较器。
        // 使用加载时后台预计算的 isDirectory/fileSize 字段，
        // 排序过程（O(N log N) 次比较）完全无磁盘 IO，避免主线程掉帧
        Comparator<Comic> dirFirst = (a, b) -> Boolean.compare(!a.isDirectory(), !b.isDirectory());

        switch (currentSort) {
            case Collection.SORT_BY_NAME:
                FileSorter.sortComics(dirFirst, displayComics, false);

                break;
            case Collection.SORT_BY_NAME_REVERSE:
                FileSorter.sortComics(dirFirst, displayComics, true);

                break;
            case Collection.SORT_BY_SIZE:
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(b.getFileSize(), a.getFileSize())));

                break;
            case Collection.SORT_BY_PROGRESS:
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(b.getReadProgress(), a.getReadProgress())));
                break;
            case Collection.SORT_BY_LAST_READ_TIME:
                displayComics.sort(dirFirst.thenComparing((a, b) -> Long.compare(b.getLastRead(), a.getLastRead())));
                break;
        }
    }

    // ==================== 电视端操作块 ====================

    /**
     * 电视端没有 Toolbar 溢出菜单：把排序、刷新、移动、分享、返回等动作
     * 直接绑定到独立可聚焦操作块上。
     */
    private void setupTvActions() {
        if (!isTv()) {
            return;
        }
        setClick(findId(R.id.tv_btn_back), v -> {
            if (navController != null) {
                navController.navigateUp();
            }
        });
        setClick(findId(R.id.tv_btn_refresh), v -> loadComics());
        setClick(tvSortChip, v -> cycleTvSort());
        setClick(tvBtnMove, v -> onMoveClick());
        setClick(tvBtnShare, v -> onShareClick());

        // 电视遥控器无法直接弹出软键盘：交给 TvInputHelper 处理 OK 键唤起输入法
        TvInputHelper.bind(searchEditText);
        updateTvActionVisibility();
    }

    /**
     * 电视端排序块循环切换：名称 ↑ → 名称 ↓ → 体积 → 进度 → 最近阅读。
     */
    private void cycleTvSort() {
        switch (currentSort) {
            case Collection.SORT_BY_NAME:
                currentSort = Collection.SORT_BY_NAME_REVERSE;
                break;
            case Collection.SORT_BY_NAME_REVERSE:
                currentSort = Collection.SORT_BY_SIZE;
                break;
            case Collection.SORT_BY_SIZE:
                currentSort = Collection.SORT_BY_PROGRESS;
                break;
            case Collection.SORT_BY_PROGRESS:
                currentSort = Collection.SORT_BY_LAST_READ_TIME;
                break;
            default:
                currentSort = Collection.SORT_BY_NAME;
                break;
        }
        applySortAndFilter();
        syncSortMenuCheckState();
    }

    private String tvSortLabel(int sort) {
        switch (sort) {
            case Collection.SORT_BY_NAME_REVERSE:
                return getString(R.string.tv_sort_name_desc);
            case Collection.SORT_BY_SIZE:
                return getString(R.string.tv_sort_size);
            case Collection.SORT_BY_PROGRESS:
                return getString(R.string.tv_sort_progress);
            case Collection.SORT_BY_LAST_READ_TIME:
                return getString(R.string.tv_sort_last_read);
            case Collection.SORT_BY_NAME:
            default:
                return getString(R.string.tv_sort_name_asc);
        }
    }

    /**
     * 刷新电视端标题下方的摘要（漫画数 + 当前排序），并同步排序块文字。
     */
    private void updateTvSummary() {
        if (!isTv() || tvDetailSummary == null) {
            return;
        }
        int count = comicAdapter != null ? comicAdapter.getItemCount() : displayComics.size();
        String sortLabel = tvSortLabel(currentSort);
        tvDetailSummary.setText(getString(R.string.tv_summary_format, count, sortLabel));
        if (tvSortChip != null) {
            tvSortChip.setText(getString(R.string.tv_sort_format, sortLabel));
        }
    }

    // ==================== 排序菜单 ====================

    /**
     * 将溢出菜单中排序项的勾选状态与 currentSort 同步。
     */
    private void syncSortMenuCheckState() {
        updateTvSummary();
        updateTvActionVisibility();
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
