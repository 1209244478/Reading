package com.wrz.reading.ui.main.view;

import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.wrz.reading.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 底部导航栏：替代 BottomNavigationView 的轻量实现，仅图标无文字。
 * item 的 view id 即导航 destination id（mainFragment/wheelFragment/randomFragment/settingFragment）。
 * <p>
 * 使用自实现的原因：Material BottomNavigationView 在 HyperOS/Android 15 上测量行为不可控
 * （navigationBars inset 会参与 MenuView 测量、wrap_content 期望高度 80dp 且 MenuView 顶对齐），
 * 导致三键导航下图标不渲染、各导航模式下图标无法稳定居中。自实现保证图标恒定居中、行为确定。
 */
public class BottomNavBar extends LinearLayout {

    /** item 点击回调，返回 true 表示已处理并更新选中态 */
    public interface OnItemSelectedListener {
        boolean onNavigationItemSelected(int itemId);
    }

    private final List<ImageView> iconViews = new ArrayList<>();
    private OnItemSelectedListener listener;
    private int selectedItemId = View.NO_ID;
    private final int checkedColor;
    private final int defaultColor;

    public BottomNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 电视端使用新粗野主义配色：选中亮黄、未选中米灰；手机/平板保持原蓝色
        boolean tv = context.getResources().getBoolean(R.bool.is_tv);
        checkedColor = ContextCompat.getColor(context,
                tv ? R.color.tv_yellow : R.color.ios_blue);
        defaultColor = ContextCompat.getColor(context,
                tv ? R.color.tv_muted : R.color.bottom_nav_default);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                iconViews.add((ImageView) child);
                child.setOnClickListener(v -> selectFromUser(v.getId()));
                // TV 遥控器：Tab 可聚焦，使用自定义焦点高亮（关闭系统默认灰色方块）
                child.setFocusable(true);
                child.setClickable(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    child.setDefaultFocusHighlightEnabled(false);
                }
                child.setBackgroundResource(R.drawable.bg_tv_focusable);
            }
        }
    }

    private void selectFromUser(int itemId) {
        if (itemId == selectedItemId) {
            return;
        }
        if (listener != null && listener.onNavigationItemSelected(itemId)) {
            setSelectedItemId(itemId);
        }
    }

    /** 程序化设置选中项，不触发 OnItemSelectedListener（与 BottomNavigationView 语义一致） */
    public void setSelectedItemId(int itemId) {
        selectedItemId = itemId;
        for (ImageView iconView : iconViews) {
            boolean selected = iconView.getId() == itemId;
            iconView.setSelected(selected);
            iconView.setColorFilter(selected ? checkedColor : defaultColor);
        }
    }

    public int getSelectedItemId() {
        return selectedItemId;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // 忽略状态恢复：选中态由代码管理。横↔竖屏切换时同一 id（bottom_navigation）
        // 在 BottomNavBar 与 NavigationRailView 两种组件间切换，异类 SavedState
        // （NavigationBarView$SavedState）传入默认实现会抛 IllegalArgumentException
        super.onRestoreInstanceState(null);
    }
}
