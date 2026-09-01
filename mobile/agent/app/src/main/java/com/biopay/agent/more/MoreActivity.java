package com.biopay.agent.more;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.alternates.AlternatesActivity;
import com.biopay.agent.attendance.AttendanceActivity;
import com.biopay.agent.location.MyLocationActivity;
import com.biopay.agent.payments.PaymentsActivity;
import com.biopay.agent.profile.ProfileActivity;
import com.biopay.agent.reports.ReportsActivity;
import com.biopay.agent.security.SecurityActivity;
import com.biopay.agent.settings.SettingsActivity;
import com.biopay.agent.sync.SyncCenterActivity;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.vouchers.VoucherRedemptionActivity;

/**
 * Everything that isn't Home/Households/Activity: the new home for screens that used to hang
 * off the old Settings-tab-adjacent navigation before the 5-tab redesign.
 */
public class MoreActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        setupMainNavigation(R.id.bottomNavigation, R.id.navMore);
        setupPaymentFab(R.id.fabPayment);

        bindRow(R.id.rowProfile, R.drawable.ic_profile, R.string.more_profile, R.string.more_profile_body,
                new Intent(this, ProfileActivity.class));
        bindRow(R.id.rowSettings, R.drawable.ic_settings, R.string.more_settings, R.string.more_settings_body,
                new Intent(this, SettingsActivity.class));
        bindRow(R.id.rowSecurity, R.drawable.ic_lock, R.string.more_security, R.string.more_security_body,
                new Intent(this, SecurityActivity.class));
        bindRow(R.id.rowSyncCenter, R.drawable.ic_sync, R.string.more_sync_center, R.string.more_sync_center_body,
                new Intent(this, SyncCenterActivity.class));
        bindRow(R.id.rowAttendance, R.drawable.ic_attendance, R.string.more_attendance, R.string.more_attendance_body,
                new Intent(this, AttendanceActivity.class));
        bindRow(R.id.rowPayments, R.drawable.ic_payments, R.string.more_payments, R.string.more_payments_body,
                new Intent(this, PaymentsActivity.class));
        bindRow(R.id.rowVouchers, R.drawable.ic_voucher, R.string.more_vouchers, R.string.more_vouchers_body,
                new Intent(this, VoucherRedemptionActivity.class));
        bindRow(R.id.rowAlternates, R.drawable.ic_alternates, R.string.more_alternates, R.string.more_alternates_body,
                new Intent(this, AlternatesActivity.class));
        bindRow(R.id.rowMyLocation, R.drawable.ic_location_pin, R.string.more_my_location, R.string.more_my_location_body,
                new Intent(this, MyLocationActivity.class));
        bindRow(R.id.rowReports, R.drawable.ic_reports, R.string.more_reports, R.string.more_reports_body,
                new Intent(this, ReportsActivity.class));
    }

    private void bindRow(int rowId, int iconRes, int titleRes, int subtitleRes, Intent destination) {
        View row = findViewById(rowId);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(subtitleRes);
        row.setOnClickListener(v -> startActivity(destination));
    }
}
