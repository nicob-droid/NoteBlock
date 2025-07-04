package com.example.noteblock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText uidInput, currentUserIdEditText;
    private Button saveBtn, shareIdBtn;
    private MaterialButton deleteBtn;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        uidInput = findViewById(R.id.uid_input);
        saveBtn = findViewById(R.id.save_btn);
        deleteBtn = findViewById(R.id.delete_btn);
        shareIdBtn = findViewById(R.id.share_id_btn);
        currentUserIdEditText = findViewById(R.id.current_user_id);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String sharedUid = prefs.getString("shared_user_id", "");

        uidInput.setText(sharedUid);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            currentUserIdEditText.setText(getString(R.string.message_logged_to_share));
            shareIdBtn.setEnabled(false);
        } else {
            String currentUserId = currentUser.getUid();
            currentUserIdEditText.setText(currentUserId);
        }

        saveBtn.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(this, getString(R.string.message_logged_to_share), Toast.LENGTH_SHORT).show();
                return;
            }
            String sharedUserId = uidInput.getText().toString().trim();
            if (sharedUserId.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_user_uid), Toast.LENGTH_SHORT).show();
                return;
            }
            if (sharedUserId.equals(currentUser.getUid())) {
                Toast.makeText(this, getString(R.string.cant_share_with_yourself), Toast.LENGTH_SHORT).show();
                return;
            }

            saveBtn.setEnabled(false); // Désactive pendant la sauvegarde

            saveSharedUserId(currentUser.getUid(), sharedUserId);
        });

        deleteBtn.setOnClickListener(v -> {
            if (currentUser == null) {
                Toast.makeText(this, getString(R.string.message_logged_to_delete), Toast.LENGTH_SHORT).show();
                return;
            }

            deleteSharedUserId(currentUser.getUid());
        });

        shareIdBtn.setOnClickListener(v -> {
            if (currentUser != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.my_current_id) + "\n" + currentUser.getUid());
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_id_with)));
            }
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
                    Toast.makeText(this, getString(R.string.share_saved), Toast.LENGTH_SHORT).show();
                    saveBtn.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.share_save_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveBtn.setEnabled(true);
                });
    }

    private void deleteSharedUserId(String ownerUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("shared_users")
                .document(ownerUserId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.share_deleted), Toast.LENGTH_SHORT).show();
                    uidInput.setText(""); // Efface le champ UI
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.share_delete_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


}

