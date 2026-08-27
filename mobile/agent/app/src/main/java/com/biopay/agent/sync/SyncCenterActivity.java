package com.biopay.agent.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.ui.BaseActivity;

/**
 * Pulled out of Settings: a dedicated view of what's still queued to sync, built on the same
 * {@link DatabaseHelper} counts Settings' storage section already computed.
 */
public class SyncCenterActivity extends BaseActivity {

    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_center);
        setupBackToolbar(R.id.toolbar);
        databaseHelper = DatabaseHelper.get(this);

        findViewById(R.id.btnSyncNow).setOnClickListener(v -> {
            SyncScheduler.triggerNow(this);
            refresh();
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

        int households = databaseHelper.countPendingHouseholds();
        int members = databaseHelper.countPendingMembers();
        int transactions = databaseHelper.countPendingTransactions();
        int total = databaseHelper.countPendingSyncWork();

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
