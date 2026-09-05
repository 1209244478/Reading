package com.wrz.reading.ui.read.adapter;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.Comic;
import com.wrz.reading.ui.read.model.FileType;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayListAdapter extends BaseQuickAdapter<Comic, BaseViewHolder> {
    private final String TAG = "PlayListAdapter";
    private long currentId;
    /** 记录每个目录型 Comic 的展开状态 */
    private final Map<Long, Boolean> expandedMap = new HashMap<>();
    /** 子项点击回调：(parentComic, childFile) */
    public interface OnSubItemClickListener {
        void onSubItemClick(Comic parent, File videoFile);
    }

    private OnSubItemClickListener subItemClickListener;

    public void setOnSubItemClickListener(OnSubItemClickListener listener) {
        this.subItemClickListener = listener;
    }

    public void setSelectedId(long id) {
        long oldId = currentId;
        currentId = id;
        for (int i = 0; i < getData().size(); i++) {
            Comic c = getData().get(i);
            if (c.getId() == oldId || c.getId() == id) {
                notifyItemChanged(i);
            }
        }
    }

    private String subFileName;

    public void setSubSelect(String fileName) {
        this.subFileName = fileName;
    }

    public PlayListAdapter(int layoutResId, @Nullable List<Comic> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Comic comic) {
        TextView tv_title = holder.getView(R.id.tv_title);
        ImageView ivArrow = holder.getView(R.id.iv_expand_arrow);
        RecyclerView subRecycler = holder.getView(R.id.sub_recycler_view);
        ProgressBar comicProgressBar = holder.getView(R.id.comic_progress_bar);

        tv_title.setText(comic.getTitle());
        holder.findView(R.id.ll_item).setSelected(currentId == comic.getId());

        comicProgressBar.setMax((int) comic.getTotal());
        FileType fileType = comic.getFileTypeEnum();

        if (fileType == FileType.VIDEO || fileType == FileType.MUSIC) {
            comicProgressBar.setProgress((int) comic.getVideoPosition());
        } else {
            comicProgressBar.setProgress(comic.getReadProgress());
        }

        // 判断是否为目录
        File dir = new File(comic.getPath());
        boolean isDir = dir.exists() && dir.isDirectory();

        if (isDir) {
            // 扫描目录下的视频文件
            List<File> videoFiles = FileUtils.getVideoFiles(dir);
            Log.e(TAG, "convert: " + videoFiles.isEmpty() );
            if (!videoFiles.isEmpty()) {
                ivArrow.setVisibility(View.VISIBLE);

                boolean expanded = Boolean.TRUE.equals(expandedMap.get(comic.getId()));
                /*ivArrow.setRotation(expanded ? 180f : 0f);*/
                subRecycler.setVisibility(expanded ? View.VISIBLE : View.GONE);

                if (expanded) {
                    setupSubList(subRecycler, comic, videoFiles);
                }

                // 点击主行切换展开/收起
                holder.findView(R.id.ll_item).setOnClickListener(v -> {
                    boolean isExpanded = Boolean.TRUE.equals(expandedMap.get(comic.getId()));
                    expandedMap.put(comic.getId(), !isExpanded);
                    notifyItemChanged(holder.getAdapterPosition());
                });
            } else {
                ivArrow.setVisibility(View.GONE);
                subRecycler.setVisibility(View.GONE);
                /*holder.findView(R.id.ll_item).setOnClickListener(null);*/
            }
        } else {
            ivArrow.setVisibility(View.GONE);
            subRecycler.setVisibility(View.GONE);
            /*holder.findView(R.id.ll_item).setOnClickListener(null);*/
        }
    }



    /** 设置子列表 */
    private void setupSubList(RecyclerView subRecycler, Comic parent, List<File> videoFiles) {
        if (subRecycler.getAdapter() != null) {
            // 已设置过，只更新数据
            ((SubAdapter) subRecycler.getAdapter()).setData(videoFiles);
            return;
        }
        SubAdapter subAdapter = new SubAdapter(videoFiles);
        subAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (subItemClickListener != null) {
                subItemClickListener.onSubItemClick(parent, videoFiles.get(position));
            }
        });
        subAdapter.setSelected(subFileName);
        subRecycler.setLayoutManager(new LinearLayoutManager(subRecycler.getContext()));
        subRecycler.setAdapter(subAdapter);
    }

    /** 子列表 Adapter */
    private static class SubAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

        private String fileName;

        public void setSelected(String name) {
            String oldFileName = fileName;
            fileName = name;
            for (int i = 0; i < getData().size(); i++) {
                File file = getData().get(i);
                if (file != null && file.getName().equals(name)) {
                    notifyItemChanged(i);
                }
            }
        }

        SubAdapter(List<File> data) {
            super(R.layout.item_play_list_sub, data);
        }

        @Override
        protected void convert(@NonNull BaseViewHolder holder, File file) {
            holder.setText(R.id.tv_sub_title, file.getName());

            holder.findView(R.id.tv_sub_title).setSelected(file.getName().equals(fileName));
        }

        void setData(List<File> newData) {
            getData().clear();
            getData().addAll(newData);
            notifyDataSetChanged();
        }
    }
}
