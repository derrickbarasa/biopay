package com.biopay.agent.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Canvas donut chart -- no charting library dependency for what's a couple of status
 * slices. Draws each slice as a ring segment with the total centered in the hole.
 */
public class SimpleDonutChartView extends View {

    public static class Slice {
        public final String label;
        public final float value;
        public final int color;

        public Slice(String label, float value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();
    private String centerLabel = "";
    /** Compact mode: for small placements (e.g. the home hero) -- scales the total text to the
     *  view's own size instead of a fixed sp, and never draws the caption line beneath it. */
    private boolean compact = false;

    public SimpleDonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        ringPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bp_outline_variant));
        centerValuePaint.setTextAlign(Paint.Align.CENTER);
        centerValuePaint.setColor(ContextCompat.getColor(context, R.color.bp_text_primary));
        centerValuePaint.setFakeBoldText(true);
        centerValuePaint.setTextSize(22f * scaledDensity);
        centerLabelPaint.setTextAlign(Paint.Align.CENTER);
        centerLabelPaint.setColor(ContextCompat.getColor(context, R.color.bp_text_secondary));
        centerLabelPaint.setTextSize(12f * scaledDensity);
    }

    /** @param centerLabel small caption shown under the total in the donut hole, e.g. "records" */
    public void setSlices(List<Slice> newSlices, String centerLabel) {
        slices.clear();
        slices.addAll(newSlices);
        this.centerLabel = centerLabel;
        invalidate();
    }

    /** Enables compact styling for small placements -- see {@link #compact}. */
    public void setCompact(boolean compact) {
        this.compact = compact;
        invalidate();
    }

    /** Overrides the empty-track ring color, e.g. a translucent white when drawn on a solid
     *  brand-color background where the default outline color would be invisible. */
    public void setTrackColor(int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    /** Overrides the centered total's text color, e.g. white when drawn on a dark background. */
    public void setCenterValueColor(int color) {
        centerValuePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        float strokeWidth = size * (compact ? 0.22f : 0.16f);
        ringPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeWidth(strokeWidth);
        if (compact) {
            centerValuePaint.setTextSize(size * 0.34f);
        }
        float inset = strokeWidth / 2f + 4f;
        float left = (getWidth() - size) / 2f + inset;
        float top = (getHeight() - size) / 2f + inset;
        ringBounds.set(left, top, left + size - inset * 2, top + size - inset * 2);

        canvas.drawArc(ringBounds, 0, 360, false, trackPaint);

        float total = 0f;
        for (Slice s : slices) total += Math.max(0f, s.value);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        if (total > 0f) {
            float startAngle = -90f;
            for (Slice s : slices) {
                float sweep = (Math.max(0f, s.value) / total) * 360f;
                if (sweep <= 0f) continue;
                ringPaint.setColor(s.color);
                canvas.drawArc(ringBounds, startAngle, sweep, false, ringPaint);
                startAngle += sweep;
            }
            float valueY = compact ? centerY + centerValuePaint.getTextSize() * 0.35f : centerY;
            canvas.drawText(String.valueOf((int) total), centerX, valueY, centerValuePaint);
            if (!compact && centerLabel != null && !centerLabel.isEmpty()) {
                canvas.drawText(centerLabel, centerX, centerY + centerLabelPaint.getTextSize() + 4f, centerLabelPaint);
            }
        } else {
            float zeroY = compact ? centerY + centerValuePaint.getTextSize() * 0.35f : centerY;
            canvas.drawText("0", centerX, zeroY, centerValuePaint);
        }
    }
}
