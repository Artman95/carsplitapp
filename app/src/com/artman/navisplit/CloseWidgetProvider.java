package com.artman.navisplit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Desktop widget: one tap closes every freeform window. */
public class CloseWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.label, "✕ Закрыть окна");

            Intent i = new Intent(ctx, LaunchActivity.class);
            i.putExtra(LaunchActivity.EXTRA_CLOSE, true);
            i.setData(Uri.parse("navisplit://close/" + id));
            rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            mgr.updateAppWidget(id, rv);
        }
    }
}
