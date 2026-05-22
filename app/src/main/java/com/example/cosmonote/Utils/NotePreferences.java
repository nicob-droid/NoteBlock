package com.example.cosmonote.Utils;


import android.content.Context;
import android.content.SharedPreferences;

import java.util.Date;

public class NotePreferences {

    private static final String PREFS_NAME = "NoteLockPrefs";
    private static final String KEY_LAST_SEEN_TIMESTAMP = "last_seen_timestamp";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    public static void saveLastSeenTimestamp(Context context, Date timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SEEN_TIMESTAMP, timestamp.getTime()).apply();
    }

    public static Date loadLastSeenTimestamp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long millis = prefs.getLong(KEY_LAST_SEEN_TIMESTAMP, 0);
        return new Date(millis);
    }

    public static void saveStoredPinHash(Context context, String hash) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PIN_HASH, hash).apply();
    }

    public static String loadStoredPinHash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PIN_HASH, null);
    }

    public static boolean isPinEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PIN_ENABLED, false);
    }

    public static void setPinEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PIN_ENABLED, enabled).apply();
    }

    public static void clearPinHash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PIN_HASH).apply();
    }
}
