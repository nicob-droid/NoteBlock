package com.example.cosmonote.Settings;


import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

import com.example.cosmonote.BaseActivity;
import com.cosmonote.app.R;

import java.util.Objects;

public class SettingsPreferencesActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_preferences);

        // Affichage du fragment Settings
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

        // Configuration de la toolbar si nécessaire
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.settings); // depuis strings.xml
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
