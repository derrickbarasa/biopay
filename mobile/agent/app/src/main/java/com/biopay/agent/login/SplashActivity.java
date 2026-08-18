package com.biopay.agent.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;

/**
 * Launcher screen. Also doubles as a quick sanity check that the active
 * product flavor resolved the right {@link BiometricDevice} implementation
 * -- see {@link BiometricDeviceFactory}.
 */
public class SplashActivity extends BaseActivity {

    private static final long DISPLAY_MILLIS = 700;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        BiometricDevice device = BiometricDeviceFactory.create();
        TextView tvDeviceLabel = findViewById(R.id.tvDeviceLabel);
        tvDeviceLabel.setText(getString(R.string.splash_scanner_label, device.getDisplayName()));

        boolean loggedIn = new SessionManager(this).isLoggedIn();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, loggedIn ? HomeActivity.class : LoginActivity.class);
            startActivity(intent);
            finish();
        }, DISPLAY_MILLIS);
    }
}
