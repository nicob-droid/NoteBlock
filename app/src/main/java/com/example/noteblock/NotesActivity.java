package com.example.noteblock;

import static com.example.noteblock.MainActivity.KEY_PIN_HASH;
import static com.example.noteblock.MainActivity.PREFS_NAME;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteblock.Utils.HashUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotesActivity extends AppCompatActivity implements NotesAdapter.OnNoteClickListener {
    private static final String TAG = "NotesActivity";
    private RecyclerView recyclerView;
    private NotesAdapter adapter;
    private List<Note> notesList;
    private NoteDatabase db;
    private byte[] aesKey;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_notes);

        recyclerView = findViewById(R.id.notes_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Exemple de données
        notesList = new ArrayList<>();
        /*notesList.add(new Note("Note 1", "Contenu de la note 1"));
        notesList.add(new Note("Note 2", "Contenu de la note 2"));*/

        // Récupérer le PIN
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedPinHash = prefs.getString(KEY_PIN_HASH, null);
        // Récupérer la Clef AES
        aesKey = HashUtils.hexStringToByteArray(storedPinHash);
        // Init database
        db = new NoteDatabase(this, aesKey);
        // Charger la database
        loadNotes();
        // Init affichage des notes
        adapter = new NotesAdapter(notesList, this);
        recyclerView.setAdapter(adapter);
        // Init du floating button
        FloatingActionButton fab = findViewById(R.id.fab_add_note);
        fab.setOnClickListener(v -> {
            // Ouvre activité d'édition pour créer une nouvelle note
            Intent intent = new Intent(this, EditNoteActivity.class);
            startActivity(intent);
        });

        // Ajouter un ItemTouchHelper pour gérer le déplacement des notes
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                // Mets à jour les données de ta liste (ex : swap dans l'ArrayList)
                Collections.swap(notesList, fromPosition, toPosition);
                // Notifie l'adapter du déplacement
                adapter.notifyItemMoved(fromPosition, toPosition);

                return true;
            }
            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                // L'utilisateur a fini de déplacer
                // => maintenant on sauvegarde la nouvelle position dans la base
                // Met à jour les positions dans la base
                updateItemPositionsInDatabase();
            }
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // Ici, pas de swipe donc rien à faire
            }
            @Override
            public boolean isLongPressDragEnabled() {
                return true; // Active le drag au long press
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

    }

    private void loadNotes() {
        new Thread(() -> {
            notesList = db.getAllNotes();
            runOnUiThread(() -> adapter.setNotes(notesList));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Recharge localement les notes depuis SQLite (optionnel)
        loadNotesFromLocalDatabase();

        // Synchronise depuis Firestore pour récupérer les notes partagées/mises à jour par d'autres
        //fetchNotesFromFirestoreIfLoggedIn();

        // Synchronise depuis Firestore pour récupérer toutes les notes (locales + partagées)
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            fetchAllRelevantNotes(firebaseUser.getUid());
        } else {
            Toast.makeText(this, "Vous devez être connecté pour synchroniser les notes", Toast.LENGTH_SHORT).show();
            // Tu peux rester en local ici
        }
    }

    private void loadNotesFromLocalDatabase() {
        try {
            List<Note> encryptedNotes = db.getAllNotesRaw(); // notes chiffrées
            List<Note> decryptedNotes = new ArrayList<>();

            for (Note note : encryptedNotes) {
                String title = HashUtils.decrypt(note.getTitle(), aesKey);
                String content = HashUtils.decrypt(note.getContent(), aesKey);
                Note decryptedNote = new Note(note.getId(), title, content, note.getColor(), note.getPosition());
                decryptedNotes.add(decryptedNote);
            }

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NotesDiffCallback(notesList, decryptedNotes));
            notesList.clear();
            notesList.addAll(decryptedNotes);
            diffResult.dispatchUpdatesTo(adapter);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur lors du chargement des notes", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_change_pin) {
            // Ouvre l'activité pour changer le PIN
            Intent intent = new Intent(this, ChangePinActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_share_with_user) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_login) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNoteClick(Note note) {
        // Ouvre l'édition de la note (ex: activity EditNote)
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId()); // ou autre méthode pour identifier la note
        intent.putExtra("note_color", note.getColor());
        intent.putExtra("note_position", note.getPosition());
        startActivity(intent);
    }

    @Override
    public void onColorPickerClick(Note note) {
        // Ouvre le BottomSheet de sélection de couleur (comme avant)
        showColorPickerBottomSheet(note);
    }


    private void showColorPickerBottomSheet(Note note) {
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_color_picker, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);

        view.findViewById(R.id.color_white).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#FFFFFF"));
            dialog.dismiss();
        });
        view.findViewById(R.id.color_red).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#F44336"));
            dialog.dismiss();
        });
        view.findViewById(R.id.color_green).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#4CAF50"));
            dialog.dismiss();
        });
        view.findViewById(R.id.color_blue).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#2196F3"));
            dialog.dismiss();
        });
        view.findViewById(R.id.color_yellow).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#FFEB3B"));
            dialog.dismiss();
        });
        view.findViewById(R.id.color_purple).setOnClickListener(v -> {
            updateNoteColor(note, Color.parseColor("#9C27B0"));
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateNoteColor(Note note, int newColor) {
        note.setColor(newColor);
        new Thread(() -> {
            try {
                db.updateNote(note.getId(), note.getTitle(), note.getContent(), newColor, note.getPosition());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            runOnUiThread(this::loadNotes);
        }).start();
    }

    private void updateItemPositionsInDatabase() {
        new Thread(() -> {
            try {
                db.updateAllNotePositions(notesList);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            runOnUiThread(this::loadNotes);
        }).start();
    }

    private void fetchNotesFromFirestoreIfLoggedIn() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            return;
        }

        String currentUserId = firebaseUser.getUid();
        fetchNotesFromFirestore(currentUserId);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("shared_users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String sharedUserId = documentSnapshot.getString("sharedUserId");
                        if (sharedUserId != null && !sharedUserId.isEmpty() && !sharedUserId.equals(currentUserId)) {
                            fetchNotesFromFirestore(sharedUserId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Erreur récupération partage", e);
                });
    }


    private void fetchNotesFromFirestore(String userId) {
        FirebaseFirestore firebase = FirebaseFirestore.getInstance();

        firebase.collection("users")
                .document(userId)
                .collection("notes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                long id = document.getLong("id");
                                String encryptedTitle = document.getString("title");
                                String encryptedContent = document.getString("content");
                                int color = document.getLong("color").intValue();
                                int position = document.getLong("position").intValue();

                                try {
                                    String title = HashUtils.decrypt(encryptedTitle, aesKey);
                                    String content = HashUtils.decrypt(encryptedContent, aesKey);

                                    Note note = new Note(id, title, content, color, position);

                                    // Chercher si la note existe déjà en local
                                    Note localNote = db.getNoteById(id);
                                    if (localNote == null) {
                                        // Pas trouvée, insertion
                                        db.insertOrUpdateNote(note);
                                    } else {
                                        // Trouvée, comparer et mettre à jour si différent
                                        if (!localNote.equals(note)) {
                                            db.insertOrUpdateNote(note);
                                        }
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            Log.w(TAG, "Erreur fetch Firestore", task.getException());
                        }
                    }
                });
    }


    private void fetchAllRelevantNotes(String userId) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            // Pas connecté, on peut juste charger local
            loadNotesFromLocalDatabase();
            return;
        }

        //String currentUserId = firebaseUser.getUid();

        // 1. Charger les notes locales (optionnel)
        loadNotesFromLocalDatabase();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 2. Récupérer l'UID du sharedUser (s'il y en a) depuis Firestore
        db.collection("shared_users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String sharedUserId = documentSnapshot.getString("sharedUserId");
                        // On récupère les notes des 2 utilisateurs (self + shared)
                        fetchNotesFromFirestore(userId);
                        if (sharedUserId != null && !sharedUserId.isEmpty() && !sharedUserId.equals(userId)) {
                            fetchNotesFromFirestore(sharedUserId);
                        }
                    } else {
                        // Pas de partage configuré, on charge juste les notes de l'utilisateur connecté
                        fetchNotesFromFirestore(userId);
                    }
                })
                .addOnFailureListener(e -> {
                    // Erreur Firestore, on charge au moins les notes de l'utilisateur connecté
                    fetchNotesFromFirestore(userId);
                });
    }


}
