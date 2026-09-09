package com.biopay.agent.reports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/** Seven small daily bars: enough trend context without turning the report into analytics UI. */
public class SevenDayActivityChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] values = new int[7];
    private final String[] labels = new String[7];

    public SevenDayActivityChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -6);
        String[] weekdays = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        for (int i = 0; i < 7; i++) {
            String label = weekdays[day.get(Calendar.DAY_OF_WEEK)];
            labels[i] = label == null || label.isEmpty() ? "-" : label.substring(0, 1);
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setValues(int[] source) {
        int total = 0;
        for (int i = 0; i < values.length; i++) {
            values[i] = source != null && i < source.length ? Math.max(0, source[i]) : 0;
            total += values[i];
        }
        setContentDescription(getResources().getQuantityString(
                R.plurals.reports_activity_chart_accessibility, total, total));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int max = 1;
        for (int value : values) max = Math.max(max, value);
        float labelHeight = dp(22);
        float chartBottom = getHeight() - getPaddingBottom() - labelHeight;
        float chartTop = getPaddingTop() + dp(8);
        float available = Math.max(0, chartBottom - chartTop);
        float slot = (getWidth() - getPaddingLeft() - getPaddingRight()) / 7f;
        float barWidth = Math.min(dp(22), slot * .54f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(11));

        for (int i = 0; i < 7; i++) {
            float center = getPaddingLeft() + slot * (i + .5f);
            float height = values[i] == 0 ? dp(3) : Math.max(dp(8), available * values[i] / max);
            int color = i == 6 ? R.color.bp_secondary : R.color.bp_primary;
            paint.setColor(ContextCompat.getColor(getContext(), values[i] == 0 ? R.color.bp_surface_variant : color));
            RectF bar = new RectF(center - barWidth / 2, chartBottom - height, center + barWidth / 2, chartBottom);
            canvas.drawRoundRect(bar, dp(4), dp(4), paint);
            paint.setColor(ContextCompat.getColor(getContext(), R.color.bp_text_secondary));
            canvas.drawText(labels[i], center, getHeight() - getPaddingBottom() - dp(2), paint);
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
