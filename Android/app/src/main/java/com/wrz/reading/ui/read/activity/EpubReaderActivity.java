package com.wrz.reading.ui.read.activity;

import static com.wrz.reading.common.Constant.EXTRA_COMIC_NAME;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.read.model.BookMixAToc;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.common.Constant;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.adapter.EPubReaderAdapter;
import com.wrz.reading.ui.read.utils.FileUtils;
import com.wrz.reading.ui.read.view.dialog.ChaptersDialog;
import com.wrz.reading.ui.read.view.epubview.DirectionalViewpager;
import com.wrz.reading.ui.read.view.epubview.ReaderCallback;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubReader;

public class EpubReaderActivity extends BaseReaderActivity implements ReaderCallback {

    public static void start(Context context, Comic comic) {
        BaseReaderActivity.start(context, EpubReaderActivity.class, comic);
    }

    private DirectionalViewpager viewPager;

    View ivMenu;
    TextView tvTitle;
    private List<SpineReference> mSpineReferences;
    private List<TOCReference> mTocReferences;
    private final List<BookMixAToc.mixToc.Chapters> mChapterList = new ArrayList<>();
    private Book mBook;
    private String mFileName;

    private ChaptersDialog chaptersDialog;

    @Override
    public int getLayoutId() {
        return R.layout.activity_epub_reader;
    }

    @Override
    public void initView() {
        viewPager = findViewById(R.id.epubViewPager);
        ivMenu = findViewById(R.id.toolbar_menu);
        ivMenu.setOnClickListener(view -> showMenu());
        tvTitle = findViewById(R.id.toolbar_title);
    }

    @Override
    protected void onComicLoaded(Comic comic) {
        if (getIntent().hasExtra(EXTRA_COMIC_NAME)) {
            tvTitle.setText(getIntent().getStringExtra(EXTRA_COMIC_NAME));
        }
        loadBook(comic);
    }

    @Override
    public void initToolBar() {
        if (mCommonToolbar != null) {
            mCommonToolbar.getViewTreeObserver()
                    .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            mCommonToolbar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            hideToolBarIfVisible();
                        }
                    });
        }
    }

    private void loadBook(Comic comic) {
        singleThread.submit(() -> {
            mFileName = comic.getTitle();

            String mFilePath = comic.getPath();
            try {
                // 打开书籍
                EpubReader reader = new EpubReader();
                try (InputStream is = new FileInputStream(mFilePath)) {
                    mBook = reader.readEpub(is);
                }

                mTocReferences = mBook.getTableOfContents().getTocReferences();

                mSpineReferences = mBook.getSpine().getSpineReferences();

                if (comic.getTotal() != mSpineReferences.size()) {
                    comic.setTotal(mSpineReferences.size());
                }

                MyApplication.comicDatabase.comicDao().updateComic(comic);

                FileUtils.unzipFile(mFilePath, Constant.getPathEpub() + "/" + comic.getTitle());
            } catch (Exception e) {
                Log.e("EPUB_READ", "loadBook: ", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            runOnUiThread(() -> {
                setSpineReferenceTitle();


                setupPhoneView();
            });

        });
    }

    public String getPageHref(int position) {
        if (mTocReferences == null || mTocReferences.isEmpty()) return "";
        if (position >= mTocReferences.size()) {
            position = mTocReferences.size() - 1;
        }
        String pageHref = mTocReferences.get(position).getResource().getHref();
        String opfpath = FileUtils.getPathOPF(FileUtils.getEpubFolderPath(mFileName));
        if (FileUtils.checkOPFInRootDirectory(FileUtils.getEpubFolderPath(mFileName))) {
            pageHref = FileUtils.getEpubFolderPath(mFileName) + "/" + pageHref;
        } else {
            pageHref = FileUtils.getEpubFolderPath(mFileName) + "/" + opfpath + "/" + pageHref;
        }
        return pageHref;
    }

    @Override
    public void toggleToolBarVisible() {
        toggleToolBarVisibleOrGone();
    }

    private void setupPhoneView() {
        EPubReaderAdapter mAdapter = new EPubReaderAdapter(getSupportFragmentManager(),
                mSpineReferences, mBook, comic.getTitle(), comic.getId());
        viewPager.setAdapter(mAdapter);

        viewPager.setOnPageChangeListener(new DirectionalViewpager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (comic != null) {
                    comic.setReadProgress(position);
                    currentChapter = position + 1;
                    if (comic.getTotal() != mChapterList.size()) {
                        comic.setTotal(mChapterList.size());
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        viewPager.setCurrentItem((int) comic.getReadProgress());
    }

    private int currentChapter;

    private void initTocList() {
        chaptersDialog = new ChaptersDialog(this, mChapterList);

        chaptersDialog.setListener(new BaseDialog.ShowOrDismissListener() {
            @Override
            public void show() {
                chaptersDialog.setCurrentChapter(currentChapter);

                chaptersDialog.setOnItemClickListener((adapter, view, position) -> {
                    chaptersDialog.dismiss();
                    currentChapter = position + 1;
                    chaptersDialog.setCurrentChapter(currentChapter);
                    viewPager.setCurrentItem(position);
                });
            }

            @Override
            public void dismiss() {
                hideToolBarIfVisible();
            }
        });
    }

    public void showMenu() {
        if (chaptersDialog == null) {
            initTocList();
        }

        if (chaptersDialog != null && !chaptersDialog.isShowing()
                && !isFinishing() && !isDestroyed()) {
            mCommonToolbar.post(() -> chaptersDialog.show());
        }
    }

    private void setSpineReferenceTitle() {
        int srSize = mSpineReferences.size();
        int trSize = mTocReferences.size();
        for (int j = 0; j < srSize; j++) {
            String href = mSpineReferences.get(j).getResource().getHref();
            for (int i = 0; i < trSize; i++) {
                if (mTocReferences.get(i).getResource().getHref().equalsIgnoreCase(href)) {
                    mSpineReferences.get(j).getResource().setTitle(mTocReferences.get(i).getTitle());
                    break;
                } else {
                    mSpineReferences.get(j).getResource().setTitle("");
                }
            }
        }

        for (int i = 0; i < trSize; i++) {
            Resource resource = mTocReferences.get(i).getResource();
            if (resource != null) {
                mChapterList.add(new BookMixAToc.mixToc.Chapters(resource.getTitle(), resource.getHref()));
            }
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}