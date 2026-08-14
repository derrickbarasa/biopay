package com.biopay.agent.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs one {@link SyncManager} pass. Only ever scheduled with a network-connected constraint --
 * see {@link SyncScheduler} -- so a run here means the device is online right now. */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean allSucceeded = new SyncManager(getApplicationContext()).syncAll();
        return allSucceeded ? Result.success() : Result.retry();
    }
}
