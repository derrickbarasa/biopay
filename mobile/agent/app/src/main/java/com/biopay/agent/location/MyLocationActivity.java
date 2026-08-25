package com.biopay.agent.location;

import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.Date;

/** Offline "sitemap" view of the officer's last known GPS fix -- see {@link SiteMapView}. */
public class MyLocationActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_location);
        setupBackToolbar(R.id.toolbar);
        findViewById(R.id.btnRefreshLocation).setOnClickListener(v -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        SiteMapView map = findViewById(R.id.siteMapView);
        TextView tvCoordinates = findViewById(R.id.tvCoordinates);
        TextView tvAccuracy = findViewById(R.id.tvAccuracy);
        TextView tvTimestamp = findViewById(R.id.tvTimestamp);

        Location location = LocationHelper.getLastKnownLocation(this);
        if (location == null) {
            map.setFix(false, 0f);
            tvCoordinates.setText(R.string.home_location_unavailable);
            tvAccuracy.setText("");
            tvTimestamp.setText("");
            Snackbar.make(map, R.string.home_location_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }

        map.setFix(true, location.getAccuracy());
        tvCoordinates.setText(getString(R.string.my_location_coordinates, location.getLatitude(), location.getLongitude()));
        tvAccuracy.setText(location.hasAccuracy()
                ? getString(R.string.my_location_accuracy, location.getAccuracy())
                : getString(R.string.my_location_accuracy_unknown));
        tvTimestamp.setText(getString(R.string.my_location_timestamp,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(location.getTime()))));
    }
}
