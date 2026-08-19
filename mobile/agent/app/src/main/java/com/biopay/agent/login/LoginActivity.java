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
    private EditText etServerUrl;
    private TextView tvErrorMessage;
    private ProgressBar progressBar;
    private Button btnLogin;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etServerUrl = findViewById(R.id.etServerUrl);
        etServerUrl.setText(ApiClient.getBaseUrl(this));
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
        try {
            ApiClient.setBaseUrl(this, etServerUrl.getText().toString());
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
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
                sessionManager.saveSession(
                        response.optString("accessToken"),
                        response.optString("refreshToken"),
                        user != null ? user.optInt("id") : -1,
                        user != null ? user.optString("email") : email,
                        user != null ? user.optString("firstName") : "",
                        user != null ? user.optString("lastName") : "",
                        anchorId,
                        user != null ? user.optString("partnerCode", null) : null);

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
                });
            }

            @Override
            public void onError(String message, String responseCode) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        etServerUrl.setEnabled(!loading);
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
