package com.biopay.agent.households;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;

/** Searchable list of locally-registered households (dca's HouseholdActivity, which nca lacked). */
public class HouseholdListActivity extends BaseActivity {

    private HouseholdDao householdDao;
    private HouseholdListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_list);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        adapter = new HouseholdListAdapter(household ->
                startActivity(HouseholdFormActivity.editIntent(this, household.householdNumber)));

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

        findViewById(R.id.btnAddHousehold).setOnClickListener(v ->
                startActivity(new Intent(this, HouseholdFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHouseholds(null);
    }

    private void loadHouseholds(String query) {
        List<HouseholdDao.Household> households = householdDao.search(query);
        adapter.submitList(households);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.household_list_summary, households.size()));
        findViewById(R.id.emptyState).setVisibility(households.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerHouseholds).setVisibility(households.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
