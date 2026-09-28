package com.example.myapplication.dl;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.youtubedl_android.mapper.VideoFormat;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The real download engine, backed by yt-dlp + ffmpeg (youtubedl-android).
 * Supports YouTube (up to 4K), Instagram, Facebook, Snapchat and 1000+ sites,
 * merges DASH video+audio to mp4, and can pause (kill) / resume (-c continue).
 */
public final class DownloadEngine {

    private static final String TAG = "GODX_ENGINE";
    private static DownloadEngine INSTANCE;

    /** Quality tiers offered in the popup (highest first). */
    public static final Quality[] QUALITIES = new Quality[]{
            new Quality("4K · 2160p", 2160, false),
            new Quality("2K · 1440p", 1440, false),
            new Quality("1080p", 1080, false),
            new Quality("720p", 720, false),
            new Quality("480p", 480, false),
            new Quality("360p", 360, false),
            new Quality("240p", 240, false),
            new Quality("144p", 144, false),
            new Quality("Audio · MP3", 0, true),
    };

    public static final class Quality {
        public final String label;
        public final int height;      // 0 = audio only
        public final boolean audio;

        Quality(String label, int height, boolean audio) {
            this.label = label;
            this.height = height;
            this.audio = audio;
        }

        public String selector() {
            if (audio) return "bestaudio/best";
            return "bestvideo[height<=" + height + "]+bestaudio/best[height<=" + height + "]/best";
        }
    }

    /** Result of reading a URL: title, thumbnail and an estimated size per quality. */
    public static final class Probe {
        public String title;
        public String thumbnail;
        public int maxHeight = 0;
        public final long[] sizes = new long[QUALITIES.length];
    }

    /** Blocking — call off the main thread. Reads formats and estimates sizes. */
    public Probe probe(String url) throws Exception {
        return probe(url, null);
    }

    public Probe probe(String url, String referer) throws Exception {
        ensureInit();
        Probe p = new Probe();
        VideoInfo info;
        if (referer != null && !referer.isEmpty()) {
            YoutubeDLRequest req = new YoutubeDLRequest(url);
            req.addOption("--referer", referer);
            req.addOption("--no-playlist");
            info = YoutubeDL.getInstance().getInfo(req);
        } else {
            info = YoutubeDL.getInstance().getInfo(url);
        }
        if (info != null) {
            p.title = info.getTitle();
            p.thumbnail = info.getThumbnail();
            computeSizes(info, p);
        }
        return p;
    }

    private void computeSizes(VideoInfo info, Probe p) {
        int dur = info.getDuration();
        List<VideoFormat> fmts = info.getFormats();
        if (fmts == null) return;
        VideoFormat bestAudio = null;
        List<VideoFormat> videos = new ArrayList<>();
        for (VideoFormat f : fmts) {
            boolean hasV = f.getVcodec() != null && !"none".equals(f.getVcodec());
            boolean hasA = f.getAcodec() != null && !"none".equals(f.getAcodec());
            if (!hasV && hasA) {
                bestAudio = preferFormat(bestAudio, f); // largest real audio
            }
            if (hasV) {
                videos.add(f);
                if (f.getHeight() > p.maxHeight) p.maxHeight = f.getHeight();
            }
        }
        long bestAudioSz = bestAudio != null ? fmtSize(bestAudio, dur) : 0;

        for (int i = 0; i < QUALITIES.length; i++) {
            Quality q = QUALITIES[i];
            if (q.audio) {
                p.sizes[i] = bestAudioSz;
                continue;
            }
            VideoFormat chosen = null;
            for (VideoFormat f : videos) {
                int h = f.getHeight();
                if (h > 0 && h <= q.height) chosen = pickForTier(chosen, f);
            }
            if (chosen == null) { // nothing at/below tier -> smallest available
                for (VideoFormat f : videos) {
                    if (f.getHeight() > 0 && (chosen == null || f.getHeight() < chosen.getHeight())) {
                        chosen = f;
                    }
                }
            }
            if (chosen == null) {
                p.sizes[i] = 0;
                continue;
            }
            long sz = fmtSize(chosen, dur);
            boolean chosenHasA = chosen.getAcodec() != null && !"none".equals(chosen.getAcodec());
            if (!chosenHasA) sz += bestAudioSz;
            p.sizes[i] = sz;
        }
    }

    /** True if the format reports an exact byte size (DASH/https), not a guess. */
    private boolean hasExactSize(VideoFormat f) {
        return f != null && f.getFileSize() > 0;
    }

