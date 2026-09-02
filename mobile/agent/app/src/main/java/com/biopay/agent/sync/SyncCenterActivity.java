package com.biopay.agent.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;

/**
 * Pulled out of Settings: a dedicated view of what's still queued to sync, built on the same
 * {@link DatabaseHelper} counts Settings' storage section already computed.
 */
public class SyncCenterActivity extends BaseActivity {

    private DatabaseHelper databaseHelper;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_center);
        setupBackToolbar(R.id.toolbar);
        databaseHelper = DatabaseHelper.get(this);
        sessionManager = new SessionManager(this);

        findViewById(R.id.btnSyncNow).setOnClickListener(v -> {
            v.setEnabled(false);
            SyncFeedback.observe(this, this, v, SyncScheduler.triggerNow(this), () -> {
                v.setEnabled(true);
                refresh();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean online = isNetworkAvailable();
        ((ImageView) findViewById(R.id.ivConnectionStatus)).setImageResource(R.drawable.ic_sync);
        ((TextView) findViewById(R.id.tvConnectionStatus)).setText(
                online ? R.string.sync_center_connected : R.string.sync_center_offline);

        String partnerCode = sessionManager.getPartnerCode();
        int households = databaseHelper.countPendingHouseholds(partnerCode);
        int members = databaseHelper.countPendingMembers(partnerCode);
        int transactions = databaseHelper.countPendingTransactions(partnerCode);
        int total = databaseHelper.countPendingSyncWork(partnerCode);

        ((TextView) findViewById(R.id.tvPendingHeader)).setText(
                total > 0
                        ? getString(R.string.sync_center_pending_header, total)
                        : getString(R.string.sync_center_all_synced));
        ((TextView) findViewById(R.id.tvPendingHouseholds)).setText(String.valueOf(households));
        ((TextView) findViewById(R.id.tvPendingMembers)).setText(String.valueOf(members));
        ((TextView) findViewById(R.id.tvPendingTransactions)).setText(String.valueOf(transactions));
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo active = cm.getActiveNetworkInfo();
        return active != null && active.isConnected();
    }
}
