package com.example.myapplication.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

/**
 * Frosted dark card with a thin neon gradient hairline border.
 */
public class NeonCard extends FrameLayout {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private float radius;
    private float borderWidth;
    private int colorA;
    private int colorB;
    private int fillColor;
    private Shader borderShader;
    private Shader sheenShader;
    private int lastW = -1;
    private int lastH = -1;

    public NeonCard(Context context) {
        super(context);
        init(context, null);
    }

    public NeonCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NeonCard(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        radius = dp(24f);
        borderWidth = dp(1.3f);
        colorA = ContextCompat.getColor(context, R.color.neon_cyan);
        colorB = ContextCompat.getColor(context, R.color.neon_magenta);
        fillColor = 0xF50A1122;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NeonCard);
            radius = a.getDimension(R.styleable.NeonCard_neonCardRadius, radius);
            borderWidth = a.getDimension(R.styleable.NeonCard_neonCardBorderWidth, borderWidth);
            colorA = a.getColor(R.styleable.NeonCard_neonCardStartColor, colorA);
            colorB = a.getColor(R.styleable.NeonCard_neonCardEndColor, colorB);
            fillColor = a.getColor(R.styleable.NeonCard_neonCardFill, fillColor);
            a.recycle();
        }

        setWillNotDraw(false);
        setBackground(null);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        sheenPaint.setStyle(Paint.Style.FILL);
    }

    public void setNeonColors(int a, int b) {
        colorA = a;
        colorB = b;
        lastW = -1;
        invalidate();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildShaders(int w, int h) {
        borderShader = new LinearGradient(0f, 0f, w, h,
                new int[]{withAlpha(colorA, 200), withAlpha(colorB, 40), withAlpha(colorA, 30), withAlpha(colorB, 210)},
                new float[]{0f, 0.35f, 0.65f, 1f},
                Shader.TileMode.CLAMP);
        borderPaint.setShader(borderShader);
        sheenShader = new LinearGradient(0f, 0f, w * 0.9f, h * 0.5f,
                new int[]{0x1AFFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP);
        sheenPaint.setShader(sheenShader);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
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
        if (lastW != getWidth() || lastH != getHeight()) {
            lastW = getWidth();
            lastH = getHeight();
            buildShaders(lastW, lastH);
        }
        float half = borderWidth / 2f;
        rect.set(half, half, getWidth() - half, getHeight() - half);
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.drawRect(rect, sheenPaint);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        canvas.drawRoundRect(rect, radius, radius, borderPaint);
    }
}
