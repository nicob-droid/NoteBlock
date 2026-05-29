package com.example.cosmonote;

import com.cosmonote.app.R;

import android.Manifest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.cosmonote.Settings.SettingsPreferencesActivity;
import com.example.cosmonote.Utils.NotePreferences;
import com.example.cosmonote.Utils.NotificationHelper;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;
import com.google.android.gms.tasks.Tasks;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NotesActivity extends BaseActivity  implements NotesAdapter.OnNoteClickListener {
    private static final String TAG = "NotesActivity";
    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_NOTE_COLOR = "note_color";
    public static final String EXTRA_NOTE_POSITION = "note_position";
    public static final String EXTRA_NOTE_OWNER_UID = "note_owner_uid";
    private static final int REQUEST_CODE_POST_NOTIF = 1001;
    private NoteDatabase db;
    private final List<ListenerRegistration> activeListeners = new ArrayList<>();
    private final Map<String, ListenerRegistration> listenersByUserId = new ConcurrentHashMap<>();
    private Date lastSeenNoteTimestamp;
    private BroadcastReceiver reloadReceiver;
    private ImageView notesBackgroundImageView;
    private NotesListFragment notesListFragment;
    private final Map<String, NoteLockState> activeNoteLocks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> ownerSharedUserLabels = new ConcurrentHashMap<>();
    private String lastListenerUserId;
    private AdView adView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_notes);
        notesBackgroundImageView = findViewById(R.id.notes_background_image);
        applySavedBackgroundImage();

        // Lire la date de la dernière note
        lastSeenNoteTimestamp = NotePreferences.loadLastSeenTimestamp(this);
        // Init database
        db = new NoteDatabase(this);

        if (savedInstanceState == null) {
            notesListFragment = NotesListFragment.newInstanceAll();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.notes_fragment_container, notesListFragment)
                    .commit();
        } else {
            notesListFragment = (NotesListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.notes_fragment_container);
        }

         // Init du floating button
         FloatingActionButton fab = findViewById(R.id.fab_add_note);
         fab.setOnClickListener(v -> {
             // Ouvre activité d'édition pour créer une nouvelle note
             // Les notes sont toujours créées en LOCAL (pas de synchronisation immédiate)
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

        // Charger la bannière AdMob
        adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void applySavedBackgroundImage() {
        if (notesBackgroundImageView == null) {
            return;
        }

        String backgroundUriString = NotePreferences.loadNotesBackgroundImageUri(this);
        if (backgroundUriString == null || backgroundUriString.trim().isEmpty()) {
            notesBackgroundImageView.setImageDrawable(null);
            notesBackgroundImageView.setVisibility(View.GONE);
            return;
        }

        try {
            notesBackgroundImageView.setImageURI(Uri.parse(backgroundUriString));
            notesBackgroundImageView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.w(TAG, "Impossible de charger l'image de fond: " + backgroundUriString, e);
            NotePreferences.clearNotesBackgroundImageUri(this);
            notesBackgroundImageView.setImageDrawable(null);
            notesBackgroundImageView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adView != null) adView.resume();

        applySavedBackgroundImage();

        refreshAllNotes();

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

        // Enregistrer le receiver pour forcer rechargement après partage
        reloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                Log.d(TAG, "Broadcast reçu : rechargement des listeners Firestore");
                for (ListenerRegistration l : activeListeners) l.remove();
                activeListeners.clear();
                startFirestoreListeners();
            }
        };
        IntentFilter filter = new IntentFilter("com.cosmonote.app.RELOAD_NOTES");
        ContextCompat.registerReceiver(this, reloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;
        boolean authStateChanged = !Objects.equals(lastListenerUserId, currentUid);

        if (authStateChanged) {
            for (ListenerRegistration l : activeListeners) {
                l.remove();
            }
            activeListeners.clear();
            activeNoteLocks.clear();
            ownerSharedUserLabels.clear();
            lastListenerUserId = currentUid;
            refreshAllNotes();
            startFirestoreListeners();
        } else if (activeListeners.isEmpty()) {
            startFirestoreListeners();
        }
    }

    private void refreshAllNotes() {
        if (notesListFragment == null) {
            notesListFragment = (NotesListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.notes_fragment_container);
        }

        if (notesListFragment != null) {
            notesListFragment.refreshNotes();
        }
    }


    public Map<String, NoteLockState> getActiveNoteLocks() {
        return new HashMap<>(activeNoteLocks);
    }

    private void startFirestoreListeners() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            startNoteLocksListener();

            String currentUserId = firebaseUser.getUid();
            loadSharedUserLabels(currentUserId);
            fetchNotesFromFirestore(this, currentUserId, currentUserId);
            startListeningNotes(currentUserId, currentUserId);
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
                                        loadSharedUserLabels(sharedUserId);
                                        fetchNotesFromFirestore(this, sharedUserId, currentUserId);
                                        startListeningNotes(sharedUserId, currentUserId);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Impossible de démarrer le listener pour sharedUserId: " + sharedUserId, e);
                                    }
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "Impossible de récupérer sharedUserId", e));
        } else {
            activeNoteLocks.clear();
            refreshAllNotes();
        }
    }

    private void startNoteLocksListener() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        try {
            ListenerRegistration lockListener = firestore
                    .collection("note_locks")
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.w(TAG, "Lock listener failed", error);
                            return;
                        }
                        if (snapshots == null) {
                            return;
                        }

                        long now = System.currentTimeMillis();
                        Map<String, NoteLockState> newLocks = new HashMap<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String firebaseDocId = doc.getString("firebaseDocId");
                            String lockedByUid = doc.getString("lockedByUid");
                            String lockedByName = doc.getString("lockedByName");
                            Timestamp expiresAt = doc.getTimestamp("expiresAt");
                            long expiresAtMillis = expiresAt != null ? expiresAt.toDate().getTime() : 0L;

                            if (firebaseDocId == null || firebaseDocId.trim().isEmpty()) {
                                firebaseDocId = doc.getId();
                            }
                            if (!firebaseDocId.trim().isEmpty() && expiresAtMillis > now) {
                                newLocks.put(firebaseDocId, new NoteLockState(lockedByUid, lockedByName, expiresAtMillis));
                            }
                        }

                        activeNoteLocks.clear();
                        activeNoteLocks.putAll(newLocks);
                        refreshAllNotes();
                    });
            activeListeners.add(lockListener);
        } catch (Exception e) {
            Log.w(TAG, "Impossible de démarrer le lock listener", e);
        }
    }

    @Override
    protected void onPause() {
        if (adView != null) adView.pause();
        super.onPause();
        // Ne pas supprimer les listeners ici pour garder la synchro en arrière-plan
        // Les listeners sont supprimés dans onDestroy
        if (reloadReceiver != null) {
            unregisterReceiver(reloadReceiver);
            reloadReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (adView != null) adView.destroy();
        super.onDestroy();
        for (ListenerRegistration l : activeListeners) {
            l.remove();
        }
        activeListeners.clear();
        if (db != null) {
            db.close();
            db = null;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIF
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Permission notifications non accordée", Toast.LENGTH_SHORT).show();
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
        intent.putExtra(EXTRA_NOTE_OWNER_UID, note.getOwnerUid());
        startActivity(intent);
    }

    @Override
    public void onColorPickerClick(Note note) {
        // Ouvre le BottomSheet de sélection de couleur (comme avant)
        showColorPickerBottomSheet(note);
    }


    private void showColorPickerBottomSheet(Note note) {
        View view = getLayoutInflater().inflate(
                R.layout.bottom_sheet_color_picker,
                findViewById(android.R.id.content),
                false
        );
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
        // Ne pas modifier l'objet en mémoire ici — laisser SQLite être la source de vérité
        // pour que DiffUtil détecte bien le changement
        new Thread(() -> {
            try {
                db.updateNote(note.getId(), note.getTitle(), note.getContent(), newColor, note.getPosition());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // Créer une note avec la nouvelle couleur pour Firestore
            Note updatedNote = new Note(note.getId(), note.getFirebaseDocId(), note.getOwnerUid(),
                    note.getTitle(), note.getContent(), newColor, note.getPosition());
            syncNoteColorToFirestore(updatedNote);
            runOnUiThread(this::refreshAllNotes);
        }).start();
    }

    private void syncNoteColorToFirestore(Note note) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || note.getFirebaseDocId() == null) {
            Log.w(TAG, "syncNoteColorToFirestore: user=" + currentUser + ", docId=" + (note != null ? note.getFirebaseDocId() : "null"));
            return;
        }

        String userId = currentUser.getUid();
        String ownerUid = note.getOwnerUid();
        if (ownerUid == null || ownerUid.trim().isEmpty()) {
            ownerUid = userId;
        }
        Map<String, Object> noteData = new HashMap<>();
        noteData.put("firebaseDocId", note.getFirebaseDocId());
        noteData.put("ownerUid", ownerUid);
        noteData.put("title", note.getTitle());
        noteData.put("content", note.getContent());
        noteData.put("color", note.getColor());
        noteData.put("position", note.getPosition());
        noteData.put("timestamp", new com.google.firebase.Timestamp(new java.util.Date()));

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(ownerUid)
                .collection("notes")
                .document(note.getFirebaseDocId())
                .set(noteData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Couleur synchronisée Firestore: " + note.getColor()))
                .addOnFailureListener(e -> Log.e(TAG, "Erreur sync couleur Firestore", e));
    }


    private void deleteAllNotes() {
        new Thread(() -> {
            List<Note> allNotes = db.getAllNotes();
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            Set<String> fallbackOwnerIds = new HashSet<>();
            if (currentUser != null) {
                fallbackOwnerIds = loadFallbackOwnerIds(firestore, currentUser.getUid());
            }

            int failedRemoteDeletes = 0;
            for (Note note : allNotes) {
                String docId = note.getFirebaseDocId();
                if (docId == null || docId.trim().isEmpty()) {
                    db.deleteNoteById(note.getId());
                    continue;
                }

                if (currentUser == null) {
                    failedRemoteDeletes++;
                    continue;
                }

                boolean deletedRemotely = deleteRemoteNoteFromFirestore(
                        firestore,
                        docId,
                        note.getOwnerUid(),
                        fallbackOwnerIds
                );

                if (deletedRemotely) {
                    db.deleteNoteById(note.getId());
                } else {
                    failedRemoteDeletes++;
                }
            }

            int finalFailedRemoteDeletes = failedRemoteDeletes;
            runOnUiThread(() -> {
                refreshAllNotes();
                if (finalFailedRemoteDeletes == 0) {
                    Toast.makeText(this, getString(R.string.all_notes_deleted), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Set<String> loadFallbackOwnerIds(FirebaseFirestore firestore, String currentUserId) {
        Set<String> fallbackOwnerIds = new HashSet<>();
        fallbackOwnerIds.add(currentUserId);

        try {
            QuerySnapshot sharedUsersSnapshot = Tasks.await(
                    firestore.collection("users")
                            .document(currentUserId)
                            .collection("shared_users")
                            .get()
            );
            if (sharedUsersSnapshot != null) {
                for (DocumentSnapshot sharedDoc : sharedUsersSnapshot.getDocuments()) {
                    String sharedUserId = sharedDoc.getString("sharedUserId");
                    if (sharedUserId == null || sharedUserId.trim().isEmpty()) {
                        sharedUserId = sharedDoc.getId();
                    }
                    if (sharedUserId != null && !sharedUserId.trim().isEmpty()) {
                        fallbackOwnerIds.add(sharedUserId.trim());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Impossible de charger shared_users pour suppression fallback", e);
        }

        return fallbackOwnerIds;
    }

    private boolean deleteRemoteNoteFromFirestore(FirebaseFirestore firestore, String firebaseDocId, String ownerUid, Set<String> fallbackOwnerIds) {
        Set<String> candidateOwners = new HashSet<>();
        if (ownerUid != null && !ownerUid.trim().isEmpty()) {
            candidateOwners.add(ownerUid.trim());
        }
        candidateOwners.addAll(fallbackOwnerIds);

        for (String candidateOwnerId : candidateOwners) {
            DocumentReference docRef = firestore.collection("users")
                    .document(candidateOwnerId)
                    .collection("notes")
                    .document(firebaseDocId);

            try {
                DocumentSnapshot snapshot = Tasks.await(docRef.get());
                if (!snapshot.exists()) {
                    continue;
                }
                Tasks.await(docRef.delete());
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Suppression Firestore partielle pour ownerId=" + candidateOwnerId + ", docId=" + firebaseDocId, e);
            }
        }

        return false;
    }

    private void fetchNotesFromFirestore(Context context, String userId, String currentUserId) {
        FirebaseFirestore firebase = FirebaseFirestore.getInstance();
        boolean isRemoteUser = currentUserId != null && !userId.equals(currentUserId);

        firebase.collection("users")
                .document(userId)
                .collection("notes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null) {
                            boolean hasLocalChanges = false;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String firebaseDocId = document.getString("firebaseDocId");
                                if (!hasAccessToRemoteNote(document, currentUserId, isRemoteUser)) {
                                    continue;
                                }

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
                                        if (firebaseDocId == null && localNote.getFirebaseDocId() != null) {
                                            pushFirebaseDocIdToFirestore(userId, document.getId(), localNote.getFirebaseDocId(), localNote);
                                        }
                                        // Mettre à jour si contenu OU couleur différents
                                        // Pour utilisateur distant : on prend toujours la couleur distante
                                        // Pour ses propres notes : on prend aussi la couleur distante (multi-device)
                                        String docId = (firebaseDocId != null) ? firebaseDocId : localNote.getFirebaseDocId();
                                        boolean contentChanged = !localNote.getTitle().equals(title) || !localNote.getContent().equals(content);
                                        boolean colorChanged = (localNote.getColor() != color);
                                        boolean positionChanged = (localNote.getPosition() != position);
                                        boolean ownerChanged = localNote.getOwnerUid() == null || !userId.equals(localNote.getOwnerUid());
                                        String ownerDisplayLabel = resolveOwnerDisplayLabelForNote(userId, currentUserId);
                                        boolean ownerLabelChanged = !Objects.equals(localNote.getOwnerDisplayLabel(), ownerDisplayLabel);
                                        String sharedWithSummary = buildSharedWithSummary(document, userId);
                                        boolean sharingChanged = !Objects.equals(localNote.getSharedWithSummary(), sharedWithSummary);
                                        if (contentChanged || colorChanged || positionChanged || ownerChanged || ownerLabelChanged || sharingChanged) {
                                            Note updated = new Note(localNote.getId(), docId, userId, title, content,
                                                    color, position, System.currentTimeMillis(), sharedWithSummary, ownerDisplayLabel);
                                            db.insertOrUpdateNote(updated);
                                            hasLocalChanges = true;
                                        }
                                    } else {
                                        // Nouvelle note (venant d'un autre appareil ou utilisateur partagé)
                                        if (firebaseDocId == null) {
                                            firebaseDocId = document.getId();
                                        }
                                        String sharedWithSummary = buildSharedWithSummary(document, userId);
                                        String ownerDisplayLabel = resolveOwnerDisplayLabelForNote(userId, currentUserId);
                                        Note note = new Note(0, firebaseDocId, userId, title, content, color, position, System.currentTimeMillis(), sharedWithSummary, ownerDisplayLabel);
                                        db.insertOrUpdateNote(note);
                                        hasLocalChanges = true;

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
                            // Rafraîchir l'affichage seulement si la base locale a changé
                            if (hasLocalChanges) {
                                runOnUiThread(this::refreshAllNotes);
                            }
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
        noteData.put("ownerUid", userId);
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

    private void startListeningNotes(String userId, String currentUserId) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // Charger le dernier timestamp enregistré
        lastSeenNoteTimestamp = NotePreferences.loadLastSeenTimestamp(this);
        Log.d(TAG, "lastSeenNoteTimestamp = " + lastSeenNoteTimestamp);

        boolean isRemoteUser = currentUserId != null && !userId.equals(currentUserId);

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
                                DocumentSnapshot doc = dc.getDocument();
                                String firebaseDocId = doc.getString("firebaseDocId");
                                if (firebaseDocId == null) firebaseDocId = doc.getId();
                                if (!hasAccessToRemoteNote(doc, currentUserId, isRemoteUser)) {
                                    continue;
                                }

                                switch (dc.getType()) {
                                    case ADDED:
                                        // Vérifier si la note existe déjà localement
                                        if (db.noteExistsByFirebaseDocId(firebaseDocId)) {
                                            // La note existe déjà : mettre à jour seulement si nécessaire
                                            // (couvre le cas où la couleur a changé pendant qu'on était déconnecté)
                                            boolean updated = updateRemoteNoteLocally(doc, firebaseDocId, userId, currentUserId);
                                            if (updated) {
                                                Log.d(TAG, "Note ADDED déjà présente, mise à jour silencieuse: " + firebaseDocId);
                                            }
                                            break;
                                        }
                                        // Note absente localement : l'ajouter avec notification
                                        Log.d(TAG, "Nouvelle note distante détectée: " + firebaseDocId);
                                        onRemoteNoteAdded(doc, userId, currentUserId);
                                        // Mettre à jour le timestamp si possible
                                        Object timestampObj = doc.get("timestamp");
                                        Date createdDate = null;
                                        if (timestampObj instanceof com.google.firebase.Timestamp) {
                                            createdDate = ((com.google.firebase.Timestamp) timestampObj).toDate();
                                        } else if (timestampObj instanceof Long) {
                                            createdDate = new Date((Long) timestampObj);
                                        } else if (timestampObj instanceof Double) {
                                            createdDate = new Date(((Double) timestampObj).longValue());
                                        }
                                        if (createdDate != null && createdDate.after(lastSeenNoteTimestamp)) {
                                            lastSeenNoteTimestamp = createdDate;
                                            NotePreferences.saveLastSeenTimestamp(this, lastSeenNoteTimestamp);
                                        }
                                        break;

                                    case MODIFIED:
                                        // Mettre à jour la note locale (couleur, contenu, etc.)
                                        if (updateRemoteNoteLocally(doc, firebaseDocId, userId, currentUserId)) {
                                            NotificationHelper.showNoteNotification(
                                                    getApplicationContext(),
                                                    "Note modifiée",
                                                    doc.getString("title"));
                                        }
                                        break;

                                    case REMOVED:
                                        // Supprimer la note locale
                                        String removedTitle = doc.getString("title");
                                        deleteRemoteNoteLocally(firebaseDocId);
                                        NotificationHelper.showNoteNotification(
                                                getApplicationContext(),
                                                "Note supprimée",
                                                removedTitle);
                                        break;
                                }
                            }
                        }
                    });
            activeListeners.add(listener);
                            listenersByUserId.put(userId, listener);
                            Log.d(TAG, "Listener enregistré pour userId: " + userId);
        } catch (Exception e) {
            Log.w(TAG, "Impossible de démarrer le listener pour userId: " + userId, e);
        }
    }






    private void onRemoteNoteAdded(DocumentSnapshot doc, String ownerUid, String currentUserId) {
        // 1) Sauvegarde la note en local
        saveRemoteNoteLocally(doc, ownerUid, currentUserId);

        // 2) Recharge la liste pour afficher immédiatement la note
        refreshAllNotes();

        // 3) Prépare et affiche la notification
        NotificationHelper.showNoteNotification(
                getApplicationContext(),
                getApplicationContext().getString(R.string.new_note_from_distant),
                doc.getString("title")
        );
        /*...existing code...*/
    }


    /**
     * Déchiffre le DocumentSnapshot reçu de Firestore et l'insère ou met à jour
     * la note dans ta base locale, puis rafraîchit l'affichage.
     */
    private boolean updateRemoteNoteLocally(DocumentSnapshot doc, String firebaseDocId, String ownerUid, String currentUserId) {
        try {
            String title = doc.getString("title");
            String content = doc.getString("content");
            Long colorObj = doc.getLong("color");
            Long positionObj = doc.getLong("position");
            String sharedWithSummary = buildSharedWithSummary(doc, ownerUid);
            String ownerDisplayLabel = resolveOwnerDisplayLabelForNote(ownerUid, currentUserId);

            Note localNote = db.getNoteByFirebaseDocId(firebaseDocId);
            int color = (colorObj != null) ? colorObj.intValue() : (localNote != null ? localNote.getColor() : 0);
            int position = (positionObj != null) ? positionObj.intValue() : (localNote != null ? localNote.getPosition() : 0);

            if (localNote == null) {
                // La note n'existe pas encore localement : l'insérer
                Note note = new Note(0, firebaseDocId, ownerUid, title, content, color, position, System.currentTimeMillis(), sharedWithSummary, ownerDisplayLabel);
                db.insertOrUpdateNote(note);
                runOnUiThread(this::refreshAllNotes);
                Log.d(TAG, "Note distante ajoutée localement: " + firebaseDocId + " couleur=" + color);
                return true;
            } else {
                boolean sameTitle = java.util.Objects.equals(localNote.getTitle(), title);
                boolean sameContent = java.util.Objects.equals(localNote.getContent(), content);
                boolean sameColor = localNote.getColor() == color;
                boolean samePosition = localNote.getPosition() == position;
                boolean sameOwner = java.util.Objects.equals(localNote.getOwnerUid(), ownerUid);
                boolean sameOwnerLabel = java.util.Objects.equals(localNote.getOwnerDisplayLabel(), ownerDisplayLabel);
                boolean sameSharing = java.util.Objects.equals(localNote.getSharedWithSummary(), sharedWithSummary);

                if (sameTitle && sameContent && sameColor && samePosition && sameOwner && sameOwnerLabel && sameSharing) {
                    return false;
                }

                Note updated = new Note(localNote.getId(), firebaseDocId, ownerUid, title, content, color, position, System.currentTimeMillis(), sharedWithSummary, ownerDisplayLabel);
                db.insertOrUpdateNote(updated);
                runOnUiThread(this::refreshAllNotes);
                Log.d(TAG, "Note distante mise à jour localement: " + firebaseDocId + " couleur=" + color);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur updateRemoteNoteLocally", e);
        }
        return false;
    }

    private void deleteRemoteNoteLocally(String firebaseDocId) {
        try {
            Note localNote = db.getNoteByFirebaseDocId(firebaseDocId);
            if (localNote == null) return;

            db.deleteNoteById(localNote.getId());
            runOnUiThread(this::refreshAllNotes);
            Log.d(TAG, "Note distante supprimée localement: " + firebaseDocId);
        } catch (Exception e) {
            Log.e(TAG, "Erreur deleteRemoteNoteLocally", e);
        }
    }

    private void saveRemoteNoteLocally(DocumentSnapshot doc, String ownerUid, String currentUserId) {
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
        String sharedWithSummary = buildSharedWithSummary(doc, ownerUid);
        String ownerDisplayLabel = resolveOwnerDisplayLabelForNote(ownerUid, currentUserId);

        try {
            Note note = new Note(0, firebaseDocId, ownerUid, title, content, color, position, System.currentTimeMillis(), sharedWithSummary, ownerDisplayLabel);
            db.insertOrUpdateNote(note);

            // Recharge la liste dans l'UI
            runOnUiThread(this::refreshAllNotes);

        } catch (Exception e) {
            Log.e(TAG, "Erreur décrypt/save local", e);
        }
    }

    private boolean hasAccessToRemoteNote(DocumentSnapshot doc, String currentUserId, boolean isRemoteUser) {
        if (!isRemoteUser) {
            return true;
        }
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return false;
        }

        Object sharedWithObj = doc.get("sharedWith");
        if (sharedWithObj instanceof Map) {
            // Si sharedWith est une map, vérifier si on est dedans
            Object value = ((Map<?, ?>) sharedWithObj).get(currentUserId);
            if (Boolean.TRUE.equals(value)) {
                return true;
            }
            // Partage par note strict: si on n'est pas dans la map, accès refusé
            // (y compris map vide = note privée, non partagée)
            Map<?, ?> sharedWithMap = (Map<?, ?>) sharedWithObj;
            return !sharedWithMap.isEmpty() && Boolean.TRUE.equals(value);
        }

        // Compatibilité migration: anciennes notes sans sharedWith restent visibles.
        return true;
    }

    private String buildSharedWithSummary(DocumentSnapshot doc, String ownerUid) {
        Object sharedWithObj = doc.get("sharedWith");
        if (!(sharedWithObj instanceof Map)) {
            return null;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        List<String> sharedUserIds = new ArrayList<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) sharedWithObj).entrySet()) {
            if (!(entry.getKey() instanceof String) || !Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }

            String uid = ((String) entry.getKey()).trim();
            if (uid.isEmpty()) {
                continue;
            }
            if (ownerUid != null && ownerUid.equals(uid)) {
                continue;
            }
            if (currentUid != null && currentUid.equals(uid)) {
                continue;
            }
            sharedUserIds.add(resolveSharedUserLabel(ownerUid, uid));
        }

        if (sharedUserIds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sharedUserIds.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sharedUserIds.get(i));
        }
        return sb.toString();
    }

    private void loadSharedUserLabels(String ownerUid) {
        if (ownerUid == null || ownerUid.trim().isEmpty()) {
            return;
        }
        String normalizedOwnerUid = ownerUid.trim();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(normalizedOwnerUid)
                .collection("shared_users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, String> labels = new HashMap<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String uid = doc.getString("sharedUserId");
                            if (uid == null || uid.trim().isEmpty()) {
                                uid = doc.getId();
                            }
                            if (uid == null || uid.trim().isEmpty()) {
                                continue;
                            }
                            uid = uid.trim();

                            String label = firstNonEmpty(
                                    doc.getString("sharedUserName"),
                                    doc.getString("sharedUserEmail"),
                                    doc.getString("displayName"),
                                    doc.getString("email"),
                                    uid
                            );
                            labels.put(uid, label);
                        }
                    }
                    ownerSharedUserLabels.put(normalizedOwnerUid, labels);

                    // Recalculer les résumés "Partagée avec" déjà en base locale avec les nouveaux labels.
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        fetchNotesFromFirestore(this, normalizedOwnerUid, currentUser.getUid());
                    }
                    refreshAllNotes();
                })
                .addOnFailureListener(e -> Log.w(TAG, "Impossible de charger les labels shared_users pour owner=" + normalizedOwnerUid, e));
    }

    private String resolveSharedUserLabel(String ownerUid, String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return "";
        }
        String normalizedUid = uid.trim();
        String normalizedOwnerUid = ownerUid != null ? ownerUid.trim() : null;

        Map<String, String> labels = normalizedOwnerUid != null ? ownerSharedUserLabels.get(normalizedOwnerUid) : null;
        if (labels != null) {
            String cached = labels.get(normalizedUid);
            if (cached != null && !cached.trim().isEmpty()) {
                return cached;
            }
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && normalizedUid.equals(currentUser.getUid())) {
            String meLabel = firstNonEmpty(currentUser.getDisplayName(), currentUser.getEmail(), normalizedUid);
            return meLabel;
        }

        return normalizedUid;
    }

    private String resolveOwnerDisplayLabelForNote(String ownerUid, String currentUserId) {
        if (ownerUid == null || ownerUid.trim().isEmpty()) {
            return null;
        }
        if (currentUserId != null && ownerUid.equals(currentUserId)) {
            return null;
        }
        String label = resolveSharedUserLabel(currentUserId, ownerUid);
        if (label == null || label.trim().isEmpty()) {
            return ownerUid;
        }
        return label;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Arrête le listener pour un utilisateur distant donné.
     * Appelée quand on retire le partage avec cet utilisateur.
     */
    public void stopListeningForUser(String remoteUserId) {
        ListenerRegistration listener = listenersByUserId.get(remoteUserId);
        if (listener != null) {
            listener.remove();
            listenersByUserId.remove(remoteUserId);
            activeListeners.remove(listener);
            ownerSharedUserLabels.remove(remoteUserId);
            Log.d(TAG, "Listener arrêté et labels nettoyés pour userId: " + remoteUserId);
        }
    }

}
