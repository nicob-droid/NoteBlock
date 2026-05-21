package com.example.cosmonote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.util.Log;

import com.example.cosmonote.Utils.HashUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NoteDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notes_db";
    private static final int DATABASE_VERSION = 4;
    public static final String TABLE_NAME = "notes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FIREBASE_DOC_ID = "firebase_doc_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_COLOR = "color";
    public static final String COLUMN_POSITION = "position";


    public NoteDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_FIREBASE_DOC_ID + " TEXT UNIQUE,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_CONTENT + " TEXT,"
                + COLUMN_COLOR + " INTEGER DEFAULT 16777215,"
                + COLUMN_POSITION + " INTEGER"
                + ")";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_POSITION + " INTEGER");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_FIREBASE_DOC_ID + " TEXT");
            // Générer un UUID pour les notes existantes qui n'en ont pas
            Cursor cursor = db.rawQuery("SELECT id FROM " + TABLE_NAME + " WHERE " + COLUMN_FIREBASE_DOC_ID + " IS NULL", null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    ContentValues cv = new ContentValues();
                    cv.put(COLUMN_FIREBASE_DOC_ID, java.util.UUID.randomUUID().toString());
                    db.update(TABLE_NAME, cv, "id = ?", new String[]{String.valueOf(id)});
                }
                cursor.close();
            }
            // Ajouter l'index unique après migration
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_firebase_doc_id ON " + TABLE_NAME + "(" + COLUMN_FIREBASE_DOC_ID + ")");
        }
    }

    public long insertNote(String title, String content, int color, int position) throws Exception {
        return insertNote(java.util.UUID.randomUUID().toString(), title, content, color, position);
    }

    public long insertNote(String firebaseDocId, String title, String content, int color, int position) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_FIREBASE_DOC_ID, firebaseDocId);
        cv.put(COLUMN_TITLE, title);
        cv.put(COLUMN_CONTENT, content);
        cv.put(COLUMN_COLOR, color);
        cv.put(COLUMN_POSITION, position);
        return db.insert(TABLE_NAME, null, cv);
    }

    public void updateNote(long id, String title, String content, int color, int position) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TITLE, title);
        cv.put(COLUMN_CONTENT, content);
        cv.put(COLUMN_COLOR, color);
        cv.put(COLUMN_POSITION, position);
        db.update(TABLE_NAME, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateAllNotePositions(List<Note> notesList) throws Exception {
        SQLiteDatabase db = getWritableDatabase();

        for (int i = 0; i < notesList.size(); i++) {
            long id = notesList.get(i).getId();
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_POSITION, i);
            db.update(TABLE_NAME, cv, "id = ?", new String[]{String.valueOf(id)});
        }
    }

    public int deleteNoteById(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
    }

    public int deleteAllNotes() {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_NAME, null, null);
    }

    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, "position ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String firebaseDocId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_DOC_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
                int position = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSITION));
                try {
                    notes.add(new Note(id, firebaseDocId, title, content, color, position));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            cursor.close();
        }
        return notes;
    }

    public Note getNoteById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Note note = null;

        Cursor cursor = db.query(TABLE_NAME, null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String firebaseDocId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_DOC_ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
            int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
            int position = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSITION));

            note = new Note(id, firebaseDocId, title, content, color, position);
            cursor.close();
        }

        return note;
    }

    /**
     * Cherche une note par son firebaseDocId (UUID unique partagé).
     */
    public Note getNoteByFirebaseDocId(String firebaseDocId) {
        if (firebaseDocId == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Note note = null;

        Cursor cursor = db.query(TABLE_NAME, null, COLUMN_FIREBASE_DOC_ID + " = ?",
                new String[]{firebaseDocId}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
            String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
            int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
            int position = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSITION));

            note = new Note(id, firebaseDocId, title, content, color, position);
            cursor.close();
        }

        return note;
    }

    public List<Note> getAllNotesRaw() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, firebase_doc_id, title, content, color, position FROM notes ORDER BY position ASC", null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String firebaseDocId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_DOC_ID));
                String encryptedTitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String encryptedContent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
                int position = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSITION));
                Note note = new Note(id, firebaseDocId, encryptedTitle, encryptedContent, color, position);
                notes.add(note);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return notes;
    }



    public void updateNoteEncrypted(long noteId, String encryptedTitle, String encryptedContent) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", encryptedTitle);
        values.put("content", encryptedContent);

        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(noteId)});
        db.close();
    }

    /**
     * Insère ou met à jour une note en se basant sur le firebaseDocId (UUID unique).
     * Évite les doublons entre utilisateurs qui ont des auto-increments différents.
     */
    public void insertOrUpdateNote(Note note) throws Exception {
        Note existing = getNoteByFirebaseDocId(note.getFirebaseDocId());
        if (existing != null) {
            // Met à jour la note existante (avec l'ID local correct)
            updateNote(existing.getId(), note.getTitle(), note.getContent(), note.getColor(), note.getPosition());
        } else {
            // Insère avec le firebaseDocId, ID local auto-généré
            insertNote(note.getFirebaseDocId(), note.getTitle(), note.getContent(), note.getColor(), note.getPosition());
        }
    }

    private boolean noteExists(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[] {COLUMN_ID},
                COLUMN_ID + "=?", new String[] {String.valueOf(id)},
                null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

    public boolean noteExistsByFirebaseDocId(String firebaseDocId) {
        if (firebaseDocId == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[] {COLUMN_ID},
                COLUMN_FIREBASE_DOC_ID + "=?", new String[] {firebaseDocId},
                null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

    // Méthode insertNoteWithId conservée pour compatibilité mais utilise désormais firebaseDocId
    public void insertNoteWithId(long id, String title, String content, int color, int position) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_ID, id);
        cv.put(COLUMN_FIREBASE_DOC_ID, java.util.UUID.randomUUID().toString());
        cv.put(COLUMN_TITLE, title);
        cv.put(COLUMN_CONTENT, content);
        cv.put(COLUMN_COLOR, color);
        cv.put(COLUMN_POSITION, position);
        db.insert(TABLE_NAME, null, cv);
    }

}