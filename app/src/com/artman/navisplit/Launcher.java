package com.artman.navisplit;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.reflect.Method;

/** Freeform tiling launcher shared by the config UI, the desktop widget and shortcuts. */
public class Launcher {

    static final int WINDOWING_MODE_FREEFORM = 5;
    static boolean unsealed = false;

    static void launchPair(final Context ctx, Scenario s) {
        if (s == null) return;
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
