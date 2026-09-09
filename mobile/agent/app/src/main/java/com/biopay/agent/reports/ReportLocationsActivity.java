package com.biopay.agent.reports;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;

/** Full, virtualized location ranking opened from the compact Field reports summary. */
public class ReportLocationsActivity extends BaseActivity {
    private static final String EXTRA_RANGE = "range";

    static Intent intent(Context context, ReportDao.Range range) {
        Intent intent = new Intent(context, ReportLocationsActivity.class);
        intent.putExtra(EXTRA_RANGE, range.name());
        return intent;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_locations);
        setupBackToolbar(R.id.toolbar);

        ReportDao.Range range = selectedRange();
        ((TextView) findViewById(R.id.tvLocationRange)).setText(scopeText(range));
        List<ReportDao.LocationCount> locations = new ReportDao(this).loadAllLocations(range);

        RecyclerView list = findViewById(R.id.recyclerLocations);
        list.setLayoutManager(new LinearLayoutManager(this));
        ReportLocationAdapter adapter = new ReportLocationAdapter();
        list.setAdapter(adapter);
        adapter.submitList(locations);

        boolean empty = locations.isEmpty();
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        findViewById(R.id.tvLocationsEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private ReportDao.Range selectedRange() {
        try {
            return ReportDao.Range.valueOf(getIntent().getStringExtra(EXTRA_RANGE));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return ReportDao.Range.TODAY;
        }
    }

    private int scopeText(ReportDao.Range range) {
        if (range == ReportDao.Range.SEVEN_DAYS) return R.string.reports_range_scope_seven;
        if (range == ReportDao.Range.THIRTY_DAYS) return R.string.reports_range_scope_thirty;
        if (range == ReportDao.Range.ALL) return R.string.reports_range_scope_all;
        return R.string.reports_range_scope_today;
    }
}
