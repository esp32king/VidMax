package com.example.myapplication.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A glassy neon progress ring with the live percentage in the middle — the
 * shrunken download bubble. Shows an indeterminate sweep until progress arrives.
 */
public class CircleProgressView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private static final int DONE_GREEN = 0xFF00E676;
    private float progress = 0f;      // 0..100
    private boolean indeterminate = true;
    private boolean done = false;
    private float spin = 0f;
    private int colorA = 0xFF00E676;   // neon green
    private int colorB = 0xFF76FFB0;   // mint green
    private String centerText = "0%";

    public CircleProgressView(Context c) {
        super(c);
        init();
    }

    public CircleProgressView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        float w = dp(6f);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x33FFFFFF);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);

        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(w);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setMaskFilter(new BlurMaskFilter(dp(8f), BlurMaskFilter.Blur.NORMAL));

        fill.setStyle(Paint.Style.FILL);

        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);

        check.setStyle(Paint.Style.STROKE);
        check.setStrokeCap(Paint.Cap.ROUND);
        check.setStrokeJoin(Paint.Join.ROUND);
        check.setColor(0xFFFFFFFF);
        check.setStrokeWidth(dp(7f));
    }

    /** Flip the bubble to a solid green tick to celebrate completion. */
    public void showDone() {
        done = true;
        indeterminate = false;
        progress = 100f;
        invalidate();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setProgress(float p) {
        indeterminate = false;
        progress = Math.max(0f, Math.min(100f, p));
        centerText = Math.round(progress) + "%";
        invalidate();
    }

    public void setIndeterminate(boolean ind) {
        indeterminate = ind;
        invalidate();
    }

    public void setCenterText(String t) {
        centerText = t;
        indeterminate = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        Shader s = new LinearGradient(0, 0, w, h, colorA, colorB, Shader.TileMode.CLAMP);
        arc.setShader(s);
        glow.setShader(s);
        text.setTextSize(h * 0.26f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float pad = dp(9f);
        rect.set(pad, pad, w - pad, h - pad);

        if (done) {
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - pad;
            // green glow halo
            glow.setShader(null);
            glow.setColor(DONE_GREEN);
            glow.setAlpha(150);
            canvas.drawCircle(cx, cy, r, glow);
            // solid green disc
            fill.setColor(DONE_GREEN);
            canvas.drawCircle(cx, cy, r, fill);
            // white check mark
            float s = r;
            canvas.drawLine(cx - s * 0.42f, cy + s * 0.02f,
                    cx - s * 0.08f, cy + s * 0.34f, check);
            canvas.drawLine(cx - s * 0.08f, cy + s * 0.34f,
                    cx + s * 0.46f, cy - s * 0.32f, check);
            return;
        }

        // glassy inner disc
        fill.setColor(0xE60C1428);
        canvas.drawCircle(w / 2f, h / 2f, (Math.min(w, h) / 2f) - pad + dp(2f), fill);

        canvas.drawArc(rect, 0, 360, false, track);

        float sweep;
        float startAngle;
        if (indeterminate) {
            spin += 6f;
            if (spin >= 360f) spin -= 360f;
            startAngle = spin;
            sweep = 90f;
        } else {
            startAngle = -90f;
            sweep = 360f * (progress / 100f);
        }
        glow.setAlpha(150);
        canvas.drawArc(rect, startAngle, sweep, false, glow);
        canvas.drawArc(rect, startAngle, sweep, false, arc);

        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(centerText, w / 2f, ty, text);

        if (indeterminate) postInvalidateOnAnimation();
    }
}
