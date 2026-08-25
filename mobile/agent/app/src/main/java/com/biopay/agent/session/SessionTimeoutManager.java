package com.biopay.agent.session;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;

import com.biopay.agent.R;
import com.biopay.agent.login.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

/**
 * Logs the officer out after 5 minutes without a single touch anywhere in the app -- a signed-in
 * phone left unattended on a table exposes beneficiary PII and enrolled fingerprint templates.
 * Rather than signing out silently, it gives the officer a 30-second window to confirm they're
 * still there (a stray touch is easy to miss on a field visit); no response in that window clears
 * the session exactly like a manual sign-out. Complements the immediate sign-out that {@link
 * com.biopay.agent.BioPayAgentApplication} applies when the app itself is backgrounded -- this
 * covers the case where the app stays open and visible but the officer has simply stopped using it.
 */
public final class SessionTimeoutManager {

    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000;
    private static final long PROMPT_WINDOW_MS = 30 * 1000;

    private static final SessionTimeoutManager INSTANCE = new SessionTimeoutManager();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable showPrompt = this::showPrompt;
    private final Runnable expire = this::expire;

    private WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private AlertDialog promptDialog;

    private SessionTimeoutManager() {
    }

    public static SessionTimeoutManager get() {
        return INSTANCE;
    }

    /** Call from an activity's onResume once a session exists -- marks it as the screen to prompt on. */
    public void attach(Activity activity) {
        currentActivity = new WeakReference<>(activity);
        reset();
    }

    /** Call from an activity's onPause -- stops a backgrounded screen from popping the prompt later. */
    public void detach(Activity activity) {
        if (currentActivity.get() == activity) {
            handler.removeCallbacks(showPrompt);
            handler.removeCallbacks(expire);
        }
    }

    /** Call on every user touch/key event; restarts the 5-minute idle countdown. */
    public void reset() {
        dismissPrompt();
        handler.removeCallbacks(showPrompt);
        handler.removeCallbacks(expire);
        handler.postDelayed(showPrompt, IDLE_TIMEOUT_MS);
    }

    /** Call when the officer signs out explicitly, so no stale timers fire on the login screen. */
    public void stop() {
        dismissPrompt();
        handler.removeCallbacks(showPrompt);
        handler.removeCallbacks(expire);
        currentActivity = new WeakReference<>(null);
    }

    private void showPrompt() {
        Activity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        promptDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.session_timeout_title)
                .setMessage(R.string.session_timeout_message)
                .setCancelable(false)
                .setPositiveButton(R.string.session_timeout_confirm, (dialog, which) -> reset())
                .show();
        handler.postDelayed(expire, PROMPT_WINDOW_MS);
    }

    private void expire() {
        dismissPrompt();
        Activity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new SessionManager(activity).clear();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    /**
     * A {@link Dialog} runs on its own window, so touches inside it never reach the hosting
     * Activity's {@link Activity#onUserInteraction()} -- a long biometric-capture or data-entry
     * dialog would otherwise keep counting down to the idle prompt/logout underneath it even
     * while the officer is actively working. Call right after building a dialog that can stay
     * open for a while (capture progress, verification, multi-field entry) so any touch on it
     * also counts as activity.
     */
    public static void keepAlive(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null && !(window.getCallback() instanceof KeepAliveWindowCallback)) {
            window.setCallback(new KeepAliveWindowCallback(window.getCallback()));
        }
    }

    private void dismissPrompt() {
        if (promptDialog != null) {
            promptDialog.dismiss();
            promptDialog = null;
        }
    }
}
