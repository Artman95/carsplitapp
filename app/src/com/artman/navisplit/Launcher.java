package com.artman.navisplit;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Freeform tiling launcher shared by the config UI, the desktop widget and shortcuts. */
public class Launcher {

    static final int WINDOWING_MODE_FREEFORM = 5;
    static boolean unsealed = false;
    static Boolean rootCache;

    static void launchPair(final Context ctx, final Scenario s) {
        if (s == null) return;
        final Context app = ctx.getApplicationContext();
        // su calls block; never run them on the UI thread
        new Thread(() -> {
            if (!tilePairViaRoot(app, s)) {
                new Handler(Looper.getMainLooper()).post(() -> launchNonRoot(app, s));
            }
        }).start();
    }

    // ---- root path: re-tiles even ALREADY-RUNNING apps (the non-root path can't) ----

    /** @return true if root handled it; false to fall back to the non-root path. */
    static boolean tilePairViaRoot(Context ctx, Scenario s) {
        if (!hasRoot()) return false;
        Point sz = realSize(ctx);
        int w = sz.x, h = sz.y;
        int split = Math.round(w * s.leftPercent / 100f);
        boolean left = tileViaRoot(ctx, s.left, 0, 0, split, h);
        boolean right = tileViaRoot(ctx, s.right, split, 0, w, h);
        return left && right;
    }

    /** am start --windowingMode 5 flips/launches the task into freeform; am task resize then
     *  applies the tile bounds (a no-op on a fullscreen task, hence the windowing-mode flip first). */
    static boolean tileViaRoot(Context ctx, String pkg, int l, int t, int r, int b) {
        Intent li = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        ComponentName cn = li == null ? null : li.getComponent();
        if (cn == null) { toast(ctx, "Не найдено: " + pkg); return false; }
        runSu("am start --windowingMode " + WINDOWING_MODE_FREEFORM
                + " -n " + cn.getPackageName() + "/" + cn.getClassName());
        String tid = null;
        for (int i = 0; i < 10 && tid == null; i++) {
            tid = taskIdOf(pkg);
            if (tid == null) sleep(300);
        }
        if (tid == null) { Log.w("navisplit", "no taskId for " + pkg); return false; }
        runSu("am task resize " + tid + " " + l + " " + t + " " + r + " " + b);
        return true;
    }

    static String taskIdOf(String pkg) {
        Matcher m = Pattern.compile("#(\\d+) type=standard A=\\d+:" + Pattern.quote(pkg) + "(?:\\s|$)")
                .matcher(runSu("dumpsys activity activities"));
        return m.find() ? m.group(1) : null;
    }

    static boolean hasRoot() {
        if (rootCache == null) rootCache = runSu("id").contains("uid=0");
        return rootCache;
    }

    /** Run one command as root; returns stdout ("" if su is absent/denied). */
    static String runSu(String cmd) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                for (String line; (line = br.readLine()) != null; ) out.append(line).append('\n');
            }
            p.waitFor();
        } catch (Exception e) {
            Log.w("navisplit", "su failed: " + e);
        }
        return out.toString();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    static void toast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    // ---- non-root fallback: only tiles apps that aren't already running ----

    static void launchNonRoot(final Context ctx, Scenario s) {
        unsealHiddenApi();
        Point sz = realSize(ctx);
        // keep window bottoms above the navigation bar so its ◁○□ buttons
        // don't overlap the app's own bottom menu
        int gap = Math.round(4 * ctx.getResources().getDisplayMetrics().density);
        final int w = sz.x, h = sz.y - navBarHeight(ctx) - gap;
        final int split = Math.round(w * s.leftPercent / 100f);
        final String right = s.right;
        launch(ctx, s.left, new Rect(0, 0, split, h));
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> launch(ctx, right, new Rect(split, 0, w, h)), 500);
    }

    static void launch(Context ctx, String pkg, Rect bounds) {
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(ctx, "Не найдено: " + pkg, Toast.LENGTH_LONG).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setLaunchBounds(bounds);
        try {
            Method m = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
            m.invoke(opts, WINDOWING_MODE_FREEFORM);
        } catch (Throwable t) {
            Log.w("navisplit", "setFreeform failed: " + t);
        }
        ctx.startActivity(i, opts.toBundle());
    }

    static Point realSize(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    static int navBarHeight(Context ctx) {
        int id = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * setLaunchWindowingMode is blocklisted on this ROM. Exempt every hidden API for
     * THIS process only via VMRuntime.setHiddenApiExemptions, reached through
     * meta-reflection (the reflectively-invoked getDeclaredMethod is attributed to the
     * bootclasspath, so the lookups themselves are not blocked). Nothing on the device
     * is changed. Same technique as tiann/FreeReflection.
     */
    static synchronized void unsealHiddenApi() {
        if (unsealed) return;
        unsealed = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                    "getDeclaredMethod", String.class, Class[].class);
            Log.i("navisplit", "unseal step1 meta ok");
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Log.i("navisplit", "unseal step2 vmRuntime=" + vmRuntime);
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntime, "getRuntime", null);
            Method setExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntime, "setHiddenApiExemptions", new Class[]{String[].class});
            Log.i("navisplit", "unseal step3 methods ok");
            Object runtime = getRuntime.invoke(null);
            Log.i("navisplit", "unseal step4 runtime=" + runtime);
            setExemptions.invoke(runtime, new Object[]{new String[]{"L"}});
            Log.i("navisplit", "unseal step5 DONE");
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            Log.w("navisplit", "unseal failed", c);
        }
    }
}
