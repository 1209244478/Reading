package com.wrz.reading.view.dialog;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.model.Comic;
import com.wrz.reading.ui.read.adapter.PlayListAdapter;

import java.util.ArrayList;
import java.util.List;

public class PlayListDialog extends BaseDialog {

    onClickListener listener;
    private PlayListAdapter adapter;
    private RecyclerView recycler_view;

    public interface onClickListener {
        void onClicked(Comic c);
    }

    ArrayList<Comic> comics = new ArrayList<>();

    public PlayListDialog(@NonNull Context context, List<Comic> comics) {
        super(context);

        this.comics.addAll(comics);
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_play_list;
    }

    @Override
    public void initView() {
        recycler_view = parentView.findViewById(R.id.recycler_view);

        adapter = new PlayListAdapter(R.layout.item_play_list, comics);

        recycler_view.setAdapter(adapter);
        recycler_view.setLayoutManager(new LinearLayoutManager(parentView.getContext()));

        adapter.setOnItemClickListener((adapter1, view, position) -> {
            adapter.setSelectedId(comics.get(position).getId());
            if (listener != null) listener.onClicked(comics.get(position));
        });
    }

    public void setCurrentId(long id) {
        if (adapter != null) {
            adapter.setSelectedId(id);

            int pos = -1;

            for (Comic c : comics) {
                if (c.getId() == id) {
                    pos = comics.indexOf(c);
                }
            }
            Log.e("PlayListDialog", "setCurrentId: " + pos);
            if (pos != -1) {
                recycler_view.scrollToPosition(pos);
            }
        }
    }

    public void setListener(onClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void setDialogSize() {
        if (this.getWindow() != null) {
            this.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
