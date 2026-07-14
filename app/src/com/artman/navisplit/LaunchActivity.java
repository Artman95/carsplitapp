package com.artman.navisplit;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Invisible trampoline: launches a scenario (by id) then finishes. Used by widgets/shortcuts. */
public class LaunchActivity extends Activity {

    public static final String EXTRA_ID = "sid";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        Launcher.launchPair(this, Store.byId(this, id));
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 900);
    }
}
