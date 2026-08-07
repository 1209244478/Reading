package com.wrz.reading.ui.read.fragment;

import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.SeekBar;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseFragment;
import com.wrz.reading.model.Comic;
import com.wrz.reading.ui.read.activity.EpubReaderActivity;
import com.wrz.reading.view.epubview.ObservableWebView;
import com.wrz.reading.view.epubview.VerticalSeekbar;

import java.lang.ref.WeakReference;

import nl.siegmann.epublib.domain.Book;

public class EPubReaderFragment extends BaseFragment {
    private static final String TAG = "EPubReaderFragment";
    private static final String BUNDLE_POSITION = "position";
    private static final String BUNDLE_BOOK = "book";
    private static final String BUNDLE_EPUB_FILE_NAME = "filename";
    private static final String BUNDLE_IS_SMIL_AVAILABLE = "smilavailable";
    private static final String BUNDLE_COMIC_ID = "comicId";

    VerticalSeekbar mScrollSeekbar;
    ObservableWebView mWebview;

    private int mPosition = -1;
    private Book mBook = null;
    private String mEpubFileName = null;
    private boolean mIsSmilAvailable;

    private int mScrollY;

    private EpubReaderActivity activity;

    private Animation mFadeInAnimation, mFadeOutAnimation;
    private Handler mHandler = new Handler();
    private long mComicId;
    private volatile Comic comic;

    public static Fragment newInstance(int position, Book book, String epubFileName, boolean isSmilAvailable, long comicId) {
        EPubReaderFragment fragment = new EPubReaderFragment();
        Bundle args = new Bundle();
        args.putInt(BUNDLE_POSITION, position);
        args.putSerializable(BUNDLE_BOOK, book);
        args.putString(BUNDLE_EPUB_FILE_NAME, epubFileName);
        args.putSerializable(BUNDLE_IS_SMIL_AVAILABLE, isSmilAvailable);
        args.putSerializable(BUNDLE_COMIC_ID, comicId);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        if (getArguments() != null) {
            mPosition = getArguments().getInt(BUNDLE_POSITION);
            mBook = (Book) getArguments().getSerializable(BUNDLE_BOOK);
            mEpubFileName = getArguments().getString(BUNDLE_EPUB_FILE_NAME);
            mIsSmilAvailable = getArguments().getBoolean(BUNDLE_IS_SMIL_AVAILABLE);
            mComicId = getArguments().getLong(BUNDLE_COMIC_ID);
        }
        return super.onCreateView(inflater, container, state);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mScrollSeekbar = view.findViewById(R.id.scrollSeekbar);
        mWebview = view.findViewById(R.id.contentWebView);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_epub_reader;
    }

    @Override
    public void initView() {

    }

    @Override
    public void initData() {
        activity = (EpubReaderActivity) getActivity();

        if (getArguments() == null) return;
        mPosition = getArguments().getInt(BUNDLE_POSITION);
        mBook = (Book) getArguments().getSerializable(BUNDLE_BOOK);
        mEpubFileName = getArguments().getString(BUNDLE_EPUB_FILE_NAME);
        mIsSmilAvailable = getArguments().getBoolean(BUNDLE_IS_SMIL_AVAILABLE);
    }

    private static class SafeHandler extends Handler {
        private final WeakReference<Fragment> fragmentRef;

        SafeHandler(Fragment fragment) {
            super(Looper.getMainLooper());
            fragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            Fragment fragment = fragmentRef.get();
            if (fragment != null && fragment.isAdded()) {
                // 处理消息
            }
        }
    }

    private SafeHandler mSafeHandler;

    @Override
    public void configViews() {
        initAnimations();

        initSeekbar();

        mSafeHandler = new SafeHandler(this);

        singleThread.execute(() -> {
            comic = MyApplication.comicDatabase.comicDao().getComicById(mComicId);

            mSafeHandler.post(() -> {
                Fragment fragment = mSafeHandler.fragmentRef.get();
                if (fragment != null && fragment.isAdded() && fragment.getView() != null) {
                    initWebView();
                }
            });

        });
    }

    private void initSeekbar() {

        mScrollSeekbar.setFragment(this);
        if (mScrollSeekbar.getProgressDrawable() != null)
            mScrollSeekbar.getProgressDrawable()
                    .setColorFilter(ContextCompat.getColor(mContext, R.color.colorAccent),
                            PorterDuff.Mode.SRC_IN);

        mScrollSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress, boolean fromUser) {
                if (fromUser) {
                    mWebview.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mWebview.setScrollY((int) (mWebview.getContentHeight() * mWebview.getScale() * progress / seekBar.getMax()));
                        }
                    }, 200);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    @SuppressLint("JavascriptInterface")
    private void initWebView() {
        mWebview.setFragment(this);
        mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.setVerticalScrollBarEnabled(false);
        mWebview.getSettings().setAllowFileAccess(true);
        mWebview.setHorizontalScrollBarEnabled(false);
        mWebview.addJavascriptInterface(this, "Highlight");

        mWebview.setScrollListener(percent -> {
            if (comic != null) {
                comic.getPercentMap().put(mPosition, percent);
            }
            if (mWebview.getScrollY() != 0) {
                mScrollY = mWebview.getScrollY();
            }

            int height = (int) Math.floor(mWebview.getContentHeight() * mWebview.getScale());
            int webViewHeight = mWebview.getMeasuredHeight();
            mScrollSeekbar.setMax(height - webViewHeight);

            mScrollSeekbar.setProgress(percent);
        });

        mWebview.getSettings().setDefaultTextEncodingName("utf-8");

        String herf = activity.getPageHref(mPosition);

        mWebview.loadUrl("file://" + herf);

    }

    private boolean isNight() {
        return getResources().getBoolean(R.bool.is_night);
    }

    private void initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.fadein);
        mFadeInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mScrollSeekbar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mFadeOutAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.fadeout);
        mFadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mScrollSeekbar.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }


    private final Runnable mHideSeekbarRunnable = this::fadeoutSeekbarIfVisible;

    public void fadeInSeekbarIfInvisible() {
        if (mScrollSeekbar != null && !isVisible(mScrollSeekbar)) {
            mScrollSeekbar.startAnimation(mFadeInAnimation);
        }
    }

    public void fadeoutSeekbarIfVisible() {
        if (mScrollSeekbar != null && isVisible(mScrollSeekbar)) {
            mScrollSeekbar.startAnimation(mFadeOutAnimation);
        }
    }

    public void removeCallback() {
        mHandler.removeCallbacks(mHideSeekbarRunnable);
    }

    public void startCallback() {
        mHandler.postDelayed(mHideSeekbarRunnable, 3000);
    }


    @Override
    public void onPause() {
        super.onPause();
        /*if (comic != null && comic.getPercentMap().get(mPosition) != null) {
            executor.submit(() -> MyApplication.database.comicDao().updateComic(comic));
        }*/
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
        if (mSafeHandler != null) {
            mSafeHandler.removeCallbacksAndMessages(null);
        }
    }
}
