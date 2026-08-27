package com.biopay.agent.security;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.session.SubscriptionGate;
import com.biopay.agent.session.SubscriptionLockedActivity;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SecurityActivity extends BaseActivity {
    private static final String EXTRA_FORCED = "forced";
    private static final String EXTRA_ANCHOR_ID = "anchorId";

    /** A field officer's temporary first-login password must be changed before the app is
     *  usable -- mirrors the web dashboard's must-change-password router guard. The screen
     *  hides its back navigation and, on success, proceeds straight into the same
     *  {@link SubscriptionGate} check {@link com.biopay.agent.login.LoginActivity} would
     *  otherwise have run, rather than returning here. */
    public static Intent newForcedIntent(Context context, Integer anchorId) {
        Intent intent = new Intent(context, SecurityActivity.class);
        intent.putExtra(EXTRA_FORCED, true);
        if (anchorId != null) {
            intent.putExtra(EXTRA_ANCHOR_ID, anchorId);
        }
        return intent;
    }

    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;
    private MaterialButton changePasswordButton;
    private boolean forced;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        forced = getIntent().getBooleanExtra(EXTRA_FORCED, false);
        if (forced) {
            findViewById(R.id.toolbar).setVisibility(android.view.View.GONE);
            ((TextView) findViewById(R.id.tvScreenTitle)).setText(R.string.security_forced_title);
            ((TextView) findViewById(R.id.tvScreenSubtitle)).setText(R.string.security_forced_subtitle);
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() {
                    // No-op: this screen can't be dismissed until the password is changed.
                }
            });
        } else {
            setupBackToolbar(R.id.toolbar);
        }

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
                if (forced) {
                    Integer anchorId = getIntent().hasExtra(EXTRA_ANCHOR_ID)
                            ? getIntent().getIntExtra(EXTRA_ANCHOR_ID, -1) : null;
                    SubscriptionGate.check(SecurityActivity.this, anchorId, new SubscriptionGate.Callback() {
                        @Override public void onAllowed() {
                            startActivity(new Intent(SecurityActivity.this, HomeActivity.class));
                            finish();
                        }

                        @Override public void onLocked() {
                            startActivity(new Intent(SecurityActivity.this, SubscriptionLockedActivity.class));
                            finish();
                        }
                    });
                    return;
                }
                Snackbar.make(changePasswordButton, R.string.settings_password_changed, Snackbar.LENGTH_SHORT).show();
            }

            @Override public void onError(String message, String responseCode) {
                changePasswordButton.setEnabled(true);
                Snackbar.make(changePasswordButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
