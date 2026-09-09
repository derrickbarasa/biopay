package com.biopay.agent.attendance;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.households.HouseholdListAdapter;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.SearchViewHelper;

import java.util.List;

/** Searchable household picker -- the entry point into attendance clock-in/out, structurally
 * identical to {@link com.biopay.agent.households.HouseholdListActivity} but routing the tap to
 * the beneficiary/verify screen instead of the household edit form. */
public class AttendanceActivity extends BaseActivity {

    private HouseholdDao householdDao;
    private HouseholdListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        adapter = new HouseholdListAdapter(household ->
                startActivity(AttendanceBeneficiariesActivity.intentFor(this, household.householdNumber)));

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        RecyclerView recyclerView = findViewById(R.id.recyclerHouseholds);
        recyclerView.setLayoutManager(isLandscape
                ? new GridLayoutManager(this, 2)
                : new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (isLandscape) {
            findViewById(R.id.tvScreenTitle).setVisibility(View.GONE);
            findViewById(R.id.tvScreenDescription).setVisibility(View.GONE);
        }

        SearchView searchView = findViewById(R.id.searchView);
        SearchViewHelper.makeFullyClickable(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                loadHouseholds(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                loadHouseholds(newText);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHouseholds(null);
    }

    private void loadHouseholds(String query) {
        List<HouseholdDao.Household> households = householdDao.search(query);
        adapter.submitList(households);
        findViewById(R.id.emptyState).setVisibility(households.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerHouseholds).setVisibility(households.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
