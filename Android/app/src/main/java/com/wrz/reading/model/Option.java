package com.wrz.reading.model;

import com.chad.library.adapter.base.entity.MultiItemEntity;

public class Option implements MultiItemEntity {
    private String option;
    private int color = -1;

    private int weight = 1;
    private int percent = -1;

    private boolean isSelected = false;

    private int itemType = 0;

    public Option(String option) {
        this.option = option;
    }
    public Option(int itemType, String option) {
        this.option = option;
        this.itemType = itemType;
    }

    public Option(String option, int color, int weight) {
        this.option = option;
        this.color = color;
        this.weight = weight;
    }

    public Option(String option, int weight) {
        this.option = option;
        this.weight = weight;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    @Override
    public int getItemType() {
        return itemType;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }
}