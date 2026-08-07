package com.wrz.reading.view.dialog;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.wrz.reading.R;
import com.wrz.reading.bean.BookMixAToc;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.ui.read.adapter.TocListAdapter;

import java.util.List;

public class ChaptersDialog extends BaseDialog {

    private List<BookMixAToc.mixToc.Chapters> list;

    public TocListAdapter mTocListAdapter;

    public ChaptersDialog(@NonNull Context context, List<BookMixAToc.mixToc.Chapters> chapters) {
        super(context);
        this.list = chapters;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_recycler_view;
    }

    @Override
    public void initView() {
        // 初始化 RecyclerView
        RecyclerView recyclerView = parentView.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 创建适配器
        mTocListAdapter = new TocListAdapter(R.layout.item_book_read_toc_list, list);
        recyclerView.setAdapter(mTocListAdapter);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mTocListAdapter.setOnItemClickListener(listener);
    }

    public void setCurrentChapter(int currentChapter) {
        mTocListAdapter.setCurrentChapter(currentChapter);
    }

    @Override
    public void setDialogSize() {
        if (this.getWindow() != null) {
            this.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

}
