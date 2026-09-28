package com.example.myapplication.data;

import android.content.Context;

/**
 * Tiny SharedPreferences helper kept from the original project so the splash and
 * settings can persist small flags. All caller-lookup logic has been removed.
 */
public final class IdentityCache {

    private static final String PREFS = "neon_prefs";

    private IdentityCache() {
    }

    public static boolean flag(Context ctx, String key) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
    }

    public static void setFlag(Context ctx, String key, boolean value) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
    }

    public static long stamp(Context ctx, String key) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, 0L);
    }

    public static void setStamp(Context ctx, String key, long value) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(key, value).apply();
    }

    public static void putJson(Context ctx, String key, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, json).apply();
    }

    public static String getJson(Context ctx, String key) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
    }
}
