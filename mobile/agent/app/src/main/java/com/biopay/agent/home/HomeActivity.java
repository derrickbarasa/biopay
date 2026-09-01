package com.biopay.agent.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.households.HouseholdFormActivity;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.reports.ReportsActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.settings.SettingsActivity;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
        setupPaymentFab(R.id.fabPayment);

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

        // Every destination below already has exactly one other, permanent entry point
        // (the bottom-nav tabs or the More menu) -- Home only keeps the shortcuts that
        // aren't duplicates: the hero's primary actions and the KPI card's own "View all".
        findViewById(R.id.btnQuickRegister).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdFormActivity.class)));
        findViewById(R.id.btnQuickSync).setOnClickListener(v -> {
            SyncScheduler.triggerNow(this);
            Snackbar.make(findViewById(R.id.btnQuickSync), R.string.settings_sync_queued, Snackbar.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnSummaryViewAll).setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshIdentity();
        refreshDashboard();
        checkSubscriptionGrace();
        SyncScheduler.triggerNow(this);
    }

    /** Mirrors the web dashboard's grace banner (DefaultLayout.vue) for officers who may never
     *  open it -- purely informational (only an anchor admin can actually renew, via the web
     *  dashboard's RENEW_SUBSCRIPTION action), unlike SubscriptionGate's login-time ARCHIVED
     *  lock. Fails silently offline, same reasoning as SubscriptionGate: a field officer without
     *  signal shouldn't see a spurious error for a check that's purely advisory anyway. */
    private void checkSubscriptionGrace() {
        Integer anchorId = sessionManager.getAnchorId();
        if (anchorId == null) return;
        Map<String, Object> params = new HashMap<>();
        params.put("anchorId", anchorId);
        ApiClient.get(this).dispatch("GET_SUBSCRIPTION", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject results = response.optJSONObject("results");
                String status = results != null ? results.optString("status", "NONE") : "NONE";
                if ("GRACE".equals(status)) {
                    int daysToArchive = results.optInt("daysToArchive", 0);
                    Snackbar.make(findViewById(R.id.tvWelcome),
                            getString(R.string.home_subscription_grace, daysToArchive), Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String message, String responseCode) {
                // Offline or the check failed -- purely advisory, so say nothing.
            }
        });
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

        SimpleDonutChartView chart = findViewById(R.id.chartPayments);
        chart.setSlices(Arrays.asList(
                new SimpleDonutChartView.Slice("Paid", paidCount, ContextCompat.getColor(this, R.color.bp_primary)),
                new SimpleDonutChartView.Slice("Pending", pendingPaymentCount, ContextCompat.getColor(this, R.color.bp_secondary))),
                "Total");
    }
}
