package com.artman.navisplit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * One-tap setup over the app's own adb channel: grants WRITE_SECURE_SETTINGS to itself and turns
 * on freeform. Replaces the manual `adb shell pm grant` / `settings put` steps.
 */
public class SetupActivity extends Activity {

    static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";

    TextView status;
    Button setup;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Настройка бокса");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        setup = button(root, "Настроить автоматически");
        setup.setOnClickListener(v -> runSetup());
        button(root, "Открыть настройки разработчика").setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        button(root, "Перезагрузить бокс").setOnClickListener(v -> confirmReboot());
        button(root, "Обновить статус").setOnClickListener(v -> refresh());

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    Button button(LinearLayout root, String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(btn, lp);
        return btn;
    }

    // ---- state ----

    boolean permGranted() {
        return checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean globalOn(Context c, String key) {
        return Settings.Global.getInt(c.getContentResolver(), key, 0) == 1;
    }

    boolean freeformOn() {
        return globalOn(this, "enable_freeform_support") && globalOn(this, "force_resizable_activities");
    }

    /** Re-reads everything; the adb probe blocks, so it runs off the UI thread. */
    void refresh() {
        status.setText("Проверка…");
        new Thread(() -> {
            boolean channel = Launcher.hasPriv(getApplicationContext());
            boolean perm = permGranted(), freeform = freeformOn();
            String text = line(channel, "Канал adb (127.0.0.1:5555)"
                    + (channel ? " — " + Launcher.privMode : ""))
                    + line(perm, "Право WRITE_SECURE_SETTINGS (автовключение отладки после перезагрузки)")
                    + line(freeform, "Freeform-окна включены")
                    + (channel ? "" : "\nВключите «Отладку по Wi-Fi» в настройках разработчика и нажмите «Обновить статус».\n")
                    + (channel && perm && freeform ? "\nВсё готово. Если freeform включали только что, перезагрузите бокс.\n" : "");
            runOnUiThread(() -> {
                status.setText(text);
                setup.setEnabled(channel && !(perm && freeform));
            });
        }).start();
    }

    static String line(boolean ok, String label) {
        return (ok ? "✓ " : "✗ ") + label + "\n";
    }

    // ---- actions ----

    void runSetup() {
        setup.setEnabled(false);
        status.setText("Настройка…");
        final Context app = getApplicationContext();
        new Thread(() -> {
            if (!permGranted()) {
                Launcher.runPriv(app, "pm grant " + getPackageName() + " " + PERM);
            }
            boolean changed = false;
            for (String key : new String[]{"enable_freeform_support", "force_resizable_activities"}) {
                if (!globalOn(app, key)) {
                    Launcher.runPriv(app, "settings put global " + key + " 1");
                    changed = true;
                }
            }
            final boolean needReboot = changed;
            runOnUiThread(() -> {
                refresh();
                if (needReboot) confirmReboot();
            });
        }).start();
    }

    void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle("Перезагрузить бокс?")
                .setMessage("Freeform включается только после перезагрузки. Отладка по Wi-Fi вернётся"
                        + " сама примерно через минуту.")
                .setPositiveButton("Перезагрузить", (d, w) -> new Thread(
                        () -> Launcher.runPriv(getApplicationContext(), "reboot")).start())
                .setNegativeButton("Позже", null)
                .show();
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
