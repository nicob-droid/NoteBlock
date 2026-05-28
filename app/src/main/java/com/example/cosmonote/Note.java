package com.example.cosmonote;

public class Note {
    private long id;
    private String firebaseDocId; // UUID unique, partagé entre utilisateurs
    private String ownerUid; // propriétaire Firestore de la note
    private String ownerDisplayLabel; // libellé lisible du propriétaire (nom/email)
    private String sharedWithSummary; // ex: "aliceUid, bobUid"
    private final String title;
    private final String content;
    private int color;
    private int position;
    private long timestamp;

    public Note(long id, String firebaseDocId, String title, String content, int color, int position, long timestamp) {
        this(id, firebaseDocId, null, title, content, color, position, timestamp);
    }

    public Note(long id, String firebaseDocId, String ownerUid, String title, String content, int color, int position, long timestamp) {
        this(id, firebaseDocId, ownerUid, title, content, color, position, timestamp, null);
    }

    public Note(long id, String firebaseDocId, String ownerUid, String title, String content, int color, int position, long timestamp, String sharedWithSummary) {
        this(id, firebaseDocId, ownerUid, title, content, color, position, timestamp, sharedWithSummary, null);
    }

    public Note(long id, String firebaseDocId, String ownerUid, String title, String content, int color, int position, long timestamp, String sharedWithSummary, String ownerDisplayLabel) {
        this.id = id;
        this.firebaseDocId = firebaseDocId;
        this.ownerUid = ownerUid;
        this.sharedWithSummary = sharedWithSummary;
        this.ownerDisplayLabel = ownerDisplayLabel;
        this.title = title;
        this.content = content;
        this.color = color;
        this.position = position;
        this.timestamp = timestamp;
    }

    // Constructeur sans timestamp (par défaut, timestamp = now)
    public Note(long id, String firebaseDocId, String title, String content, int color, int position) {
        this(id, firebaseDocId, title, content, color, position, System.currentTimeMillis());
    }

    public Note(long id, String firebaseDocId, String ownerUid, String title, String content, int color, int position) {
        this(id, firebaseDocId, ownerUid, title, content, color, position, System.currentTimeMillis());
    }


    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFirebaseDocId() { return firebaseDocId; }
    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }
    public String getOwnerDisplayLabel() { return ownerDisplayLabel; }
    public void setOwnerDisplayLabel(String ownerDisplayLabel) { this.ownerDisplayLabel = ownerDisplayLabel; }
    public String getSharedWithSummary() { return sharedWithSummary; }
    public void setSharedWithSummary(String sharedWithSummary) { this.sharedWithSummary = sharedWithSummary; }
    public void setColor(int color) { this.color = color; }
    public int getColor() { return color; }
    public int getPosition() { return position; }
    public long getTimestamp() { return timestamp; }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Note other = (Note) obj;

        return id == other.id
                && color == other.color
                && position == other.position
                && (firebaseDocId != null ? firebaseDocId.equals(other.firebaseDocId) : other.firebaseDocId == null)
                && (ownerUid != null ? ownerUid.equals(other.ownerUid) : other.ownerUid == null)
                && (ownerDisplayLabel != null ? ownerDisplayLabel.equals(other.ownerDisplayLabel) : other.ownerDisplayLabel == null)
                && (sharedWithSummary != null ? sharedWithSummary.equals(other.sharedWithSummary) : other.sharedWithSummary == null)
                && (title != null ? title.equals(other.title) : other.title == null)
                && (content != null ? content.equals(other.content) : other.content == null);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (firebaseDocId != null ? firebaseDocId.hashCode() : 0);
        result = 31 * result + (ownerUid != null ? ownerUid.hashCode() : 0);
        result = 31 * result + (ownerDisplayLabel != null ? ownerDisplayLabel.hashCode() : 0);
        result = 31 * result + (sharedWithSummary != null ? sharedWithSummary.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + color;
        result = 31 * result + position;
        return result;
    }
}
