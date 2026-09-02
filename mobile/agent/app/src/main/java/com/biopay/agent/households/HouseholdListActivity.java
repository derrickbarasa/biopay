package com.biopay.agent.households;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Searchable, filterable list of locally-registered households. */
public class HouseholdListActivity extends BaseActivity {

    private HouseholdDao householdDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private PaymentDao paymentDao;
    private HouseholdListAdapter adapter;
    private String currentQuery;
    private int checkedFilterId = R.id.chipAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_list);
        setupMainNavigation(R.id.bottomNavigation, R.id.navHouseholds);

        householdDao = new HouseholdDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        paymentDao = new PaymentDao(this);
        adapter = new HouseholdListAdapter(household ->
                startActivity(HouseholdDetailActivity.intent(this, household.householdNumber)));

        RecyclerView recyclerView = findViewById(R.id.recyclerHouseholds);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                loadHouseholds();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                loadHouseholds();
                return true;
            }
        });

        ((ChipGroup) findViewById(R.id.chipGroupFilter)).setOnCheckedStateChangeListener((group, checkedIds) -> {
            checkedFilterId = checkedIds.isEmpty() ? R.id.chipAll : checkedIds.get(0);
            loadHouseholds();
        });

        findViewById(R.id.btnAddHousehold).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHouseholds();
    }

    private void loadHouseholds() {
        List<HouseholdDao.Household> matches = householdDao.search(currentQuery);
        List<HouseholdDao.Household> filtered = applyStatusFilter(matches);
        adapter.submitList(filtered);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.household_list_summary, filtered.size()));
        findViewById(R.id.emptyState).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerHouseholds).setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<HouseholdDao.Household> applyStatusFilter(List<HouseholdDao.Household> households) {
        if (checkedFilterId == R.id.chipAll) {
            return households;
        }
        List<HouseholdDao.Household> filtered = new ArrayList<>();
        for (HouseholdDao.Household household : households) {
            if (checkedFilterId == R.id.chipPendingSync) {
                if (household.syncStatus != DatabaseHelper.SYNC_SYNCED) {
                    filtered.add(household);
                }
                continue;
            }
            HouseholdStatus status = HouseholdStatus.compute(household, fingerprintDao, faceDao, paymentDao);
            if ((checkedFilterId == R.id.chipIncomplete && status == HouseholdStatus.INCOMPLETE)
                    || (checkedFilterId == R.id.chipReady && status == HouseholdStatus.READY)
                    || (checkedFilterId == R.id.chipPaid && status == HouseholdStatus.PAID)) {
                filtered.add(household);
            }
        }
        return filtered;
    }
}
