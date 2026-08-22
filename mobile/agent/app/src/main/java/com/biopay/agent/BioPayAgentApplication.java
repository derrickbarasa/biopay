package com.biopay.agent;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.biopay.agent.session.SessionManager;
import com.biopay.agent.session.SessionTimeoutManager;
import com.biopay.agent.sync.NotificationHelper;
import com.biopay.agent.sync.SyncScheduler;

public class BioPayAgentApplication extends Application {

    // A grace window before treating a background move as "the officer left the app" -- long
    // enough to switch to another app (e.g. check a message) without losing their place, short
    // enough that anyone who actually stepped away still has to log back in.
    private static final long EXIT_LOGOUT_GRACE_MS = 2 * 60 * 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable logoutOnExit = this::logoutOnExit;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        SyncScheduler.schedulePeriodic(this);

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                handler.postDelayed(logoutOnExit, EXIT_LOGOUT_GRACE_MS);
            }

            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                handler.removeCallbacks(logoutOnExit);
            }
        });
    }

    private void logoutOnExit() {
        SessionTimeoutManager.get().stop();
        new SessionManager(this).clear();
    }
}
