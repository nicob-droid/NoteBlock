package com.example.noteblock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText uidInput;
    private Button saveBtn, deleteBtn;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        uidInput = findViewById(R.id.uid_input);
        saveBtn = findViewById(R.id.save_btn);
        deleteBtn = findViewById(R.id.delete_btn);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String sharedUid = prefs.getString("shared_user_id", "");

        uidInput.setText(sharedUid);

        saveBtn.setOnClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Vous devez être connecté pour partager", Toast.LENGTH_SHORT).show();
                return;
            }
            String sharedUserId = uidInput.getText().toString().trim();
            if (sharedUserId.isEmpty()) {
                Toast.makeText(this, "Entrez un UID utilisateur", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sharedUserId.equals(currentUser.getUid())) {
                Toast.makeText(this, "Vous ne pouvez pas partager avec vous-même", Toast.LENGTH_SHORT).show();
                return;
            }

            saveBtn.setEnabled(false); // Désactive pendant la sauvegarde

            saveSharedUserId(currentUser.getUid(), sharedUserId);
        });

        deleteBtn.setOnClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Vous devez être connecté pour supprimer le partage", Toast.LENGTH_SHORT).show();
                return;
            }

            deleteSharedUserId(currentUser.getUid());
        });
    }

    private void saveSharedUserId(String ownerUserId, String sharedUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", ownerUserId);
        data.put("sharedUserId", sharedUserId);

        db.collection("shared_users")
                .document(ownerUserId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Partage sauvegardé", Toast.LENGTH_SHORT).show();
                    saveBtn.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur sauvegarde partage: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveBtn.setEnabled(true);
                });
    }

    private void deleteSharedUserId(String ownerUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("shared_users")
                .document(ownerUserId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Partage supprimé", Toast.LENGTH_SHORT).show();
                    uidInput.setText(""); // Efface le champ UI
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur suppression partage: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


}

