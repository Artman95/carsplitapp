package com.artman.navisplit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Desktop widget: one tile per scenario. Tapping it launches the tiled pair. */
public class WidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateOne(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) Store.unmapWidget(ctx, id);
    }

    static void updateOne(Context ctx, AppWidgetManager mgr, int widgetId) {
        long sid = Store.widgetScenario(ctx, widgetId);
        Scenario s = Store.byId(ctx, sid);

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.label, s != null ? s.name : "— выбрать —");

        Intent i = new Intent(ctx, LaunchActivity.class);
        i.putExtra(LaunchActivity.EXTRA_ID, sid);
        // unique data so each widget's PendingIntent stays distinct
        i.setData(Uri.parse("navisplit://widget/" + widgetId));
        PendingIntent pi = PendingIntent.getActivity(ctx, widgetId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.root, pi);

        mgr.updateAppWidget(widgetId, rv);
    }
}
