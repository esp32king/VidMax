package com.example.myapplication.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

/**
 * The call-screen identity card: a glass panel with a neon gradient border and a
 * slow breathing outer glow (cyan -> violet -> magenta).
 */
public class NeonGlowCard extends FrameLayout {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private int colorA;
    private int colorB;
    private float radius;
    private float borderWidth;
    private float glowSpread;
    private boolean pulse = true;

    private float phase = 0f;
    private int lastW = -1;
    private int lastH = -1;
    private Shader bodyShader;
    private float bodyH = -1f;

    private final Runnable pulseTick = new Runnable() {
        @Override
        public void run() {
            if (!pulse || !isAttachedToWindow()) return;
            phase += 0.04f;
            if (phase > 1f) phase -= 1f;
            invalidate();
            postDelayed(this, 80L);
        }
    };

    public NeonGlowCard(Context context) {
        super(context);
        init(context, null);
    }

    public NeonGlowCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NeonGlowCard(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        colorA = ContextCompat.getColor(context, R.color.neon_cyan);
        colorB = ContextCompat.getColor(context, R.color.neon_magenta);
        radius = dp(26f);
        borderWidth = dp(2.2f);
        glowSpread = dp(13f);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NeonGlowCard);
            colorA = a.getColor(R.styleable.NeonGlowCard_neonGlowColorA, colorA);
            colorB = a.getColor(R.styleable.NeonGlowCard_neonGlowColorB, colorB);
            radius = a.getDimension(R.styleable.NeonGlowCard_neonGlowCorner, radius);
            borderWidth = a.getDimension(R.styleable.NeonGlowCard_neonGlowStroke, borderWidth);
            glowSpread = a.getDimension(R.styleable.NeonGlowCard_neonGlowSpread, glowSpread);
            pulse = a.getBoolean(R.styleable.NeonGlowCard_neonGlowPulse, pulse);
            a.recycle();
        }

        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(7f));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        innerPaint.setStyle(Paint.Style.FILL);
    }

    public void setNeonColors(int a, int b) {
        colorA = a;
        colorB = b;
        lastW = -1;
        invalidate();
    }

    public void setPulse(boolean enabled) {
        pulse = enabled;
        if (enabled) startPulse();
        else removeCallbacks(pulseTick);
        invalidate();
    }

    private void startPulse() {
        removeCallbacks(pulseTick);
        postDelayed(pulseTick, 80L);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pulse) startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(pulseTick);
        super.onDetachedFromWindow();
    }

    private void buildShaders(int w, int h) {
        Shader border = new LinearGradient(0f, 0f, w, h,
                new int[]{colorA, 0xFF7A5CFF, colorB, colorA},
                new float[]{0f, 0.45f, 0.8f, 1f},
                Shader.TileMode.MIRROR);
        borderPaint.setShader(border);
        Shader glow = new LinearGradient(0f, 0f, w, h,
                new int[]{colorA, 0xFF7A5CFF, colorB},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.MIRROR);
        glowPaint.setShader(glow);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        lastW = w;
        lastH = h;
        buildShaders(w, h);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (lastW != w || lastH != h) {
            lastW = w;
            lastH = h;
            buildShaders(w, h);
        }

        float t = pulse
                ? (float) (0.5f + 0.5f * Math.sin(phase * Math.PI * 2f))
                : 0.75f;
        float pad = glowSpread * (0.55f + 0.45f * t);
        float half = borderWidth / 2f + pad;

        rect.set(half, half, w - half, h - half);
        float r = radius;

        // breathing outer glow
        float blur = Math.max(2f, glowSpread * (0.7f + 0.6f * t));
        glowPaint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        glowPaint.setAlpha((int) (120 + 95 * t));
        canvas.drawRoundRect(rect, r, r, glowPaint);

        // panel body
        if (bodyShader == null || bodyH != h) {
            bodyH = h;
            bodyShader = new LinearGradient(0f, 0f, 0f, h,
                    new int[]{0xF7081020, 0xF70A1122, 0xF7120A26},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        innerPaint.setShader(bodyShader);
        canvas.drawRoundRect(rect, r, r, innerPaint);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);

        // crisp neon hairline on top
        canvas.drawRoundRect(rect, r, r, borderPaint);
    }
}
