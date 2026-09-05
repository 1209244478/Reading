package com.wrz.reading.ui.read.view.dialog;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wrz.reading.R;
import com.wrz.reading.common.BaseDialog;
import com.wrz.reading.ui.read.adapter.MoveTargetAdapter;

import java.util.ArrayList;
import java.util.List;

public class MoveDialog extends BaseDialog {

    List<MoveTargetAdapter.MoveTarget> targets;

    onClickListener listener;

    public MoveDialog(@NonNull Context context, List<MoveTargetAdapter.MoveTarget> targets) {
        super(context);
        this.targets = targets;
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_move_to_collection;
    }

    @Override
    public void initView() {
        TextView subtitleView = findId(R.id.move_dialog_subtitle);
        RecyclerView listView = findId(R.id.move_target_list);

        subtitleView.setText("选择目标集合");

        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        MoveTargetAdapter adapter = new MoveTargetAdapter(targets);
        listView.setAdapter(adapter);


        adapter.setOnItemClickListener((target -> {
            listener.onItemClick(target);
            this.dismiss();
        }));
        findId(R.id.move_btn_cancel).setOnClickListener(v -> this.dismiss());
        // 创建新集合：创建后刷新目标列表
        findId(R.id.move_btn_create).setOnClickListener(v -> {
            listener.onCreate();
            this.dismiss();
        });
    }

    public interface onClickListener {
        void onItemClick(MoveTargetAdapter.MoveTarget target);

        void onCreate();
    }


    public void setListener(onClickListener listener) {
        this.listener = listener;
    }

    public void setMoveTitle(String title) {
        setText(R.id.move_dialog_title, title);
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
