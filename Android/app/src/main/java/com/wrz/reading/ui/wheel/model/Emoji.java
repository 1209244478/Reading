package com.wrz.reading.ui.wheel.model;

import java.util.List;

public class Emoji {

    private String title;

    private List<String> list;

    public Emoji(String title, List<String> list) {
        this.title = title;
        this.list = list;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getList() {
        return list;
    }

    public void setList(List<String> list) {
        this.list = list;
    }
}
