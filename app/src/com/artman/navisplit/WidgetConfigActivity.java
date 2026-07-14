package com.artman.navisplit;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Shown when a widget is placed: pick which scenario this tile launches. */
public class WidgetConfigActivity extends Activity {

    int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED); // if user backs out, widget is not added

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Выберите сценарий для плитки");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        List<Scenario> scenarios = Store.load(this);
        for (Scenario s : scenarios) {
            Button btn = new Button(this);
            btn.setText(s.name);
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            btn.setPadding(dp(16), dp(18), dp(16), dp(18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            btn.setOnClickListener(v -> choose(s));
            root.addView(btn, lp);
        }

        setContentView(scroll);
    }

    void choose(Scenario s) {
        Store.mapWidget(this, widgetId, s.id);
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        WidgetProvider.updateOne(this, mgr, widgetId);

        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