    /**
     * Pick the representative format for a tier the way yt-dlp roughly would:
     * highest resolution first, and — crucially — prefer formats that report an
     * exact byte size (progressive/DASH over HLS/m3u8, whose sizes are inflated
     * bitrate guesses) so the estimate matches the real download.
     */
    private VideoFormat pickForTier(VideoFormat cur, VideoFormat f) {
        if (cur == null) return f;
        if (f.getHeight() != cur.getHeight()) return f.getHeight() > cur.getHeight() ? f : cur;
        boolean fe = hasExactSize(f), ce = hasExactSize(cur);
        if (fe != ce) return fe ? f : cur;          // prefer the one with a real size
        return sizeOf(f) >= sizeOf(cur) ? f : cur;  // same exactness -> higher quality
    }

    /** For audio: prefer real-size formats, then the largest. */
    private VideoFormat preferFormat(VideoFormat cur, VideoFormat f) {
        if (cur == null) return f;
        boolean fe = hasExactSize(f), ce = hasExactSize(cur);
        if (fe != ce) return fe ? f : cur;
        return sizeOf(f) >= sizeOf(cur) ? f : cur;
    }

    private long sizeOf(VideoFormat f) {
        return f.getFileSize() > 0 ? f.getFileSize() : f.getFileSizeApproximate();
    }

    private long fmtSize(VideoFormat f, int dur) {
        long s = f.getFileSize() > 0 ? f.getFileSize() : f.getFileSizeApproximate();
        if (s <= 0 && f.getTbr() > 0 && dur > 0) s = (long) (f.getTbr() * 125.0 * dur);
        return s;
    }

    public interface ProgressUi {
        void onProgress(DownloadItem item);

        void onState(DownloadItem item);
    }

    private final Context app;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, ProgressUi> uiCallbacks = new ConcurrentHashMap<>();
    private volatile boolean inited = false;
    private volatile boolean initFailed = false;
    private volatile boolean updateTried = false;

    private static final Pattern SPEED = Pattern.compile("at\\s+([0-9.]+\\s?[KMG]?i?B/s)");

    private DownloadEngine(Context ctx) {
        this.app = ctx.getApplicationContext();
    }

