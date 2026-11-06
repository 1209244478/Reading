package com.wrz.reading.ui.read.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.wrz.reading.ui.compose.SelectionState
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.main.Log.LogUtil
import com.wrz.reading.ui.main.utils.DialogHelper
import com.wrz.reading.ui.read.activity.EpubReaderActivity
import com.wrz.reading.ui.read.activity.FolderPickerActivity
import com.wrz.reading.ui.read.activity.PdfReaderActivity
import com.wrz.reading.ui.read.activity.ReaderActivity
import com.wrz.reading.ui.read.activity.VideoPlayerActivity
import com.wrz.reading.ui.read.adapter.MoveTargetAdapter
import com.wrz.reading.ui.read.compose.CollectionDetailScreen
import com.wrz.reading.ui.read.compose.ComicListSorter
import com.wrz.reading.ui.read.data.ComicDatabase
import com.wrz.reading.ui.read.data.ComicSizeCalculator
import com.wrz.reading.ui.read.data.VideoPlaylistStore
import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.Comic
import com.wrz.reading.ui.read.model.FileType
import com.wrz.reading.ui.read.model.ReadItem
import com.wrz.reading.ui.read.transfer.LocalImportServer
import com.wrz.reading.ui.read.utils.FileUtils
import com.wrz.reading.ui.read.utils.QRCodeUtil
import com.wrz.reading.ui.read.view.dialog.ImportSourceDialog
import com.wrz.reading.ui.read.view.dialog.MoveDialog
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 集合详情页 Compose 实现。
 *
 * 保留原 CollectionDetailFragment 的全部业务逻辑：
 * 搜索/排序、选择模式、重命名/删除/移动/分享、本地与远程导入、
 * 视频连播状态源、两阶段差量加载与返回页面时的进度刷新。
 *
 * 原 Java Fragment、ComicAdapter 与四套布局保留作为回退备份。
 */
class CollectionDetailComposeFragment : BaseFragment() {

    private var collectionId: Long = CollectionFragment.UNCATEGORIZED_ID

    private val allComics = ArrayList<Comic>()
    private val displayComics = mutableStateListOf<Comic>()

    private var selection by mutableStateOf(SelectionState())
    private var titleState by mutableStateOf("")
    private var currentSort by mutableIntStateOf(Collection.SORT_DEFAULT)
    private var searchVisible by mutableStateOf(false)
    private var searchText by mutableStateOf("")
    private var allComicsEmpty by mutableStateOf(false)
    private var dataVersion by mutableIntStateOf(0)
    private var resumeTick by mutableIntStateOf(0)

    private var searchKeyword = ""

    private val loadGeneration = AtomicInteger()

    private var navController: NavController? = null

    private var isFirst = true

    private val mSplash = Handler(Looper.getMainLooper())
    private val mSplashRunnable = object : Runnable {
        override fun run() {
            if (ComicDatabase.isUpdating) {
                mSplash.postDelayed(this, 1000)
            } else {
                loadComics(true)
            }
        }
    }

    /** 导入方式对话框与远程接收服务 */
    private var importDialog: ImportSourceDialog? = null
    private var importServer: LocalImportServer? = null
    private val remoteImportGeneration = AtomicInteger()
    private var remoteImportedCount = 0

    /**
     * 视频连播状态源：注册到 VideoPlaylistStore 供 VideoPlayerActivity 读取，
     * 与旧实现一致，回写当前显示列表中的视频播放位置。
     */
    private val collectionPlaylistProvider = object : VideoPlaylistStore.PlaylistProvider {
        override fun getSubFiles(): List<ReadItem>? = null

        override fun getReaderComic(): Comic? = null

        // 必须显式限定外层 Fragment 的同名属性，否则会解析为匿名对象自身的
        // getDisplayComics() 合成属性，造成无限递归 StackOverflowError。
        override fun getDisplayComics(): List<Comic> =
            this@CollectionDetailComposeFragment.displayComics

        override fun updateVideoPosition(comicId: Long, position: Long) {
            for (comic in this@CollectionDetailComposeFragment.displayComics) {
                if (comic.id == comicId) {
                    comic.videoPosition = position
                    return
                }
            }
        }
    }

