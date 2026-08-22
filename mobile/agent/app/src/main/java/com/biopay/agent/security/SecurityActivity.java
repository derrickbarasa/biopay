package com.biopay.agent.security;

import android.os.Bundle;
import android.widget.EditText;

import com.biopay.agent.R;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SecurityActivity extends BaseActivity {
    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;
    private MaterialButton changePasswordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);
        setupBackToolbar(R.id.toolbar);

        currentPassword = findViewById(R.id.etCurrentPassword);
        newPassword = findViewById(R.id.etNewPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        changePasswordButton = findViewById(R.id.btnChangePassword);
        changePasswordButton.setOnClickListener(view -> changePassword());
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
        changePasswordButton.setEnabled(false);
        ApiClient.get(this).dispatch("CHANGE_PASSWORD", params, new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                changePasswordButton.setEnabled(true);
                currentPassword.setText("");
                newPassword.setText("");
                confirmPassword.setText("");
                Snackbar.make(changePasswordButton, R.string.settings_password_changed, Snackbar.LENGTH_SHORT).show();
            }

            @Override public void onError(String message, String responseCode) {
                changePasswordButton.setEnabled(true);
                Snackbar.make(changePasswordButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
