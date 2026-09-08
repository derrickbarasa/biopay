package com.biopay.agent.session;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;

import com.biopay.agent.R;
import com.biopay.agent.login.LoginActivity;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.snackbar.Snackbar;

/**
 * Blocking screen shown when {@link SubscriptionGate} finds the signed-in officer's
 * anchor subscription ARCHIVED (grace period exhausted) -- the mobile mirror of the
 * web dashboard's archived gate. Reached only right after login (see
 * {@link com.biopay.agent.login.LoginActivity}); the officer can retry (in case the
 * anchor just renewed) or log out. A lock restored from an offline login has no bearer token,
 * so retry sends the officer through online login before checking again.
 */
public class SubscriptionLockedActivity extends BaseActivity {

    private ProgressBar progressBar;
    private Button btnRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription_locked);

        progressBar = findViewById(R.id.progressBar);
        btnRetry = findViewById(R.id.btnRetry);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnRetry.setOnClickListener(v -> retry());
        btnLogout.setOnClickListener(v -> logout());

        // No "back" out of a locked session -- log out is the only exit besides retrying.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                logout();
            }
        });
    }

    private void retry() {
        SessionManager sessionManager = new SessionManager(this);
        if (!sessionManager.hasOnlineSession()) {
            sessionManager.clear();
            Intent intent = new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        setChecking(true);
        SubscriptionGate.check(this, sessionManager.getAnchorId(), new SubscriptionGate.Callback() {
            @Override
            public void onAllowed() {
                setChecking(false);
                startActivity(new Intent(SubscriptionLockedActivity.this, com.biopay.agent.home.HomeActivity.class));
                finish();
            }

            @Override
            public void onLocked() {
                setChecking(false);
                // Still archived -- stay on this screen.
            }

            @Override
            public void onOnlineRequired() {
                setChecking(false);
                Snackbar.make(btnRetry, R.string.subscription_locked_online_required,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void logout() {
        new SessionManager(this).clear();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setChecking(boolean checking) {
        progressBar.setVisibility(checking ? View.VISIBLE : View.GONE);
        btnRetry.setEnabled(!checking);
    }
}