    private val folderPickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                return@registerForActivityResult
            }
            val folders = result.data
                ?.getStringArrayListExtra(FolderPickerActivity.EXTRA_SELECTED_FOLDERS)
            if (folders.isNullOrEmpty()) {
                return@registerForActivityResult
            }
            importComicsFromFolders(folders)
        }

    override fun getLayoutResId(): Int = R.layout.fragment_collection_detail_compose

    override fun initView() {
        val composeView = findId<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                CollectionDetailScreen(
                    collectionName = titleState,
                    comics = displayComics,
                    allComicsEmpty = allComicsEmpty,
                    selection = selection,
                    currentSort = currentSort,
                    searchVisible = searchVisible,
                    searchText = searchText,
                    dataVersion = dataVersion,
                    resumeTick = resumeTick,
                    onBack = { navController?.navigateUp() },
                    onToggleSearch = ::toggleSearch,
                    onSearchTextChange = ::onSearchTextChange,
                    onSortSelected = ::selectSort,
                    onCycleSort = ::cycleSort,
                    onRefresh = { loadComics() },
                    onToggleSelectMode = ::toggleSelectMode,
                    onRenameSelected = ::renameSelected,
                    onMoveSelected = ::moveSelected,
                    onShareSelected = ::shareSelected,
                    onImportOrDelete = ::onFabClick,
                    onItemClick = ::onItemClick,
                    onItemLongClick = ::onItemLongClick
                )
            }
        }
    }

    override fun initData() {
        val args = arguments
        collectionId = args?.getLong(
            CollectionFragment.ARG_COLLECTION_ID,
            CollectionFragment.UNCATEGORIZED_ID
        ) ?: CollectionFragment.UNCATEGORIZED_ID
        val name = args?.getString(CollectionFragment.ARG_COLLECTION_NAME) ?: ""
        titleState = name.ifEmpty { getString(R.string.uncategorized) }

        // 旧实现在视图重建时会新建 ComicAdapter（选择模式关闭）；这里同步复位。
        // 搜索关键词沿用旧行为：视图重建后搜索框隐藏，但过滤关键词保留。
        selection = SelectionState()
        searchVisible = false
        searchText = ""

        navController = NavHostFragment.findNavController(this)
        loadComics()
    }

    override fun configViews() {
        // Compose 随状态自动刷新
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        if (isFirst) {
            isFirst = false
        } else {
            mSplash.postDelayed(mSplashRunnable, 1000)
        }
    }

    override fun goBack() {
        navController?.navigateUp()
    }

    // ==================== 数据加载 ====================

    private fun loadComics() {
        loadComics(false)
    }

    /**
     * useDiff=true：从阅读器/播放器返回时只刷新进度等字段，沿用内存中的体积数据；
     * false：导入/删除/移动/刷新时全量加载并重新统计体积，同时持久化集合总大小。
     */
    private fun loadComics(useDiff: Boolean) {
        val generation = loadGeneration.incrementAndGet()
        threadPool.execute {
            val comics = if (collectionId == CollectionFragment.UNCATEGORIZED_ID) {
                MyApplication.comicDatabase.comicDao().getUncategorizedComics()
            } else {
                MyApplication.comicDatabase.comicDao().getComicsByCollection(collectionId)
            }

            // 后台预校验封面文件存在性
            for (comic in comics) {
                val cover = comic.coverPath
                if (!cover.isNullOrEmpty() && !File(cover).exists()) {
                    comic.coverPath = ""
                }
            }

            if (!useDiff) {
                val totalSize = ComicSizeCalculator.calculate(comics)
                if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                    updateCollectionSizeInDb(collectionId, totalSize[0], totalSize[1])
                }
            }

            val fresh = ArrayList(comics)
            runOnUiIfAlive {
                if (generation != loadGeneration.get()) return@runOnUiIfAlive

                if (useDiff) {
                    val oldById = HashMap<Long, Comic>(allComics.size)
                    for (old in allComics) {
                        oldById[old.id] = old
                    }
                    for (comic in fresh) {
                        val old = oldById[comic.id] ?: continue
                        comic.cacheSize = old.cacheSize
                        comic.fileSize = old.fileSize
                        comic.isDirectory = old.isDirectory
                    }
                }

                allComics.clear()
                allComics.addAll(fresh)
                rebuildDisplayList()
                allComicsEmpty = allComics.isEmpty()
                selection = selection.clearSelection()
                dataVersion++
            }
        }
    }

    private fun rebuildDisplayList() {
        val sorted = ComicListSorter.sortAndFilter(allComics, searchKeyword, currentSort)
        displayComics.clear()
        displayComics.addAll(sorted)
    }

    private fun updateCollectionSizeInDb(collectionId: Long, cacheSize: Long, fileSize: Long) {
        val collection = MyApplication.comicDatabase.collectionDao()
            .getCollectionById(collectionId) ?: return
        collection.cacheSize = cacheSize
        collection.fileSize = fileSize
        MyApplication.comicDatabase.collectionDao().updateCollection(collection)
    }

    // ==================== 搜索 / 排序 ====================

    private fun toggleSearch() {
        if (searchVisible) {
            searchVisible = false
            searchKeyword = ""
            searchText = ""
            rebuildDisplayList()
        } else {
            searchVisible = true
        }
    }

    private fun onSearchTextChange(text: String) {
        searchText = text
        searchKeyword = text.trim().lowercase(Locale.getDefault())
        rebuildDisplayList()
    }

    private fun selectSort(sort: Int) {
        currentSort = sort
        rebuildDisplayList()
    }

    private fun cycleSort() {
        currentSort = when (currentSort) {
            Collection.SORT_BY_NAME -> Collection.SORT_BY_NAME_REVERSE
            Collection.SORT_BY_NAME_REVERSE -> Collection.SORT_BY_SIZE
            Collection.SORT_BY_SIZE -> Collection.SORT_BY_PROGRESS
            Collection.SORT_BY_PROGRESS -> Collection.SORT_BY_LAST_READ_TIME
            else -> Collection.SORT_BY_NAME
        }
        rebuildDisplayList()
    }

    // ==================== 选择模式 ====================

    private fun toggleSelectMode() {
        selection = selection.toggleMode()
    }

    private fun onItemClick(comic: Comic) {
        if (selection.selectMode) {
            selection = selection.toggleSelectionOnClick(comic.id)
        } else {
            openComic(comic)
        }
    }

    private fun onItemLongClick(comic: Comic) {
        selection = selection.selectOnLongPress(comic.id)
    }

    private fun selectedComics(): List<Comic> =
        displayComics.filter { it.id in selection.selectedIds }

    private fun onFabClick() {
        if (selection.selectMode) {
            onDeleteFabClick()
        } else {
            showImportDialog()
        }
    }

    // ==================== 打开漫画 ====================

    private fun openComic(comic: Comic) {
        val activity = activity ?: return
        if (MyApplication.checkPermissions(activity)) {
            when (comic.fileTypeEnum) {
                FileType.PDF -> PdfReaderActivity.start(activity, comic)
                FileType.EPUB -> EpubReaderActivity.start(activity, comic)
                FileType.VIDEO, FileType.MUSIC -> openVideoWithExternalPlayer(comic)
                else -> ReaderActivity.start(activity, comic)
            }
        } else {
            MyApplication.requestPermissions(activity)
        }
    }

    private fun openVideoWithExternalPlayer(comic: Comic) {
        threadPool.execute {
            val file = File(comic.path)
            if (!file.exists()) {
                runOnUiIfAlive {
                    Toast.makeText(
                        activity,
                        getString(R.string.msg_video_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@execute
            }

            var videoPath = comic.path
            if (file.isDirectory) {
                val videos = FileUtils.getVideoFiles(file)
                if (videos.isEmpty()) {
                    runOnUiIfAlive {
                        Toast.makeText(
                            activity,
                            getString(R.string.msg_dir_no_video),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@execute
                }
                videoPath = videos[0].absolutePath
            }

            val path = videoPath
            runOnUiIfAlive {
                val activity = activity ?: return@runOnUiIfAlive
                VideoPlaylistStore.set(collectionPlaylistProvider)
                VideoPlayerActivity.start(
                    activity,
                    comic.id,
                    path,
                    comic.title,
                    false,
                    1000
                )
            }
        }
    }

    // ==================== 删除 ====================

    private fun onDeleteFabClick() {
        val activity = activity ?: return
        val selected = selectedComics()
        if (selected.isEmpty()) {
            Toast.makeText(
                activity,
                getString(R.string.msg_select_comics_first),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        DialogHelper.showDeleteConfirmDialog(
            activity,
            "删除漫画",
            "确定删除选中的 ${selected.size} 本漫画？此操作不可撤销。"
        ) { del ->
            deleteComics(selected, del)
        }
    }

    private fun deleteComics(comics: List<Comic>, deleteFile: Boolean) {
        val targets = comics.toList()
        singleThread.execute {
            for (comic in targets) {
                MyApplication.comicDatabase.comicDao().deleteComic(comic)
                if (deleteFile) {
                    File(comic.path).delete()
                }
            }
            runOnUiIfAlive {
                Toast.makeText(
                    activity,
                    getString(R.string.msg_deleted_comics, targets.size),
                    Toast.LENGTH_SHORT
                ).show()
                toggleSelectMode()
                loadComics()
            }
        }
    }

    // ==================== 重命名 ====================

    private fun renameSelected() {
        val selected = selectedComics()
        if (selected.isEmpty()) return
        showRenameDialog(selected)
    }

    private fun showRenameDialog(list: List<Comic>) {
        val activity = activity ?: return
        DialogHelper.showRenameComicsDialog(
            activity,
            "重命名",
            "输入新的名称",
            "保存",
            list
        ) { edited ->
            renameComics(edited)
        }
    }

    private fun renameComics(list: List<Comic>) {
        val targets = list.toList()
        singleThread.execute {
            ComicDatabase.updateList(targets)
            runOnUiIfAlive {
                loadComics(true)
                Toast.makeText(activity, getString(R.string.msg_renamed), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // ==================== 移动 ====================

    private fun moveSelected() {
        val activity = activity ?: return
        val selected = selectedComics()
        if (selected.isEmpty()) {
            Toast.makeText(
                activity,
                getString(R.string.msg_select_comics_first),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showMoveDialog(selected)
    }

    private fun showMoveDialog(comics: List<Comic>) {
        singleThread.execute {
            val collections = MyApplication.comicDatabase.collectionDao().getAllCollections()
            val targets = ArrayList<MoveTargetAdapter.MoveTarget>()
            if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                targets.add(
                    MoveTargetAdapter.MoveTarget(
                        CollectionFragment.UNCATEGORIZED_ID,
                        "未分组",
                        "未归入任何集合"
                    )
                )
            }
            for (collection in collections) {
                if (collection.id != collectionId) {
                    val count = MyApplication.comicDatabase.collectionDao()
                        .getComicCountInCollection(collection.id)
                    targets.add(
                        MoveTargetAdapter.MoveTarget(
                            collection.id,
                            collection.name.orEmpty(),
                            "$count 本"
                        )
                    )
                }
            }
            runOnUiIfAlive {
                val activity = activity ?: return@runOnUiIfAlive
                if (targets.isEmpty()) {
                    Toast.makeText(
                        activity,
                        getString(R.string.msg_no_move_target),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@runOnUiIfAlive
                }

                val dialog = MoveDialog(activity, targets)
                dialog.setListener(object : MoveDialog.onClickListener {
                    override fun onItemClick(target: MoveTargetAdapter.MoveTarget) {
                        moveComics(comics, target.id)
                    }

                    override fun onCreate() {
                        showCreateCollectionDialog { name ->
                            createCollectionAndResumeMove(comics, name)
                        }
                    }
                })
                dialog.show()
                dialog.setMoveTitle("移动 ${comics.size} 本到")
            }
        }
    }

    private fun createCollectionAndResumeMove(comics: List<Comic>, name: String) {
        singleThread.execute {
            val newId = MyApplication.comicDatabase.collectionDao()
                .insertCollection(Collection.create(name))
            runOnUiIfAlive {
                Toast.makeText(
                    activity,
                    getString(R.string.msg_collection_created, name),
                    Toast.LENGTH_SHORT
                ).show()
                moveComics(comics, newId)
            }
        }
    }

    private fun showCreateCollectionDialog(onCreated: (String) -> Unit) {
        val activity = activity ?: return
        DialogHelper.showTextInputDialog(activity, null, null, null, null) { name ->
            onCreated(name)
        }
    }

    private fun moveComics(comics: List<Comic>, targetCollectionId: Long) {
        val ids = comics.map { it.id }
        singleThread.execute {
            if (targetCollectionId == CollectionFragment.UNCATEGORIZED_ID) {
                MyApplication.comicDatabase.comicDao().moveOutOfCollection(ids)
            } else {
                MyApplication.comicDatabase.comicDao().moveToCollection(targetCollectionId, ids)
            }
            // 目标集合不在当前页展示，需单独持久化体积；源集合由全量加载统计
            persistCollectionSize(targetCollectionId)
            runOnUiIfAlive {
                Toast.makeText(
                    activity,
                    getString(R.string.msg_moved_comics, ids.size),
                    Toast.LENGTH_SHORT
                ).show()
                toggleSelectMode()
                loadComics()
            }
        }
    }

    private fun persistCollectionSize(targetCollectionId: Long) {
        if (targetCollectionId == CollectionFragment.UNCATEGORIZED_ID) return
        threadPool.execute {
            val comics = MyApplication.comicDatabase.comicDao()
                .getComicsByCollection(targetCollectionId)
            val totalSize = ComicSizeCalculator.calculate(comics)
            updateCollectionSizeInDb(targetCollectionId, totalSize[0], totalSize[1])
        }
    }

    // ==================== 分享 ====================

    private fun shareSelected() {
        val activity = activity ?: return
        val selected = selectedComics()
        val files = ArrayList<File>()
        for (comic in selected) {
            val file = File(comic.path)
            if (comic.fileTypeEnum != FileType.IMAGE && file.exists() && file.isFile) {
                files.add(file)
            }
        }
        if (files.isEmpty()) {
            Toast.makeText(
                activity,
                getString(R.string.msg_no_shareable_file),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        FileUtils.shareFiles(activity, files)
    }

    // ==================== 导入方式选择 ====================

    private fun showImportDialog() {
        val activity = activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (importDialog != null && importDialog?.isShowing == true) return

        remoteImportedCount = 0
        val dialog = ImportSourceDialog(activity)
        importDialog = dialog
        dialog.setListener(object : ImportSourceDialog.Listener {
            override fun onLocalImport() {
                launchFolderPicker()
            }

            override fun onRemoteImport() {
                startRemoteImport()
            }

            override fun onStopRemoteImport() {
                stopRemoteImport()
            }

            override fun onDismissed() {
                importDialog = null
            }
        })
        dialog.show()
    }

    private fun launchFolderPicker() {
        val activity = activity ?: return
        if (MyApplication.checkPermissions(activity)) {
            val intent = Intent(activity, FolderPickerActivity::class.java)
            folderPickerLauncher.launch(intent)
        } else {
            MyApplication.requestPermissions(activity)
        }
    }

    private fun importComicsFromFolders(folderPaths: List<String>) {
        singleThread.execute {
            runOnUiIfAlive { showLoading() }
            var imported = 0
            for (path in folderPaths) {
                val file = File(path)
                if (!file.exists()) continue
                val comic = Comic.buildComicFromFile(file)
                if (comic != null && !Comic.isComicExists(comic.originalPath)) {
                    if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                        comic.collectionId = collectionId
                    }
                    MyApplication.comicDatabase.comicDao().insertComic(comic)
                    imported++
                }
            }
            val count = imported
            runOnUiIfAlive {
                dismissLoading()
                Toast.makeText(
                    activity,
                    getString(R.string.msg_imported_comics, count),
                    Toast.LENGTH_SHORT
                ).show()
                loadComics()
            }
        }
    }

    // ==================== 远程导入 ====================

    private fun startRemoteImport() {
        val dialog = importDialog ?: return
        remoteImportedCount = 0
        val generation = remoteImportGeneration.incrementAndGet()

        threadPool.execute {
            val destDir = resolveRemoteImportDir()
            if (destDir == null) {
                runOnUiIfAlive {
                    if (importDialog === dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_dir_failed))
                    }
                }
                return@execute
            }

            val ip = LocalImportServer.findLanIpAddress()
            if (ip.isNullOrEmpty()) {
                runOnUiIfAlive {
                    if (importDialog === dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_no_wifi))
                    }
                }
                return@execute
            }

            val token = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            val server = LocalImportServer.startServer(
                MyApplication.app,
                token,
                destDir,
                createImportServerListener()
            )
            if (server == null) {
                runOnUiIfAlive {
                    if (importDialog === dialog) {
                        dialog.showRemoteError(getString(R.string.msg_remote_server_failed))
                    }
                }
                return@execute
            }

            importServer = server
            if (generation != remoteImportGeneration.get()) {
                if (importServer === server) {
                    importServer = null
                }
                stopServerAsync(server)
                return@execute
            }

            val url = "http://" + ip + ":" + server.listeningPort + "/t/" + token + "/"
            val qrBitmap = QRCodeUtil.generate(url, 640)
            runOnUiIfAlive {
                if (importDialog === dialog && generation == remoteImportGeneration.get()) {
                    dialog.showRemoteInfo(url, qrBitmap)
                }
            }
        }
    }

    private fun stopRemoteImport() {
        remoteImportGeneration.incrementAndGet()
        val server = importServer
        importServer = null
        if (server != null) {
            stopServerAsync(server)
        }
    }

    private fun stopServerAsync(server: LocalImportServer) {
        Thread({
            try {
                server.stop()
            } catch (e: Exception) {
                LogUtil.e(
                    "CollectionDetailComposeFragment",
                    "stop import server: " + e.message
                )
            }
        }, "LocalImportServerStop").start()
    }

    private fun resolveRemoteImportDir(): File? {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "漫画"
        )
        if (isDirWritable(publicDir)) {
            return publicDir
        }
        val context = MyApplication.app ?: return null
        val base = context.getExternalFilesDir(null) ?: context.filesDir ?: return null
        val fallback = File(base, "import")
        return if (isDirWritable(fallback)) fallback else null
    }

    private fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) {
                return false
            }
            val probe = File(dir, ".write_probe_" + System.currentTimeMillis())
            try {
                FileOutputStream(probe).use { }
                true
            } finally {
                probe.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createImportServerListener(): LocalImportServer.Listener {
        return object : LocalImportServer.Listener {
            override fun onUploadStarted(name: String, totalBytes: Long) {
                runOnUiIfAlive {
                    importDialog?.showReceiving(name, 0)
                }
            }

            override fun onUploadProgress(name: String, received: Long, totalBytes: Long) {
                val percent = if (totalBytes > 0) {
                    (received * 100 / totalBytes).toInt()
                } else {
                    -1
                }
                runOnUiIfAlive {
                    importDialog?.showReceiving(name, percent)
                }
            }

            override fun onFileSaved(file: File) {
                runOnUiIfAlive {
                    importDialog?.showReceived(file.name)
                }
                importRemoteFile(file)
            }

            override fun onUploadFailed(name: String, message: String) {
                runOnUiIfAlive {
                    importDialog?.showUploadFailed(name, message)
                }
            }
        }
    }

    private fun importRemoteFile(file: File) {
        singleThread.execute {
            var comic: Comic? = null
            var failReason = ""
            try {
                comic = Comic.buildComicFromFile(file)
            } catch (e: Exception) {
                failReason = e.message ?: "解析失败"
            }

            var imported = false
            if (comic != null) {
                if (Comic.isComicExists(comic.originalPath)) {
                    failReason = "文件已导入过"
                } else {
                    if (collectionId != CollectionFragment.UNCATEGORIZED_ID) {
                        comic.collectionId = collectionId
                    }
                    MyApplication.comicDatabase.comicDao().insertComic(comic)
                    imported = true
                }
            } else if (failReason.isEmpty()) {
                failReason = "不支持的文件格式"
            }

            val success = imported
            val reason = failReason
            runOnUiIfAlive {
                if (success) {
                    remoteImportedCount++
                    importDialog?.showImported(file.name, remoteImportedCount)
                    loadComics()
                } else {
                    importDialog?.showImportFailed(file.name, reason)
                    Toast.makeText(
                        activity,
                        getString(R.string.import_remote_import_failed, file.name) + "：" + reason,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        importDialog?.dismiss()
        importDialog = null
        stopRemoteImport()
        VideoPlaylistStore.clear(collectionPlaylistProvider)
        mSplash.removeCallbacks(mSplashRunnable)
        super.onDestroy()
    }
}
