package com.biopay.agent.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.security.SecurityActivity;
import com.biopay.agent.session.OfflineAccessManager;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.session.SubscriptionGate;
import com.biopay.agent.session.SubscriptionLockedActivity;
import com.biopay.agent.ui.BaseActivity;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** LOGIN_SUPERVISOR against the BioPay backend -- the app's only login path (field officers only). */
public class LoginActivity extends BaseActivity {

    private EditText etEmail;
    private EditText etPassword;
    private TextView tvErrorMessage;
    private ProgressBar progressBar;
    private Button btnLogin;
    private SessionManager sessionManager;
    private OfflineAccessManager offlineAccessManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        offlineAccessManager = new OfflineAccessManager(this);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        progressBar = findViewById(R.id.progressBar);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        etPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        hideKeyboard();
        tvErrorMessage.setVisibility(View.GONE);
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_required));
            return;
        }
        setLoading(true);

        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("password", password);

        ApiClient.get(this).dispatch("LOGIN_SUPERVISOR", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject user = response.optJSONObject("user");
                Integer anchorId = user != null && !user.isNull("anchorId") ? user.optInt("anchorId") : null;
                int offlineAccessDays = response.optInt("offlineAccessDays",
                        com.biopay.agent.BuildConfig.BIOPAY_OFFLINE_ACCESS_DAYS);
                sessionManager.saveSession(
                        response.optString("accessToken"),
                        response.optString("refreshToken"),
                        user != null ? user.optInt("id") : -1,
                        user != null ? user.optString("email") : email,
                        user != null ? user.optString("firstName") : "",
                        user != null ? user.optString("lastName") : "",
                        anchorId,
                        user != null ? user.optString("partnerCode", null) : null,
                        user != null ? user.optString("verificationMethod", "BIOMETRIC") : "BIOMETRIC");

                // A first-login, system-generated password (mirrors the web dashboard's
                // must-change-password router guard) has to be replaced before anything
                // else in the app is reachable -- checked ahead of the subscription gate,
                // which SecurityActivity runs itself once the password is actually changed.
                if (user != null && user.optBoolean("mustChangePassword", false)) {
                    setLoading(false);
                    startActivity(SecurityActivity.newForcedIntent(LoginActivity.this, anchorId));
                    finish();
                    return;
                }

                offlineAccessManager.cacheAuthenticatedIdentity(
                        password,
                        user != null ? user.optInt("id") : -1,
                        user != null ? user.optString("email") : email,
                        user != null ? user.optString("firstName") : "",
                        user != null ? user.optString("lastName") : "",
                        anchorId,
                        user != null ? user.optString("partnerCode", null) : null,
                        user != null ? user.optString("verificationMethod", "BIOMETRIC") : "BIOMETRIC",
                        offlineAccessDays);

                // Mirrors the web dashboard's archived-subscription gate -- see
                // SubscriptionGate's javadoc for why this only needs checking here.
                SubscriptionGate.check(LoginActivity.this, anchorId, new SubscriptionGate.Callback() {
                    @Override
                    public void onAllowed() {
                        setLoading(false);
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
                    }

                    @Override
                    public void onLocked() {
                        setLoading(false);
                        startActivity(new Intent(LoginActivity.this, SubscriptionLockedActivity.class));
                        finish();
                    }

                    @Override
                    public void onOnlineRequired() {
                        sessionManager.clear();
                        setLoading(false);
                        showError(getString(R.string.login_online_required,
                                offlineAccessManager.getOnlineRevalidationDays()));
                    }
                });
            }

            @Override
            public void onError(String message, String responseCode) {
                setLoading(false);
                // Only transport failures may fall back to the local verifier. A server-side
                // rejection (wrong password, inactive account, etc.) is always authoritative.
                if (responseCode == null) {
                    attemptOfflineLogin(email, password);
                } else {
                    showError(message);
                }
            }
        });
    }

    private void attemptOfflineLogin(String email, String password) {
        OfflineAccessManager.Decision decision =
                offlineAccessManager.evaluateOfflineLogin(email, password);
        if (decision == OfflineAccessManager.Decision.INVALID_CREDENTIALS) {
            showError(getString(R.string.login_offline_credentials_unavailable));
            return;
        }
        if (decision == OfflineAccessManager.Decision.ONLINE_REQUIRED) {
            showError(getString(R.string.login_online_required,
                    offlineAccessManager.getOnlineRevalidationDays()));
            return;
        }

        OfflineAccessManager.CachedProfile profile = offlineAccessManager.getCachedProfile();
        if (profile == null) {
            showError(getString(R.string.login_offline_credentials_unavailable));
            return;
        }
        sessionManager.saveOfflineSession(profile);
        Class<?> destination = decision == OfflineAccessManager.Decision.LOCKED
                ? SubscriptionLockedActivity.class : HomeActivity.class;
        startActivity(new Intent(this, destination));
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? R.string.login_signing_in : R.string.login_button);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void hideKeyboard() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            return;
        }
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        focusedView.clearFocus();
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
