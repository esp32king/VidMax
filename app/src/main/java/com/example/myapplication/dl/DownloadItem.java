package com.example.myapplication.dl;

import org.json.JSONException;
import org.json.JSONObject;

/** One download row, persisted as JSON. */
public class DownloadItem {

    public static final int QUEUED = 0;
    public static final int RUNNING = 1;
    public static final int PAUSED = 2;
    public static final int DONE = 3;
    public static final int ERROR = 4;

    public String id;
    public String url;
    public String title;
    public String referer;          // optional Referer header (e.g. resolved DiskWala)
    public String qualityLabel;     // e.g. "1080p", "4K", "Audio (MP3)"
    public String formatSelector;   // yt-dlp -f expression
    public boolean audioOnly;
    public String filePath;         // final file once known
    public int state = QUEUED;
    public float percent = 0f;
    public long totalBytes = 0L;
    public long downloadedBytes = 0L;
    public String speed = "";
    public long etaSeconds = -1L;
    public long createdAt = System.currentTimeMillis();
    public String error = "";

    public DownloadItem() {
    }

    public DownloadItem(String id, String url) {
        this.id = id;
        this.url = url;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("url", url);
            o.put("title", title);
            o.put("referer", referer);
            o.put("qualityLabel", qualityLabel);
            o.put("formatSelector", formatSelector);
            o.put("audioOnly", audioOnly);
            o.put("filePath", filePath);
            o.put("state", state);
            o.put("percent", percent);
            o.put("totalBytes", totalBytes);
            o.put("downloadedBytes", downloadedBytes);
            o.put("createdAt", createdAt);
            o.put("error", error);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static DownloadItem fromJson(JSONObject o) {
        DownloadItem d = new DownloadItem();
        d.id = o.optString("id");
        d.url = o.optString("url");
        d.title = o.optString("title");
        d.referer = o.optString("referer", null);
        d.qualityLabel = o.optString("qualityLabel");
        d.formatSelector = o.optString("formatSelector");
        d.audioOnly = o.optBoolean("audioOnly");
        d.filePath = o.optString("filePath", null);
        d.state = o.optInt("state", QUEUED);
        d.percent = (float) o.optDouble("percent", 0);
        d.totalBytes = o.optLong("totalBytes", 0);
        d.downloadedBytes = o.optLong("downloadedBytes", 0);
        d.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        d.error = o.optString("error", "");
        // a download that was running when the app died counts as paused
        if (d.state == RUNNING) d.state = PAUSED;
        return d;
    }

    public boolean isActive() {
        return state == QUEUED || state == RUNNING || state == PAUSED;
    }
}
