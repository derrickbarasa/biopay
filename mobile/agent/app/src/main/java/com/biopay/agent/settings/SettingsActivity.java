package com.biopay.agent.settings;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import com.biopay.agent.BuildConfig;
import com.biopay.agent.R;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    private EditText serverUrl;
    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupMainNavigation(R.id.bottomNavigation, R.id.navSettings);

        serverUrl = findViewById(R.id.etSettingsServer);
        currentPassword = findViewById(R.id.etCurrentPassword);
        newPassword = findViewById(R.id.etNewPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        serverUrl.setText(ApiClient.getBaseUrl(this));

        findViewById(R.id.btnSaveServer).setOnClickListener(view -> saveServer());
        findViewById(R.id.btnTestConnection).setOnClickListener(view -> testConnection());
        findViewById(R.id.btnSyncNow).setOnClickListener(view -> {
            SyncScheduler.triggerNow(this);
            show(R.string.settings_sync_queued);
        });
        findViewById(R.id.btnChangePassword).setOnClickListener(view -> changePassword());

        ((TextView) findViewById(R.id.tvVersion)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        ((TextView) findViewById(R.id.tvScanner)).setText(getString(R.string.settings_scanner, BuildConfig.BIOMETRIC_DEVICE_LABEL));
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

    private void show(int stringId) {
        Snackbar.make(serverUrl, stringId, Snackbar.LENGTH_SHORT).show();
    }
}
