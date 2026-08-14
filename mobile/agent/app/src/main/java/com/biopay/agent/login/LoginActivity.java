package com.biopay.agent.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.session.SessionManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** LOGIN_SUPERVISOR against the BioPay backend -- the app's only login path (field officers only). */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
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
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        progressBar = findViewById(R.id.progressBar);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
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
                setLoading(false);
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

                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
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
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
