package com.wrz.reading.ui.read.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wrz.reading.R;
import com.wrz.reading.ui.read.fragment.CollectionFragment;

import java.util.List;

/**
 * "移动到集合"对话框的目标列表适配器。
 * 从 CollectionDetailFragment 中提取，降低 Fragment 体积与职责。
 */
public class MoveTargetAdapter extends RecyclerView.Adapter<MoveTargetAdapter.TargetHolder> {

    /** 移动目标项数据 */
    public static class MoveTarget {
        public final long id;
        public final String name;
        public final String subtitle;

        public MoveTarget(long id, String name, String subtitle) {
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
        }
    }

    public interface OnTargetClickListener {
        void onClick(MoveTarget target);
    }

    private final List<MoveTarget> data;
    private OnTargetClickListener listener;

    public MoveTargetAdapter(List<MoveTarget> data) {
        this.data = data;
    }

    public void setOnItemClickListener(OnTargetClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TargetHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_move_target, parent, false);
        return new TargetHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TargetHolder holder, int position) {
        MoveTarget t = data.get(position);
        holder.name.setText(t.name);
        holder.subtitle.setText(t.subtitle);
        // 未分组使用灰色图标，已分组使用蓝色图标
        if (t.id == CollectionFragment.UNCATEGORIZED_ID) {
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.icon.setColorFilter(holder.itemView.getContext().getColor(R.color.ios_text_secondary));
        } else {
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.icon.setColorFilter(holder.itemView.getContext().getColor(R.color.ios_blue));
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class TargetHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView subtitle;

        TargetHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            name = itemView.findViewById(R.id.item_name);
            subtitle = itemView.findViewById(R.id.item_subtitle);
        }
    }
}
