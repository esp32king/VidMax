package com.example.myapplication;

import android.app.Application;
import android.util.Log;

import com.example.myapplication.data.IdentityCache;

/** Process entry. Keeps a light crash breadcrumb; no network / no services here. */
public class NeonApp extends Application {

    private static final String TAG = "GODX_APP";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String msg = e == null ? "unknown" : e.getClass().getSimpleName() + ": " + e.getMessage();
                IdentityCache.putJson(this, "last_crash", msg);
                Log.e(TAG, "uncaught", e);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
