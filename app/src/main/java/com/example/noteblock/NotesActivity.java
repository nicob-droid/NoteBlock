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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotesActivity extends AppCompatActivity implements NotesAdapter.OnNoteClickListener {

    private RecyclerView recyclerView;
    private NotesAdapter adapter;
    private List<Note> notesList;
    private NoteDatabase db;

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
        byte[] aesKey = HashUtils.hexStringToByteArray(storedPinHash);
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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedPinHash = prefs.getString(KEY_PIN_HASH, null);
        byte[] aesKey = HashUtils.hexStringToByteArray(storedPinHash);

        try {
            List<Note> encryptedNotes = db.getAllNotesRaw(); // notes chiffrées
            List<Note> decryptedNotes = new ArrayList<>();

            for (Note note : encryptedNotes) {
                String title = HashUtils.decrypt(note.getTitle(), aesKey);
                String content = HashUtils.decrypt(note.getContent(), aesKey);
                Note decryptedNote = new Note(note.getId(), title, content, note.getColor());
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
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNoteClick(Note note) {
        // Ouvre l'édition de la note (ex: activity EditNote)
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId()); // ou autre méthode pour identifier la note
        intent.putExtra("note_color", note.getColor());
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
                db.updateNote(note.getId(), note.getTitle(), note.getContent(), newColor);
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

}
