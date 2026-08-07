package com.wrz.reading.ui.read.activity;

import static com.wrz.reading.common.Constant.EXTRA_COMIC_NAME;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.model.Comic;
import com.wrz.reading.ui.read.adapter.ImageAdapter;
import com.wrz.reading.util.AutoPlayController;
import com.wrz.reading.util.DialogHelper;
import com.wrz.reading.util.FileSorter;
import com.wrz.reading.util.FileUtils;
import com.wrz.reading.util.ZipUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReaderActivity extends BaseReaderActivity {

    public static void start(Context context, Comic comic) {
        BaseReaderActivity.start(context, ReaderActivity.class, comic);
    }

    private ViewPager2 comicViewPager;
    private TextView pageCounterText;
    private Spinner folderSpinner;
    private ImageAdapter imageAdapter;
    private List<File> imageFiles;

    // 当前排序方式：0=名称升序(默认), 1=名称降序, 2=修改时间
    private int currentSortType = 0;

    // 文件夹选择相关
    private List<File> subdirectories;
    private boolean isFolderSelection = false;

    // 自动播放：委托给独立的 Controller，避免在 Activity 中持有 Handler/Runnable/状态
    private final AutoPlayController autoPlay = new AutoPlayController(() -> onSwipe(true));

    @Override
    public int getLayoutId() {
        return R.layout.activity_comic_reader;
    }

    @Override
    public void initToolBar() {
        if (mCommonToolbar != null && getIntent().hasExtra(EXTRA_COMIC_NAME)) {
            mCommonToolbar.setTitle(getIntent().getStringExtra(EXTRA_COMIC_NAME));
        }
    }

    @Override
    public void initView() {
        comicViewPager = findViewById(R.id.comic_view_pager);
        pageCounterText = findViewById(R.id.page_counter_text);
        pageCounterText.setOnClickListener(v -> showPageJumpDialog());

        // 初始化文件夹选择Spinner
        folderSpinner = findViewById(R.id.folder_spinner);

        comicViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageCounter(position);
                updateReadProgress(position);
            }
        });
    }

    @Override
    protected void onComicLoaded(Comic comic) {
        // 后台加载图片文件
        singleThread.execute(() -> {
            imageFiles = loadImageFiles(comic);

            if (imageFiles == null || imageFiles.isEmpty()) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showToastAndFinish("漫画文件夹中没有图片文件");
                });
                return;
            }

            // 排序
            imageFiles = FileSorter.sort(imageFiles);

            // 更新封面和总数
            updateComicMetadata(comic, imageFiles);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setupViewPager();
            });
        });
    }

    /**
     * 加载图片文件列表
     */
    private List<File> loadImageFiles(Comic comic) {
        File comicFile = new File(comic.getPath());
        File originalDirectory = new File(comic.getOriginalPath());

        // 优先从源路径获取（节省解压时间）
        if (!comic.getOriginalPath().isEmpty() && originalDirectory.exists()) {
            List<File> files = FileUtils.getMediaFilesRecursive(originalDirectory);
            if (!files.isEmpty()) {
                // 检查是否有多个子目录
                checkAndSetupFolderSelector(originalDirectory);
                return files;
            }
        }

        // 处理压缩文件
        if (comicFile.exists() && comicFile.getName().endsWith(FileUtils.SPECIAL_FORMATS)) {
            return loadFromCompressedFile(comic.getPath());
        }

        // 直接从路径加载
        if (comicFile.exists() && comicFile.isDirectory()) {
            List<File> files = FileUtils.getMediaFilesRecursive(comicFile);
            if (!files.isEmpty()) {
                checkAndSetupFolderSelector(comicFile);
            }
            return files;
        }

        runOnUiThread(() -> Toast.makeText(this, "文件丢失或损坏", Toast.LENGTH_SHORT).show());
        return null;
    }

    /**
     * 从压缩文件加载图片
     */
    private List<File> loadFromCompressedFile(String zipPath) {
        File tempExtractDir = ZipUtils.getTempExtractDir(zipPath);

        // 检查是否已经解压过
        if (tempExtractDir.exists()) {
            List<File> files = FileUtils.getMediaFilesRecursive(tempExtractDir);
            if (!files.isEmpty()) {
                checkAndSetupFolderSelector(tempExtractDir);
            }
            return files;
        }

        // 解压文件
        runOnUiThread(() -> Toast.makeText(this, "正在解压漫画文件...", Toast.LENGTH_SHORT).show());
        List<File> files = ZipUtils.extractImagesFromZip(zipPath, tempExtractDir);
        if (files != null && !files.isEmpty()) {
            checkAndSetupFolderSelector(tempExtractDir);
        }
        return files;
    }

    /**
     * 检查并设置文件夹选择器
     */
    private void checkAndSetupFolderSelector(File rootDir) {
        subdirectories = FileUtils.getSubdirectoriesWithImages(rootDir);
        if (subdirectories.size() > 1) {
            runOnUiThread(() -> setupFolderSpinner(subdirectories));
        } else {
            runOnUiThread(() -> hideFolderSpinner());
        }
    }

    /**
     * 设置文件夹下拉选择器
     */
    private void setupFolderSpinner(List<File> folders) {
        if (folderSpinner == null) {
            return;
        }

        isFolderSelection = true;

        // 创建文件夹名称列表，添加"全部"选项
        List<String> folderNames = new ArrayList<>();
        folderNames.add("全部文件夹");
        for (File folder : folders) {
            folderNames.add(folder.getName());
        }

        // 使用自定义布局确保文字颜色正确显示
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, folderNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setVisibility(View.VISIBLE);

        // 设置选择监听器
        folderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFolderSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 隐藏文件夹选择器
     */
    private void hideFolderSpinner() {
        if (folderSpinner != null) {
            folderSpinner.setVisibility(View.GONE);
            folderSpinner.setOnItemSelectedListener(null);
        }
        isFolderSelection = false;
        subdirectories = null;
    }

    public int subPosition = 0;

    /**
     * 文件夹选择事件处理
     */
    private void onFolderSelected(int position) {
        if (!isFolderSelection || subdirectories == null) {
            return;
        }

        subPosition = position;

        List<File> newImageFiles;
        if (position == 0) {
            // 选择"全部文件夹"
            newImageFiles = new ArrayList<>();
            for (File folder : subdirectories) {
                newImageFiles.addAll(FileUtils.getMediaFilesRecursive(folder));
            }
        } else {
            // 选择特定文件夹
            if (position - 1 < subdirectories.size()) {
                newImageFiles = FileUtils.getMediaFilesRecursive(subdirectories.get(position - 1));
            } else {
                return;
            }
        }

        if (newImageFiles.isEmpty()) {
            Toast.makeText(this, "所选文件夹没有可支持的文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 排序并更新
        imageFiles = FileSorter.sort(newImageFiles);
        updateViewPager();
    }

    /**
     * 更新漫画元数据
     */
    private void updateComicMetadata(Comic comic, List<File> imageFiles) {
        if (comic.getCoverPath().isEmpty() && !imageFiles.isEmpty()) {
            comic.setCoverPath(FileUtils.getAndSetCoverImagePath(imageFiles.get(0), comic.getTitle()));
        }

        if (comic.getTotal() == 0) {
            comic.setTotal(imageFiles.size());
        }

        MyApplication.comicDatabase.comicDao().updateComic(comic);
    }

    /**
     * 设置ViewPager
     */
    private void setupViewPager() {
        imageAdapter = new ImageAdapter(R.layout.image_item, imageFiles, new ImageAdapter.OnComicClickListener() {
            @Override
            public void onComicClick() {
                toggleToolBarVisibleOrGone();
            }

            @Override
            public void onScroll() {
                hideToolBarIfVisible();
            }

            @Override
            public void onSwipe(boolean next) {
                ReaderActivity.this.onSwipe(next);
            }

            @Override
            public void onVideoClick(File videoFile) {
                openVideoWithExternalPlayer(videoFile);
            }
        });

        comicViewPager.setAdapter(imageAdapter);

        // 恢复上次阅读进度
        setScrollOrientation(comic.isHorizontal(), false);
        if (comic.getReadProgress() > 0 && comic.getReadProgress() < imageFiles.size()) {
            Log.e(TAG, "setupViewPager: comic.getReadProgress():" + comic.getReadProgress());
            comicViewPager.setCurrentItem((int) comic.getReadProgress(), false);
        } else {
            updatePageCounter(0);
        }
    }

    /**
     * 更新ViewPager（切换文件夹时使用）
     */
    private void updateViewPager() {
        if (imageAdapter != null && imageFiles != null) {
            // 使用 setList 更新 Adapter 内部数据
            imageAdapter.setList(imageFiles);
            if (comic.getPercentMap() != null && comic.getPercentMap().get(subPosition) != null
                    && comic.getPercentMap().get(subPosition) != -1) {
                Log.e(TAG, "updateViewPager: comic.getPercentMap().get(subPosition):" + comic.getPercentMap().get(subPosition));
                comicViewPager.setCurrentItem(comic.getPercentMap().get(subPosition), false);
                updatePageCounter(comic.getPercentMap().get(subPosition));
            } else {
                comicViewPager.setCurrentItem(0, false);
                updatePageCounter(0);
            }
        }
    }

    // ==================== 页面操作 ====================

    @SuppressLint("SetTextI18n")
    private void updatePageCounter(int position) {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            Log.e(TAG, "updatePageCounter: position:" + position);
            pageCounterText.setText((position + 1) + "/" + imageFiles.size());
        }
    }

    private void updateReadProgress(int position) {
        if (comic != null && imageFiles != null && !imageFiles.isEmpty()) {
            Log.e(TAG, "updateReadProgress: position:" + position);
            Log.e(TAG, "updateReadProgress: subPosition:" + subPosition);
            comic.getPercentMap().put(subPosition, position);
            comic.setReadProgress(position);
            comic.setLastRead(System.currentTimeMillis());
        }
    }

    /**
     * 使用应用内 ExoPlayer 播放视频文件
     */
    private void openVideoWithExternalPlayer(File videoFile) {
        if (!videoFile.exists()) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, videoFile.getAbsolutePath());
        intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, videoFile.getName());
        startActivity(intent);
    }

    public void onSwipe(boolean next) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return;
        }

        int currentPosition = comicViewPager.getCurrentItem();
        int targetPosition;

        if (next) {
            targetPosition = (currentPosition + 1 < imageFiles.size()) ? currentPosition + 1 : 0;
        } else {
            targetPosition = (currentPosition - 1 >= 0) ? currentPosition - 1 : imageFiles.size() - 1;
        }

        comicViewPager.setCurrentItem(targetPosition, false);
    }

    // ==================== 排序功能 ====================

    @SuppressLint("NotifyDataSetChanged")
    private void sortImagesByName(boolean ascending) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return;
        }

        imageFiles = ascending ? FileSorter.sort(imageFiles) : FileSorter.sortReverse(imageFiles);
        currentSortType = ascending ? 0 : 1;
        refreshViewPager("已按名称" + (ascending ? "升序" : "降序") + "排序");
    }

    @SuppressLint("NotifyDataSetChanged")
    private void sortImagesByModifiedTime() {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return;
        }

        imageFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        currentSortType = 2;
        refreshViewPager("已按修改时间排序");
    }

    private void refreshViewPager(String message) {
        int currentPosition = comicViewPager.getCurrentItem();
        if (imageAdapter != null && imageFiles != null) {
            imageAdapter.setList(imageFiles);
        }
        comicViewPager.setCurrentItem(currentPosition, false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ==================== 翻页方向 ====================

    private void setScrollOrientation(boolean horizontal, boolean isAction) {
        comicViewPager.setOrientation(horizontal ? ViewPager2.ORIENTATION_HORIZONTAL : ViewPager2.ORIENTATION_VERTICAL);
        if (isAction) {
            Toast.makeText(this, horizontal ? "已切换为左右滑动" : "已切换为上下滑动", Toast.LENGTH_SHORT).show();
            if (comic != null) {
                comic.setHorizontal(horizontal);
            }
        }
    }

    // ==================== 封面设置 ====================

    private void setCover() {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return;
        }

        // 删除旧封面
        if (comic.getCoverPath() != null && !comic.getCoverPath().isEmpty()) {
            new File(comic.getCoverPath()).delete();
        }

        String coverPath = FileUtils.getAndSetCoverImagePath(
                imageFiles.get(comicViewPager.getCurrentItem()),
                comic.getTitle() + "_" + System.currentTimeMillis()
        );
        comic.setCoverPath(coverPath);
        Toast.makeText(this, "已设置为漫画封面", Toast.LENGTH_SHORT).show();
    }

    // ==================== 自动播放 ====================

    private void startAutoPlay() {
        autoPlay.start();
        startItem.setChecked(true);
    }

    private void stopAutoPlay() {
        autoPlay.stop();
    }

    // ==================== 页码跳转 ====================

    private void showPageJumpDialog() {
        if (imageFiles == null || imageFiles.isEmpty()) {
            return;
        }
        DialogHelper.showPageJumpDialog(this,
                comicViewPager.getCurrentItem(), imageFiles.size(),
                page -> comicViewPager.setCurrentItem(page, true));
    }

    // ==================== 菜单 ====================

    private MenuItem startItem;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader_menu, menu);

        // 设置排序选中状态
        MenuItem sortAsc = menu.findItem(R.id.action_sort_name_asc);
        MenuItem sortDesc = menu.findItem(R.id.action_sort_name_desc);
        MenuItem sortModified = menu.findItem(R.id.action_sort_by_modified_time);
        if (sortAsc != null) sortAsc.setChecked(currentSortType == 0);
        if (sortDesc != null) sortDesc.setChecked(currentSortType == 1);
        if (sortModified != null) sortModified.setChecked(currentSortType == 2);

        // 设置翻页方向标题：当前是水平则提示切换为垂直，反之亦然
        MenuItem toggleItem = menu.findItem(R.id.action_toggle_scroll_orientation);
        if (toggleItem != null && comic != null) {
            boolean isHorizontal = comic.isHorizontal();
            toggleItem.setTitle(isHorizontal ? "切换为上下滑动翻页" : "切换为左右滑动翻页");
        }

        // 设置缩放模式选中状态
        MenuItem zoomItem = menu.findItem(R.id.action_toggle_zoom);
        if (zoomItem != null && imageAdapter != null) {
            zoomItem.setChecked(imageAdapter.isZoomMode());
        }

        // 设置自动播放间隔选中状态
        MenuItem interval1s = menu.findItem(R.id.action_interval_1s);
        MenuItem interval3s = menu.findItem(R.id.action_interval_3s);
        MenuItem interval5s = menu.findItem(R.id.action_interval_5s);
        MenuItem interval10s = menu.findItem(R.id.action_interval_10s);
        long delay = autoPlay.getDelay();
        if (interval1s != null) interval1s.setChecked(delay == 1000);
        if (interval3s != null) interval3s.setChecked(delay == 3000);
        if (interval5s != null) interval5s.setChecked(delay == 5000);
        if (interval10s != null) interval10s.setChecked(delay == 10000);

        // 设置自动播放开启/关闭选中状态
        startItem = menu.findItem(R.id.action_auto_play_start);
        MenuItem stopItem = menu.findItem(R.id.action_auto_play_stop);
        if (startItem != null) startItem.setChecked(autoPlay.isPlaying());
        if (stopItem != null) stopItem.setChecked(!autoPlay.isPlaying());

        // 为首页/末页 actionLayout 按钮设置点击监听
        MenuItem pageJumpItem = menu.findItem(R.id.action_page_jump);
        if (pageJumpItem != null) {
            View actionView = pageJumpItem.getActionView();
            if (actionView != null) {
                View btnFirst = actionView.findViewById(R.id.btn_go_first_page);
                View btnLast = actionView.findViewById(R.id.btn_go_last_page);
                if (btnFirst != null) {
                    btnFirst.setOnClickListener(v -> {
                        if (comicViewPager != null) {
                            comicViewPager.setCurrentItem(0, false);
                        }
                    });
                }
                if (btnLast != null) {
                    btnLast.setOnClickListener(v -> {
                        if (imageFiles != null && !imageFiles.isEmpty() && comicViewPager != null) {
                            comicViewPager.setCurrentItem(imageFiles.size() - 1, false);
                        }
                    });
                }
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_sort_name_asc) {
            sortImagesByName(true);
            item.setChecked(true);
        } else if (itemId == R.id.action_sort_name_desc) {
            sortImagesByName(false);
            item.setChecked(true);
        } else if (itemId == R.id.action_sort_by_modified_time) {
            sortImagesByModifiedTime();
            item.setChecked(true);
        } else if (itemId == R.id.action_toggle_scroll_orientation) {
            // 切换翻页方向：当前水平则切为垂直，反之亦然
            boolean currentHorizontal = comic != null && comic.isHorizontal();
            setScrollOrientation(!currentHorizontal, true);
            item.setTitle(currentHorizontal ? "切换为左右滑动翻页" : "切换为上下滑动翻页");
        } else if (itemId == R.id.action_toggle_zoom) {
            // 切换缩放模式：开启后可双指/双击缩放，关闭后恢复单击翻页
            boolean newMode = !imageAdapter.isZoomMode();
            imageAdapter.setZoomMode(newMode);
            item.setChecked(newMode);
            // 关闭缩放模式时若自动播放仍在进行不受影响
            Toast.makeText(this, newMode ? "已开启缩放模式" : "已关闭缩放模式", Toast.LENGTH_SHORT).show();
        } else if (itemId == R.id.set_cover_image) {
            setCover();
        } else if (itemId == R.id.action_auto_play_start) {
            if (!autoPlay.isPlaying()) {
                startAutoPlay();
                Toast.makeText(this, "已开始自动播放", Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_auto_play_stop) {
            if (autoPlay.isPlaying()) {
                stopAutoPlay();
                Toast.makeText(this, "已停止自动播放", Toast.LENGTH_SHORT).show();
            }
            item.setChecked(true);
        } else if (itemId == R.id.action_interval_1s) {
            changeAutoPlayInterval(1000, item);

        } else if (itemId == R.id.action_interval_3s) {
            changeAutoPlayInterval(3000, item);
            if (!autoPlay.isPlaying()) {
                startAutoPlay();
                Toast.makeText(this, "已开始自动播放", Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_interval_5s) {
            changeAutoPlayInterval(5000, item);
            if (!autoPlay.isPlaying()) {
                startAutoPlay();
                Toast.makeText(this, "已开始自动播放", Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_interval_10s) {
            changeAutoPlayInterval(10000, item);
            if (!autoPlay.isPlaying()) {
                startAutoPlay();
                Toast.makeText(this, "已开始自动播放", Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_exit) {
            finish();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * 切换自动播放间隔：若正在播放则重启定时器使新间隔立即生效
     */
    private void changeAutoPlayInterval(long newDelay, MenuItem item) {
        autoPlay.setDelay(newDelay);
        item.setChecked(true);
        Toast.makeText(this, "间隔已设为 " + (newDelay / 1000) + " 秒", Toast.LENGTH_SHORT).show();
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoPlay.destroy();
    }

    // ==================== 按键处理 ====================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            onSwipe(true);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            onSwipe(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
