package com.wrz.reading.ui.read.model;

import java.util.ArrayList;
import java.util.Objects;

public class SubProgress {
    public String title;
    public long progress;

    public SubProgress(String title, long progress) {
        this.title = title;
        this.progress = progress;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getProgress() {
        return progress;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public static long getSubProgressIndex(String title, ArrayList<SubProgress> list) {
        long index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getTitle(), title)) {
                index = i;
                break;
            }
        }

        return index;
    }

    public static long getSubProgress(String title, ArrayList<SubProgress> list) {
        long index = getSubProgressIndex(title, list);
        if (index != -1) {
            return list.get((int) index).getProgress();
        }

        return index;
    }

    public static void updateSubProgress(String title, long progress, ArrayList<SubProgress> list) {
        long index = SubProgress.getSubProgressIndex(title, list);

        if (index == -1) {
            list.add(new SubProgress(title, progress));
        } else {
            list.get((int) index).setProgress(progress);
        }
    }
}