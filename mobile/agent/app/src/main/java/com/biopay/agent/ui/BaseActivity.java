package com.biopay.agent.ui;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.households.HouseholdListActivity;
import com.biopay.agent.more.MoreActivity;
import com.biopay.agent.network.NetworkStatus;
import com.biopay.agent.payments.PaymentsActivity;
import com.biopay.agent.vouchers.VoucherRedemptionActivity;

import android.content.Intent;

/** Shared window and navigation behavior for BioPay task screens. */
public abstract class BaseActivity extends AppCompatActivity {

    @Nullable
    private BottomNavigationView mainNavigation;
    private int mainNavigationSelectedItemId = View.NO_ID;

    // Connectivity is only ever surfaced as a change, never on every screen open -- null means
    // "haven't heard from NetworkStatus yet on this activity instance," which suppresses the
    // very first callback (the officer's current state, not a transition) so opening a screen
    // while already offline doesn't itself pop a Snackbar.
    @Nullable
    private Boolean lastKnownOnline;
    @Nullable
    private ConnectivityManager.NetworkCallback connectivityCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        lastKnownOnline = null;
        connectivityCallback = NetworkStatus.observe(this, this::onConnectivityChanged);
    }

    @Override
    protected void onStop() {
        super.onStop();
        NetworkStatus.stopObserving(this, connectivityCallback);
        connectivityCallback = null;
    }

    private void onConnectivityChanged(boolean online) {
        Boolean previous = lastKnownOnline;
        lastKnownOnline = online;
        if (previous == null || previous == online) {
            return;
        }
        runOnUiThread(() -> {
            View root = findViewById(android.R.id.content);
            if (root == null || isFinishing() || isDestroyed()) {
                return;
            }
            Snackbar.make(root,
                            online ? R.string.connectivity_back_online : R.string.connectivity_offline,
                            Snackbar.LENGTH_LONG)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncMainNavigationSelection();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResId) {
        super.setContentView(layoutResId);
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    protected void setupBackToolbar(int toolbarId) {
        MaterialToolbar toolbar = findViewById(toolbarId);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
    }

    protected void setupMainNavigation(int navigationId, int selectedItemId) {
        BottomNavigationView navigation = findViewById(navigationId);
        mainNavigation = navigation;
        mainNavigationSelectedItemId = selectedItemId;
        navigation.setOnItemSelectedListener(item -> {
            int targetId = item.getItemId();
            if (targetId == selectedItemId) {
                return true;
            }
            Class<?> destination;
            if (targetId == R.id.navPayment) {
                destination = PaymentsActivity.class;
            } else if (targetId == R.id.navHouseholds) {
                destination = HouseholdListActivity.class;
            } else if (targetId == R.id.navVouchers) {
                destination = VoucherRedemptionActivity.class;
            } else if (targetId == R.id.navMore) {
                destination = MoreActivity.class;
            } else {
                destination = HomeActivity.class;
            }
            Intent intent = new Intent(this, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            // The destination activity owns the new selected state. Keeping this screen's
            // current item selected prevents a stale highlight when the user navigates back.
            return false;
        });
        syncMainNavigationSelection();
    }

    private void syncMainNavigationSelection() {
        if (mainNavigation != null
                && mainNavigationSelectedItemId != View.NO_ID
                && mainNavigation.getSelectedItemId() != mainNavigationSelectedItemId) {
            mainNavigation.setSelectedItemId(mainNavigationSelectedItemId);
        }
    }
}
