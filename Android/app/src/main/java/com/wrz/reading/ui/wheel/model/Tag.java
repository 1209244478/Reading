package com.wrz.reading.ui.wheel.model;

public class Tag {

    private String tag;

    private int open;

    private boolean isSelected = false;

    public Tag(String tag) {
        this.tag = tag;
    }

    public Tag(String tag, int open) {
        this.tag = tag;
        this.open = open;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getOpen() {
        return open;
    }

    public void setOpen(int open) {
        this.open = open;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
