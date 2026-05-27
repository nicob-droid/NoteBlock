package com.example.cosmonote;

import com.cosmonote.app.R;

import static com.example.cosmonote.NotesActivity.EXTRA_CREATE_SYNCED;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_COLOR;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_ID;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_OWNER_UID;
import static com.example.cosmonote.NotesActivity.EXTRA_NOTE_POSITION;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;


public class EditNoteActivity extends BaseActivity  {
    private static final String TAG = "EditNoteActivity";
    private static final long LOCK_TIMEOUT_MS = 60_000L;
    private static final long LOCK_HEARTBEAT_MS = 20_000L;
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
    private boolean createSyncedOnCreate = false;
    private String noteOwnerUid;
    private boolean hasActiveLock = false;
    private boolean isReadOnlyLocked = false;
    private final Handler lockHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            refreshLockHeartbeat();
            if (hasActiveLock) {
                lockHandler.postDelayed(this, LOCK_HEARTBEAT_MS);
            }
        }
    };

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

        tryAcquireLockIfNeeded();

        // Manage button DELETE
        manageDeleteButton();

        // Manage button SHARE
        btnShare.setOnClickListener(v -> showShareActionsDialog());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        // save note
        saveNote();
        releaseLockIfHeld();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lockHandler.removeCallbacks(lockHeartbeatRunnable);
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
        createSyncedOnCreate = getIntent().getBooleanExtra(EXTRA_CREATE_SYNCED, false);
        noteOwnerUid = getIntent().getStringExtra(EXTRA_NOTE_OWNER_UID);
    }

    private void initNoteFromDatabase() {
        db = new NoteDatabase(this);

        if (noteId != -1) {
            note = db.getNoteById(noteId);
            if (note != null) {
                firebaseDocId = note.getFirebaseDocId();
                noteOwnerUid = note.getOwnerUid();
                // remplis les champs de l'UI
                titleInput.setText(note.getTitle());
                contentInput.setText(note.getContent());
            }
        }
    }

    private void tryAcquireLockIfNeeded() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || noteId == -1 || TextUtils.isEmpty(firebaseDocId)) {
            isReadOnlyLocked = false;
            setInputsEnabled(true);
            return;
        }

        setInputsEnabled(false);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference lockRef = firestore.collection("note_locks").document(firebaseDocId);

        firestore.runTransaction(transaction -> {
            long now = System.currentTimeMillis();
            String currentUid = currentUser.getUid();
            String currentName = resolveCurrentUserName(currentUser);

            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(lockRef);
            if (snapshot.exists()) {
                String lockedByUid = snapshot.getString("lockedByUid");
                com.google.firebase.Timestamp expiresAt = snapshot.getTimestamp("expiresAt");
                long expiresAtMs = expiresAt != null ? expiresAt.toDate().getTime() : 0L;
                boolean lockStillActive = expiresAtMs > now;
                boolean lockedByOther = lockedByUid != null && !lockedByUid.equals(currentUid);
                if (lockStillActive && lockedByOther) {
                    String lockedByName = snapshot.getString("lockedByName");
                    throw new IllegalStateException("LOCKED_BY:" + (lockedByName == null ? "" : lockedByName));
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("firebaseDocId", firebaseDocId);
            data.put("lockedByUid", currentUid);
            data.put("lockedByName", currentName);
            data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            data.put("expiresAt", new com.google.firebase.Timestamp(new Date(now + LOCK_TIMEOUT_MS)));
            transaction.set(lockRef, data, SetOptions.merge());
            return null;
        }).addOnSuccessListener(aVoid -> {
            hasActiveLock = true;
            isReadOnlyLocked = false;
            setInputsEnabled(true);
            startLockHeartbeat();
        }).addOnFailureListener(e -> {
            hasActiveLock = false;
            isReadOnlyLocked = true;
            setInputsEnabled(false);
            stopLockHeartbeat();

            String byName = extractLockOwnerName(e);
            if (!TextUtils.isEmpty(byName)) {
                Toast.makeText(this, getString(R.string.note_lock_take_failed, byName), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.note_lock_take_failed_generic), Toast.LENGTH_LONG).show();
            }
            finish();
        });
    }

    private void startLockHeartbeat() {
        lockHandler.removeCallbacks(lockHeartbeatRunnable);
        lockHandler.postDelayed(lockHeartbeatRunnable, LOCK_HEARTBEAT_MS);
    }

    private void stopLockHeartbeat() {
        lockHandler.removeCallbacks(lockHeartbeatRunnable);
    }

    private void refreshLockHeartbeat() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (!hasActiveLock || currentUser == null || TextUtils.isEmpty(firebaseDocId)) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("firebaseDocId", firebaseDocId);
        data.put("lockedByUid", currentUser.getUid());
        data.put("lockedByName", resolveCurrentUserName(currentUser));
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        data.put("expiresAt", new com.google.firebase.Timestamp(new Date(now + LOCK_TIMEOUT_MS)));

        FirebaseFirestore.getInstance()
                .collection("note_locks")
                .document(firebaseDocId)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Heartbeat lock impossible", e));
    }

    private void releaseLockIfHeld() {
        stopLockHeartbeat();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (!hasActiveLock || currentUser == null || TextUtils.isEmpty(firebaseDocId)) {
            hasActiveLock = false;
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference lockRef = firestore.collection("note_locks").document(firebaseDocId);
        String currentUid = currentUser.getUid();

        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(lockRef);
            if (snapshot.exists()) {
                String lockedByUid = snapshot.getString("lockedByUid");
                if (currentUid.equals(lockedByUid)) {
                    transaction.delete(lockRef);
                }
            }
            return null;
        }).addOnFailureListener(e -> Log.w(TAG, "Release lock impossible", e));

        hasActiveLock = false;
    }

    private String resolveCurrentUserName(FirebaseUser user) {
        if (user == null) {
            return "";
        }
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        if (!TextUtils.isEmpty(user.getEmail())) {
            return user.getEmail();
        }
        return user.getUid();
    }

    private String extractLockOwnerName(Exception e) {
        if (e == null || e.getMessage() == null) {
            return null;
        }
        String prefix = "LOCKED_BY:";
        int idx = e.getMessage().indexOf(prefix);
        if (idx < 0) {
            return null;
        }
        String raw = e.getMessage().substring(idx + prefix.length()).trim();
        return raw.isEmpty() ? null : raw;
    }

    private void setInputsEnabled(boolean enabled) {
        titleInput.setEnabled(enabled);
        contentInput.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnShare.setEnabled(enabled);
    }

    private void manageDeleteButton() {
        if (note != null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String ownerUid = resolveNoteOwnerUid(currentUser);
            boolean canDelete = TextUtils.isEmpty(note.getFirebaseDocId())
                    || (currentUser != null && Objects.equals(currentUser.getUid(), ownerUid));
            btnDelete.setEnabled(canDelete);

            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_note_confirmation_title))
                    .setMessage(getString(R.string.delete_note_confirmation_message))
                    .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                        if (!canDelete) {
                            Toast.makeText(this, getString(R.string.note_delete_owner_only), Toast.LENGTH_SHORT).show();
                            return;
                        }
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
        // Note locale uniquement
        if (TextUtils.isEmpty(note.getFirebaseDocId())) {
            deleteNoteLocal(note);
            isNoteDeleted = true;
            finish();
            return;
        }

        // Note synchronisée: supprimer d'abord Firestore pour éviter la réapparition.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            new Thread(() -> {
                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                Set<String> ownerCandidates = new HashSet<>();
                String explicitOwnerUid = note.getOwnerUid();
                if (!TextUtils.isEmpty(explicitOwnerUid)) {
                    ownerCandidates.add(explicitOwnerUid.trim());
                }

                String resolvedOwnerUid = resolveNoteOwnerUid(currentUser);
                if (!TextUtils.isEmpty(resolvedOwnerUid)) {
                    ownerCandidates.add(resolvedOwnerUid.trim());
                }
                ownerCandidates.add(currentUser.getUid());

                try {
                    com.google.firebase.firestore.QuerySnapshot sharedUsersSnapshot = com.google.android.gms.tasks.Tasks.await(
                            firestore.collection("users")
                                    .document(currentUser.getUid())
                                    .collection("shared_users")
                                    .get()
                    );
                    if (sharedUsersSnapshot != null) {
                        for (DocumentSnapshot sharedDoc : sharedUsersSnapshot.getDocuments()) {
                            String sharedUserId = sharedDoc.getString("sharedUserId");
                            if (TextUtils.isEmpty(sharedUserId)) {
                                sharedUserId = sharedDoc.getId();
                            }
                            if (!TextUtils.isEmpty(sharedUserId)) {
                                ownerCandidates.add(sharedUserId.trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Impossible de charger shared_users pour suppression", e);
                }

                boolean deletedRemotely = false;
                for (String ownerId : ownerCandidates) {
                    try {
                        DocumentReference docRef = firestore.collection("users")
                                .document(ownerId)
                                .collection("notes")
                                .document(note.getFirebaseDocId());
                        DocumentSnapshot snapshot = com.google.android.gms.tasks.Tasks.await(docRef.get());
                        if (snapshot.exists()) {
                            com.google.android.gms.tasks.Tasks.await(docRef.delete());
                            deletedRemotely = true;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Suppression Firestore partielle ownerId=" + ownerId, e);
                    }
                }

                boolean finalDeletedRemotely = deletedRemotely;
                runOnUiThread(() -> {
                    if (finalDeletedRemotely) {
                        deleteNoteLocal(note);
                        isNoteDeleted = true;
                        finish();
                    } else {
                        Log.w(TAG, "Suppression Firestore impossible pour firebaseDocId=" + note.getFirebaseDocId());
                        Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        } else {
            Toast.makeText(this, getString(R.string.message_connect_to_share), Toast.LENGTH_SHORT).show();
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

        if (isReadOnlyLocked) {
            return;
        }

        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();

        if (title.isEmpty()) return;

        int position = selectedPosition;
        long id;

        try {
            if (noteId == -1) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                boolean canCreateSynced = createSyncedOnCreate && currentUser != null;
                noteOwnerUid = canCreateSynced ? currentUser.getUid() : null;

                // Création depuis onglet Synced -> note synchronisée ; sinon note locale.
                firebaseDocId = canCreateSynced ? java.util.UUID.randomUUID().toString() : null;
                id = db.insertNote(firebaseDocId, title, content, selectedColor, position, noteOwnerUid);
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
            Note note = new Note((int) id, firebaseDocId, noteOwnerUid, title, content, selectedColor, position, timestamp);

            // Synchroniser uniquement les notes déjà synchronisées.
            if(!isNoteDeleted && !TextUtils.isEmpty(firebaseDocId)) {
                syncNoteToFirestore(note);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur saveNote", e);
        }
    }


    private void showShareActionsDialog() {
        String[] options = new String[] {
                getString(R.string.share_note_text_option),
                getString(R.string.manage_note_access_option)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.share_note_actions_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        shareNoteText();
                    } else {
                        manageNoteAccess();
                    }
                })
                .show();
    }

    private void shareNoteText() {
        String noteTitle = titleInput.getText().toString();
        String noteContent = contentInput.getText().toString();
        if ((!noteTitle.isEmpty()) && (!noteContent.isEmpty())) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, noteTitle + "\n" + noteContent);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_note_with)));
        } else {
            Toast.makeText(this, getString(R.string.note_empty), Toast.LENGTH_SHORT).show();
        }
    }

    private void manageNoteAccess() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.message_connect_to_share), Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(firebaseDocId)) {
            Toast.makeText(this, getString(R.string.manage_note_access_requires_synced), Toast.LENGTH_SHORT).show();
            return;
        }

        String ownerUid = resolveNoteOwnerUid(currentUser);
        if (TextUtils.isEmpty(ownerUid)) {
            Toast.makeText(this, getString(R.string.manage_note_access_requires_synced), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Objects.equals(currentUser.getUid(), ownerUid)) {
            Toast.makeText(this, getString(R.string.manage_note_access_owner_only), Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference noteRef = firestore.collection("users")
                .document(ownerUid)
                .collection("notes")
                .document(firebaseDocId);

        firestore.collection("users")
                .document(ownerUid)
                .collection("shared_users")
                .get()
                .addOnSuccessListener(sharedUsersSnapshot -> noteRef.get().addOnSuccessListener(noteDoc -> {
                    List<String> candidates = new ArrayList<>();
                    if (sharedUsersSnapshot != null) {
                        for (DocumentSnapshot doc : sharedUsersSnapshot.getDocuments()) {
                            String sharedUid = doc.getString("sharedUserId");
                            if (TextUtils.isEmpty(sharedUid)) {
                                sharedUid = doc.getId();
                            }
                            if (!TextUtils.isEmpty(sharedUid) && !sharedUid.equals(ownerUid)) {
                                candidates.add(sharedUid);
                            }
                        }
                    }

                    Map<String, Object> currentSharedWith = new HashMap<>();
                    Object sharedWithObj = noteDoc.get("sharedWith");
                    if (sharedWithObj instanceof Map) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) sharedWithObj).entrySet()) {
                            if (entry.getKey() instanceof String && Boolean.TRUE.equals(entry.getValue())) {
                                currentSharedWith.put((String) entry.getKey(), true);
                            }
                        }
                    }

                    if (candidates.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_shared_users), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String[] items = candidates.toArray(new String[0]);
                    boolean[] checked = new boolean[items.length];
                    for (int i = 0; i < items.length; i++) {
                        checked[i] = Boolean.TRUE.equals(currentSharedWith.get(items[i]));
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.manage_note_access_dialog_title))
                            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                            .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                                Map<String, Object> newSharedWith = new HashMap<>();
                                for (int i = 0; i < items.length; i++) {
                                    if (checked[i]) {
                                        newSharedWith.put(items[i], true);
                                    }
                                }

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("ownerUid", ownerUid);
                                updates.put("sharedWith", newSharedWith);
                                updates.put("timestamp", Timestamp.now());

                                noteRef.set(updates, SetOptions.merge())
                                        .addOnSuccessListener(aVoid -> Toast.makeText(this, getString(R.string.note_access_saved), Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Impossible de sauvegarder le partage par note", e);
                                            Toast.makeText(this, getString(R.string.note_access_save_error), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Lecture note impossible pour gérer accès", e);
                    Toast.makeText(this, getString(R.string.note_access_save_error), Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lecture shared_users impossible", e);
                    Toast.makeText(this, getString(R.string.note_access_save_error), Toast.LENGTH_SHORT).show();
                });
    }

    private String resolveNoteOwnerUid(FirebaseUser currentUser) {
        if (!TextUtils.isEmpty(noteOwnerUid)) {
            return noteOwnerUid;
        }
        if (note != null && !TextUtils.isEmpty(note.getOwnerUid())) {
            noteOwnerUid = note.getOwnerUid();
            return noteOwnerUid;
        }
        if (currentUser != null && !TextUtils.isEmpty(firebaseDocId)) {
            noteOwnerUid = currentUser.getUid();
            return noteOwnerUid;
        }
        return null;
    }


    private void syncNoteToFirestore(Note note) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Utilisateur non connecté, pas de sync Firestore");
            return;
        }

        if (noteId != -1 && !hasActiveLock) {
            Log.w(TAG, "syncNoteToFirestore ignoré: lock non acquis");
            return;
        }

        String userId = currentUser.getUid();
        String ownerUid = note.getOwnerUid();
        if (TextUtils.isEmpty(ownerUid)) {
            ownerUid = userId;
            note.setOwnerUid(ownerUid);
        }
        noteOwnerUid = ownerUid;
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference docRef = firestore.collection("users")
                .document(ownerUid)
                .collection("notes")
                .document(note.getFirebaseDocId());

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("firebaseDocId", note.getFirebaseDocId());
        noteData.put("ownerUid", ownerUid);
        noteData.put("id", note.getId());
        noteData.put("title", note.getTitle());
        noteData.put("content", note.getContent());
        noteData.put("color", note.getColor());
        noteData.put("position", note.getPosition());
        noteData.put("timestamp", new Timestamp(new Date(note.getTimestamp())));
        if (noteId == -1) {
            noteData.put("sharedWith", new HashMap<String, Object>());
        }

        docRef.set(noteData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Note synchronisée dans Firestore firebaseDocId=" + note.getFirebaseDocId()))
                .addOnFailureListener(e -> Log.e(TAG, "Erreur lors de la sync Firestore", e));
    }
}
