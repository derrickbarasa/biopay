package com.biopay.agent.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.alternates.AlternatesActivity;
import com.biopay.agent.attendance.AttendanceActivity;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.households.HouseholdListActivity;
import com.biopay.agent.households.HouseholdFormActivity;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.payments.PaymentsActivity;
import com.biopay.agent.reports.ReportsActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.vouchers.VoucherRedemptionActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;

/** Main menu + at-a-glance dashboard (KPIs and a paid/pending chart), all sourced from local data. */
public class HomeActivity extends BaseActivity {

    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;
    private HouseholdDao householdDao;
    private PaymentDao paymentDao;

    // Requested once here, right after login, rather than inside Attendance/HouseholdForm --
    // this way the permission dialog never interrupts the fingerprint live-verify flow later.
    // Every screen that reads a location already tolerates a null result if this is denied.
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setupMainNavigation(R.id.bottomNavigation, R.id.navHome);

        sessionManager = new SessionManager(this);
        databaseHelper = DatabaseHelper.get(this);
        householdDao = new HouseholdDao(this);
        paymentDao = new PaymentDao(this);

        if (!LocationHelper.hasPermission(this)) {
            locationPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
        }

        refreshIdentity();

        findViewById(R.id.btnHouseholds).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdListActivity.class)));
        findViewById(R.id.btnAttendance).setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceActivity.class)));
        findViewById(R.id.btnVouchers).setOnClickListener(v ->
                startActivity(new Intent(this, VoucherRedemptionActivity.class)));
        findViewById(R.id.btnAlternates).setOnClickListener(v ->
                startActivity(new Intent(this, AlternatesActivity.class)));
        findViewById(R.id.btnPayments).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentsActivity.class)));
        findViewById(R.id.btnReports).setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
        findViewById(R.id.btnQuickRegister).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdFormActivity.class)));
        findViewById(R.id.btnQuickSync).setOnClickListener(v -> {
            SyncScheduler.triggerNow(this);
            Snackbar.make(findViewById(R.id.btnQuickSync), R.string.settings_sync_queued, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshIdentity();
        refreshDashboard();
        SyncScheduler.triggerNow(this);
    }

    private void refreshIdentity() {
        ((TextView) findViewById(R.id.tvWelcome)).setText(
                getString(R.string.home_welcome, sessionManager.getFullName()));
        String partnerCode = sessionManager.getPartnerCode();
        ((TextView) findViewById(R.id.tvOrgLabel)).setText(
                partnerCode == null || partnerCode.isEmpty() ? sessionManager.getEmail() : partnerCode);
    }

    private void refreshDashboard() {
        int householdCount = householdDao.countAll();
        int pendingSync = databaseHelper.countPendingSyncWork();
        int paidCount = paymentDao.countByStatus(PaymentDao.STATUS_PAID);
        int pendingPaymentCount = paymentDao.countByStatus(PaymentDao.STATUS_PENDING);

        ((TextView) findViewById(R.id.tvKpiHouseholds)).setText(String.valueOf(householdCount));
        ((TextView) findViewById(R.id.tvKpiPendingSync)).setText(String.valueOf(pendingSync));
        ((TextView) findViewById(R.id.tvKpiPaid)).setText(String.valueOf(paidCount));
        ((TextView) findViewById(R.id.tvKpiPending)).setText(String.valueOf(pendingPaymentCount));
        ((TextView) findViewById(R.id.tvSyncMessage)).setText(pendingSync == 0
                ? getString(R.string.home_sync_clear)
                : getString(R.string.home_sync_pending, pendingSync));

        SimpleBarChartView chart = findViewById(R.id.chartPayments);
        chart.setBars(Arrays.asList(
                new SimpleBarChartView.Bar("Paid", paidCount, ContextCompat.getColor(this, R.color.bp_primary)),
                new SimpleBarChartView.Bar("Pending", pendingPaymentCount, ContextCompat.getColor(this, R.color.bp_secondary))));
    }
}
