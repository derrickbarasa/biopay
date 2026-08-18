package com.biopay.agent.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.session.AppLockManager;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;

/**
 * Launcher screen. Also doubles as a quick sanity check that the active
 * product flavor resolved the right {@link BiometricDevice} implementation
 * -- see {@link BiometricDeviceFactory} -- and, when the officer has turned
 * on App lock in Settings, gates entry behind the phone's own biometric/PIN
 * confirmation before a signed-in session is ever shown.
 */
public class SplashActivity extends BaseActivity {

    private static final long DISPLAY_MILLIS = 700;

    private boolean loggedIn;
    private AppLockManager appLockManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        BiometricDevice device = BiometricDeviceFactory.create();
        TextView tvDeviceLabel = findViewById(R.id.tvDeviceLabel);
        tvDeviceLabel.setText(getString(R.string.splash_scanner_label, device.getDisplayName()));

        loggedIn = new SessionManager(this).isLoggedIn();
        appLockManager = new AppLockManager(this);

        findViewById(R.id.btnUnlockRetry).setOnClickListener(v -> requestUnlock());

        new Handler(Looper.getMainLooper()).postDelayed(this::proceed, DISPLAY_MILLIS);
    }

    private void proceed() {
        if (loggedIn && appLockManager.isEnabled() && AppLockManager.isSupported(this)) {
            requestUnlock();
        } else {
            goToNextScreen();
        }
    }

    private void requestUnlock() {
        showLocked(false);
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.applock_prompt_title))
                .setSubtitle(getString(R.string.applock_prompt_subtitle))
                .setDeviceCredentialAllowed(true)
                .build();
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        goToNextScreen();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        showLocked(true);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // A single misread finger/PIN attempt -- the prompt itself stays open and
                        // lets the officer retry, so no UI change is needed here.
                    }
                });
        prompt.authenticate(promptInfo);
    }

    private void showLocked(boolean locked) {
        findViewById(R.id.progressSplash).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.tvLockedMessage).setVisibility(locked ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnUnlockRetry).setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private void goToNextScreen() {
        Intent intent = new Intent(this, loggedIn ? HomeActivity.class : LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
