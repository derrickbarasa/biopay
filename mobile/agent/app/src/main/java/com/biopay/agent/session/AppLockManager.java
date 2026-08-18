package com.biopay.agent.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.biometric.BiometricManager;

/**
 * Persists whether the officer wants their phone's own lock (fingerprint, face, or PIN/pattern)
 * required to open BioPay. Distinct from {@code BiometricDevice}/{@code FaceRecognitionEngine},
 * which match a *beneficiary's* enrolled biometric to a Morpho scanner or camera capture -- this
 * uses the device's built-in {@link androidx.biometric.BiometricPrompt} to protect the app itself,
 * since a signed-in phone left unlocked exposes beneficiary PII and enrolled fingerprint templates.
 */
public final class AppLockManager {

    private static final String PREFS_NAME = "biopay_app_lock";
    private static final String KEY_ENABLED = "enabled";

    private final SharedPreferences prefs;

    public AppLockManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** True if this device has a secure lock screen or enrolled biometric it could prompt against.
     * Checked as two separate single-authenticator queries -- combined-authenticator queries have
     * inconsistent behavior across API levels, but a device passing either check can be prompted
     * via {@code setDeviceCredentialAllowed(true)}, which is the broadly-compatible path back to
     * API 21. */
    public static boolean isSupported(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        boolean biometricOk = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
        boolean credentialOk = manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                == BiometricManager.BIOMETRIC_SUCCESS;
        return biometricOk || credentialOk;
    }
}
