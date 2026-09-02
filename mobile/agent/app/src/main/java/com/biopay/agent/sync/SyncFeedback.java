package com.biopay.agent.sync;

import android.content.Context;
import android.view.View;

import androidx.lifecycle.LifecycleOwner;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.session.SessionManager;
import com.google.android.material.snackbar.Snackbar;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Turns a manual WorkManager sync into honest, terminal user feedback. */
public final class SyncFeedback {
    private SyncFeedback() { }

    public static void observe(LifecycleOwner owner, Context context, View anchor, UUID workId,
            Runnable onFinished) {
        AtomicBoolean reported = new AtomicBoolean(false);
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workId).observe(owner, info -> {
            if (info == null || reported.get()) return;
            if (info.getState() == WorkInfo.State.RUNNING) {
                Snackbar.make(anchor, R.string.sync_running, Snackbar.LENGTH_SHORT).show();
                return;
            }
            if (!info.getState().isFinished() || !reported.compareAndSet(false, true)) return;

            int pending = info.getOutputData().getInt(SyncWorker.OUTPUT_PENDING_COUNT,
                    DatabaseHelper.get(context).countPendingSyncWork(new SessionManager(context).getPartnerCode()));
            int message = info.getState() == WorkInfo.State.SUCCEEDED
                    ? R.string.sync_completed : R.string.sync_failed_server;
            Snackbar.make(anchor,
                    message == R.string.sync_completed
                            ? context.getString(message)
                            : context.getString(message, pending),
                    Snackbar.LENGTH_LONG).show();
            if (onFinished != null) onFinished.run();
        });
    }
}
