package com.example.noteblock;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.noteblock.Utils.HashUtils;
import com.example.noteblock.Utils.NotePreferences;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class EditNoteActivity extends AppCompatActivity {
    private static final String TAG = "EditNoteActivity";
    private EditText titleInput;
    private EditText contentInput;
    private LinearLayout ll_delete;
    private int selectedColor = Color.WHITE;  // Valeur par défaut (blanc)
    private FloatingActionButton btnDelete, btnShare;

    private NoteDatabase db;
    private long noteId;
    private Note note;
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
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareNote();
            }
        });
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
        //btnSave = findViewById(R.id.btn_save_note);
        btnDelete = findViewById(R.id.fab_delete);
        ll_delete = findViewById(R.id.ll_delete);
        btnShare = findViewById(R.id.fab_share);
        noteId = getIntent().getLongExtra("note_id", -1);
        selectedColor = getIntent().getIntExtra("note_color", Color.WHITE);
        selectedPosition = getIntent().getIntExtra("note_position", -1);
    }

    private void initNoteFromDatabase() {
        db = new NoteDatabase(this);

        if (noteId != -1) {
            note = db.getNoteById(noteId);
            if (note != null) {
                // remplis les champs de l’UI
                titleInput.setText(note.getTitle());
                contentInput.setText(note.getContent());
            }
        }
    }

    private void manageDeleteButton() {
        if (note != null) {
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.delete_note_confirmation_title))
                        .setMessage(getString(R.string.delete_note_confirmation_message))
                        .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                            /*int deleted = db.deleteNoteById(note.getId());
                            if (deleted > 0) {
                                Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                            }
                            finish();*/
                            deleteNote(note);
                        })
                        .setNegativeButton(getString(R.string.no), null)
                        .show();
            });
        } else {
            ll_delete.setVisibility(View.GONE); // Masquer le bouton si c'est une nouvelle note
        }
    }

    private void deleteNote(Note note) {
        // 1. Supprimer localement
        int deleted = db.deleteNoteById(note.getId());
        if (deleted > 0) {
            Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
        }

        // 2. Supprimer depuis Firestore si connecté
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(String.valueOf(note.getId()))
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("DeleteNote", "Note supprimée de Firestore");
                        isNoteDeleted = true;  // Ici, seulement après réussite
                        finish();              // Et on quitte après
                    })
                    .addOnFailureListener(e -> {
                        Log.e("DeleteNote", "Erreur lors de la suppression de Firestore", e);
                    });
        } else {
            isNoteDeleted = true; // local seulement
            finish();
        }
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
                // Création : insère et récupère l'id généré
                id = db.insertNote(title, content, selectedColor, position);
                Log.i(TAG, "Note créée avec id = " + id);
            } else {
                // Modification : update, id reste le même
                db.updateNote(noteId, title, content, selectedColor, position);
                id = noteId;
                Log.i(TAG, "Note modifiée id = " + id);
            }

            // Création du timestamp courant (millis depuis epoch)
            long timestamp = System.currentTimeMillis();

            // Construire Note avec l'id correct
            Note note = new Note((int) id, title, content, selectedColor, position, timestamp);

            // Synchroniser Firestore : timestamp seulement à la création
            if(!isNoteDeleted) {
                syncNoteToFirestore(note, noteId == -1);
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


    private void syncNoteToFirestore(Note note, boolean isNew) {
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
                .document(String.valueOf(note.getId()));

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("id", note.getId());
        noteData.put("title", note.getTitle());
        noteData.put("content", note.getContent());
        noteData.put("color", note.getColor());
        noteData.put("position", note.getPosition());
        noteData.put("timestamp", new Timestamp(new Date(note.getTimestamp())));

        docRef.set(noteData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Note synchronisée dans Firestore id=" + note.getId());
                    if (isNew) {
                        showNotification("Nouvelle note créée", note.getTitle());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erreur lors de la sync Firestore", e);
                });
    }


    // Exemple simple de méthode notification
    private void showNotification(String title, String content) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "note_channel";
        NotificationChannel channel = new NotificationChannel(channelId, "Notes", NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_note) // adapte l'icône
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }







}
