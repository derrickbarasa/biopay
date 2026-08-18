package com.biopay.agent.alternates;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;

/** Searchable, offline list of alternate beneficiaries assigned to households. */
public class AlternatesActivity extends BaseActivity {

    private AlternateDao alternateDao;
    private AlternateListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alternates);
        setupBackToolbar(R.id.toolbar);

        alternateDao = new AlternateDao(this);
        adapter = new AlternateListAdapter();
        RecyclerView recyclerView = findViewById(R.id.recyclerAlternates);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { load(query); return true; }
            @Override public boolean onQueryTextChange(String query) { load(query); return true; }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(null);
    }

    private void load(String query) {
        List<AlternateDao.Alternate> alternates = alternateDao.search(query);
        adapter.submitList(alternates);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.alternate_summary, alternates.size()));
        findViewById(R.id.emptyState).setVisibility(alternates.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerAlternates).setVisibility(alternates.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
