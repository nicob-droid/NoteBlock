package com.example.cosmonote;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cosmonote.Settings.ThemeHelper;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this); // Appliquer thème avant super.onCreate()
        // Active l'affichage bord à bord (edge-to-edge) de façon rétrocompatible.
        // Nécessaire pour Android 15+ (SDK 35) et remplace l'usage des API dépréciées
        // setStatusBarColor / setNavigationBarColor.
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }
}
