package com.wrz.reading.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Event implements Serializable {

    public static int REPEAT_NONE = 0;
    public static int REPEAT_WEEK = 1;
    public static int REPEAT_MONTH = 2;
    public static int REPEAT_YEAR = 3;
    private long id;
    private String title;
    private LocalDateTime date;
    private String category;
    private String color;
    private int repeat;

    public Event() {
    }


    public Event(long id, String title, LocalDateTime date, String category, String color, int repeat) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.category = category;
        this.color = color;
        this.repeat = repeat;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getRepeat() {
        return repeat;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }
}