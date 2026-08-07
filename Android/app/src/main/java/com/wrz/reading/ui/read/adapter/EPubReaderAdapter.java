package com.wrz.reading.ui.read.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.wrz.reading.ui.read.fragment.EPubReaderFragment;

import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.SpineReference;

/**
 * EPUB阅读器适配器
 * 注意：由于DirectionalViewpager基于旧版ViewPager，这里仍使用FragmentPagerAdapter
 */
@SuppressWarnings("deprecation")
public class EPubReaderAdapter extends FragmentPagerAdapter {

    private final List<SpineReference> mSpineReferences;
    private final Book mBook;
    private final String mEpubFileName;
    private final long mComicId;

    public EPubReaderAdapter(FragmentManager fm, List<SpineReference> spineReferences, Book book, String epubFilename, long comicId) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.mSpineReferences = spineReferences;
        this.mBook = book;
        this.mEpubFileName = epubFilename;
        this.mComicId = comicId;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return EPubReaderFragment.newInstance(position, mBook, mEpubFileName, false, mComicId);
    }

    @Override
    public int getCount() {
        return mSpineReferences.size();
    }
}
