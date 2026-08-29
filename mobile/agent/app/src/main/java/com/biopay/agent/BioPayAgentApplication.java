package com.biopay.agent;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.NotificationHelper;
import com.biopay.agent.sync.SyncScheduler;

public class BioPayAgentApplication extends Application {

    // Foreground use never expires. Leaving BioPay starts this one-minute grace period instead.
    private static final long EXIT_LOGOUT_GRACE_MS = 60 * 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable logoutOnExit = this::logoutOnExit;
    private boolean loggedOutWhileBackgrounded;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        SyncScheduler.schedulePeriodic(this);

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                SessionManager session = new SessionManager(BioPayAgentApplication.this);
                if (session.isLoggedIn()) {
                    session.markAppBackgrounded();
                    handler.removeCallbacks(logoutOnExit);
                    handler.postDelayed(logoutOnExit, EXIT_LOGOUT_GRACE_MS);
                }
            }

            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                handler.removeCallbacks(logoutOnExit);
                SessionManager session = new SessionManager(BioPayAgentApplication.this);
                boolean expired = loggedOutWhileBackgrounded
                        || session.isBackgroundLogoutDue(EXIT_LOGOUT_GRACE_MS);
                loggedOutWhileBackgrounded = false;
                session.clearAppBackgrounded();
                if (expired) {
                    session.clear();
                    Intent intent = new Intent(BioPayAgentApplication.this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            }
        });
    }

    private void logoutOnExit() {
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            session.clear();
            loggedOutWhileBackgrounded = true;
        }
    }
}
