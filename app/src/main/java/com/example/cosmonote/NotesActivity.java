package com.example.cosmonote;

import com.cosmonote.app.R;

import android.Manifest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cosmonote.Settings.SettingsPreferencesActivity;
import com.example.cosmonote.Utils.NotePreferences;
import com.example.cosmonote.Utils.NotificationHelper;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesActivity extends BaseActivity  implements NotesAdapter.OnNoteClickListener {
    private static final String TAG = "NotesActivity";
    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_NOTE_COLOR = "note_color";
    public static final String EXTRA_NOTE_POSITION = "note_position";
    private static final int REQUEST_CODE_POST_NOTIF = 1001;
    private NotesAdapter adapter;
    private List<Note> notesList;
    private NoteDatabase db;
    private final List<ListenerRegistration> activeListeners = new ArrayList<>();
    private Date lastSeenNoteTimestamp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_notes);

        RecyclerView recyclerView = findViewById(R.id.notes_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Données
        notesList = new ArrayList<>();

        // Lire la date de la dernière note
        lastSeenNoteTimestamp = NotePreferences.loadLastSeenTimestamp(this);
        // Init database
        db = new NoteDatabase(this);
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

        // Init du bouton supprimer tout
        FloatingActionButton fabDeleteAll = findViewById(R.id.fab_delete_all);
        fabDeleteAll.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_all_notes_confirmation_title))
                .setMessage(getString(R.string.delete_all_notes_confirmation_message))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> deleteAllNotes())
                .setNegativeButton(getString(R.string.no), null)
                .show());

        // Ajouter un ItemTouchHelper pour gérer le déplacement des notes
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                // Mets à jour les données de ta liste (ex : swap dans l'ArrayList)
                Collections.swap(notesList, fromPosition, toPosition);
                // Notifie l'adapter du déplacement
                adapter.notifyItemMoved(fromPosition, toPosition);

                return true;
            }
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                // L'utilisateur a fini de déplacer
                // => maintenant on sauvegarde la nouvelle position dans la base
                // Met à jour les positions dans la base
                updateItemPositionsInDatabase();
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
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
            List<Note> loaded = db.getAllNotes();
            runOnUiThread(() -> adapter.setNotes(loaded));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Recharge localement les notes depuis SQLite (optionnel)
        loadNotesFromLocalDatabase();

        // Pour Android 13+ : check et request permission POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                        REQUEST_CODE_POST_NOTIF
                );
            }
        }

        // Synchronisation Firestore seulement si aucun listener actif
        if (activeListeners.isEmpty()) {
            // Synchronise depuis Firestore pour récupérer toutes les notes (locales + partagées)
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                String currentUserId = firebaseUser.getUid();

                // Sync propres notes depuis Firestore (multi-device)
                fetchNotesFromFirestore(this, currentUserId);

                // Écoute en temps réel les notes des utilisateurs partagés uniquement
                startListeningNotes(currentUserId);

                // 2) Récupère le sharedUserId depuis Firestore, puis écoute-le
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUserId)
                        .collection("shared_users")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                    String sharedUserId = doc.getString("sharedUserId");
                                    if (sharedUserId != null
                                            && !sharedUserId.isEmpty()
                                            && !sharedUserId.equals(currentUserId)) {
                                        try {
                                            startListeningNotes(sharedUserId);
                                        } catch (Exception e) {
                                            Log.w(TAG, "Impossible de démarrer le listener pour sharedUserId: "
                                                    + sharedUserId, e);
                                        }
                                    }
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.w(TAG, "Impossible de récupérer sharedUserId", e));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (ListenerRegistration l : activeListeners) {
            l.remove();
        }
        activeListeners.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null) {
            db.close();   // ← ferme la SQLiteDatabase et son pool
            db = null;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission accordée : on peut poster des notifs
            } else {
                // Permission refusée : tu peux désactiver la notif ou prévenir l'utilisateur
                Toast.makeText(this, "Permission notifications non accordée", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void loadNotesFromLocalDatabase() {
        try {
            List<Note> encryptedNotes = db.getAllNotesRaw(); // notes chiffrées
            List<Note> decryptedNotes = new ArrayList<>();

            for (Note note : encryptedNotes) {
                Note decryptedNote = new Note(note.getId(), note.getFirebaseDocId(), note.getTitle(), note.getContent(), note.getColor(), note.getPosition());
                decryptedNotes.add(decryptedNote);
            }

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NotesDiffCallback(notesList, decryptedNotes));
            notesList.clear();
            notesList.addAll(decryptedNotes);
            diffResult.dispatchUpdatesTo(adapter);

        } catch (Exception e) {
            Log.e(TAG, "[loadNotesFromLocalDatabase] Exception " + e.getMessage());
            Toast.makeText(this, getString(R.string.load_notes_error), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // Lancer l'activité des paramètres
            Intent intent = new Intent(this, SettingsPreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNoteClick(Note note) {
        // Ouvre l'édition de la note (ex: activity EditNote)
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(EXTRA_NOTE_ID, note.getId()); // ou autre méthode pour identifier la note
        intent.putExtra(EXTRA_NOTE_COLOR, note.getColor());
        intent.putExtra(EXTRA_NOTE_POSITION, note.getPosition());
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

        FlexboxLayout layout = view.findViewById(R.id.color_picker_layout);

        final View[] selectedFrame = {null};

        for (int i = 0; i < layout.getChildCount(); i++) {
            View frame = layout.getChildAt(i); // FrameLayout

            if (frame instanceof ViewGroup && ((ViewGroup) frame).getChildCount() > 0) {
                View colorView = ((ViewGroup) frame).getChildAt(0);

                // Lire la couleur pastel depuis backgroundTint
                ColorStateList tintList = colorView.getBackgroundTintList();
                int color = tintList != null ? tintList.getDefaultColor() : Color.TRANSPARENT;

                frame.setOnClickListener(v -> {
                    // Désélectionner l'ancienne frame si besoin
                    if (selectedFrame[0] != null) {
                        selectedFrame[0].setSelected(false);
                    }

                    // Sélectionner la nouvelle
                    v.setSelected(true);
                    selectedFrame[0] = v;

                    // Appliquer la couleur à la note
                    updateNoteColor(note, color);
                    dialog.dismiss();
                });
            }
        }

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
            // Synchroniser la couleur vers Firestore
            syncNoteColorToFirestore(note);
            runOnUiThread(this::loadNotes);
        }).start();
    }

    private void syncNoteColorToFirestore(Note note) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || note.getFirebaseDocId() == null) return;

        String userId = currentUser.getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("color", note.getColor());

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("notes")
                .document(note.getFirebaseDocId())
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Couleur synchronisée Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Erreur sync couleur Firestore", e));
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

    private void deleteAllNotes() {
        new Thread(() -> {
            db.deleteAllNotes();
            // Supprimer aussi sur Firestore si connecté
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String userId = currentUser.getUid();
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("notes")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                doc.getReference().delete();
                            }
                        });
            }
            runOnUiThread(() -> {
                notesList.clear();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, getString(R.string.all_notes_deleted), Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void fetchNotesFromFirestore(Context context, String userId) {
        FirebaseFirestore firebase = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isRemoteUser = currentUser != null && !userId.equals(currentUser.getUid());

        firebase.collection("users")
                .document(userId)
                .collection("notes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String firebaseDocId = document.getString("firebaseDocId");

                                String title = document.getString("title");
                                String content = document.getString("content");
                                Long colorObj = document.getLong("color");
                                int color = (colorObj != null) ? colorObj.intValue() : 0;
                                Long positionObj = document.getLong("position");
                                int position = (positionObj != null) ? positionObj.intValue() : 0;

                                try {
                                    // Chercher si la note existe déjà en local
                                    Note localNote = null;

                                    if (firebaseDocId != null) {
                                        localNote = db.getNoteByFirebaseDocId(firebaseDocId);
                                    }

                                    // Fallback : chercher par ancien ID numérique (notes pré-migration)
                                    if (localNote == null) {
                                        Long idObj = document.getLong("id");
                                        if (idObj != null) {
                                            localNote = db.getNoteById(idObj);
                                        }
                                    }

                                    if (localNote != null) {
                                        // La note existe déjà localement
                                        // Si Firestore n'a pas de firebaseDocId, on le pousse
                                        if (firebaseDocId == null && localNote.getFirebaseDocId() != null) {
                                            pushFirebaseDocIdToFirestore(userId, document.getId(), localNote.getFirebaseDocId(), localNote);
                                        }
                                        // Mettre à jour localement seulement si le contenu distant est différent
                                        // mais préserver la couleur locale si Firestore a une couleur par défaut
                                        if (!localNote.getTitle().equals(title) || !localNote.getContent().equals(content)) {
                                            // Contenu différent sur Firestore → mettre à jour local
                                            String docId = (firebaseDocId != null) ? firebaseDocId : localNote.getFirebaseDocId();
                                            Note updated = new Note(localNote.getId(), docId, title, content,
                                                    localNote.getColor(), localNote.getPosition());
                                            db.insertOrUpdateNote(updated);
                                        }
                                    } else {
                                        // Nouvelle note (venant d'un autre appareil ou utilisateur partagé)
                                        if (firebaseDocId == null) {
                                            firebaseDocId = document.getId();
                                        }
                                        Note note = new Note(0, firebaseDocId, title, content, color, position);
                                        db.insertOrUpdateNote(note);

                                        if (isRemoteUser) {
                                            NotificationHelper.showNoteNotification(
                                                    context,
                                                    context.getString(R.string.new_note_from_distant),
                                                    note.getTitle()
                                            );
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "[fetchNotesFromFirestore] Exception " + e.getMessage());
                                }
                            }
                            // Rafraîchir l'affichage après la synchro
                            runOnUiThread(this::loadNotesFromLocalDatabase);
                        } else {
                            Log.w(TAG, "Erreur fetch Firestore", task.getException());
                        }
                    }
                });
    }

    /**
     * Pousse le firebaseDocId local vers Firestore pour les notes créées avant la migration.
     * Migre aussi le document Firestore vers le nouveau document ID = firebaseDocId.
     */
    private void pushFirebaseDocIdToFirestore(String userId, String oldDocId, String firebaseDocId, Note localNote) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("firebaseDocId", firebaseDocId);
        noteData.put("id", localNote.getId());
        noteData.put("title", localNote.getTitle());
        noteData.put("content", localNote.getContent());
        noteData.put("color", localNote.getColor());
        noteData.put("position", localNote.getPosition());
        noteData.put("timestamp", new com.google.firebase.Timestamp(new java.util.Date(localNote.getTimestamp())));

        // Créer le nouveau document avec le firebaseDocId comme clé
        firestore.collection("users")
                .document(userId)
                .collection("notes")
                .document(firebaseDocId)
                .set(noteData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Note migrée vers Firestore avec firebaseDocId=" + firebaseDocId);
                    // Supprimer l'ancien document (ancien ID numérique)
                    if (!oldDocId.equals(firebaseDocId)) {
                        firestore.collection("users")
                                .document(userId)
                                .collection("notes")
                                .document(oldDocId)
                                .delete();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Erreur migration Firestore", e));
    }

    private void startListeningNotes(String userId) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // Charger le dernier timestamp enregistré
        lastSeenNoteTimestamp = NotePreferences.loadLastSeenTimestamp(this);
        Log.d(TAG, "lastSeenNoteTimestamp = " + lastSeenNoteTimestamp);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isRemoteUser = currentUser != null && !userId.equals(currentUser.getUid());

        // Ne pas écouter ses propres notes (elles sont déjà sauvées localement à la création)
        if (!isRemoteUser) {
            Log.d(TAG, "Skipping listener for own user notes (already saved locally)");
            return;
        }

        try {
            ListenerRegistration listener = firestore
                    .collection("users")
                    .document(userId)
                    .collection("notes")
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.w(TAG, "Listen failed.", error);
                            return;
                        }
                        if (snapshots != null) {
                            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                                if (dc.getType() == DocumentChange.Type.ADDED) {
                                    DocumentSnapshot doc = dc.getDocument();

                                    // Vérifier si la note existe déjà localement via firebaseDocId
                                    String firebaseDocId = doc.getString("firebaseDocId");
                                    if (firebaseDocId != null && db.noteExistsByFirebaseDocId(firebaseDocId)) {
                                        Log.d(TAG, "Note déjà présente localement, skip: " + firebaseDocId);
                                        continue;
                                    }

                                    // Extraction robuste du timestamp
                                    Object timestampObj = doc.get("timestamp");
                                    Date createdDate = null;

                                    if (timestampObj instanceof com.google.firebase.Timestamp) {
                                        createdDate = ((com.google.firebase.Timestamp) timestampObj).toDate();
                                    } else if (timestampObj instanceof Long) {
                                        createdDate = new Date((Long) timestampObj);
                                    } else if (timestampObj instanceof Double) {
                                        createdDate = new Date(((Double) timestampObj).longValue());
                                    } else {
                                        Log.w(TAG, "Format de timestamp non reconnu : " + timestampObj);
                                    }

                                    if (createdDate != null) {
                                        if (createdDate.after(lastSeenNoteTimestamp)) {
                                            Log.d(TAG, "Nouvelle note détectée avec timestamp récent : " + createdDate);

                                            onRemoteNoteAdded(doc);

                                            // Mettre à jour lastSeenNoteTimestamp et sauvegarder
                                            lastSeenNoteTimestamp = createdDate;
                                            NotePreferences.saveLastSeenTimestamp(this, lastSeenNoteTimestamp);
                                        }
                                    } else {
                                        Log.w(TAG, "Note sans timestamp utilisable");
                                    }
                                }
                            }
                        }
                    });
            activeListeners.add(listener);
        } catch (Exception e) {
            Log.w(TAG, "Impossible de démarrer le listener pour userId: " + userId, e);
        }
    }






    private void onRemoteNoteAdded(DocumentSnapshot doc) {
        // 1) Sauvegarde la note en local
        saveRemoteNoteLocally(doc);

        // 2) Prépare et affiche la notification
        NotificationHelper.showNoteNotification(
                getApplicationContext(),
                getApplicationContext().getString(R.string.new_note_from_distant),
                doc.getString("title")
        );
        /*String title = doc.getString("title");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MyApplication.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("Nouvelle note partagée")
                .setContentText(title)
                .setAutoCancel(true);

        Intent intent = new Intent(this, NotesActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pi);

        Long idLong = doc.getLong("id");
        int notificationId = (idLong != null) ? idLong.intValue() : 0;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this)
                    .notify(notificationId, builder.build());
        } else {
            Log.w(TAG, "Notification non envoyée : permission manquante");
        }*/
    }


    /**
     * Déchiffre le DocumentSnapshot reçu de Firestore et l'insère ou met à jour
     * la note dans ta base locale, puis rafraîchit l'affichage.
     */
    private void saveRemoteNoteLocally(DocumentSnapshot doc) {
        String firebaseDocId = doc.getString("firebaseDocId");
        if (firebaseDocId == null) {
            // Fallback: utiliser l'ID du document Firestore
            firebaseDocId = doc.getId();
        }

        String title   = doc.getString("title");
        String content = doc.getString("content");

        Long colorObj = doc.getLong("color");
        if (colorObj == null) colorObj = 0L;
        int color = colorObj.intValue();

        Long positionObj = doc.getLong("position");
        if (positionObj == null) positionObj = 0L;
        int position = positionObj.intValue();

        try {
            Note note = new Note(0, firebaseDocId, title, content, color, position);
            db.insertOrUpdateNote(note);

            // Recharge la liste dans l'UI
            runOnUiThread(this::loadNotesFromLocalDatabase);

        } catch (Exception e) {
            Log.e(TAG, "Erreur décrypt/save local", e);
        }
    }

}
