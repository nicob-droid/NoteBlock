package com.example.noteblock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class NoteWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);

            // Intent pour ouvrir l'écran "Nouvelle note"
            Intent intent = new Intent(context, EditNoteActivity.class);
            intent.putExtra("isNewNote", true);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            views.setOnClickPendingIntent(R.id.widget_button_add, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}

