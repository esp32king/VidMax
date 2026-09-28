package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.myapplication.dl.DownloadEngine;
import com.example.myapplication.dl.DownloadItem;
import com.example.myapplication.ui.CircleProgressView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns every floating window: the right-side quality popup and the shrinking
 * circular download bubbles. Runs as a foreground service so downloads survive.
 */
public class FloatingService extends Service {

    public static final String ACTION_QUALITY = "godx.QUALITY";
    public static final String EXTRA_URL = "url";

    private static final String CH_ID = "godx_floating";
    private static final int NOTI_ID = 7;

    private WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View qualityView;
    private DownloadEngine.Probe lastProbe;
    private String lastProbeUrl;
    private String qReferer;   // Referer to use for the current popup/download (DiskWala)
    private final Map<String, Bubble> bubbles = new LinkedHashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(NOTI_ID, buildNotification());
        // Warm up early so tapping a quality starts the download immediately.
        DownloadEngine.get(this).warmUp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_QUALITY.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (!TextUtils.isEmpty(url)) main.post(() -> handleUrl(url));
        }
        return START_STICKY;
    }

    // -------------------------------------------------------- notification

    private Notification.Builder newBuilder() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "GodxShadow downloads",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CH_ID)
                : new Notification.Builder(this);
    }

    /** Tapping the notification opens the app. */
    private PendingIntent contentPI() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, i, flags);
    }

    private Notification buildNotification() {
        return newBuilder()
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("Reaper Downloader")
                .setContentText("Ready to download")
                .setContentIntent(contentPI())
                .setOngoing(true)
                .build();
    }

    /** Live download progress in the notification: percent + speed + size. */
    private void updateNotif(DownloadItem it) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            String t = it.title != null && !it.title.isEmpty() ? it.title : "GodxShadow";
            Notification.Builder b = newBuilder()
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(t)
                    .setContentIntent(contentPI())
                    .setOnlyAlertOnce(true);
            int pct = Math.round(it.percent);
            switch (it.state) {
                case DownloadItem.RUNNING:
                    b.setContentText(pct + "%"
                            + (it.speed != null && !it.speed.isEmpty() ? " · " + it.speed : "")
                            + " · " + sizeLabel(it));
                    b.setProgress(100, pct, false).setOngoing(true);
                    break;
                case DownloadItem.PAUSED:
                    b.setContentText("Paused · " + pct + "% · " + sizeLabel(it));
                    b.setProgress(100, pct, false).setOngoing(false);
                    break;
                case DownloadItem.DONE:
                    b.setContentText("Completed · " + FloatingService.human(it.totalBytes));
                    b.setOngoing(false);
                    break;
                case DownloadItem.ERROR:
                    b.setContentText("Failed — open app to retry");
                    b.setOngoing(false);
                    break;
                default:
                    b.setContentText("Queued…").setOngoing(true);
            }
            nm.notify(NOTI_ID, b.build());
        } catch (Throwable ignored) {
        }
    }

    // ----------------------------------------------------- window helpers

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private WindowManager.LayoutParams params(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        return lp;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    // -------------------------------------------------------- quality popup

    @SuppressLint("ClickableViewAccessibility")
    /** Entry point for a shared link: DiskWala needs resolving via WebView first. */
    private void handleUrl(final String url) {
        if (DiskwalaResolver.isDiskwala(url)) {
            if (!canOverlay()) {
                Toast.makeText(this, "Enable 'Display over other apps' for DiskWala", Toast.LENGTH_LONG).show();
                openApp();
                return;
            }
            Toast.makeText(this, "Reading DiskWala video…", Toast.LENGTH_SHORT).show();
            DiskwalaResolver.resolve(this, url, mediaUrl -> main.post(() -> {
                if (mediaUrl == null || mediaUrl.isEmpty()) {
                    Toast.makeText(this,
                            "DiskWala: couldn't read this video (it may be private or protected).",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                qReferer = "https://www.diskwala.com/";
                showQualityPopup(mediaUrl);
            }));
            return;
        }
        qReferer = null;
        showQualityPopup(url);
    }

    private void showQualityPopup(final String url) {
        if (!canOverlay()) {
            Toast.makeText(this, "Enable 'Display over other apps' for GodxShadow", Toast.LENGTH_LONG).show();
            openApp();
            return;
        }
        removeQuality();

        final View v = LayoutInflater.from(this).inflate(R.layout.overlay_quality, null);
        final WindowManager.LayoutParams lp = params(dp(238), WindowManager.LayoutParams.WRAP_CONTENT);
        lp.x = dp(8);
        lp.y = dp(80);

        final TextView title = v.findViewById(R.id.qTitle);
        final ImageView thumb = v.findViewById(R.id.qThumb);
        title.setText(url);
        LinearLayout list = v.findViewById(R.id.qualityList);

        final DownloadEngine.Quality[] qs = DownloadEngine.QUALITIES;
        final View[] rows = new View[qs.length];
        final TextView[] sizeViews = new TextView[qs.length];

        for (int i = 0; i < qs.length; i++) {
            final DownloadEngine.Quality q = qs[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_quality_btn);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(6);
            row.setLayoutParams(rlp);

            TextView label = new TextView(this);
            label.setText(q.label);
            label.setTextColor(0xFFF2F6FF);
            label.setTextSize(13f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView size = new TextView(this);
            size.setTextColor(0xFF8A93A6);
            size.setTextSize(11f);
            size.setText("Loading…");

            row.addView(label);
            row.addView(size);
            row.setOnClickListener(view -> onQualityChosen(url, q));
            list.addView(row);
            rows[i] = row;
            sizeViews[i] = size;
        }

        v.findViewById(R.id.qClose).setOnClickListener(view -> removeQuality());
        attachDrag(v.findViewById(R.id.qDrag), v, lp, null);

        try {
            wm.addView(v, lp);
            qualityView = v;
        } catch (Exception e) {
            Toast.makeText(this, "Overlay error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // read the video in the background: title, thumbnail, per-quality sizes
        new Thread(() -> {
            DownloadEngine.Probe probe = null;
            try {
                probe = DownloadEngine.get(this).probe(url, qReferer);
            } catch (Throwable ignored) {
            }
            final DownloadEngine.Probe pr = probe;
            lastProbe = pr;
            lastProbeUrl = url;
            main.post(() -> {
                if (qualityView != v) return;
                if (pr == null) {
                    for (TextView sv : sizeViews) if (sv != null) sv.setText("—");
                    return;
                }
                if (pr.title != null && !pr.title.isEmpty()) title.setText(pr.title);
                for (int i = 0; i < qs.length; i++) {
                    boolean available = qs[i].audio || pr.maxHeight <= 0 || qs[i].height <= pr.maxHeight;
                    TextView label = (TextView) ((LinearLayout) rows[i]).getChildAt(0);
                    if (!available) {
                        // video not offered in this quality -> not highlighted / not tappable
                        rows[i].setEnabled(false);
                        rows[i].setClickable(false);
                        rows[i].setOnClickListener(null);
                        rows[i].setAlpha(0.30f);
                        label.setTextColor(0xFF8A93A6);
                        sizeViews[i].setTextColor(0xFF8A93A6);
                        sizeViews[i].setText("N/A");
                    } else {
                        long sz = pr.sizes[i];
                        sizeViews[i].setTextColor(0xFF6FF6FF);
                        sizeViews[i].setText(sz > 0 ? "~" + human(sz) : "—");
                    }
                }
                if (pr.thumbnail != null && !pr.thumbnail.isEmpty()) loadThumb(pr.thumbnail, thumb);
            });
        }).start();
    }

    private void loadThumb(final String url, final ImageView iv) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setInstanceFollowRedirects(true);
                java.io.InputStream in = c.getInputStream();
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();
                if (bmp != null) main.post(() -> iv.setImageBitmap(bmp));
            } catch (Throwable ignored) {
            }
        }).start();
    }

    private void onQualityChosen(String url, DownloadEngine.Quality q) {
        removeQuality();
        DownloadItem item = new DownloadItem(genId(), url);
        item.qualityLabel = q.label;
        item.audioOnly = q.audio;
        item.formatSelector = q.selector();
        item.referer = qReferer;
        item.state = DownloadItem.RUNNING;

        // Reuse what the popup already probed so the worker can skip a second
        // network metadata fetch and start downloading immediately.
        try {
            if (lastProbe != null && url.equals(lastProbeUrl)) {
                if (lastProbe.title != null && !lastProbe.title.isEmpty()) item.title = lastProbe.title;
                DownloadEngine.Quality[] qs = DownloadEngine.QUALITIES;
                for (int i = 0; i < qs.length; i++) {
                    if (qs[i] == q && lastProbe.sizes[i] > 0) {
                        item.totalBytes = lastProbe.sizes[i];
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        addBubble(item);
        DownloadEngine.get(this).start(item);
    }

    private void removeQuality() {
        if (qualityView != null) {
            try {
                wm.removeViewImmediate(qualityView);
            } catch (Exception ignored) {
            }
            qualityView = null;
        }
    }

    // -------------------------------------------------------------- bubbles

    @SuppressLint("ClickableViewAccessibility")
    private void addBubble(final DownloadItem item) {
        if (!canOverlay()) return;
        if (bubbles.containsKey(item.id)) return;

        final View v = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null);
        final WindowManager.LayoutParams lp = params(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        lp.x = dp(10);
        lp.y = dp(120) + bubbles.size() * dp(92);

        final Bubble bubble = new Bubble();
        bubble.item = item;
        bubble.view = v;
        bubble.params = lp;
        bubble.circle = v.findViewById(R.id.bubbleCircle);
        bubble.size = v.findViewById(R.id.bubbleSize);
        bubble.controls = v.findViewById(R.id.bubbleControls);
        bubble.pauseResume = v.findViewById(R.id.btnPauseResume);
        bubble.openApp = v.findViewById(R.id.btnOpenApp);
        bubble.cancel = v.findViewById(R.id.btnCancel);
        bubble.circle.setIndeterminate(true);

        // tap the circle -> toggle the pause/open controls; drag -> move
        attachDrag(bubble.circle, v, lp, () -> toggleControls(bubble));

        bubble.openApp.setOnClickListener(view -> openApp());
        bubble.pauseResume.setOnClickListener(view -> {
            if (bubble.item.state == DownloadItem.RUNNING) {
                DownloadEngine.get(this).pause(bubble.item);
            } else {
                DownloadEngine.get(this).resume(bubble.item);
            }
        });
        bubble.cancel.setOnClickListener(view -> {
            DownloadEngine.get(this).cancel(bubble.item);
            removeBubble(bubble.item.id);
        });

        DownloadEngine.get(this).registerUi(item.id, new DownloadEngine.ProgressUi() {
            @Override
            public void onProgress(DownloadItem it) {
                bubble.item = it;
                bubble.circle.setProgress(it.percent);
                bubble.size.setText(sizeLabel(it));
                updateNotif(it);
            }

            @Override
            public void onState(DownloadItem it) {
                bubble.item = it;
                refreshBubbleState(bubble);
                updateNotif(it);
            }
        });

        try {
            wm.addView(v, lp);
            bubbles.put(item.id, bubble);
        } catch (Exception e) {
            Toast.makeText(this, "Overlay error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleControls(Bubble b) {
        boolean show = b.controls.getVisibility() != View.VISIBLE;
        b.controls.setVisibility(show ? View.VISIBLE : View.GONE);
        refreshBubbleState(b);
    }

    private void refreshBubbleState(Bubble b) {
        DownloadItem it = b.item;
        if (it.state == DownloadItem.PAUSED) {
            b.pauseResume.setImageResource(R.drawable.ic_play);
            b.circle.setCenterText(Math.round(it.percent) + "%");
        } else if (it.state == DownloadItem.RUNNING) {
            b.pauseResume.setImageResource(R.drawable.ic_pause);
        } else if (it.state == DownloadItem.ERROR) {
            b.pauseResume.setImageResource(R.drawable.ic_retry);
            b.circle.setCenterText("!");
            String msg = it.error != null && !it.error.isEmpty() ? it.error : "Download failed";
            if (msg.length() > 120) msg = msg.substring(0, 120) + "…";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else if (it.state == DownloadItem.DONE) {
            b.controls.setVisibility(View.GONE);
            b.size.setVisibility(View.GONE);
            final CircleProgressView c = b.circle;
            // quick flip -> green tick -> vanish
            c.animate().rotationY(90f).setDuration(150).withEndAction(() -> {
                c.showDone();
                c.setRotationY(-90f);
                c.animate().rotationY(0f).setDuration(150).start();
            }).start();
            main.postDelayed(() -> removeBubble(it.id), 1400L);
        }
    }

    private void removeBubble(String id) {
        Bubble b = bubbles.remove(id);
        DownloadEngine.get(this).unregisterUi(id);
        if (b != null && b.view != null) {
            try {
                wm.removeViewImmediate(b.view);
            } catch (Exception ignored) {
            }
        }
        if (bubbles.isEmpty() && qualityView == null) {
            // nothing on screen -> let the service idle (keep foreground for engine)
        }
    }

    // ----------------------------------------------------------- utilities

    private String sizeLabel(DownloadItem it) {
        if (it.totalBytes > 0) {
            return human(it.downloadedBytes) + "/" + human(it.totalBytes);
        }
        if (it.downloadedBytes > 0) return human(it.downloadedBytes);
        return "";
    }

    static String human(long bytes) {
        if (bytes <= 0) return "0B";
        String[] u = {"B", "KB", "MB", "GB"};
        int i = 0;
        double b = bytes;
        while (b >= 1024 && i < u.length - 1) {
            b /= 1024;
            i++;
        }
        return (b >= 100 ? Math.round(b) : Math.round(b * 10) / 10.0) + u[i];
    }

    private String genId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1296), 36);
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    /** Drag handling that also fires a tap callback when the touch did not move. */
    @SuppressLint("ClickableViewAccessibility")
    private void attachDrag(View handle, final View moveTarget,
                            final WindowManager.LayoutParams lp, @Nullable final Runnable onTap) {
        final int slop = dp(8);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) dragging = true;
                        if (dragging) {
                            // gravity is END, so a rightward finger move (dx>0) shrinks x
                            lp.x = (int) Math.max(0, startX - dx);
                            lp.y = (int) Math.max(0, startY + dy);
                            try {
                                wm.updateViewLayout(moveTarget, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging && onTap != null) onTap.run();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    static final class Bubble {
        DownloadItem item;
        View view;
        WindowManager.LayoutParams params;
        CircleProgressView circle;
        TextView size;
        LinearLayout controls;
        ImageView pauseResume;
        ImageView openApp;
        ImageView cancel;
    }

    @Override
    public void onDestroy() {
        removeQuality();
        for (String id : new java.util.ArrayList<>(bubbles.keySet())) removeBubble(id);
        super.onDestroy();
    }
}
