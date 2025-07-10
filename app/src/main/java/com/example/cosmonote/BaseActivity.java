package com.example.cosmonote;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cosmonote.Settings.ThemeHelper;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this); // Appliquer thème avant super.onCreate()
        super.onCreate(savedInstanceState);
    }
}
