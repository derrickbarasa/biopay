package com.biopay.agent.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Canvas bar chart -- no charting library dependency for what's
 * just a handful of bars. Single category axis, values labelled above each
 * bar, matching the web dashboard's teal/amber palette so the two surfaces
 * read as one system.
 */
public class SimpleBarChartView extends View {

    public static class Bar {
        public final String label;
        public final float value;
        public final int color;

        public Bar(String label, float value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final List<Bar> bars = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SimpleBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        labelPaint.setTextSize(28f);
        labelPaint.setColor(0xFF6B6B6B);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(32f);
        valuePaint.setColor(0xFF1A1A1A);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
    }

    public void setBars(List<Bar> newBars) {
        bars.clear();
        bars.addAll(newBars);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bars.isEmpty()) return;

        float width = getWidth();
        float height = getHeight();
        float topPadding = 50f;
        float bottomPadding = 60f;
        float chartHeight = height - topPadding - bottomPadding;

        float maxValue = 1f;
        for (Bar b : bars) maxValue = Math.max(maxValue, b.value);

        float gap = 24f;
        float barWidth = (width - gap * (bars.size() + 1)) / bars.size();

        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            float barHeight = (bar.value / maxValue) * chartHeight;
            float left = gap + i * (barWidth + gap);
            float top = topPadding + (chartHeight - barHeight);
            float right = left + barWidth;
            float bottom = topPadding + chartHeight;

            barPaint.setColor(bar.color);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 8f, 8f, barPaint);
            canvas.drawText(String.valueOf((int) bar.value), left + barWidth / 2, top - 12f, valuePaint);
            canvas.drawText(bar.label, left + barWidth / 2, bottom + 36f, labelPaint);
        }
    }
}
