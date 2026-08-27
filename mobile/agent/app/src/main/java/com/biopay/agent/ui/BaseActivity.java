package com.biopay.agent.ui;

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
import com.biopay.agent.R;
import com.biopay.agent.feed.ActivityFeedActivity;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.households.HouseholdListActivity;
import com.biopay.agent.more.MoreActivity;
import com.biopay.agent.scan.ScanBottomSheetFragment;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.session.SessionTimeoutManager;

import android.content.Intent;

/** Shared window and navigation behavior for BioPay task screens. */
public abstract class BaseActivity extends AppCompatActivity {

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
    protected void onResume() {
        super.onResume();
        if (new SessionManager(this).isLoggedIn()) {
            SessionTimeoutManager.get().attach(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionTimeoutManager.get().detach(this);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (new SessionManager(this).isLoggedIn()) {
            SessionTimeoutManager.get().reset();
        }
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
        navigation.setSelectedItemId(selectedItemId);
        navigation.setOnItemSelectedListener(item -> {
            int targetId = item.getItemId();
            if (targetId == selectedItemId) {
                return true;
            }
            Class<?> destination;
            if (targetId == R.id.navHouseholds) {
                destination = HouseholdListActivity.class;
            } else if (targetId == R.id.navActivityFeed) {
                destination = ActivityFeedActivity.class;
            } else if (targetId == R.id.navMore) {
                destination = MoreActivity.class;
            } else {
                destination = HomeActivity.class;
            }
            Intent intent = new Intent(this, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        });
    }

    /** Wires the floating center action shared by every bottom-nav tab screen to the Scan sheet. */
    protected void setupScanFab(int fabId) {
        View fab = findViewById(fabId);
        if (fab == null) {
            return;
        }
        fab.setOnClickListener(v ->
                new ScanBottomSheetFragment().show(getSupportFragmentManager(), "scan"));
    }
}
