package com.biopay.agent.reports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;

/** Compact, accessible paid/pending/failed distribution for a phone report. */
public class PaymentStatusBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int paid;
    private int pending;
    private int failed;

    public PaymentStatusBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setValues(int paid, int pending, int failed) {
        this.paid = Math.max(0, paid);
        this.pending = Math.max(0, pending);
        this.failed = Math.max(0, failed);
        setContentDescription(getResources().getString(
                R.string.reports_payment_bar_accessibility, this.paid, this.pending, this.failed));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom();
        RectF bounds = new RectF(left, top, right, bottom);
        float radius = dp(6);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.bp_surface_variant));
        canvas.drawRoundRect(bounds, radius, radius, paint);

        int total = paid + pending + failed;
        if (total == 0 || bounds.width() <= 0) return;
        Path clip = new Path();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float x = left;
        x = drawSegment(canvas, x, paid, total, bounds, R.color.bp_success);
        x = drawSegment(canvas, x, pending, total, bounds, R.color.bp_warning);
        drawSegment(canvas, x, failed, total, bounds, R.color.bp_error);
        canvas.restore();
    }

    private float drawSegment(Canvas canvas, float x, int value, int total, RectF bounds, int color) {
        if (value <= 0) return x;
        float width = bounds.width() * value / total;
        paint.setColor(ContextCompat.getColor(getContext(), color));
        canvas.drawRect(x, bounds.top, x + width, bounds.bottom, paint);
        return x + width;
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
