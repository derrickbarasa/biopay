package com.biopay.agent.settings;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
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
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncAlertsManager;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.OutcomeFeedback;
import com.biopay.agent.update.AppUpdateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SettingsActivity extends BaseActivity {
    private SessionManager sessionManager;
    private SyncAlertsManager syncAlertsManager;
    private TextView tvScannerStatus;
    private TextView tvUpdateStatus;
    private MaterialButton btnCheckForUpdates;
    private AppUpdateManager.UpdateInfo availableUpdate;
    private long pendingUpdateDownloadId = -1;

    /** Registered for the whole activity lifetime (not just while a download is in flight) so a
     *  download that finishes after the officer has navigated away within Settings, or after the
     *  activity is recreated (rotation), still gets picked up when they come back to it. */
    private final BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (finishedId == -1 || finishedId != pendingUpdateDownloadId) return;
            onUpdateDownloadFinished();
        }
    };

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
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        btnCheckForUpdates = findViewById(R.id.btnCheckForUpdates);

        setupNotifications();
        setupBiometrics();
        setupDataAndStorage();
        setupUpdates();

        findViewById(R.id.btnSendFeedback).setOnClickListener(view -> sendFeedback());
        ((TextView) findViewById(R.id.tvVersion)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        ContextCompat.registerReceiver(this, downloadCompleteReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScannerStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(downloadCompleteReceiver);
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
     *  -- nothing here is saved or synced. The embedding comes from a benchmarked open-source
     *  model whose accept threshold is not yet calibrated for this deployment; see
     *  {@link MlKitFaceRecognitionEngine} for its provenance/status. */
    private void runFaceDetectionTest(String imagePath) {
        TextView tvFaceBody = findViewById(R.id.tvFaceBody);
        tvFaceBody.setText(R.string.face_test_running);
        new Thread(() -> {
            String outcome;
            boolean successful;
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                byte[] bytes = readFile(imagePath);
                FaceRecognitionEngine.CaptureResult result = engine.createEmbedding(bytes);
                outcome = getString(R.string.face_test_embedding_ok,
                        result.embedding.length, result.qualityScore);
                successful = true;
            } catch (FaceRecognitionException ex) {
                outcome = getString(R.string.face_test_capture_failed, ex.getMessage());
                successful = false;
            } catch (IOException ex) {
                outcome = getString(R.string.face_test_capture_failed, ex.getMessage());
                successful = false;
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
            String finalOutcome = outcome;
            boolean finalSuccessful = successful;
            runOnUiThread(() -> {
                tvFaceBody.setText(finalOutcome);
                if (finalSuccessful) {
                    OutcomeFeedback.success(this, finalOutcome);
                } else {
                    OutcomeFeedback.error(this, finalOutcome);
                }
            });
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
        findViewById(R.id.btnTestScanner).setEnabled(false);
        new Thread(() -> {
            boolean available = BiometricDeviceFactory.create().isAvailable(this);
            runOnUiThread(() -> {
                tvScannerStatus.setText(available
                        ? R.string.settings_scanner_connected
                        : R.string.settings_scanner_disconnected);
                tvScannerStatus.setBackgroundResource(available
                        ? R.drawable.bg_status_success
                        : R.drawable.bg_status_warning);
                tvScannerStatus.setTextColor(ContextCompat.getColor(this,
                        available ? R.color.bp_success : R.color.bp_warning));
                findViewById(R.id.btnTestScanner).setEnabled(true);
            });
        }, "biopay-scanner-check").start();
    }

    private void setupDataAndStorage() {
        int households = new HouseholdDao(this).countAll();
        int pending = DatabaseHelper.get(this).countPendingSyncWork(new SessionManager(this).getPartnerCode());
        ((TextView) findViewById(R.id.tvStorageHouseholds)).setText(
                getString(R.string.settings_storage_households, households));
        ((TextView) findViewById(R.id.tvStoragePendingSync)).setText(
                getString(R.string.settings_storage_pending, pending));
    }

    /** One button that walks through check -> download -> install as its own label and click
     *  action change with the state -- see {@link #handleUpdateButtonClick}. There's no Play
     *  Store for this sideloaded app, so this (plus the passive nudge on Home) is the whole
     *  update mechanism; see AppUpdateManager's javadoc for how it talks to the backend. */
    private void setupUpdates() {
        btnCheckForUpdates.setOnClickListener(v -> handleUpdateButtonClick());
    }

    private void handleUpdateButtonClick() {
        if (pendingUpdateDownloadId != -1) {
            if (AppUpdateManager.isDownloadSuccessful(this, pendingUpdateDownloadId)) {
                offerInstall();
            } else {
                Snackbar.make(btnCheckForUpdates, R.string.settings_update_downloading, Snackbar.LENGTH_SHORT).show();
            }
        } else if (availableUpdate != null) {
            startDownload(availableUpdate);
        } else {
            checkForUpdates();
        }
    }

    private void checkForUpdates() {
        btnCheckForUpdates.setEnabled(false);
        AppUpdateManager.checkForUpdate(this, true, new AppUpdateManager.Callback() {
            @Override
            public void onUpdateAvailable(AppUpdateManager.UpdateInfo info) {
                availableUpdate = info;
                btnCheckForUpdates.setEnabled(true);
                btnCheckForUpdates.setText(R.string.settings_update_download);
                tvUpdateStatus.setVisibility(View.VISIBLE);
                tvUpdateStatus.setText(getString(R.string.settings_update_available, info.versionName));
            }

            @Override
            public void onUpToDate() {
                btnCheckForUpdates.setEnabled(true);
                OutcomeFeedback.success(SettingsActivity.this,
                        getString(R.string.settings_update_up_to_date, BuildConfig.VERSION_NAME));
            }

            @Override
            public void onError(String message) {
                btnCheckForUpdates.setEnabled(true);
                OutcomeFeedback.error(SettingsActivity.this, message);
            }
        });
    }

    private void startDownload(AppUpdateManager.UpdateInfo info) {
        try {
            pendingUpdateDownloadId = AppUpdateManager.enqueueDownload(this, info);
        } catch (IllegalStateException ex) {
            OutcomeFeedback.error(this, ex.getMessage());
            return;
        }
        btnCheckForUpdates.setText(R.string.settings_update_downloading);
    }

    /** Called by {@link #downloadCompleteReceiver} once DownloadManager reports the update's
     *  download id finished (success or failure). */
    private void onUpdateDownloadFinished() {
        if (!AppUpdateManager.isDownloadSuccessful(this, pendingUpdateDownloadId)) {
            pendingUpdateDownloadId = -1;
            btnCheckForUpdates.setText(R.string.settings_check_for_updates);
            OutcomeFeedback.error(this, getString(R.string.settings_update_download_failed));
            return;
        }
        btnCheckForUpdates.setText(R.string.settings_update_install);
        offerInstall();
    }

    private void offerInstall() {
        if (!AppUpdateManager.canInstallPackages(this)) {
            Snackbar.make(btnCheckForUpdates, R.string.settings_update_permission_needed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.settings_update_open_settings, v ->
                            startActivity(AppUpdateManager.installPermissionSettingsIntent(this)))
                    .show();
            return;
        }
        AppUpdateManager.promptInstall(this, AppUpdateManager.downloadDestination(this));
    }

    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:support@biopay.africa"));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_feedback_subject, BuildConfig.VERSION_NAME));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Snackbar.make(findViewById(android.R.id.content), R.string.settings_feedback_no_app, Snackbar.LENGTH_LONG).show();
        }
    }

}
