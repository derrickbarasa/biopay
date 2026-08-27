package com.biopay.agent.feed;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.ActivityDao;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Unified chronological feed (registrations/verifications/disbursements/sync), backed by real
 * local data via {@link ActivityDao}'s union across households/alternates/payments/vouchers plus
 * the local verification-events log. */
public class ActivityFeedActivity extends BaseActivity {

    private ActivityDao activityDao;
    private ActivityFeedAdapter adapter;
    private int checkedFilterId = R.id.chipActivityAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_feed);
        setupMainNavigation(R.id.bottomNavigation, R.id.navActivityFeed);
        setupScanFab(R.id.fabScan);

        activityDao = new ActivityDao(this);
        adapter = new ActivityFeedAdapter();
        RecyclerView recyclerView = findViewById(R.id.recyclerActivity);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        ((ChipGroup) findViewById(R.id.chipGroupActivityFilter)).setOnCheckedStateChangeListener((group, checkedIds) -> {
            checkedFilterId = checkedIds.isEmpty() ? R.id.chipActivityAll : checkedIds.get(0);
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        List<ActivityDao.Event> events = activityDao.listAll();
        List<ActivityDao.Event> filtered = new ArrayList<>();
        for (ActivityDao.Event event : events) {
            if (matchesFilter(event)) filtered.add(event);
        }
        adapter.submitList(filtered);
        findViewById(R.id.emptyState).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerActivity).setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean matchesFilter(ActivityDao.Event event) {
        if (checkedFilterId == R.id.chipActivityAll) return true;
        if (checkedFilterId == R.id.chipActivityRegistrations) return event.category == ActivityDao.Category.REGISTRATION;
        if (checkedFilterId == R.id.chipActivityVerification) return event.category == ActivityDao.Category.VERIFICATION;
        if (checkedFilterId == R.id.chipActivityDisbursements) return event.category == ActivityDao.Category.DISBURSEMENT;
        return true;
    }
}
