package com.biopay.agent.alternates;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.households.HouseholdListAdapter;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.SearchViewHelper;

import java.util.List;

/** Entry point for the alternates feature: pick a household first, then
 *  {@link HouseholdAlternatesActivity} shows and manages that household's alternates. Alternates
 *  only ever belong to one household, so browsing them without that context (the old flat,
 *  read-only list this screen used to show) gave no way to add or edit one. */
public class AlternatesActivity extends BaseActivity {

    private HouseholdDao householdDao;
    private HouseholdListAdapter adapter;
    private String currentQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alternates);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        adapter = new HouseholdListAdapter(household -> startActivity(
                HouseholdAlternatesActivity.intent(this, household.householdNumber, household.householdName)));
        RecyclerView recyclerView = findViewById(R.id.recyclerHouseholds);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        SearchViewHelper.makeFullyClickable(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { currentQuery = query; load(); return true; }
            @Override public boolean onQueryTextChange(String query) { currentQuery = query; load(); return true; }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        List<HouseholdDao.Household> households = householdDao.search(currentQuery);
        adapter.submitList(households);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.alternate_summary, households.size()));
        findViewById(R.id.emptyState).setVisibility(households.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerHouseholds).setVisibility(households.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
