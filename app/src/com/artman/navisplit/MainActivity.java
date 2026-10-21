package com.artman.navisplit;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Config hub: create / edit / delete tiling scenarios and launch them. Scenarios are
 * shared with the desktop widget (Store) and executed by Launcher.
 */
public class MainActivity extends Activity {

    final List<Scenario> scenarios = new ArrayList<>();
    LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        scenarios.addAll(Store.load(this));
        buildUi();
    }

    // ---------- UI ----------

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Сценарии мультиокна");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        Button add = new Button(this);
        add.setText("＋ Новый сценарий");
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        add.setOnClickListener(v -> showEditor(null));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(16);
        root.addView(add, alp);

        Button closeAll = new Button(this);
        closeAll.setText("✕ Закрыть все окна");
        closeAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        closeAll.setOnClickListener(v -> Launcher.closeAll(this));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        root.addView(closeAll, clp);

        Button setupBtn = new Button(this);
        setupBtn.setText("⚙ Настройка бокса");
        setupBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        setupBtn.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        root.addView(setupBtn, new LinearLayout.LayoutParams(clp));

        TextView hint = new TextView(this);
        hint.setText("Короткий тап — запустить. Долгий тап — изменить или удалить.\n"
                + "Плитки на рабочий стол: добавь виджет «Мультиокно» и выбери сценарий.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);

        TextView version = new TextView(this);
        version.setText("Версия " + versionName());
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        version.setPadding(0, dp(8), 0, 0);
        root.addView(version);

        setContentView(scroll);
        renderList();
    }

    void renderList() {
        listContainer.removeAllViews();
        for (Scenario s : scenarios) {
            Button btn = new Button(this);
            btn.setText(s.name);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            btn.setAllCaps(false);
            btn.setPadding(dp(20), dp(22), dp(20), dp(22));
            btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            btn.setOnClickListener(v -> launch(s));
            btn.setOnLongClickListener(v -> { showEditor(s); return true; });
            listContainer.addView(btn, lp);
        }
    }

    void launch(Scenario s) {
        Launcher.launchPair(this, s);
        new Handler(Looper.getMainLooper()).postDelayed(() -> moveTaskToBack(true), 1000);
    }

    // ---------- editor ----------

    void showEditor(final Scenario existing) {
        final List<String[]> apps = installedApps(); // [label, pkg]
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i)[0];

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));

        final EditText name = new EditText(this);
        name.setHint("Название сценария");
        if (existing != null) name.setText(existing.name);
        form.addView(labeled("Название", name));

        final Spinner left = new Spinner(this);
        left.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(labeled("Левое окно", left));

        final Spinner right = new Spinner(this);
        right.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(labeled("Правое окно", right));

        final int[] ratios = {50, 65, 35, 60, 40};
        final String[] ratioLabels = {"50 / 50", "65 / 35 (левое шире)", "35 / 65 (правое шире)",
                "60 / 40", "40 / 60"};
        final Spinner ratio = new Spinner(this);
        ratio.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ratioLabels));
        form.addView(labeled("Раскладка", ratio));

        if (existing != null) {
            left.setSelection(indexOfPkg(apps, existing.left));
            right.setSelection(indexOfPkg(apps, existing.right));
            for (int i = 0; i < ratios.length; i++) if (ratios[i] == existing.leftPercent) ratio.setSelection(i);
        } else if (apps.size() > 1) {
            right.setSelection(1);
        }

        AlertDialog.Builder db = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Новый сценарий" : "Изменить сценарий")
                .setView(wrapScroll(form))
                .setPositiveButton("Сохранить", (d, w) -> {
                    String nm = name.getText().toString().trim();
                    if (nm.isEmpty()) nm = "Без названия";
                    String lp = apps.get(left.getSelectedItemPosition())[1];
                    String rp = apps.get(right.getSelectedItemPosition())[1];
                    int pct = ratios[ratio.getSelectedItemPosition()];
                    if (existing == null) {
                        scenarios.add(new Scenario(System.currentTimeMillis(), nm, lp, rp, pct));
                    } else {
                        existing.name = nm; existing.left = lp; existing.right = rp; existing.leftPercent = pct;
                    }
                    persist();
                })
                .setNegativeButton("Отмена", null);
        if (existing != null) {
            db.setNeutralButton("Удалить", (d, w) -> { scenarios.remove(existing); persist(); });
        }
        db.show();
    }

    void persist() {
        Store.save(this, scenarios);
        refreshWidgets();
        renderList();
    }

    void refreshWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(this, WidgetProvider.class));
        for (int id : ids) WidgetProvider.updateOne(this, mgr, id);
    }

    LinearLayout labeled(String label, View field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(2));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        box.addView(t);
        box.addView(field);
        return box;
    }

    ScrollView wrapScroll(View v) {
        ScrollView s = new ScrollView(this);
        s.addView(v);
        return s;
    }

    // ---------- helpers ----------

    List<String[]> installedApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (!seen.add(pkg)) continue;
            out.add(new String[]{ri.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(out, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return out;
    }

    int indexOfPkg(List<String[]> apps, String pkg) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i)[1].equals(pkg)) return i;
        return 0;
    }

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
