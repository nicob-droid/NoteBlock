package com.example.cosmonote;

import android.graphics.Color;

public class Note {
    private long id;
    private String title;
    private String content;
    private int color;
    private int position;
    private long timestamp;

    public Note(long id, String title, String content, int color, int position, long timestamp) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.color = color;
        this.position = position;
        this.timestamp = timestamp;
    }

    // Constructeur sans timestamp (par défaut, timestamp = now)
    public Note(long id, String title, String content, int color, int position) {
        this(id, title, content, color, position, System.currentTimeMillis());
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
    public void setColor(int color) {
        this.color = color;
    }
    public int getColor() {
        return color;
    }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Note other = (Note) obj;

        return id == other.id
                && color == other.color
                && position == other.position
                && (title != null ? title.equals(other.title) : other.title == null)
                && (content != null ? content.equals(other.content) : other.content == null);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + color;
        result = 31 * result + position;
        return result;
    }
}
