package com.wrz.reading.ui.wheel.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.wrz.reading.R;
import com.wrz.reading.ui.wheel.model.Emoji;
import com.wrz.reading.ui.main.popupView.EmojiPopup;

import java.util.List;

public class EmojiRecyclerviewAdapter extends BaseQuickAdapter<Emoji, BaseViewHolder> {

    EmojiPopup.EmojiClickListener listener;

    String current_emoji = "";

    public EmojiRecyclerviewAdapter(int layoutResId, @Nullable List<Emoji> data, EmojiPopup.EmojiClickListener listener) {
        super(layoutResId, data);

        this.listener = listener;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Emoji emoji) {
        RecyclerView rv_emoji = holder.getView(R.id.rv_emoji);

        rv_emoji.setLayoutManager(new FlexboxLayoutManager(this.getContext()));

        EmojiAdapter emojiAdapter = new EmojiAdapter(R.layout.item_emoji, emoji.getList());

        rv_emoji.setAdapter(emojiAdapter);

        emojiAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (listener != null) {
                listener.emoji(emojiAdapter.getData().get(position));
            }
        });

        for (int i = 0; i < emoji.getList().size(); i++) {
            if (emoji.getList().get(i).equals(current_emoji)) {
                /*for (int j = 5; j >= 0; j--) {
                    if ((i + j) < emoji.getList().size()) {
                        rv_emoji.smoothScrollToPosition(i + j);
                    }
                }*/
                rv_emoji.smoothScrollToPosition(i);
                emojiAdapter.setPosition(i);
                break;
            }
        }
    }

    public void setCurrent_emoji(int page, String current_emoji) {
        this.current_emoji = current_emoji;

        notifyItemChanged(page);
    }
}
