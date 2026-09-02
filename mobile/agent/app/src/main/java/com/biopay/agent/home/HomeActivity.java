package com.biopay.agent.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.households.HouseholdFormActivity;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.payments.PaymentsActivity;
import com.biopay.agent.payments.PaymentVerificationActivity;
import com.biopay.agent.reports.ReportsActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.settings.SettingsActivity;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.sync.SyncFeedback;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Main menu + at-a-glance operational dashboard, all sourced from local data. */
public class HomeActivity extends BaseActivity {

    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;
    private HouseholdDao householdDao;
    private PaymentDao paymentDao;
    private PaymentDao.LocalPayment nextPayment;

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

        // Every destination below already has exactly one other, permanent entry point
        // (the bottom-nav tabs or the More menu) -- Home only keeps the shortcuts that
        // aren't duplicates: the hero's primary actions and the KPI card's own "View all".
        findViewById(R.id.btnQuickRegister).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdFormActivity.class)));
        findViewById(R.id.btnQuickSync).setOnClickListener(this::triggerManualSync);
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnSummaryViewAll).setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
        findViewById(R.id.btnPaymentsViewAll).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentsActivity.class)));
        findViewById(R.id.btnCheckPayments).setOnClickListener(this::triggerManualSync);
        findViewById(R.id.btnStartNextPayment).setOnClickListener(v -> {
            if (nextPayment == null || nextPayment.remoteId == null) return;
            startActivity(PaymentVerificationActivity.intentFor(this, nextPayment.remoteId,
                    nextPayment.householdNumber, nextPayment.amount));
        });
    }

    private void triggerManualSync(View anchor) {
        anchor.setEnabled(false);
        SyncFeedback.observe(this, this, anchor, SyncScheduler.triggerNow(this), () -> {
            anchor.setEnabled(true);
            refreshDashboard();
        });
        Snackbar.make(anchor, R.string.settings_sync_queued, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshIdentity();
        refreshDashboard();
        checkSubscriptionGrace();
        SyncScheduler.triggerAutomaticNow(this);
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
    }

    private void refreshDashboard() {
        int householdCount = householdDao.countAll();
        int pendingSync = databaseHelper.countPendingSyncWork(sessionManager.getPartnerCode());
        int paidCount = paymentDao.countByStatus(PaymentDao.STATUS_PAID);
        int pendingPaymentCount = paymentDao.countByStatus(PaymentDao.STATUS_PENDING);

        ((TextView) findViewById(R.id.tvKpiHouseholds)).setText(String.valueOf(householdCount));
        ((TextView) findViewById(R.id.tvKpiPendingSync)).setText(String.valueOf(pendingSync));
        ((TextView) findViewById(R.id.tvKpiPaid)).setText(String.valueOf(paidCount));
        ((TextView) findViewById(R.id.tvKpiPending)).setText(String.valueOf(pendingPaymentCount));
        ((TextView) findViewById(R.id.tvSyncMessage)).setText(pendingSync == 0
                ? getString(R.string.home_sync_clear)
                : getString(R.string.home_sync_pending, pendingSync));

        List<PaymentDao.LocalPayment> readyPayments = paymentDao.listPendingGeneratedPayments();
        boolean hasReadyPayment = !readyPayments.isEmpty();
        findViewById(R.id.paymentReadyState).setVisibility(hasReadyPayment ? View.VISIBLE : View.GONE);
        findViewById(R.id.paymentEmptyState).setVisibility(hasReadyPayment ? View.GONE : View.VISIBLE);

        nextPayment = hasReadyPayment ? readyPayments.get(0) : null;
        if (nextPayment == null) return;

        ((TextView) findViewById(R.id.tvPaymentReadyCount)).setText(getResources().getQuantityString(
                R.plurals.home_payment_ready_count, readyPayments.size(), readyPayments.size()));
        String householdName = nextPayment.householdName == null || nextPayment.householdName.trim().isEmpty()
                ? nextPayment.householdNumber : nextPayment.householdName.trim();
        ((TextView) findViewById(R.id.tvNextPaymentName)).setText(householdName);
        String village = nextPayment.village == null ? "" : nextPayment.village.trim();
        ((TextView) findViewById(R.id.tvNextPaymentMeta)).setText(village.isEmpty()
                ? nextPayment.householdNumber
                : getString(R.string.home_payment_next_meta, nextPayment.householdNumber, village));
        ((TextView) findViewById(R.id.tvNextPaymentAmount)).setText(getString(
                R.string.payment_amount, NumberFormat.getNumberInstance().format(nextPayment.amount)));
    }
}
