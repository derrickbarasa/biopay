package com.biopay.agent.attendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.SearchView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.households.HouseholdListAdapter;

/** Searchable household picker -- the entry point into attendance clock-in/out, structurally
 * identical to {@link com.biopay.agent.households.HouseholdListActivity} but routing the tap to
 * the beneficiary/verify screen instead of the household edit form. */
public class AttendanceActivity extends AppCompatActivity {

    private HouseholdDao householdDao;
    private HouseholdListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

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
        adapter.submitList(householdDao.search(query));
    }
}
