package com.example.noteblock;

import static com.example.noteblock.MainActivity.KEY_PIN_HASH;
import static com.example.noteblock.MainActivity.PREFS_NAME;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;

import com.example.noteblock.Utils.HashUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedPinHash = prefs.getString(KEY_PIN_HASH, null);

        assert storedPinHash != null;
        byte[] aesKey = HashUtils.hexStringToByteArray(storedPinHash);

        db = new NoteDatabase(this, aesKey);

        if (noteId != -1) {
            try {
                note = db.getNoteById(noteId);
                if (note != null) {
                    // remplis les champs de l’UI
                    titleInput.setText(note.getTitle());
                    contentInput.setText(note.getContent());
                }
            } catch (Exception e) {
                Toast.makeText(this, "Erreur déchiffrement : clé incorrecte ou données corrompues", Toast.LENGTH_LONG).show();
                Log.e("DB_NOTE", "stored PIN hash = " + storedPinHash);
                finish(); // quitte l'activité
                return;
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
                            int deleted = db.deleteNoteById(note.getId());
                            if (deleted > 0) {
                                Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                            }
                            finish();
                        })
                        .setNegativeButton(getString(R.string.no), null)
                        .show();
            });
        } else {
            ll_delete.setVisibility(View.GONE); // Masquer le bouton si c'est une nouvelle note
        }
    }

    private void saveNote() {
        Log.i(TAG, "save note");
        // Save note
        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();

        if (title.isEmpty()) {
            //Toast.makeText(this, getString(R.string.title_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        int position = selectedPosition;

        if (noteId == -1) {
            try {
                db.insertNote(title, content, selectedColor, position);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            //Toast.makeText(this, getString(R.string.note_added), Toast.LENGTH_SHORT).show();
        } else {
            try {
                db.updateNote(noteId, title, content, selectedColor, position);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            //Toast.makeText(this, getString(R.string.note_updated), Toast.LENGTH_SHORT).show();
        }

        // Et après sauvegarde locale, synchronise vers Firestore
        Note note = new Note(noteId == -1 ? /* récupère id auto */0 : noteId, title, content, selectedColor, position);
        syncNoteToFirestore(note);
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
        Map<String, Object> noteMap = new HashMap<>();
        noteMap.put("title", note.getTitle());
        noteMap.put("content", note.getContent());
        noteMap.put("color", note.getColor());
        noteMap.put("position", note.getPosition());
        noteMap.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();  // Récupère l'utilisateur connecté

        db.collection("users")
                .document(userId)
                .collection("notes")
                .document(String.valueOf(note.getId()))
                .set(noteMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Note synced successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error syncing note", e));
    }




}
