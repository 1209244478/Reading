package com.wrz.reading.ui.read.fragment

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.wrz.reading.R
import com.wrz.reading.app.MyApplication
import com.wrz.reading.common.BaseFragment
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.main.utils.DialogHelper
import com.wrz.reading.ui.read.compose.CollectionScreen
import com.wrz.reading.ui.read.compose.CollectionSelectionState
import com.wrz.reading.ui.read.compose.CollectionUiItem
import com.wrz.reading.ui.read.data.ComicSizeCalculator
import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.CollectionItem
import java.util.concurrent.atomic.AtomicInteger

/**
 * 书库首页的 Compose 实现。
 *
 * 保留原 [CollectionFragment] 的全部业务逻辑：
 * - 两阶段加载（先列表后未分组体积统计）与代际号防竞态；
 * - 集合创建、重命名、删除（含删除漫画/仅删除集合）；
 * - 选择模式、点击进入详情、电视端焦点回位。
 *
 * 原 Fragment、CollectionAdapter 与各布局变体保留作为回退备份。
 */
class CollectionComposeFragment : BaseFragment() {

    private val items = mutableStateListOf<CollectionUiItem>()
    private var selection by mutableStateOf(CollectionSelectionState())
    private var resumeTick by mutableIntStateOf(0)
    private var dataVersion by mutableIntStateOf(0)

    /** 加载代际号：并发加载时丢弃过期回调 */
    private val loadGeneration = AtomicInteger()

    /** 最近一次「未分组」缓存体积统计，下次加载先沿用旧值 */
    @Volatile
    private var lastUncategorizedCacheSize = 0L

    private var navController: NavController? = null

    override fun getLayoutResId(): Int = R.layout.fragment_collection_compose

