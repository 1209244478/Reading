package com.wrz.reading.ui.wheel.model;

import androidx.annotation.NonNull;

public enum Request {


    Create_Wheel(1,"创建转盘"),
    Edit_Wheel(2,"编辑转盘"),
    Create_Template(3,"创建模板"),
    Edit_Template(4,"编辑模板"),
    Create_Wheel_Fron_Blank(5,"从空白转盘创建"),
    ;
    private final int id;
    private final String code;

    Request(int id, @NonNull String code) {
        this.id = id;
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    public int getId() {
        return id;
    }


}
