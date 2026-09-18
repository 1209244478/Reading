package com.wrz.reading.ui.read.activity;

import android.content.Context;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.view.pdfview.PdfDocument;
import com.wrz.reading.ui.read.view.pdfview.PdfPageAdapter;

/**
 * PDF 阅读器：
 * - {@link PdfDocument}：系统 PdfRenderer 后台串行渲染
 * - {@link PdfPageAdapter}：ViewPager2 翻页 + PhotoView（第三方）缩放拖拽
 */
public class PdfReaderActivity extends BaseReaderActivity {

    public static void start(Context context, Comic comic) {
        BaseReaderActivity.start(context, PdfReaderActivity.class, comic);
    }

    private ViewPager2 pdfPager;
    private PdfPageAdapter pdfAdapter;
    private PdfDocument pdfDocument;
    private TextView pageCounterText;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            updateReadProgress();
        }
    };

    @Override
    public int getLayoutId() {
        return R.layout.activity_pdf_reader;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        pdfPager = findViewById(R.id.pdf_pager);
        pageCounterText = findViewById(R.id.page_counter_text);

        pageCounterText.setOnClickListener(v -> showPageJumpDialog());
    }

    @Override
    protected void onComicLoaded(Comic comic) {
        if (isFinishing() || isDestroyed()) return;
        // PdfRenderer 初始化涉及大文件 mmap 与页面探测，放后台线程执行
        singleThread.execute(() -> {
            PdfDocument document;
            try {
                document = new PdfDocument(comic.getPath(), singleThread);
            } catch (Exception e) {
                LogUtil.e(TAG, "PDF open failed: " + e.getMessage());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showToastAndFinish("PDF 文件打开失败或已损坏");
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    document.close();
                    return;
                }
                setupPager(document);
            });
        });
    }

    private void setupPager(PdfDocument document) {
        pdfDocument = document;
        pdfAdapter = new PdfPageAdapter(this, document, new PdfPageAdapter.OnPageInteractionListener() {
            @Override
            public void onPageTap(float xRatio) {
                int current = pdfPager.getCurrentItem();
                int count = pdfAdapter.getItemCount();
                // 点左侧翻上一页、右侧翻下一页、中间呼出/隐藏工具栏
                if (xRatio < 1f / 3f && current > 0) {
                    pdfPager.setCurrentItem(current - 1, true);
                } else if (xRatio >= 2f / 3f && current < count - 1) {
                    pdfPager.setCurrentItem(current + 1, true);
                } else {
                    toggleToolBarVisibleOrGone();
                }
            }

            @Override
            public void onPan() {
                hideToolBarIfVisible();
            }
        });
        pdfPager.setAdapter(pdfAdapter);
        pdfPager.registerOnPageChangeCallback(pageChangeCallback);

        // 恢复上次阅读进度
        int progress = comic.getReadProgress();
        int count = pdfAdapter.getItemCount();
        if (progress > 0 && progress < count) {
            pdfPager.setCurrentItem(progress, false);
        }
        updateReadProgress();
    }

    @Override
    public void updateReadProgress() {
        if (pdfPager == null || pdfAdapter == null || comic == null) return;
        int position = pdfPager.getCurrentItem();
        int count = pdfAdapter.getItemCount();

        comic.setReadProgress(position);
        if (comic.getTotal() != count) {
            comic.setTotal(count);
        }
        pageCounterText.setText((position + 1) + "/" + count);
    }

    private void showPageJumpDialog() {
        if (pdfPager == null || pdfAdapter == null) return;
        DialogHelper.showPageJumpDialog(this,
                pdfPager.getCurrentItem(), pdfAdapter.getItemCount(),
                page -> pdfPager.setCurrentItem(page, true));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pdfAdapter != null) {
            pdfAdapter.release();
            pdfAdapter = null;
        }
        if (pdfDocument != null) {
            pdfDocument.close();
            pdfDocument = null;
        }
        if (pdfPager != null) {
            pdfPager.unregisterOnPageChangeCallback(pageChangeCallback);
            pdfPager.setAdapter(null);
            pdfPager = null;
        }
    }

}