    override fun initView() {
        val composeView = findId<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                CollectionScreen(
                    items = items,
                    selection = selection,
                    resumeTick = resumeTick,
                    dataVersion = dataVersion,
                    onRefresh = ::loadCollections,
                    onToggleSelectMode = ::toggleSelectMode,
                    onRenameSelected = ::showRenameDialogForSelection,
                    onCreateOrDelete = ::onFabClick,
                    onItemClick = ::onItemClick,
                    onItemLongClick = ::onItemLongClick,
                    onSettingClick = ::openSettings
                )
            }
        }
    }

    override fun initData() {
        // 原实现在每次视图重建时都会新建 CollectionAdapter（选择模式默认关闭），
        // 因此这里同步复位选择状态，保证从详情/设置页返回后的行为一致。
        selection = CollectionSelectionState()
        navController = NavHostFragment.findNavController(this)
        loadCollections()
    }

    override fun configViews() {
        // Compose 随状态自动刷新
    }

    override fun onResume() {
        super.onResume()
        // 电视端从详情页返回时若焦点丢失，Compose 侧会回到列表首项
        resumeTick++
    }

    override fun goBack() {
        if (selection.selectMode) {
            toggleSelectMode()
        } else {
            activity?.finish()
        }
    }

    // ==================== 选择模式 ====================

    private fun toggleSelectMode() {
        selection = selection.toggleMode()
    }

    private fun onItemClick(item: CollectionUiItem) {
        if (selection.selectMode) {
            selection = selection.toggleSelectionOnClick(item.id)
        } else {
            navigateToDetail(item)
        }
    }

    private fun onItemLongClick(item: CollectionUiItem) {
        selection = selection.selectOnLongPress(item.id)
    }

    private fun onFabClick() {
        if (selection.selectMode) {
            showDeleteCollectionsDialog()
        } else {
            showCreateCollectionDialog()
        }
    }

    private fun selectedUiItems(): List<CollectionUiItem> =
        items.filter { it.id in selection.selectedIds && !it.isUncategorized }

    // ==================== 导航 ====================

    private fun navigateToDetail(item: CollectionUiItem) {
        val args = Bundle().apply {
            putLong(CollectionFragment.ARG_COLLECTION_ID, item.id)
            putString(CollectionFragment.ARG_COLLECTION_NAME, item.collection.name)
        }
        navController?.navigate(R.id.action_main_to_collectionDetail, args)
    }

    private fun openSettings() {
        navController?.navigate(R.id.action_main_to_settingfragment)
    }

    // ==================== 创建 / 重命名 / 删除 ====================

    private fun showCreateCollectionDialog() {
        val activity = activity ?: return
        DialogHelper.showTextInputDialog(activity, null, null, null, null) { name ->
            createCollection(name)
        }
    }

    private fun createCollection(name: String) {
        singleThread.execute {
            MyApplication.comicDatabase.collectionDao()
                .insertCollection(Collection.create(name))
            runOnUiIfAlive {
                Toast.makeText(
                    activity,
                    getString(R.string.msg_collection_created, name),
                    Toast.LENGTH_SHORT
                ).show()
                loadCollections()
            }
        }
    }

    private fun showRenameDialogForSelection() {
        val selected = selectedUiItems()
        if (selected.isEmpty()) return
        val activity = activity ?: return
        DialogHelper.showRenameCollectionsDialog(
            activity,
            "重命名合集",
            "输入新的合集名称",
            "保存",
            selected.map { CollectionItem(it.collection, it.comicCount) }
        ) { edited ->
            renameCollections(edited)
        }
    }

    private fun renameCollections(list: List<CollectionItem>) {
        val targets = list.toList()
        singleThread.execute {
            for (item in targets) {
                MyApplication.comicDatabase.collectionDao().updateCollection(item.collection)
            }
            runOnUiIfAlive {
                loadCollections()
                Toast.makeText(activity, getString(R.string.msg_renamed), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showDeleteCollectionsDialog() {
        val activity = activity ?: return
        val selected = selectedUiItems().map { it.collection }
        if (selected.isEmpty()) {
            Toast.makeText(
                activity,
                getString(R.string.msg_select_delete_collection),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(getString(R.string.title_delete_collection))
            .setMessage(getString(R.string.msg_delete_collections_confirm, selected.size))
            .setPositiveButton(getString(R.string.btn_delete_collection_and_comics)) { _, _ ->
                deleteCollections(selected, true)
            }
            .setNegativeButton(getString(R.string.btn_delete_collection_only)) { _, _ ->
                deleteCollections(selected, false)
            }
            .setNeutralButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun deleteCollections(collections: List<Collection>, deleteComics: Boolean) {
        val targets = collections.toList()
        singleThread.execute {
            for (collection in targets) {
                if (deleteComics) {
                    MyApplication.comicDatabase.comicDao()
                        .deleteComicsInCollection(collection.id)
                } else {
                    MyApplication.comicDatabase.comicDao()
                        .clearCollectionReference(collection.id)
                }
                MyApplication.comicDatabase.collectionDao().deleteCollection(collection)
            }
            runOnUiIfAlive {
                Toast.makeText(
                    activity,
                    getString(R.string.msg_deleted_collections, targets.size),
                    Toast.LENGTH_SHORT
                ).show()
                toggleSelectMode()
                loadCollections()
            }
        }
    }

    // ==================== 数据加载 ====================

    /**
     * 两阶段加载：
     * 1. 查库构建列表并立即渲染（未分组体积先沿用上次统计值）；
     * 2. 在后台统计未分组缓存体积，完成后只更新对应项。
     *
     * 原实现用 DiffUtil 做差量刷新；Compose 通过稳定 key 的 LazyGrid 列表重绑，
     * 同样能保留滚动位置并只刷新变化的项。
     */
    private fun loadCollections() {
        val generation = loadGeneration.incrementAndGet()
        threadPool.execute {
            val collections = MyApplication.comicDatabase.collectionDao().getAllCollections()
            val comics = MyApplication.comicDatabase.comicDao().getUncategorizedComics()
            val uncategorizedCount = comics.size

            val freshItems = ArrayList<CollectionUiItem>(collections.size + 1)
            val uncategorized = Collection().apply {
                id = CollectionFragment.UNCATEGORIZED_ID
                name = "未分组"
                cacheSize = lastUncategorizedCacheSize
            }
            freshItems.add(CollectionUiItem(uncategorized, uncategorizedCount))
            for (collection in collections) {
                val count = MyApplication.comicDatabase.collectionDao()
                    .getComicCountInCollection(collection.id)
                freshItems.add(CollectionUiItem(collection, count))
            }

            runOnUiIfAlive {
                if (generation != loadGeneration.get()) return@runOnUiIfAlive
                items.clear()
                items.addAll(freshItems)
                selection = selection.clearSelection()
                dataVersion++
            }

            // 阶段二：统计未分组体积（重磁盘 IO）
            ComicSizeCalculator.calculate(comics)
            var totalSize = 0L
            for (comic in comics) {
                totalSize += comic.cacheSize
            }
            lastUncategorizedCacheSize = totalSize
            runOnUiIfAlive {
                if (generation != loadGeneration.get()) return@runOnUiIfAlive
                val index = items.indexOfFirst { it.id == CollectionFragment.UNCATEGORIZED_ID }
                if (index >= 0) {
                    val current = items[index]
                    if (current.collection.cacheSize != totalSize) {
                        current.collection.cacheSize = totalSize
                        items[index] = current.copy()
                    }
                }
            }
        }
    }
}
