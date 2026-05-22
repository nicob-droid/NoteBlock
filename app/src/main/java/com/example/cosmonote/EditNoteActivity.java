package com.example.cosmonote;


import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_COLOR;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_ID;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_POSITION;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class EditNoteActivity extends BaseActivity  {
    private static final String TAG = "EditNoteActivity";
    private EditText titleInput;
    private EditText contentInput;
    private LinearLayout ll_delete;
    private int selectedColor = Color.WHITE;  // Valeur par défaut (blanc)
    private FloatingActionButton btnDelete, btnShare;

    private NoteDatabase db;
    private long noteId;
    private Note note;
    private String firebaseDocId; // UUID unique pour Firestore
    private int selectedPosition = 0;  // par défaut première position
    private boolean isNoteDeleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // init activity view
        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        // init note
        initNoteFromDatabase();

        // Manage button DELETE
        manageDeleteButton();

        // Manage button SHARE
        btnShare.setOnClickListener(v -> shareNote());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        // save note
        saveNote();
    }

    private void initView() {
        Log.i(TAG, "initView");
        setContentView(R.layout.activity_edit_note);

        titleInput = findViewById(R.id.edit_note_title);
        contentInput = findViewById(R.id.edit_note_content);
        btnDelete = findViewById(R.id.fab_delete);
        ll_delete = findViewById(R.id.ll_delete);
        btnShare = findViewById(R.id.fab_share);
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1);
        selectedColor = getIntent().getIntExtra(EXTRA_NOTE_COLOR, Color.WHITE);
        selectedPosition = getIntent().getIntExtra(EXTRA_NOTE_POSITION, -1);
    }

    private void initNoteFromDatabase() {
        db = new NoteDatabase(this);

        if (noteId != -1) {
            note = db.getNoteById(noteId);
            if (note != null) {
                firebaseDocId = note.getFirebaseDocId();
                // remplis les champs de l'UI
                titleInput.setText(note.getTitle());
                contentInput.setText(note.getContent());
            }
        }
    }

    private void manageDeleteButton() {
        if (note != null) {
            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_note_confirmation_title))
                    .setMessage(getString(R.string.delete_note_confirmation_message))
                    .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                        // remove note
                        deleteNote(note);
                    })
                    .setNegativeButton(getString(R.string.no), null)
                    .show());
        } else {
            // Masquer le bouton SUPPRIMER si c'est une nouvelle note
            ll_delete.setVisibility(View.GONE);
        }
    }

    private void deleteNote(Note note) {
        // 1. Supprimer localement
        deleteNoteLocal(note);

        // 2. Supprimer depuis Firestore si connecté
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(note.getFirebaseDocId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("DeleteNote", "Note supprimée de Firestore");
                        isNoteDeleted = true;  // Ici, seulement après réussite
                        finish();              // Et on quitte après
                    })
                    .addOnFailureListener(e -> Log.e("DeleteNote", "Erreur lors de la suppression de Firestore", e));
        } else {
            isNoteDeleted = true; // local seulement
            finish();
        }
    }

    private int deleteNoteLocal(Note note) {
        int deleted = db.deleteNoteById(note.getId());
        if (deleted > 0) {
            Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "delete note local: success");
        } else {
            Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "delete note local: error");
        }

        return deleted;
    }

    private void saveNote() {
        Log.i(TAG, "save note");

        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();

        if (title.isEmpty()) return;

        int position = selectedPosition;
        long id;

        try {
            if (noteId == -1) {
                // Création : génère un UUID unique pour cette note
                firebaseDocId = java.util.UUID.randomUUID().toString();
                id = db.insertNote(firebaseDocId, title, content, selectedColor, position);
                Log.i(TAG, "Note créée avec id = " + id + ", firebaseDocId = " + firebaseDocId);
            } else {
                // Modification : update, id reste le même
                db.updateNote(noteId, title, content, selectedColor, position);
                id = noteId;
                Log.i(TAG, "Note modifiée id = " + id);
            }

            // Création du timestamp courant (millis depuis epoch)
            long timestamp = System.currentTimeMillis();

            // Construire Note avec l'id correct
            Note note = new Note((int) id, firebaseDocId, title, content, selectedColor, position, timestamp);

            // Synchroniser Firestore : timestamp seulement à la création
            if(!isNoteDeleted) {
                syncNoteToFirestore(note);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur saveNote", e);
        }
    }


    private void shareNote() {
        // Get note text
        String noteTitle = titleInput.getText().toString();
        String noteContent = contentInput.getText().toString();
        // Share note
        if ((!noteTitle.isEmpty()) && (!noteContent.isEmpty())) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, noteTitle + "\n" + noteContent);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_note_with)));
        } else {
            Toast.makeText(this, getString(R.string.note_empty), Toast.LENGTH_SHORT).show();
        }
    }


    private void syncNoteToFirestore(Note note) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Utilisateur non connecté, pas de sync Firestore");
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference docRef = firestore.collection("users")
                .document(userId)
                .collection("notes")
                .document(note.getFirebaseDocId());

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("firebaseDocId", note.getFirebaseDocId());
        noteData.put("id", note.getId());
        noteData.put("title", note.getTitle());
        noteData.put("content", note.getContent());
        noteData.put("color", note.getColor());
        noteData.put("position", note.getPosition());
        noteData.put("timestamp", new Timestamp(new Date(note.getTimestamp())));

        docRef.set(noteData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Note synchronisée dans Firestore firebaseDocId=" + note.getFirebaseDocId()))
                .addOnFailureListener(e -> Log.e(TAG, "Erreur lors de la sync Firestore", e));
    }
}
