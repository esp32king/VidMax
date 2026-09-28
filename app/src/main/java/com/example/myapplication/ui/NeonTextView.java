package com.example.myapplication.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import androidx.appcompat.widget.AppCompatTextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

/**
 * Text with a neon gradient fill plus a soft outer glow.
 * Optionally runs a slow "shimmer" sweep used for loading states.
 */
public class NeonTextView extends AppCompatTextView {

    private int colorA;
    private int colorB;
    private float glowRadius = 14f;
    private boolean shimmer = false;
    private float shimmerPos = -1f;
    private long shimmerDuration = 1500L;
    private Shader baseShader;
    private Shader shimmerShader;
    private int lastW = 0;
    private ValueAnimator animator;

    public NeonTextView(Context context) {
        super(context);
        init(context, null);
    }

    public NeonTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NeonTextView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        colorA = ContextCompat.getColor(context, R.color.neon_cyan);
        colorB = ContextCompat.getColor(context, R.color.neon_magenta);
        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NeonTextView);
            colorA = a.getColor(R.styleable.NeonTextView_neonStartColor, colorA);
            colorB = a.getColor(R.styleable.NeonTextView_neonEndColor, colorB);
            glowRadius = a.getDimension(R.styleable.NeonTextView_neonTextGlowRadius, glowRadius);
            shimmer = a.getBoolean(R.styleable.NeonTextView_neonShimmer, false);
            a.recycle();
        }
        Paint p = getPaint();
        p.setAntiAlias(true);
        // A software layer makes setShadowLayer() glow reliably. It is applied in
        // onAttachedToWindow() so we can skip it when an ancestor (NeonGlowCard)
        // already renders in software - nesting software layers is slow.
        p.setShadowLayer(glowRadius, 0f, 0f, colorA);
        applyShaders(getWidth());
        if (shimmer) startShimmer();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        boolean ancestorSoftware = false;
        android.view.ViewParent parent = getParent();
        while (parent instanceof android.view.View) {
            android.view.View v = (android.view.View) parent;
            if (v.getLayerType() == LAYER_TYPE_SOFTWARE) {
                ancestorSoftware = true;
                break;
            }
            parent = v.getParent();
        }
        if (!ancestorSoftware && getLayerType() != LAYER_TYPE_SOFTWARE) {
            setLayerType(LAYER_TYPE_SOFTWARE, getPaint());
        }
    }

    public void setNeonColors(int start, int end) {
        colorA = start;
        colorB = end;
        getPaint().setShadowLayer(glowRadius, 0f, 0f, colorA);
        lastW = 0;
        invalidate();
    }

    public void setGlowRadius(float radius) {
        glowRadius = radius;
        getPaint().setShadowLayer(radius, 0f, 0f, colorA);
        invalidate();
    }

    public void setShimmer(boolean enabled) {
        if (shimmer == enabled) return;
        shimmer = enabled;
        if (enabled) {
            startShimmer();
        } else {
            stopShimmer();
            shimmerPos = -1f;
            getPaint().setShader(baseShader);
            invalidate();
        }
    }

    /** Higher = slower/softer sweep. */
    public void setShimmerDuration(long ms) {
        shimmerDuration = ms;
        if (shimmer) startShimmer();
    }

    private void startShimmer() {
        stopShimmer();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(shimmerDuration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            shimmerPos = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopShimmer() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void applyShaders(int width) {
        int w = Math.max(width, 1);
        float h = Math.max(getTextSize() * 1.3f, 1f);
        baseShader = new LinearGradient(0f, 0f, w, h,
                new int[]{colorA, colorB, colorA},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        shimmerShader = new LinearGradient(-w * 0.6f, 0f, w * 0.6f, 0f,
                new int[]{colorA, 0xFFFFFFFF, colorB, colorA},
                new float[]{0f, 0.45f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyShaders(w);
        lastW = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (lastW != getWidth()) {
            applyShaders(getWidth());
            lastW = getWidth();
        }
        Paint p = getPaint();
        if (shimmer && shimmerPos >= 0f && shimmerShader != null) {
            android.graphics.Matrix m = new android.graphics.Matrix();
            float span = Math.max(getWidth() * 1.6f, 1f);
            m.setTranslate(-span * 0.4f + span * shimmerPos, 0f);
            shimmerShader.setLocalMatrix(m);
            p.setShader(shimmerShader);
        } else {
            p.setShader(baseShader);
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(android.view.View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            if (shimmer && animator == null) startShimmer();
        } else {
            if (animator != null) animator.cancel();
        }
    }
}
