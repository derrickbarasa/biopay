package com.biopay.agent.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.biopay.agent.BuildConfig;
import com.biopay.agent.R;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncAlertsManager;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    private EditText serverUrl;
    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;

    private SessionManager sessionManager;
    private SyncAlertsManager syncAlertsManager;
    private TextView tvScannerStatus;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                MaterialSwitch swSyncAlerts = findViewById(R.id.swSyncAlerts);
                if (granted) {
                    syncAlertsManager.setEnabled(true);
                } else {
                    swSyncAlerts.setChecked(false);
                    Snackbar.make(swSyncAlerts, R.string.settings_sync_alerts_denied, Snackbar.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupMainNavigation(R.id.bottomNavigation, R.id.navSettings);

        sessionManager = new SessionManager(this);
        syncAlertsManager = new SyncAlertsManager(this);

        serverUrl = findViewById(R.id.etSettingsServer);
        currentPassword = findViewById(R.id.etCurrentPassword);
        newPassword = findViewById(R.id.etNewPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        tvScannerStatus = findViewById(R.id.tvScannerStatus);
        serverUrl.setText(ApiClient.getBaseUrl(this));

        setupAccountRow();
        setupNotifications();
        setupBiometrics();
        setupDataAndStorage();

        findViewById(R.id.btnSaveServer).setOnClickListener(view -> saveServer());
        findViewById(R.id.btnTestConnection).setOnClickListener(view -> testConnection());
        findViewById(R.id.btnSyncNow).setOnClickListener(view -> {
            SyncScheduler.triggerNow(this);
            show(R.string.settings_sync_queued);
        });
        findViewById(R.id.btnChangePassword).setOnClickListener(view -> changePassword());
        findViewById(R.id.btnSendFeedback).setOnClickListener(view -> sendFeedback());
        findViewById(R.id.btnLogout).setOnClickListener(view -> confirmLogout());

        ((TextView) findViewById(R.id.tvVersion)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScannerStatus();
    }

    private void setupAccountRow() {
        ((TextView) findViewById(R.id.tvAccountName)).setText(sessionManager.getFullName());
        String partnerCode = sessionManager.getPartnerCode();
        ((TextView) findViewById(R.id.tvAccountSubtitle)).setText(
                partnerCode == null || partnerCode.isEmpty() ? sessionManager.getEmail() : partnerCode);
        findViewById(R.id.rowAccount).setOnClickListener(v ->
                ((BottomNavigationView) findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.navProfile));
    }

    private void setupNotifications() {
        MaterialSwitch swSyncAlerts = findViewById(R.id.swSyncAlerts);
        swSyncAlerts.setChecked(syncAlertsManager.isEnabled());
        swSyncAlerts.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                syncAlertsManager.setEnabled(false);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                syncAlertsManager.setEnabled(true);
            }
        });
    }

    private void setupBiometrics() {
        BiometricDevice device = BiometricDeviceFactory.create();
        ((TextView) findViewById(R.id.tvScannerName)).setText(device.getDisplayName());
        findViewById(R.id.btnTestScanner).setOnClickListener(v -> refreshScannerStatus());
    }

    private void refreshScannerStatus() {
        boolean available = BiometricDeviceFactory.create().isAvailable(this);
        tvScannerStatus.setText(available ? R.string.settings_scanner_connected : R.string.settings_scanner_disconnected);
        tvScannerStatus.setBackgroundResource(available ? R.drawable.bg_status_success : R.drawable.bg_status_warning);
        tvScannerStatus.setTextColor(ContextCompat.getColor(this, available ? R.color.bp_success : R.color.bp_warning));
    }

    private void setupDataAndStorage() {
        int households = new HouseholdDao(this).countAll();
        int pending = DatabaseHelper.get(this).countPendingSyncWork();
        ((TextView) findViewById(R.id.tvStorageHouseholds)).setText(
                getString(R.string.settings_storage_households, households));
        ((TextView) findViewById(R.id.tvStoragePendingSync)).setText(
                getString(R.string.settings_storage_pending, pending));
    }

    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:support@biopay.app"));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_feedback_subject, BuildConfig.VERSION_NAME));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Snackbar.make(serverUrl, R.string.settings_feedback_no_app, Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean saveServer() {
        try {
            ApiClient.setBaseUrl(this, serverUrl.getText().toString());
            show(R.string.settings_server_saved);
            return true;
        } catch (IllegalArgumentException ex) {
            Snackbar.make(serverUrl, ex.getMessage(), Snackbar.LENGTH_LONG).show();
            return false;
        }
    }

    private void testConnection() {
        if (!saveServer()) return;
        findViewById(R.id.btnTestConnection).setEnabled(false);
        ApiClient.get(this).dispatch("ME", new HashMap<>(), new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                findViewById(R.id.btnTestConnection).setEnabled(true);
                show(R.string.settings_connection_ok);
            }
            @Override public void onError(String message, String responseCode) {
                findViewById(R.id.btnTestConnection).setEnabled(true);
                Snackbar.make(serverUrl, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void changePassword() {
        String current = currentPassword.getText().toString();
        String next = newPassword.getText().toString();
        String confirm = confirmPassword.getText().toString();
        if (next.length() < 8) {
            newPassword.setError(getString(R.string.settings_password_length));
            return;
        }
        if (!next.equals(confirm)) {
            confirmPassword.setError(getString(R.string.settings_password_mismatch));
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("oldPassword", current);
        params.put("newPassword", next);
        findViewById(R.id.btnChangePassword).setEnabled(false);
        ApiClient.get(this).dispatch("CHANGE_PASSWORD", params, new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                findViewById(R.id.btnChangePassword).setEnabled(true);
                currentPassword.setText("");
                newPassword.setText("");
                confirmPassword.setText("");
                show(R.string.settings_password_changed);
            }
            @Override public void onError(String message, String responseCode) {
                findViewById(R.id.btnChangePassword).setEnabled(true);
                Snackbar.make(currentPassword, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout_confirm, (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        sessionManager.clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void show(int stringId) {
        Snackbar.make(serverUrl, stringId, Snackbar.LENGTH_SHORT).show();
    }
}
