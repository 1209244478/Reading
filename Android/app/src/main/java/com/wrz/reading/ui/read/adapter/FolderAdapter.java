package com.wrz.reading.ui.read.adapter;

import android.view.View;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.wrz.reading.R;
import com.wrz.reading.ui.read.model.FolderBean;
import com.wrz.reading.ui.read.utils.FileUtils;

import java.util.List;

public class FolderAdapter extends BaseQuickAdapter<FolderBean, BaseViewHolder> {

    public FolderAdapter(int layoutResId, List<FolderBean> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, FolderBean item) {
        helper.setText(R.id.tv_folder, item.getFile().getName());

        helper.setGone(R.id.checkIcon, !item.isSelected());

        item.setSelected(helper.getView(R.id.checkIcon).getVisibility() == View.VISIBLE);

        if (item.isExist()) {
            helper.setGone(R.id.select_indicator, true);
            helper.setGone(R.id.tv_exist, false);
        } else {
            helper.setGone(R.id.select_indicator, false);
            helper.setGone(R.id.tv_exist, true);
        }

        ImageView file_icon = helper.getView(R.id.file_icon);

        if (item.getFile().isDirectory()) {
            file_icon.setImageResource(R.drawable.ic_folder);
        } else if (item.getFile().getName().endsWith(FileUtils.SPECIAL_FORMATS)) {
            file_icon.setImageResource(R.drawable.folder_zip_24px);
        } else if (item.getFile().getName().endsWith(FileUtils.PDF_FORMATS)) {
            file_icon.setImageResource(R.drawable.pdf_24px);
        } else if (item.getFile().getName().endsWith(FileUtils.EPUB_FORMATS)) {
            file_icon.setImageResource(R.drawable.menu_book_24px);
        } else /*if (item.getFile().getName().endsWith(".excel")) {*/ {
            file_icon.setImageResource(R.drawable.ic_file_excel);
        }

    }

}
