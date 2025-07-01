package com.example.noteblock;

import static com.example.noteblock.MainActivity.KEY_PIN_HASH;
import static com.example.noteblock.MainActivity.PREFS_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteblock.Utils.HashUtils;

import java.nio.charset.StandardCharsets;

public class ChangePinActivity extends AppCompatActivity {

    private EditText oldPinInput, newPinInput, confirmNewPinInput;
    private Button btnChangePin;
    private NoteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin);

        oldPinInput = findViewById(R.id.old_pin_input);
        newPinInput = findViewById(R.id.new_pin_input);
        confirmNewPinInput = findViewById(R.id.confirm_new_pin_input);
        btnChangePin = findViewById(R.id.btn_change_pin);



        btnChangePin.setOnClickListener(v -> {
            String oldPin = oldPinInput.getText().toString();
            String newPin = newPinInput.getText().toString();
            String confirmPin = confirmNewPinInput.getText().toString();

            if (oldPin.length() != 4 || newPin.length() != 4 || confirmPin.length() != 4) {
                Toast.makeText(this, getString(R.string.pin_must_be_4_digits), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPin.equals(confirmPin)) {
                Toast.makeText(this, getString(R.string.pins_do_not_match), Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String storedPinHashHex = prefs.getString(KEY_PIN_HASH, null);

            // Compare PIN hash sous forme hex
            String oldPinHashHex = HashUtils.sha256Hex(oldPin);
            if (!oldPinHashHex.equals(storedPinHashHex)) {
                Toast.makeText(this, getString(R.string.incorrect_old_pin), Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                byte[] oldAesKey = HashUtils.sha256(oldPin.getBytes(StandardCharsets.UTF_8)); // 32 bytes
                byte[] newAesKey = HashUtils.sha256(newPin.getBytes(StandardCharsets.UTF_8)); // 32 bytes

                db = new NoteDatabase(this, oldAesKey);
                db.reencryptAllNotes(oldAesKey, newAesKey);

                prefs.edit().putString(KEY_PIN_HASH, HashUtils.sha256Hex(newPin)).apply();

                Toast.makeText(this, getString(R.string.pin_changed_success), Toast.LENGTH_SHORT).show();
                finish();

            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.modify_pin_error), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }
}
