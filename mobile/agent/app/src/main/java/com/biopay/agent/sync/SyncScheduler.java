package com.biopay.agent.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** Entry point for scheduling {@link SyncWorker} runs -- both always require a connected network,
 * so WorkManager itself holds the work until the device is online rather than this app polling
 * for connectivity. */
public final class SyncScheduler {

    private static final String PERIODIC_WORK_NAME = "sync-periodic";
    private static final String IMMEDIATE_WORK_NAME = "sync-now";

    private SyncScheduler() {
    }

    /** Steady-state safety net -- called once from {@code BioPayAgentApplication.onCreate()}. */
    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Opportunistic immediate attempt -- called from {@code HomeActivity.onResume()} so captured
     * data goes out as soon as the officer opens the app on a connection, not only on the next
     * periodic tick. */
    public static UUID triggerNow(Context context) {
        return enqueueNow(context, true);
    }

    /** Background opportunity used when Home resumes. Failures retain WorkManager backoff and
     * retry rather than ending like a user-requested, feedback-driven attempt. */
    public static void triggerAutomaticNow(Context context) {
        enqueueNow(context, false);
    }

    private static UUID enqueueNow(Context context, boolean manual) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putBoolean(SyncWorker.INPUT_MANUAL_SYNC, manual)
                        .build())
                .build();
        WorkManager.getInstance(context)
                // A button press is an explicit retry. Replace an older backed-off/stuck manual
                // attempt so a connection that has just returned is used immediately.
                .enqueueUniqueWork(IMMEDIATE_WORK_NAME,
                        manual ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP, request);
        return request.getId();
    }
}
