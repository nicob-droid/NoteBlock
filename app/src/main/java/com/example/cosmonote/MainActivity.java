package com.example.cosmonote;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import com.example.cosmonote.Utils.HashUtils;
import com.example.cosmonote.Utils.NotePreferences;

public class MainActivity extends BaseActivity  {

    private EditText pinInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Si le PIN n'est pas activé, aller directement aux notes
        if (!NotePreferences.isPinEnabled(this)) {
            openNotesActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        pinInput = findViewById(R.id.pin_input);
        Button btnSubmit = findViewById(R.id.btn_submit);

        btnSubmit.setOnClickListener(v -> {
            String storedPinHash = NotePreferences.loadStoredPinHash(this);

            String enteredPin = pinInput.getText().toString();
            if (enteredPin.length() != 4) {
                Toast.makeText(this, getString(R.string.pin_must_be_4_digits), Toast.LENGTH_SHORT).show();
                return;
            }

            if (storedPinHash == null) {
                // First time setup store the hashed PIN
                String hash = HashUtils.sha256Hex(enteredPin);
                //prefs.edit().putString(KEY_PIN_HASH, hash).apply();
                NotePreferences.saveStoredPinHash(this, hash);
                Toast.makeText(this, getString(R.string.pin_set_welcome), Toast.LENGTH_SHORT).show();
                openNotesActivity();
            } else {
                // Check PIN
                String hash = HashUtils.sha256Hex(enteredPin);
                if (hash.equals(storedPinHash)) {
                    openNotesActivity();
                } else {
                    Toast.makeText(this, getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void openNotesActivity() {
        Intent intent = new Intent(this, NotesActivity.class);
        startActivity(intent);
        finish();
    }
}
