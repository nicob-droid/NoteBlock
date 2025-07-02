package com.example.noteblock;

import android.graphics.Color;

public class Note {
    private long id;
    private String title;
    private String content;
    private int color;
    private int position;

    public Note(long id, String title, String content, int color, int position) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.color = color;
        this.position = position;
    }


    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getId() { return id; }
    public void setColor(int color) {
        this.color = color;
    }
    public int getColor() {
        return color;
    }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
