package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.example.myapplication.ui.NeonTextView;

/** Splash — same look as before; the "GodxShadow" wordmark now shines. */
public class SplashActivity extends AppCompatActivity {

    private boolean launched = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);

        try {
            NeonTextView logo = findViewById(R.id.splashLogo);
            if (logo != null) {
                logo.setNeonColors(ContextCompat.getColor(this, R.color.neon_cyan),
                        ContextCompat.getColor(this, R.color.neon_magenta));
                logo.setGlowRadius(24f);
                logo.setShimmer(false);   // no shine — static neon only
            }
            NeonTextView creator = findViewById(R.id.splashCreator);
            if (creator != null) {
                creator.setNeonColors(ContextCompat.getColor(this, R.color.neon_cyan),
                        ContextCompat.getColor(this, R.color.neon_magenta));
                creator.setGlowRadius(22f);
                creator.setShimmer(false); // no shine — static neon only
            }
        } catch (Throwable ignored) {
        }

        // Colour the "v/s" in the tagline blood red (Scenic v/s Sinner).
        try {
            android.widget.TextView tag = findViewById(R.id.splashTagline);
            if (tag != null) {
                String raw = getString(R.string.splash_tagline);
                android.text.SpannableString sp = new android.text.SpannableString(raw);
                int i = raw.toLowerCase().indexOf("v/s");
                if (i >= 0) {
                    sp.setSpan(new android.text.style.ForegroundColorSpan(
                                    ContextCompat.getColor(this, R.color.blood)),
                            i, i + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                tag.setText(sp);
            }
        } catch (Throwable ignored) {
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::goHome, 2200L);
    }

    private void goHome() {
        if (launched || isFinishing()) return;
        launched = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
