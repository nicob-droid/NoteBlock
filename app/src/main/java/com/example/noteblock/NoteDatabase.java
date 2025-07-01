package com.example.noteblock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.util.Log;

import com.example.noteblock.Utils.HashUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NoteDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notes_db";
    private static final int DATABASE_VERSION = 2;
    public static final String TABLE_NAME = "notes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_COLOR = "color";

    private byte[] aesKey;

    public NoteDatabase(Context context, byte[] aesKey) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        //this.aesKey = HashUtils.sha256(pin).substring(0, 32); // 256-bit key from PIN hash
        this.aesKey = aesKey;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_CONTENT + " TEXT,"
                + COLUMN_COLOR + " INTEGER DEFAULT 16777215"
                + ")";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_COLOR + " INTEGER DEFAULT 16777215");
        }
    }

  /*  public void insertNote(String title, String content) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TITLE, HashUtils.encrypt(title, aesKey));
        cv.put(COLUMN_CONTENT, HashUtils.encrypt(content, aesKey));
        cv.put(COLUMN_COLOR, Color.parseColor("#FFFFFF"));
        db.insert(TABLE_NAME, null, cv);
    }*/

    public void insertNote(String title, String content, int color) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TITLE, HashUtils.encrypt(title, aesKey));
        cv.put(COLUMN_CONTENT, HashUtils.encrypt(content, aesKey));
        cv.put(COLUMN_COLOR, color);
        db.insert(TABLE_NAME, null, cv);
    }

    /*public void updateNote(int id, String title, String content) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TITLE, HashUtils.encrypt(title, aesKey));
        cv.put(COLUMN_CONTENT, HashUtils.encrypt(content, aesKey));
        //cv.put(COLUMN_COLOR, Color.parseColor("#FFFFFF"));
        db.update(TABLE_NAME, cv, "id=?", new String[]{String.valueOf(id)});
    }*/

    public void updateNote(int id, String title, String content, int color) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_TITLE, HashUtils.encrypt(title, aesKey));
        cv.put(COLUMN_CONTENT, HashUtils.encrypt(content, aesKey));
        cv.put(COLUMN_COLOR, color);
        db.update(TABLE_NAME, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public int deleteNoteById(int id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String encryptedTitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String encryptedContent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
                try {
                    String title = HashUtils.decrypt(encryptedTitle, aesKey);
                    String content = HashUtils.decrypt(encryptedContent, aesKey);

                    notes.add(new Note(id, title, content, color));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            cursor.close();
        }
       // db.close();
        return notes;
    }

    public Note getNoteById(int id) throws Exception {
        SQLiteDatabase db = this.getReadableDatabase();
        Note note = null;

        Cursor cursor = db.query(TABLE_NAME, null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String encryptedTitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
            String encryptedContent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
            int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
            String title = HashUtils.decrypt(encryptedTitle, aesKey);
            String content = HashUtils.decrypt(encryptedContent, aesKey);


            note = new Note(id, title, content, color);
            cursor.close();
        }

        return note;
    }

    public List<Note> getAllNotesRaw() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, title, content, color FROM notes", null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String encryptedTitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String encryptedContent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COLOR));
                Note note = new Note(id, encryptedTitle, encryptedContent, color);
                notes.add(note);
            } while (cursor.moveToNext());
        }
        cursor.close();
        //db.close();
        return notes;
    }



    public void updateNoteEncrypted(int noteId, String encryptedTitle, String encryptedContent) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", encryptedTitle);
        values.put("content", encryptedContent);

        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(noteId)});
        db.close();
    }





    public void reencryptAllNotes(byte[] oldAesKey, byte[] newAesKey) throws Exception {
        // Récupérer toutes les notes chiffrées telles qu'elles sont en base
        List<Note> notes = getAllNotesRaw();

        for (Note note : notes) {
            try {
                // Déchiffrer titre et contenu avec l'ancienne clé
                String decryptedTitle = HashUtils.decrypt(note.getTitle(), oldAesKey);
                String decryptedContent = HashUtils.decrypt(note.getContent(), oldAesKey);

                // Ré-encrypter avec la nouvelle clé
                String reencryptedTitle = HashUtils.encrypt(decryptedTitle, newAesKey);
                String reencryptedContent = HashUtils.encrypt(decryptedContent, newAesKey);

                // Mettre à jour la note avec les données ré-encryptées
                updateNoteEncrypted(note.getId(), reencryptedTitle, reencryptedContent);

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Erreur lors du ré-encryptage de la note id=" + note.getId());
            }
        }
    }




}