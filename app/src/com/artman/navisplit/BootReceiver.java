package com.artman.navisplit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** The box resets wireless adb on every boot; turn it back on so the shell path is reachable. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            Settings.Global.putInt(ctx.getContentResolver(), "adb_wifi_enabled", 1);
            Log.i("navisplit", "boot: adb_wifi_enabled=1");
        } catch (SecurityException e) {
            Log.w("navisplit", "boot: WRITE_SECURE_SETTINGS not granted", e);
        }
    }
}
