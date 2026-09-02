package com.biopay.agent.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;

import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.session.SessionManager;

/** Runs one {@link SyncManager} pass. Only ever scheduled with a network-connected constraint --
 * see {@link SyncScheduler} -- so a run here means the device is online right now. */
public class SyncWorker extends Worker {

    public static final String INPUT_MANUAL_SYNC = "manual_sync";
    public static final String OUTPUT_PENDING_COUNT = "pending_count";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean allSucceeded = new SyncManager(getApplicationContext()).syncAll();
        int pendingCount = DatabaseHelper.get(getApplicationContext())
                .countPendingSyncWork(new SessionManager(getApplicationContext()).getPartnerCode());
        NotificationHelper.reportSyncResult(getApplicationContext(), allSucceeded, pendingCount, getRunAttemptCount());
        Data output = new Data.Builder().putInt(OUTPUT_PENDING_COUNT, pendingCount).build();
        if (allSucceeded) {
            return Result.success(output);
        }
        // A manual attempt must finish with a visible failure state so the screen can explain
        // that Wi-Fi/mobile data alone does not mean the configured BioPay server is reachable.
        // Periodic work retains automatic backoff/retry behavior.
        return getInputData().getBoolean(INPUT_MANUAL_SYNC, false)
                ? Result.failure(output)
                : Result.retry();
    }
}
