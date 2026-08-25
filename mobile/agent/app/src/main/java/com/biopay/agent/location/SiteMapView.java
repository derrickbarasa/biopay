package com.biopay.agent.location;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;

/**
 * A self-contained "sitemap" -- not a real tiled map (no network/API key, matching this app's
 * offline-first design), just a graticule backdrop with a pin centered on the last known fix and
 * its GPS accuracy drawn as a radius ring, so a field officer can see at a glance roughly how
 * precise the fix is without needing connectivity to render actual map tiles.
 */
public class SiteMapView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accuracyFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accuracyStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean hasFix;
    private float accuracyMeters;

    public SiteMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.bp_outline_variant));
        gridPaint.setStrokeWidth(1.5f);
        accuracyFillPaint.setColor(ContextCompat.getColor(context, R.color.bp_primary_container));
        accuracyStrokePaint.setStyle(Paint.Style.STROKE);
        accuracyStrokePaint.setStrokeWidth(2.5f);
        accuracyStrokePaint.setColor(ContextCompat.getColor(context, R.color.bp_primary));
        pinPaint.setColor(ContextCompat.getColor(context, R.color.bp_secondary));
        pinDotPaint.setColor(ContextCompat.getColor(context, R.color.bp_surface));
    }

    /** @param accuracyMeters the fix's reported accuracy radius; ignored (a fixed dot is drawn) when no fix exists. */
    public void setFix(boolean hasFix, float accuracyMeters) {
        this.hasFix = hasFix;
        this.accuracyMeters = accuracyMeters;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float step = Math.min(width, height) / 8f;
        for (float x = 0; x <= width; x += step) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float y = 0; y <= height; y += step) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        float centerX = width / 2f;
        float centerY = height / 2f;

        if (hasFix) {
            // Accuracy rings shrink as the fix improves, floored so a very precise fix still reads
            // as a visible halo rather than vanishing under the pin.
            float ringRadius = Math.max(28f, Math.min(width, height) * 0.4f * Math.min(1f, accuracyMeters / 50f));
            canvas.drawCircle(centerX, centerY, ringRadius, accuracyFillPaint);
            canvas.drawCircle(centerX, centerY, ringRadius, accuracyStrokePaint);
        }

        float pinRadius = 16f;
        canvas.drawCircle(centerX, centerY - pinRadius, pinRadius, pinPaint);
        canvas.drawCircle(centerX, centerY - pinRadius, pinRadius * 0.4f, pinDotPaint);
        android.graphics.Path tail = new android.graphics.Path();
        tail.moveTo(centerX - pinRadius * 0.55f, centerY - pinRadius * 0.3f);
        tail.lineTo(centerX + pinRadius * 0.55f, centerY - pinRadius * 0.3f);
        tail.lineTo(centerX, centerY + pinRadius * 0.6f);
        tail.close();
        canvas.drawPath(tail, pinPaint);
    }
}
