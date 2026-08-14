package com.biopay.agent.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.biopay.agent.R;
import com.biopay.agent.attendance.AttendanceActivity;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.households.HouseholdListActivity;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncScheduler;

import java.util.Arrays;

/** Main menu + at-a-glance dashboard (KPIs and a paid/pending chart), all sourced from local data. */
public class HomeActivity extends AppCompatActivity {

    private SessionManager sessionManager;
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

        sessionManager = new SessionManager(this);
        householdDao = new HouseholdDao(this);
        paymentDao = new PaymentDao(this);

        if (!LocationHelper.hasPermission(this)) {
            locationPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
        }

        ((TextView) findViewById(R.id.tvWelcome)).setText(getString(R.string.home_welcome, sessionManager.getFullName()));
        ((TextView) findViewById(R.id.tvOrgLabel)).setText(sessionManager.getPartnerCode());

        findViewById(R.id.btnHouseholds).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdListActivity.class)));
        findViewById(R.id.btnAttendance).setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        // TODO: wire once Alternates/Payments/Reports activities land.
        findViewById(R.id.btnAlternates).setOnClickListener(v -> notYetAvailable());
        findViewById(R.id.btnPayments).setOnClickListener(v -> notYetAvailable());
        findViewById(R.id.btnReports).setOnClickListener(v -> notYetAvailable());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
        SyncScheduler.triggerNow(this);
    }

    private void refreshDashboard() {
        int householdCount = householdDao.countAll();
        int pendingSync = householdDao.countPendingSync();
        int paidCount = paymentDao.countByStatus(PaymentDao.STATUS_PAID);
        int pendingPaymentCount = paymentDao.countByStatus(PaymentDao.STATUS_PENDING);

        ((TextView) findViewById(R.id.tvKpiHouseholds)).setText(getString(R.string.home_kpi_households, householdCount));
        ((TextView) findViewById(R.id.tvKpiPendingSync)).setText(getString(R.string.home_kpi_pending_sync, pendingSync));
        ((TextView) findViewById(R.id.tvKpiPaid)).setText(getString(R.string.home_kpi_paid, paidCount));
        ((TextView) findViewById(R.id.tvKpiPending)).setText(getString(R.string.home_kpi_pending_payment, pendingPaymentCount));

        SimpleBarChartView chart = findViewById(R.id.chartPayments);
        chart.setBars(Arrays.asList(
                new SimpleBarChartView.Bar("Paid", paidCount, getColor(R.color.bp_primary)),
                new SimpleBarChartView.Bar("Pending", pendingPaymentCount, getColor(R.color.bp_secondary))));
    }

    private void notYetAvailable() {
        android.widget.Toast.makeText(this, "Coming soon", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        sessionManager.clear();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
