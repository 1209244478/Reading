package com.wrz.reading.ui.read.model;

import java.io.File;

public class FolderBean {

    private File file;

    private boolean selected = false;

    private boolean exist = false;

    public FolderBean(File file) {
        this.file = file;
    }

    public FolderBean(File file, boolean selected) {
        this.file = file;
        this.selected = selected;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isExist() {
        return exist;
    }

    public void setExist(boolean exist) {
        this.exist = exist;
    }
}
