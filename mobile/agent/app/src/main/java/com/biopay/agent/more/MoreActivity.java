package com.biopay.agent.more;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.AttendanceActivity;
import com.biopay.agent.feed.ActivityFeedActivity;
import com.biopay.agent.location.MyLocationActivity;
import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.profile.ProfileActivity;
import com.biopay.agent.reports.ReportsActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.settings.SettingsActivity;
import com.biopay.agent.sync.SyncCenterActivity;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Everything that isn't Home/Households/Payment/Vouchers: the home for secondary field tools
 * off the old Settings-tab-adjacent navigation before the 5-tab redesign.
 */
public class MoreActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        setupMainNavigation(R.id.bottomNavigation, R.id.navMore);

        bindRow(R.id.rowProfile, R.drawable.ic_profile, R.string.more_profile, R.string.more_profile_body,
                new Intent(this, ProfileActivity.class));
        bindRow(R.id.rowSettings, R.drawable.ic_settings, R.string.more_settings, R.string.more_settings_body,
                new Intent(this, SettingsActivity.class));
        bindRow(R.id.rowSyncCenter, R.drawable.ic_sync, R.string.more_sync_center, R.string.more_sync_center_body,
                new Intent(this, SyncCenterActivity.class));
        bindRow(R.id.rowAttendance, R.drawable.ic_attendance, R.string.more_attendance, R.string.more_attendance_body,
                new Intent(this, AttendanceActivity.class));
        bindRow(R.id.rowActivities, R.drawable.ic_activity, R.string.more_activities, R.string.more_activities_body,
                new Intent(this, ActivityFeedActivity.class));
        bindRow(R.id.rowMyLocation, R.drawable.ic_location_pin, R.string.more_my_location, R.string.more_my_location_body,
                new Intent(this, MyLocationActivity.class));
        bindRow(R.id.rowReports, R.drawable.ic_reports, R.string.more_reports, R.string.more_reports_body,
                new Intent(this, ReportsActivity.class));
        findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());
    }

    private void bindRow(int rowId, int iconRes, int titleRes, int subtitleRes, Intent destination) {
        View row = findViewById(rowId);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(subtitleRes);
        row.setOnClickListener(v -> startActivity(destination));
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout_confirm, (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        new SessionManager(this).clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
