package com.biopay.agent.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import com.biopay.agent.face.FaceCaptureActivity;
import com.biopay.agent.face.FaceRecognitionEngine;
import com.biopay.agent.face.FaceRecognitionException;
import com.biopay.agent.face.MlKitFaceRecognitionEngine;
import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.profile.ProfileActivity;
import com.biopay.agent.security.SecurityActivity;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncAlertsManager;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

public class SettingsActivity extends BaseActivity {
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

    private final ActivityResultLauncher<Intent> faceCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(FaceCaptureActivity.EXTRA_RESULT_IMAGE_PATH);
                if (path != null) runFaceDetectionTest(path);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupBackToolbar(R.id.toolbar);

        sessionManager = new SessionManager(this);
        syncAlertsManager = new SyncAlertsManager(this);

        tvScannerStatus = findViewById(R.id.tvScannerStatus);

        setupAccountRow();
        setupSecurityRow();
        setupNotifications();
        setupBiometrics();
        setupDataAndStorage();

        findViewById(R.id.btnTestConnection).setOnClickListener(view -> testConnection());
        findViewById(R.id.btnSyncNow).setOnClickListener(view -> {
            SyncScheduler.triggerNow(this);
            show(R.string.settings_sync_queued);
        });
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
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void setupSecurityRow() {
        findViewById(R.id.rowSecurity).setOnClickListener(v ->
                startActivity(new Intent(this, SecurityActivity.class)));
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
        findViewById(R.id.btnTestFace).setOnClickListener(v ->
                faceCaptureLauncher.launch(new Intent(this, FaceCaptureActivity.class)));

        // Read-only -- which methods are enabled is an org-admin (web dashboard) decision, not a
        // per-device toggle, so every officer in an organisation sees the same thing here.
        String enabledMethodsRes;
        switch (sessionManager.getVerificationMethod()) {
            case "FACIAL":
                enabledMethodsRes = getString(R.string.settings_enabled_methods_face);
                break;
            case "BOTH":
                enabledMethodsRes = getString(R.string.settings_enabled_methods_both);
                break;
            default:
                enabledMethodsRes = getString(R.string.settings_enabled_methods_fingerprint);
        }
        ((TextView) findViewById(R.id.tvEnabledMethods)).setText(enabledMethodsRes);
    }

    /** Runs the real capture-&gt;align-&gt;embed pipeline end to end and reports the honest outcome
     *  -- nothing here is saved or synced. The embedding comes from the explicitly-unvalidated
     *  prototype model; see {@link MlKitFaceRecognitionEngine} for its provenance/status. */
    private void runFaceDetectionTest(String imagePath) {
        TextView tvFaceBody = findViewById(R.id.tvFaceBody);
        tvFaceBody.setText(R.string.face_test_running);
        new Thread(() -> {
            String outcome;
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                byte[] bytes = readFile(imagePath);
                FaceRecognitionEngine.CaptureResult result = engine.createEmbedding(bytes);
                outcome = getString(R.string.face_test_embedding_ok,
                        result.embedding.length, result.qualityScore);
            } catch (FaceRecognitionException ex) {
                outcome = getString(R.string.face_test_capture_failed, ex.getMessage());
            } catch (IOException ex) {
                outcome = getString(R.string.face_test_capture_failed, ex.getMessage());
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
            String finalOutcome = outcome;
            runOnUiThread(() -> tvFaceBody.setText(finalOutcome));
        }).start();
    }

    private static byte[] readFile(String path) throws IOException {
        File file = new File(path);
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = in.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += read;
            }
        }
        return bytes;
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
            Snackbar.make(findViewById(android.R.id.content), R.string.settings_feedback_no_app, Snackbar.LENGTH_LONG).show();
        }
    }

    private void testConnection() {
        findViewById(R.id.btnTestConnection).setEnabled(false);
        ApiClient.get(this).dispatch("ME", new HashMap<>(), new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                findViewById(R.id.btnTestConnection).setEnabled(true);
                show(R.string.settings_connection_ok);
            }
            @Override public void onError(String message, String responseCode) {
                findViewById(R.id.btnTestConnection).setEnabled(true);
                Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
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
        Snackbar.make(findViewById(android.R.id.content), stringId, Snackbar.LENGTH_SHORT).show();
    }
}
