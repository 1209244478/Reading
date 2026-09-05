package com.wrz.reading.ui.read.activity;

import android.content.Context;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.wrz.reading.R;
import com.wrz.reading.ui.main.Log.LogUtil;
import com.wrz.reading.ui.main.utils.DialogHelper;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.view.pdfview.PDFPagerAdapter;
import com.wrz.reading.ui.read.view.pdfview.PDFViewPager;

public class PdfReaderActivity extends BaseReaderActivity {

    public static void start(Context context, Comic comic) {
        BaseReaderActivity.start(context, PdfReaderActivity.class, comic);
    }


    private final String TAG = "PdfReaderActivity";
    private LinearLayout llPdfRoot;

    private PDFViewPager pdfViewPager;
    private TextView pageCounterText;

    @Override
    public int getLayoutId() {
        return R.layout.activity_pdf_reader;
    }

    @Override
    public void initToolBar() {

    }

    @Override
    public void initView() {
        llPdfRoot = findViewById(R.id.ll_pdf_root);
        pageCounterText = findViewById(R.id.page_counter_text);

        pageCounterText.setOnClickListener(v -> showPageJumpDialog());
    }

    @Override
    protected void onComicLoaded(Comic comic) {
        if (isFinishing() || isDestroyed()) return;
        // onComicLoaded 已由 BaseReaderActivity 切回主线程，无需再包一层
        try {
            pdfViewPager = new PDFViewPager(this, comic.getPath(), new PDFPagerAdapter.OnComicClickListener() {
                @Override
                public void onComicClick() {
                    toggleToolBarVisibleOrGone();
                }

                @Override
                public void onScroll() {
                    hideToolBarIfVisible();
                }
            });
        } catch (Exception e) {
            LogUtil.e(TAG, "PDF open failed: " + e.getMessage());
            showToastAndFinish("PDF 文件打开失败或已损坏");
            return;
        }

        llPdfRoot.addView(pdfViewPager);

        pdfViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                updateReadProgress();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        pdfViewPager.setCurrentItem((int) comic.getReadProgress(), false);
    }

    @Override
    public void updateReadProgress() {
        if (pdfViewPager != null) {
            int position = pdfViewPager.getCurrentItem();
            if (comic != null) {
                comic.setReadProgress(position);

                if (comic.getTotal() != pdfViewPager.getCount()) {
                    comic.setTotal(pdfViewPager.getCount());
                }
            }
            pageCounterText.setText((position + 1) + "/" + (pdfViewPager.getCount()));
        }
    }

    private void showPageJumpDialog() {
        if (pdfViewPager == null) return;
        DialogHelper.showPageJumpDialog(this,
                pdfViewPager.getCurrentItem(), pdfViewPager.getCount(),
                page -> pdfViewPager.setCurrentItem(page, true));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pdfViewPager != null) {
            if (pdfViewPager.getAdapter() instanceof PDFPagerAdapter) {
                ((PDFPagerAdapter) pdfViewPager.getAdapter()).close();
            }
            // 移除页面变化监听并从父容器分离，避免泄漏
            pdfViewPager.clearOnPageChangeListeners();
            if (llPdfRoot != null) {
                llPdfRoot.removeView(pdfViewPager);
            }
            pdfViewPager = null;
        }
    }


}