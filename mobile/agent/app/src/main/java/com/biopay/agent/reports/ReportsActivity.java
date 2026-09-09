package com.biopay.agent.reports;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.feed.ActivityFeedActivity;
import com.biopay.agent.payments.PaymentsActivity;
import com.biopay.agent.sync.SyncCenterActivity;
import com.biopay.agent.ui.BaseActivity;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Actionable offline field report built entirely from this device's database. */
public class ReportsActivity extends BaseActivity {
    private ReportDao reportDao;
    private ReportDao.Range selectedRange = ReportDao.Range.TODAY;
    private final NumberFormat number = NumberFormat.getNumberInstance();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        setupBackToolbar(R.id.toolbar);
        reportDao = new ReportDao(this);

        com.google.android.material.button.MaterialButtonToggleGroup toggle = findViewById(R.id.rangeToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnRangeSeven) selectedRange = ReportDao.Range.SEVEN_DAYS;
            else if (checkedId == R.id.btnRangeThirty) selectedRange = ReportDao.Range.THIRTY_DAYS;
            else if (checkedId == R.id.btnRangeAll) selectedRange = ReportDao.Range.ALL;
            else selectedRange = ReportDao.Range.TODAY;
            refresh();
        });

        findViewById(R.id.btnViewPayments).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentsActivity.class)));
        findViewById(R.id.btnViewActivity).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityFeedActivity.class)));
        findViewById(R.id.btnViewLocations).setOnClickListener(v ->
                startActivity(ReportLocationsActivity.intent(this, selectedRange)));
        findViewById(R.id.rowWaitingSync).setOnClickListener(v -> openSyncCenter());
        findViewById(R.id.btnOpenSync).setOnClickListener(v -> openSyncCenter());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void openSyncCenter() {
        startActivity(new Intent(this, SyncCenterActivity.class));
    }

    private void refresh() {
        ReportDao.Summary summary = reportDao.load(selectedRange);
        updateRangeCopy();
        setText(R.id.tvRegistrations, summary.registrations);
        setText(R.id.tvVerifications, summary.verifications);
        setText(R.id.tvPayments, summary.payments);
        setText(R.id.tvAttendance, summary.attendances);
        setText(R.id.tvPaidCount, getString(R.string.reports_paid_count, summary.paidCount));
        setText(R.id.tvPendingCount, getString(R.string.reports_pending_count, summary.pendingCount));
        setText(R.id.tvFailedCount, getString(R.string.reports_failed_count, summary.failedCount));
        setText(R.id.tvPaymentAmounts, getString(R.string.reports_amount_summary,
                number.format(summary.paidAmount), number.format(summary.pendingAmount)));
        ((PaymentStatusBarView) findViewById(R.id.paymentStatusBar)).setValues(
                summary.paidCount, summary.pendingCount, summary.failedCount);
        ((SevenDayActivityChartView) findViewById(R.id.activityChart)).setValues(summary.sevenDayActivity);

        setText(R.id.tvReadyHouseholds, summary.readyHouseholds);
        setText(R.id.tvMissingBiometrics, Math.max(0, summary.totalHouseholds - summary.readyHouseholds));
        setText(R.id.tvIncompleteRecords, summary.incompleteHouseholds);
        setText(R.id.tvWaitingSync, summary.waitingSync);
        renderLocations(summary);
        renderRecent(summary);
    }

    private void updateRangeCopy() {
        int scopeText;
        int chartTitle;
        if (selectedRange == ReportDao.Range.SEVEN_DAYS) {
            scopeText = R.string.reports_range_scope_seven;
            chartTitle = R.string.reports_seven_day_activity;
        } else if (selectedRange == ReportDao.Range.THIRTY_DAYS) {
            scopeText = R.string.reports_range_scope_thirty;
            chartTitle = R.string.reports_recent_seven_day_activity;
        } else if (selectedRange == ReportDao.Range.ALL) {
            scopeText = R.string.reports_range_scope_all;
            chartTitle = R.string.reports_recent_seven_day_activity;
        } else {
            scopeText = R.string.reports_range_scope_today;
            chartTitle = R.string.reports_today_activity;
        }
        ((TextView) findViewById(R.id.tvActiveRange)).setText(scopeText);
        ((TextView) findViewById(R.id.tvActivityChartTitle)).setText(chartTitle);
    }

    private void renderLocations(ReportDao.Summary summary) {
        LinearLayout container = findViewById(R.id.locationContainer);
        container.removeAllViews();
        if (summary.locations.isEmpty()) {
            container.addView(emptyText(R.string.reports_location_empty));
            return;
        }
        int max = Math.max(1, summary.locations.get(0).count);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < summary.locations.size(); i++) {
            ReportDao.LocationCount location = summary.locations.get(i);
            View row = inflater.inflate(R.layout.item_report_location, container, false);
            ((TextView) row.findViewById(R.id.tvLocationName)).setText(location.name);
            ((TextView) row.findViewById(R.id.tvLocationCount)).setText(number.format(location.count));
            ProgressBar progress = row.findViewById(R.id.locationProgress);
            progress.setMax(max);
            progress.setProgress(location.count);
            container.addView(row);
            if (i < summary.locations.size() - 1) container.addView(divider());
        }
    }

    private void renderRecent(ReportDao.Summary summary) {
        LinearLayout container = findViewById(R.id.recentContainer);
        container.removeAllViews();
        if (summary.recent.isEmpty()) {
            container.addView(emptyText(R.string.reports_recent_empty));
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < summary.recent.size(); i++) {
            ReportDao.RecentItem item = summary.recent.get(i);
            View row = inflater.inflate(R.layout.item_report_recent, container, false);
            ((TextView) row.findViewById(R.id.tvRecentTitle)).setText(item.title);
            ((TextView) row.findViewById(R.id.tvRecentSubtitle)).setText(item.subtitle);
            ((TextView) row.findViewById(R.id.tvRecentTime)).setText(formatTime(item.createdAt));
            ((ImageView) row.findViewById(R.id.ivRecentIcon)).setImageResource(iconFor(item.title));
            container.addView(row);
            if (i < summary.recent.size() - 1) container.addView(divider());
        }
    }

    private int iconFor(String title) {
        String lower = title == null ? "" : title.toLowerCase(Locale.US);
        if (lower.contains("verified")) return R.drawable.ic_fingerprint;
        if (lower.contains("payment")) return R.drawable.ic_payments;
        if (lower.contains("attendance")) return R.drawable.ic_attendance;
        return R.drawable.ic_households;
    }

    private String formatTime(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(timestamp);
            return date == null ? timestamp : new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } catch (ParseException ignored) {
            return timestamp;
        }
    }

    private TextView emptyText(int stringId) {
        TextView text = new TextView(this);
        text.setText(stringId);
        text.setTextColor(ContextCompat.getColor(this, R.color.bp_text_secondary));
        text.setTextSize(14);
        text.setPadding(dp(16), dp(18), dp(16), dp(18));
        return text;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.bp_surface_variant));
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setText(int viewId, int value) { setText(viewId, number.format(value)); }
    private void setText(int viewId, String value) { ((TextView) findViewById(viewId)).setText(value); }
}
