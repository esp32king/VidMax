package com.example.myapplication.dl;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persists the download list and broadcasts changes to any live screen. */
public final class DownloadStore {

    public interface Listener {
        void onDownloadsChanged();
    }

    private static final String PREFS = "godx_downloads";
    private static final String KEY = "items_v1";

    private static DownloadStore INSTANCE;

    private final SharedPreferences prefs;
    private final List<DownloadItem> items = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private DownloadStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized DownloadStore get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new DownloadStore(ctx);
        return INSTANCE;
    }

    private void load() {
        items.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                items.add(DownloadItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        for (DownloadItem d : items) arr.put(d.toJson());
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    public synchronized List<DownloadItem> all() {
        return new ArrayList<>(items);
    }

    public synchronized DownloadItem byId(String id) {
        for (DownloadItem d : items) if (d.id.equals(id)) return d;
        return null;
    }

    public synchronized void upsert(DownloadItem item) {
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(item.id)) {
                items.set(i, item);
                found = true;
                break;
            }
        }
        if (!found) items.add(0, item);
        persist();
        notifyChanged();
    }

    public synchronized void remove(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                break;
            }
        }
        persist();
        notifyChanged();
    }

    /** Persist current field values without changing structure (throttled by caller). */
    public synchronized void touch() {
        persist();
        notifyChanged();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) {
            try {
                l.onDownloadsChanged();
            } catch (Exception ignored) {
            }
        }
    }
}
