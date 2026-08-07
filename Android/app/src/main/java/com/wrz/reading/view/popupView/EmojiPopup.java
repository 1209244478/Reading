package com.wrz.reading.view.popupView;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.lxj.xpopup.core.BottomPopupView;
import com.wrz.reading.R;
import com.wrz.reading.data.EmojiData;
import com.wrz.reading.model.Emoji;
import com.wrz.reading.ui.wheel.adapter.EmojiAdapter;
import com.wrz.reading.ui.wheel.adapter.EmojiRecyclerviewAdapter;

import java.util.ArrayList;
import java.util.List;

public class EmojiPopup extends BottomPopupView {

    private ViewPager2 vp_emoji;

    private EmojiClickListener listener;
    private EmojiClickListener emojiClickListener;

    private final ArrayList<Emoji> lists = new ArrayList<>();

    private final ArrayList<String> quick_list = new ArrayList<>();
    private EmojiRecyclerviewAdapter adapter;
    private RecyclerView rv_emoji_quick;

    private String current_emoji;
    private int page = 0;

    public EmojiPopup(@NonNull Context context, String current_emoji, EmojiClickListener listener) {
        super(context);

        this.listener = listener;

        this.current_emoji = current_emoji;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.popup_emoji;
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        emojiClickListener = emoji -> {
            if (listener != null) {
                listener.emoji(emoji);
            }
            dismiss();
        };

        // 使用EmojiData获取所有emoji分组
        List<Emoji> allGroups = EmojiData.getAllGroups();
        lists.addAll(allGroups);

        for (int i = 0; i < lists.size(); i++) {
            Emoji emoji = lists.get(i);
            quick_list.add(emoji.getTitle());
            if (emoji.getList().contains(current_emoji)) {
                page = i;
            }
        }

        vp_emoji = findViewById(R.id.vp_emoji);

        adapter = new EmojiRecyclerviewAdapter(R.layout.item_recycleview, lists, emojiClickListener);

        vp_emoji.setAdapter(adapter);


        rv_emoji_quick = findViewById(R.id.rv_emoji_quick);
        rv_emoji_quick.setLayoutManager(new LinearLayoutManager(this.getContext(), LinearLayoutManager.HORIZONTAL, false));

        EmojiAdapter emojiAdapter = new EmojiAdapter(R.layout.item_emoji_small, quick_list);
        rv_emoji_quick.setAdapter(emojiAdapter);

        emojiAdapter.setOnItemClickListener((adapter, view, position) -> {
            vp_emoji.setCurrentItem(position);
        });

        vp_emoji.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                emojiAdapter.setPosition(position);
            }
        });
    }


    @Override
    protected void onShow() {
        super.onShow();
        vp_emoji.setCurrentItem(page);

        adapter.setCurrent_emoji(page, current_emoji);

        rv_emoji_quick.smoothScrollToPosition(page);
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

    public interface EmojiClickListener {
        void emoji(String emoji);
    }

}
