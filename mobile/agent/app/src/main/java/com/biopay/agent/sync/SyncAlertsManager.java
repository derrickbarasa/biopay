package com.biopay.agent.sync;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists whether the officer wants a notification when locally captured records keep failing
 * to sync -- off by default so nothing posts until they opt in from Settings. */
public final class SyncAlertsManager {

    private static final String PREFS_NAME = "biopay_sync_alerts";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ALREADY_NOTIFIED = "already_notified";

    private final SharedPreferences prefs;

    public SyncAlertsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Avoids re-notifying on every retry once the officer has seen the alert for this stretch
     * of failures; cleared as soon as a sync pass fully succeeds. */
    boolean alreadyNotified() {
        return prefs.getBoolean(KEY_ALREADY_NOTIFIED, false);
    }

    void setAlreadyNotified(boolean notified) {
        prefs.edit().putBoolean(KEY_ALREADY_NOTIFIED, notified).apply();
    }
}
