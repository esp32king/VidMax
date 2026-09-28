package com.example.myapplication;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.example.myapplication.ui.NeonTextView;

/** The devilish "About" page. */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_about);

        try {
            NeonTextView name = findViewById(R.id.aboutName);
            name.setNeonColors(ContextCompat.getColor(this, R.color.blood),
                    ContextCompat.getColor(this, R.color.blood_soft));
            name.setGlowRadius(30f);
            name.setShimmerDuration(2800L); // slower, softer shine
            name.setShimmer(true);
        } catch (Throwable ignored) {
        }

        // Colour the menacing word "darkness" blood red.
        try {
            TextView tag = findViewById(R.id.aboutTag);
            String raw = getString(R.string.about_tag_full);
            SpannableString sp = new SpannableString(raw);
            String key = "darkness";
            int i = raw.toLowerCase().indexOf(key);
            if (i >= 0) {
                sp.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.blood)),
                        i, i + key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tag.setText(sp);
        } catch (Throwable ignored) {
        }

        // Bottom footer, same as home, with red "X" in GODXSHADOW.
        try {
            TextView footer = findViewById(R.id.aboutFooter);
            footer.setText(com.example.myapplication.ui.BrandText.redX(
                    getString(R.string.footer_credit), ContextCompat.getColor(this, R.color.blood)));
        } catch (Throwable ignored) {
        }

        findViewById(R.id.aboutBack).setOnClickListener(v -> finish());
    }
}