    public static synchronized DownloadEngine get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new DownloadEngine(ctx);
        return INSTANCE;
    }

    public void registerUi(String id, ProgressUi ui) {
        if (id != null && ui != null) uiCallbacks.put(id, ui);
    }

    public void unregisterUi(String id) {
        uiCallbacks.remove(id);
    }

    public File outputDir() {
        File dir = new File(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GodxShadow");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    // ---------------------------------------------------------------- init

    private synchronized void ensureInit() throws YoutubeDLException {
        if (inited) return;
        if (initFailed) throw new YoutubeDLException("engine init failed earlier");
        try {
            YoutubeDL.getInstance().init(app);
            try {
                FFmpeg.getInstance().init(app);
            } catch (Throwable t) {
                Log.w(TAG, "ffmpeg init: " + t.getMessage());
            }
            inited = true;
        } catch (YoutubeDLException e) {
            initFailed = true;
            throw e;
        }
        maybeUpdate();
    }

    /**
     * The yt-dlp binary bundled in the library ages fast — YouTube/Instagram
     * break it within weeks. Pull the latest nightly build once per session so
     * extraction keeps working. Needs network; failure is non-fatal.
     */
    private void maybeUpdate() {
        if (updateTried) return;
        updateTried = true;
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY.INSTANCE);
        } catch (Throwable t) {
            Log.w(TAG, "yt-dlp update skipped: " + t.getMessage());
        }
    }

    /**
     * Warm the engine up in the background (init + one-time yt-dlp update) so the
     * first real download can start instantly instead of waiting on setup.
     */
    public void warmUp() {
        pool.execute(() -> {
            try {
                ensureInit();
            } catch (Throwable ignored) {
            }
        });
    }

    /** Let the user force a fresh yt-dlp pull (e.g. after a failure). */
    public void forceUpdate() {
        updateTried = false;
        pool.execute(() -> {
            try {
                ensureInit();
            } catch (Throwable ignored) {
            }
        });
    }

    // ------------------------------------------------------------- control

    public void start(final DownloadItem item) {
        DownloadStore.get(app).upsert(item);
        pool.execute(() -> run(item, false));
    }

    public void resume(final DownloadItem item) {
        item.state = DownloadItem.RUNNING;
        item.error = "";
        DownloadStore.get(app).upsert(item);
        pool.execute(() -> run(item, true));
    }

    public void pause(final DownloadItem item) {
        try {
            YoutubeDL.getInstance().destroyProcessById(item.id);
        } catch (Throwable ignored) {
        }
        item.state = DownloadItem.PAUSED;
        DownloadStore.get(app).upsert(item);
        emitState(item);
    }

    public void cancel(final DownloadItem item) {
        try {
            YoutubeDL.getInstance().destroyProcessById(item.id);
        } catch (Throwable ignored) {
        }
        DownloadStore.get(app).remove(item.id);
    }

    // --------------------------------------------------------------- worker

    private void run(final DownloadItem item, boolean isResume) {
        item.state = DownloadItem.RUNNING;
        emitState(item);
        try {
            ensureInit();
        } catch (Throwable e) {
            fail(item, "Engine unavailable: " + e.getMessage());
            return;
        }

        // best-effort metadata (title + size) if we do not have it yet
        if (item.title == null || item.title.trim().isEmpty()) {
            try {
                VideoInfo info;
                if (item.referer != null && !item.referer.isEmpty()) {
                    YoutubeDLRequest ir = new YoutubeDLRequest(item.url);
                    ir.addOption("--referer", item.referer);
                    ir.addOption("--no-playlist");
                    info = YoutubeDL.getInstance().getInfo(ir);
                } else {
                    info = YoutubeDL.getInstance().getInfo(item.url);
                }
                if (info != null) {
                    if (info.getTitle() != null) item.title = info.getTitle();
                    long sz = info.getFileSize() > 0 ? info.getFileSize() : info.getFileSizeApproximate();
                    if (sz > 0) item.totalBytes = sz;
                    DownloadStore.get(app).upsert(item);
                }
            } catch (Throwable t) {
                Log.w(TAG, "getInfo: " + t.getMessage());
            }
        }

        File dir = outputDir();
        String outTmpl = dir.getAbsolutePath() + "/%(title).60B_" + item.id + ".%(ext)s";

        YoutubeDLRequest req = new YoutubeDLRequest(item.url);
        req.addOption("-o", outTmpl);
        if (item.referer != null && !item.referer.isEmpty()) {
            req.addOption("--referer", item.referer);
        }
        req.addOption("--no-mtime");
        req.addOption("--restrict-filenames");
        req.addOption("-c"); // continue partial -> this is what makes resume work
        req.addOption("--no-playlist");
        req.addOption("--retries", "10");
        // ---- speed: pull DASH/HLS fragments in parallel + bigger buffers ----
        req.addOption("--concurrent-fragments", "8");
        req.addOption("--buffer-size", "16K");
        req.addOption("--http-chunk-size", "10M");
        req.addOption("--newline");
        if (item.audioOnly) {
            req.addOption("-x");
            req.addOption("--audio-format", "mp3");
            req.addOption("--audio-quality", "0");
        } else {
            req.addOption("-f", item.formatSelector);
            req.addOption("--merge-output-format", "mp4");
        }

        try {
            Function3<Float, Long, String, Unit> cb = (progress, etaInSeconds, line) -> {
                float p = progress == null ? -1f : progress;
                long eta = etaInSeconds == null ? -1L : etaInSeconds;
                if (p >= 0) item.percent = p;
                if (eta >= 0) item.etaSeconds = eta;
                if (item.totalBytes > 0 && p >= 0) {
                    item.downloadedBytes = (long) (item.totalBytes * (p / 100f));
                }
                if (line != null) {
                    Matcher m = SPEED.matcher(line);
                    if (m.find()) item.speed = m.group(1);
                }
                emitProgress(item);
                throttledPersist(item);
                return Unit.INSTANCE;
            };
            YoutubeDL.getInstance().execute(req, item.id, cb);

            // finished
            File produced = findProduced(dir, item.id);
            if (produced != null) {
                item.filePath = produced.getAbsolutePath();
                item.totalBytes = produced.length();
                item.downloadedBytes = produced.length();
                MediaSaver.exportToGallery(app, produced, item.audioOnly);
            }
            item.percent = 100f;
            item.state = DownloadItem.DONE;
            DownloadStore.get(app).upsert(item);
            emitState(item);
        } catch (Throwable e) {
            // A kill from pause() also lands here — respect the paused state.
            if (item.state == DownloadItem.PAUSED) {
                emitState(item);
                return;
            }
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            fail(item, trim(msg));
        }
    }

    private void fail(DownloadItem item, String msg) {
        item.state = DownloadItem.ERROR;
        item.error = msg;
        DownloadStore.get(app).upsert(item);
        emitState(item);
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private File findProduced(File dir, String id) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            String n = f.getName();
            if (n.contains("_" + id + ".") && !n.endsWith(".part")
                    && !n.endsWith(".ytdl")) {
                if (best == null || f.lastModified() > best.lastModified()) best = f;
            }
        }
        return best;
    }

    // --------------------------------------------------------- persistence

    private long lastPersist = 0L;

    private void throttledPersist(DownloadItem item) {
        long now = System.currentTimeMillis();
        if (now - lastPersist > 1000L) {
            lastPersist = now;
            DownloadStore.get(app).touch();
        }
    }

    private void emitProgress(final DownloadItem item) {
        final ProgressUi ui = uiCallbacks.get(item.id);
        if (ui != null) main.post(() -> ui.onProgress(item));
    }

    private void emitState(final DownloadItem item) {
        final ProgressUi ui = uiCallbacks.get(item.id);
        if (ui != null) main.post(() -> ui.onState(item));
    }
}
