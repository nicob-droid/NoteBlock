package com.example.cosmonote;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.google.android.gms.ads.MobileAds;
import com.google.firebase.FirebaseApp;

public class MyApplication extends Application {
    public static final String CHANNEL_ID = "note_updates";  // ← public static

    @Override
    public void onCreate() {
        super.onCreate();
        // 1) Init Firebase
        FirebaseApp.initializeApp(this);

        // 2) Init AdMob
        MobileAds.initialize(this, initializationStatus -> {});

        // 3) Création du canal de notification
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        CharSequence name = "Note updates";
        String description = "Alerts when a shared user adds a new note";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel channel =
                new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager manager =
                getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
