package com.biopay.agent.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.households.HouseholdListAdapter;
import com.biopay.agent.ui.BaseActivity;

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

        RecyclerView recyclerView = findViewById(R.id.recyclerHouseholds);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
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
