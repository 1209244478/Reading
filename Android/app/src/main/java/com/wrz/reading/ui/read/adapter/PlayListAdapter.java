package com.wrz.reading.ui.read.adapter;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.model.Comic;

import java.util.List;

public class PlayListAdapter extends BaseQuickAdapter<Comic, BaseViewHolder> {

    private long currentId;

    public void setSelectedId(long id) {
        int oldPos = this.getData().indexOf(currentId);
        currentId = id;
        int newPos = this.getData().indexOf(id);
        if (oldPos >= 0) notifyItemChanged(oldPos);
        if (newPos >= 0) notifyItemChanged(newPos);
    }

    public PlayListAdapter(int layoutResId, @Nullable List<Comic> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Comic comic) {
        TextView tv_title = holder.getView(R.id.tv_title);

        tv_title.setText(comic.getTitle());

        tv_title.setSelected(currentId == comic.getId());
    }
}
