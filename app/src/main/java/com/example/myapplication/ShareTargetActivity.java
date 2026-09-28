package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Invisible catcher. Grabs the shared URL, asks for storage first, then fires the
 * floating quality popup — the app never opens full screen over the video.
 */
public class ShareTargetActivity extends AppCompatActivity {

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+");
    private static final int REQ_STORAGE = 21;

    private String pendingUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingUrl = extractUrl(getIntent());

        if (TextUtils.isEmpty(pendingUrl)) {
            Toast.makeText(this, "No video link found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 1) storage permission first
        String[] need = Perms.storage();
        if (need.length > 0 && !Perms.has(this, need)) {
            requestPermissions(need, REQ_STORAGE);
            return;
        }
        proceed();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        proceed();  // continue regardless — downloads still work in app storage
    }

    private void proceed() {
        // 2) overlay permission for the floating popup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' for GodxShadow", Toast.LENGTH_LONG).show();
            getSharedPreferences("neon_prefs", MODE_PRIVATE).edit().putString("pending_url", pendingUrl).apply();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            finish();
            return;
        }

        // 3) fire the popup
        Intent svc = new Intent(this, FloatingService.class);
        svc.setAction(FloatingService.ACTION_QUALITY);
        svc.putExtra(FloatingService.EXTRA_URL, pendingUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        finish();
    }

    private String extractUrl(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String found = firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
            if (found != null) return found;
            return firstUrl(intent.getStringExtra(Intent.EXTRA_SUBJECT));
        }
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String d = intent.getDataString();
            String found = firstUrl(d);
            return found != null ? found : d;
        }
        return null;
    }

    private String firstUrl(String s) {
        if (TextUtils.isEmpty(s)) return null;
        Matcher m = URL.matcher(s);
        return m.find() ? m.group() : null;
    }
}
