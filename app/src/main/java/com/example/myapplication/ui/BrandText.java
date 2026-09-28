package com.example.myapplication.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

/** Brand text helpers. */
public final class BrandText {
    private BrandText() {}

    /** Colours the "X" inside GODXSHADOW (case-insensitive) with {@code color}. */
    public static CharSequence redX(CharSequence text, int color) {
        if (text == null) return "";
        String s = text.toString();
        SpannableString sp = new SpannableString(s);
        int w = s.toUpperCase().indexOf("GODXSHADOW");
        if (w >= 0) {
            int x = w + 3; // G O D [X]
            sp.setSpan(new ForegroundColorSpan(color), x, x + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), x, x + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }
}
