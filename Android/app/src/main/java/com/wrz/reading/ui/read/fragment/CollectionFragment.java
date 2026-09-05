package com.wrz.reading.ui.read.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
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
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.adapter.CollectionAdapter;
import com.wrz.reading.ui.read.model.Collection;
import com.wrz.reading.ui.read.model.CollectionItem;
import com.wrz.reading.ui.read.utils.DiffCallBack;

import java.util.ArrayList;
import java.util.List;

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
    private RecyclerView collectionRecyclerView;
    private CollectionAdapter collectionAdapter;
    private View emptyStateText;
    private FloatingActionButton addFab;
    private ImageButton btnSelectMode;
    private ImageButton btnEditName;
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
        collectionRecyclerView.setLayoutManager(new GridLayoutManager(activity, 3));
        collectionAdapter = new CollectionAdapter(collectionItems);
        collectionRecyclerView.setAdapter(collectionAdapter);

        btnSplash.setOnClickListener(v -> loadCollections(true));

        addFab.setOnClickListener(v -> showCreateCollectionDialog());

        btnSelectMode.setOnClickListener(v -> toggleSelectMode());

        btnEditName.setOnClickListener(v -> {
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

            /*for (CollectionItem item : collectionItems) {
                if (item.getCollection().isSelected() && item.getCollection().getId() != UNCATEGORIZED_ID) {
                    showRenameDialog(item.getCollection().getId(), item.getCollection().getName());
                    break;
                }
            }*/
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
                Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show();
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
        singleThread.execute(() -> {
            List<Collection> collections = MyApplication.comicDatabase.collectionDao().getAllCollections();
            int uncategorizedCount = MyApplication.comicDatabase.comicDao().getUncategorizedComics().size();

            // 在后台线程构建临时列表，避免与主线程 Adapter 访问竞态
            ArrayList<CollectionItem> tempList = new ArrayList<>();
            // 未分组入口始终置顶
            Collection uncategorized = new Collection();
            uncategorized.setId(UNCATEGORIZED_ID);
            uncategorized.setName("未分组");
            tempList.add(new CollectionItem(uncategorized, uncategorizedCount));

            for (Collection c : collections) {
                int count = MyApplication.comicDatabase.collectionDao().getComicCountInCollection(c.getId());
                tempList.add(new CollectionItem(c, count));
            }

            /*boolean empty = collections.isEmpty() && uncategorizedCount == 0;*/
            runOnUiIfAlive(() -> {
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
                Toast.makeText(activity, "已创建集合「" + name + "」", Toast.LENGTH_SHORT).show();
                loadCollections(true);
            });
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void toggleSelectMode() {
        collectionAdapter.toggleSelectMode();
        if (collectionAdapter.isSelectMode()) {
            btnSelectMode.setImageResource(R.drawable.ic_close);
            btnEditName.setVisibility(View.VISIBLE);
            addFab.setImageResource(R.drawable.delete_24px);
            addFab.setOnClickListener(v -> showDeleteCollectionsDialog());
        } else {
            btnSelectMode.setImageResource(R.drawable.ic_photo_library);
            btnEditName.setVisibility(View.GONE);
            addFab.setImageResource(R.drawable.add_24px);
            addFab.setOnClickListener(v -> showCreateCollectionDialog());
            clearSelections();
        }
        collectionAdapter.notifyDataSetChanged();
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
            Toast.makeText(activity, "请选择要删除的集合（未分组不可删除）", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("删除集合")
                .setMessage("删除 " + selected.size() + " 个集合\n\n是否同时删除集合中的所有漫画？")
                .setPositiveButton("删除集合和漫画", (dialog, which) -> deleteCollections(selected, true))
                .setNegativeButton("仅删除集合", (dialog, which) -> deleteCollections(selected, false))
                .setNeutralButton("取消", null)
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
                Toast.makeText(activity, "已删除 " + collections.size() + " 个集合", Toast.LENGTH_SHORT).show();
                toggleSelectMode();
                loadCollections(true);
            });
        });
    }

    private void showEmptyState() {
        collectionRecyclerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.VISIBLE);
        btnSelectMode.setVisibility(View.GONE);
    }

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

    private void renameCollection(long collectionId, String newName) {
        singleThread.execute(() -> {
            Collection c = MyApplication.comicDatabase.collectionDao().getCollectionById(collectionId);
            if (c != null) {
                c.setName(newName);
                MyApplication.comicDatabase.collectionDao().updateCollection(c);
            }
            runOnUiIfAlive(() -> {
                loadCollections(true);
                Toast.makeText(activity, "已重命名", Toast.LENGTH_SHORT).show();
            });
        });
    }


}
