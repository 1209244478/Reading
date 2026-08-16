package com.wrz.reading.ui.read.view.pdfview;

import android.content.Context;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

public class PDFViewPager extends ViewPager {

    protected Context context;
    private PDFPagerAdapter adapter;

    private final PDFPagerAdapter.OnComicClickListener listener;

    public PDFViewPager(Context context, String pdfPath, PDFPagerAdapter.OnComicClickListener listener) {
        super(context);
        this.context = context;
        this.listener = listener;

        init(pdfPath);
    }

    protected void init(String pdfPath) {
        setClickable(true);
        initAdapter(context, pdfPath);
    }

    protected void initAdapter(Context context, String pdfPath) {
        adapter = new PDFPagerAdapter.Builder(context, listener)
                .setPdfPath(pdfPath)
                .setOffScreenSize(getOffscreenPageLimit())
                .setOnPageClickListener(clickListener)
                .create();

        setAdapter(adapter);
    }

    public int getCount() {
        if (adapter != null) {
            return adapter.getCount();
        }
        return 0;
    }

    private final OnPageClickListener clickListener = (view, x, y) -> {
        int item = getCurrentItem();
        int total = getChildCount();

        if (x < 0.33f && item > 0) {
            item -= 1;
            setCurrentItem(item);
        } else if (x >= 0.67f && item < total - 1) {
            item += 1;
            setCurrentItem(item);
        }
    };

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }
}
