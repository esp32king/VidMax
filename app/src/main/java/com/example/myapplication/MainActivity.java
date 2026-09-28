package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.dl.DownloadEngine;
import com.example.myapplication.dl.DownloadItem;
import com.example.myapplication.dl.DownloadStore;
import com.example.myapplication.ui.NeonTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
        implements DownloadStore.Listener, DownloadAdapter.RowCallback {

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+");

    private DownloadStore store;
    private DownloadAdapter adapter;
    private View overlayBanner, emptyView;
    private TextView tabActive, tabDone;
    private EditText linkInput;
    private boolean showActive = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = DownloadStore.get(this);

        NeonTextView title = findViewById(R.id.homeTitle);
        title.setNeonColors(ContextCompat.getColor(this, R.color.neon_cyan),
                ContextCompat.getColor(this, R.color.neon_magenta));
        title.setShimmer(true);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadAdapter(this);
        list.setAdapter(adapter);

        overlayBanner = findViewById(R.id.overlayBanner);
        emptyView = findViewById(R.id.emptyView);
        tabActive = findViewById(R.id.tabActive);
        tabDone = findViewById(R.id.tabDone);
        linkInput = findViewById(R.id.linkInput);

        tabActive.setOnClickListener(v -> setTab(true));
        tabDone.setOnClickListener(v -> setTab(false));
        findViewById(R.id.btnFolder).setOnClickListener(v -> setTab(false));

        View.OnClickListener openAbout = v -> startActivity(new Intent(this, AboutActivity.class));
        findViewById(R.id.btnAbout).setOnClickListener(openAbout);
        TextView footer = findViewById(R.id.footerCredit);
        footer.setText(com.example.myapplication.ui.BrandText.redX(
                getString(R.string.footer_credit), ContextCompat.getColor(this, R.color.blood)));
        footer.setOnClickListener(openAbout);

        findViewById(R.id.btnGet).setOnClickListener(v -> onGet());
        linkInput.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onGet();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnEnableOverlay).setOnClickListener(v -> requestOverlay());

        requestStartupPerms();

        // Warm the download engine (init + yt-dlp update) so downloads start fast.
        DownloadEngine.get(this).warmUp();
    }

    private void requestStartupPerms() {
        java.util.List<String> ask = new java.util.ArrayList<>();
        for (String s : Perms.storage()) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) ask.add(s);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!ask.isEmpty()) {
            try {
                requestPermissions(ask.toArray(new String[0]), 11);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.addListener(this);
        refresh();
        overlayBanner.setVisibility(canOverlay() ? View.GONE : View.VISIBLE);

        // a link shared while the permission was missing
        String pending = getSharedPreferences("neon_prefs", MODE_PRIVATE).getString("pending_url", "");
        if (!TextUtils.isEmpty(pending) && canOverlay()) {
            getSharedPreferences("neon_prefs", MODE_PRIVATE).edit().remove("pending_url").apply();
            startQuality(pending);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        store.removeListener(this);
    }

    // ------------------------------------------------------------- tabs/list

    private void setTab(boolean active) {
        showActive = active;
        tabActive.setTextColor(ContextCompat.getColor(this,
                active ? R.color.neon_cyan : R.color.text_muted));
        tabDone.setTextColor(ContextCompat.getColor(this,
                active ? R.color.text_muted : R.color.neon_cyan));
        refresh();
    }

    @Override
    public void onDownloadsChanged() {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        List<DownloadItem> filtered = new ArrayList<>();
        for (DownloadItem d : store.all()) {
            if (showActive ? d.isActive() : d.state == DownloadItem.DONE) filtered.add(d);
        }
        adapter.submit(filtered);
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------- actions

    private void onGet() {
        String raw = linkInput.getText() == null ? "" : linkInput.getText().toString();
        String url = firstUrl(raw);
        if (url == null) {
            Toast.makeText(this, "Paste a valid link", Toast.LENGTH_SHORT).show();
            return;
        }
        linkInput.setText("");
        if (!canOverlay()) {
            Toast.makeText(this, R.string.grant_overlay, Toast.LENGTH_LONG).show();
            requestOverlay();
            getSharedPreferences("neon_prefs", MODE_PRIVATE).edit().putString("pending_url", url).apply();
            return;
        }
        startQuality(url);
    }

    private void startQuality(String url) {
        Intent svc = new Intent(this, FloatingService.class);
        svc.setAction(FloatingService.ACTION_QUALITY);
        svc.putExtra(FloatingService.EXTRA_URL, url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }

    @Override
    public void onAction(DownloadItem item) {
        DownloadEngine engine = DownloadEngine.get(this);
        if (item.state == DownloadItem.RUNNING) engine.pause(item);
        else engine.resume(item); // paused or error -> (re)start
    }

    @Override
    public void onDelete(DownloadItem item) {
        DownloadEngine.get(this).cancel(item);
        if (!TextUtils.isEmpty(item.filePath)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                new File(item.filePath).delete();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onOpen(DownloadItem item) {
        if (TextUtils.isEmpty(item.filePath)) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(item.filePath);
        if (!f.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, item.audioOnly ? "audio/*" : "video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------------------------------------- permissions

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            } catch (Exception ignored) {
            }
        }
    }

    private String firstUrl(String s) {
        if (TextUtils.isEmpty(s)) return null;
        Matcher m = URL.matcher(s.trim());
        if (m.find()) return m.group();
        if (s.trim().contains(".") && !s.trim().contains(" ")) return "https://" + s.trim();
        return null;
    }
}
