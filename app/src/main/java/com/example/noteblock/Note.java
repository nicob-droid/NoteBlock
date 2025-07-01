package com.example.noteblock;

import android.graphics.Color;

public class Note {
    private int id;
    private String title;
    private String content;
    private int color;

    public Note(int id, String title, String content, int color) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.color = color;
    }
/*
    public Note(int id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.color = Color.parseColor("#FFFFFF");
    }*/

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getId() { return id; }
    public void setColor(int color) {
        this.color = color;
    }
    public int getColor() {
        return color;
    }

}
