package com.wrz.reading.ui.read.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.adapter.CollectionAdapter;
import com.wrz.reading.ui.read.data.ComicSizeCalculator;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.CollectionItem;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.utils.DiffCallBack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 书库首页：展示漫画集合列表。点击集合进入详情页查看集合内漫画。
 * 未分组的漫画通过「未分组」入口查看。
 */
public class CollectionFragment extends BaseFragment {

    /**
     * 未分组集合的特殊 ID
     */
    public static final long UNCATEGORIZED_ID = -1L;
    public static final String ARG_COLLECTION_ID = "collectionId";
    public static final String ARG_COLLECTION_NAME = "collectionName";

    private final ArrayList<CollectionItem> collectionItems = new ArrayList<>();

    /**
     * 最近一次「未分组」体积统计结果（缓存目录总大小）。
     * 进入页面先展示该旧值让列表立即可见，后台统计完成后修正。
     */
    private volatile long lastUncategorizedCacheSize = 0;

    /**
     * 加载代际号：体积统计已移至共享线程池，并发加载时以此丢弃过期的回调结果。
     */
    private final AtomicInteger loadGeneration = new AtomicInteger();

    private RecyclerView collectionRecyclerView;
    private CollectionAdapter collectionAdapter;
    private View emptyStateText;
    private View addFab;
    private ImageView addFabIcon;
    private TextView addFabLabel;
    private View btnSelectMode;
    private View btnEditName;
    private View btnSplash;
    private NavController navController;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_collection;
    }

    @Override
    public void initView() {
        collectionRecyclerView = findId(R.id.collection_recycler_view);
        emptyStateText = findId(R.id.empty_state_text);
        addFab = findId(R.id.add_comic_fab);
        addFabIcon = findId(R.id.add_comic_fab_icon);
        addFabLabel = findId(R.id.add_comic_fab_label);
        btnSelectMode = findId(R.id.btn_select_mode);
        btnSplash = findId(R.id.btn_splash);
        btnEditName = findId(R.id.btn_edit_name);
    }

    @Override
    public void initData() {
        navController = NavHostFragment.findNavController(this);
        loadCollections(false);
        hideEmptyState();
    }

    @Override
    public void configViews() {
        collectionRecyclerView.setLayoutManager(new GridLayoutManager(activity, isTv() ? 5 : (MyApplication.isTablet || isLand()) ? 4 : 3));
        collectionAdapter = new CollectionAdapter(collectionItems);
        collectionRecyclerView.setAdapter(collectionAdapter);
        collectionRecyclerView.setHasFixedSize(true);

        setClick(btnSplash,v -> loadCollections(true));

        setClick(addFab, v -> {
            if (collectionAdapter == null) return;
            if (collectionAdapter.isSelectMode()) {
                showDeleteCollectionsDialog();
            } else {
                showCreateCollectionDialog();
            }
        });

        setClick(btnSelectMode, v -> toggleSelectMode());

        setClick(R.id.btn_setting, v -> {
            navController.navigate(R.id.action_main_to_settingfragment);
        });

        setClick(btnEditName, v -> {
            List<CollectionItem> collections = new ArrayList<>();
            for (CollectionItem collection : collectionItems) {
                if (collection.getCollection().isSelected() && collection.getCollection().getId() != UNCATEGORIZED_ID) {
                    collections.add(collection);
                }
            }

            if (!collections.isEmpty()) {
                DialogHelper.showRenameCollectionsDialog(activity,
                        "重命名合集", "输入新的合集名称", "保存", collections,
                        this::renameCollections);
            }
        });

        collectionAdapter.setOnItemClickListener((adapter, view, position) -> {
            CollectionItem item = collectionItems.get(position);
            if (collectionAdapter.isSelectMode()) {
                item.getCollection().setSelected(!item.getCollection().isSelected());
                collectionAdapter.notifyItemChanged(position);
            } else {
                navigateToDetail(item);
            }
        });

        collectionAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            if (!collectionAdapter.isSelectMode()) {
                toggleSelectMode();
            }
            CollectionItem item = collectionItems.get(position);
            item.getCollection().setSelected(true);
            collectionAdapter.notifyItemChanged(position);
            return true;
        });
    }

    private void renameCollections(List<CollectionItem> collections) {
        singleThread.execute(() -> {
            for (CollectionItem collectionItem : collections) {
                if (collectionItem != null) {
                    MyApplication.comicDatabase.collectionDao().updateCollection(collectionItem.getCollection());
                }
            }

            runOnUiIfAlive(() -> {
                loadCollections(true);
                Toast.makeText(activity, getString(R.string.msg_renamed), Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void goBack() {
        if (collectionAdapter.isSelectMode()) {
            toggleSelectMode();
        } else {
            this.getSupportActivity().finish();
        }
    }

    private void navigateToDetail(CollectionItem item) {
        Bundle args = new Bundle();
        args.putLong(ARG_COLLECTION_ID, item.getCollection().getId());
        args.putString(ARG_COLLECTION_NAME, item.getCollection().getName());
        navController.navigate(R.id.action_main_to_collectionDetail, args);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadCollections(boolean useDiff) {
        final int gen = loadGeneration.incrementAndGet();
        // 只读查询与重磁盘 IO 均在共享线程池执行，不阻塞数据库单线程
        threadPool.execute(() -> {
            // 阶段一：仅查库构建列表并立即渲染，进页不再等待体积统计
            List<Collection> collections = MyApplication.comicDatabase.collectionDao().getAllCollections();
            List<Comic> comics = MyApplication.comicDatabase.comicDao().getUncategorizedComics();
            int uncategorizedCount = comics.size();

            // 在后台线程构建临时列表，避免与主线程 Adapter 访问竞态
            ArrayList<CollectionItem> tempList = new ArrayList<>();
            // 未分组入口始终置顶；体积先沿用上次统计值，阶段二修正
            Collection uncategorized = new Collection();
            uncategorized.setId(UNCATEGORIZED_ID);
            uncategorized.setName("未分组");
            uncategorized.setCacheSize(lastUncategorizedCacheSize);
            tempList.add(new CollectionItem(uncategorized, uncategorizedCount));

            for (Collection c : collections) {
                int count = MyApplication.comicDatabase.collectionDao().getComicCountInCollection(c.getId());
                tempList.add(new CollectionItem(c, count));
            }

            runOnUiIfAlive(() -> {
                if (gen != loadGeneration.get()) {
                    return;
                }
                if (useDiff) {
                    List<CollectionItem> oldList = new ArrayList<>(collectionItems);

                    collectionItems.clear();
                    collectionItems.addAll(tempList);

                    // 计算差量并分发到 adapter，保留滚动位置
                    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                            new DiffCallBack.CollectionItemDiffCallback(oldList, tempList), false);
                    diff.dispatchUpdatesTo(collectionAdapter);

                } else {
                    collectionItems.clear();
                    collectionItems.addAll(tempList);
                    collectionAdapter.notifyDataSetChanged();
                }

                if (isTv()) {
                    TvFocusHelper.focusFirstIfNeeded(collectionRecyclerView);
                }
            });

            // 阶段二：统计未分组体积（递归遍历解压目录，重磁盘 IO），完成后仅刷新「未分组」项
            ComicSizeCalculator.calculate(comics);
            long totalSize = 0;
            for (Comic c : comics) {
                totalSize += c.getCacheSize();
            }
            lastUncategorizedCacheSize = totalSize;
            final long freshTotalSize = totalSize;
            runOnUiIfAlive(() -> {
                if (gen != loadGeneration.get()) {
                    return;
                }
                for (int i = 0; i < collectionItems.size(); i++) {
                    CollectionItem item = collectionItems.get(i);
                    if (item.getCollection().getId() == UNCATEGORIZED_ID
                            && item.getCollection().getCacheSize() != freshTotalSize) {
                        item.getCollection().setCacheSize(freshTotalSize);
                        collectionAdapter.notifyItemChanged(i);
                        break;
                    }
                }
            });
        });
    }

    private void showCreateCollectionDialog() {
        DialogHelper.showTextInputDialog(activity, null, null, null, null,
                this::createCollection);
    }

    private void createCollection(String name) {
        singleThread.execute(() -> {
            MyApplication.comicDatabase.collectionDao().insertCollection(Collection.create(name));
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, getString(R.string.msg_collection_created, name), Toast.LENGTH_SHORT).show();
                loadCollections(true);
            });
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void toggleSelectMode() {
        collectionAdapter.toggleSelectMode();
        if (collectionAdapter.isSelectMode()) {
            btnEditName.setVisibility(View.VISIBLE);
            updateSelectModeIcons(true);
        } else {
            btnEditName.setVisibility(View.GONE);
            clearSelections();
            updateSelectModeIcons(false);
        }
        collectionAdapter.notifyDataSetChanged();
    }

    /**
     * 电视端操作块使用文字标签，手机端仍使用图标按钮：统一在这里切换外观。
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
                        ? R.string.tv_action_delete_selected : R.string.tv_action_new_collection);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 电视端从详情页返回时，若焦点因视图重建丢失，则回到列表首项
        if (isTv() && getView() != null && getView().findFocus() == null) {
            TvFocusHelper.focusFirstIfNeeded(collectionRecyclerView);
        }
    }

    private void clearSelections() {
        for (CollectionItem item : collectionItems) {
            item.getCollection().setSelected(false);
        }
    }

    private void showDeleteCollectionsDialog() {
        List<Collection> selected = new ArrayList<>();
        for (CollectionItem item : collectionItems) {
            if (item.getCollection().isSelected() && item.getCollection().getId() != UNCATEGORIZED_ID) {
                selected.add(item.getCollection());
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(activity, getString(R.string.msg_select_delete_collection), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.title_delete_collection))
                .setMessage(getString(R.string.msg_delete_collections_confirm, selected.size()))
                .setPositiveButton(getString(R.string.btn_delete_collection_and_comics), (dialog, which) -> deleteCollections(selected, true))
                .setNegativeButton(getString(R.string.btn_delete_collection_only), (dialog, which) -> deleteCollections(selected, false))
                .setNeutralButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void deleteCollections(List<Collection> collections, boolean deleteComics) {
        singleThread.execute(() -> {
            for (Collection c : collections) {
                if (deleteComics) {
                    MyApplication.comicDatabase.comicDao().deleteComicsInCollection(c.getId());
                } else {
                    MyApplication.comicDatabase.comicDao().clearCollectionReference(c.getId());
                }
                MyApplication.comicDatabase.collectionDao().deleteCollection(c);
            }
            runOnUiIfAlive(() -> {
                Toast.makeText(activity, getString(R.string.msg_deleted_collections, collections.size()), Toast.LENGTH_SHORT).show();
                toggleSelectMode();
                loadCollections(true);
            });
        });
    }

    /*private void showEmptyState() {
        collectionRecyclerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.VISIBLE);
        btnSelectMode.setVisibility(View.GONE);
    }*/

    private void hideEmptyState() {
        collectionRecyclerView.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        btnSelectMode.setVisibility(View.VISIBLE);
    }

    // ==================== 重命名合集 ====================

    /*private void showRenameDialog(long collectionId, String collectionName) {
        DialogHelper.showTextInputDialog(activity,
                "重命名合集", "输入新的合集名称", "保存", collectionName,
                newName -> renameCollection(collectionId, newName));
    }*/

    /*private void renameCollection(long collectionId, String newName) {
        singleThread.execute(() -> {
            Collection c = MyApplication.comicDatabase.collectionDao().getCollectionById(collectionId);
            if (c != null) {
                c.setName(newName);
                MyApplication.comicDatabase.collectionDao().updateCollection(c);
            }
            runOnUiIfAlive(() -> {
                loadCollections(true);
                Toast.makeText(activity, getString(R.string.msg_renamed), Toast.LENGTH_SHORT).show();
            });
        });
    }*/


}
