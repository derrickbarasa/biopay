package com.flexmoney.nca.payments;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.flexmoney.nca.R;
import com.flexmoney.nca.home.MainActivity;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.google.android.material.textfield.TextInputEditText;

public class PaymentPreSetupActivity extends AppCompatActivity implements LocationListener {

    TextInputEditText tietPaymentCentre;
    TextView tvLatitude, tvLongitude;
    Button btnPaymentCentre;
    GlobalApplication globalApplication;

    String lat, lon;
    private final static int ALL_PERMISSIONS_RESULT = 101;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 25;
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 10;
    LocationManager locationManager;
    Location loc;
    boolean isGPS = false;
    boolean isNetwork = false;
    boolean canGetLocation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_pre_setup);

        globalApplication = GlobalApplication.getInstance();

        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        tietPaymentCentre = (TextInputEditText) findViewById(R.id.tietPaymentCentre);
        btnPaymentCentre = findViewById(R.id.btnPaymentCentre);

        gpsImplementation();
        btnPaymentCentre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (globalApplication.hasText(tietPaymentCentre, "Enter Payment Centre")) {
                    final String paymentCentre = tietPaymentCentre.getText().toString().trim();
                    lat="0";lon="0";
                    if (globalApplication.isNullOrEmpty(lat)) {
                        Toast.makeText(PaymentPreSetupActivity.this, "Please move to an open space or two steps from current position", Toast.LENGTH_SHORT).show();
                        GlobalDialogs.error(PaymentPreSetupActivity.this, "Payment Pre-setup",
                                "Please move to an open space or two steps from current position",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent i = getIntent();
                                        finish();
                                        startActivity(i);
                                    }
                                });
                    } else {
                        GlobalDialogs.warning(PaymentPreSetupActivity.this, "Payment Pre-setup",
                                "Payment Set up has been saved successfully. You can now pay all beneficiaries in that payment centre",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        globalApplication.setPaymentCentre(paymentCentre);
                                        globalApplication.setLatitude(lat);
                                        globalApplication.setLongitude(lon);
                                        startActivity(new Intent(PaymentPreSetupActivity.this, PaymentHouseholdsActivity.class));
                                    }
                                }
                        );
                    }

                }

            }
        });
    }
    private void gpsImplementation() {
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        isGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {

            return;
        }


        if (!isGPS && !isNetwork) {
            GlobalDialogs.success(PaymentPreSetupActivity.this, "GPS not Enabled",
                    "GPS is not enabled, please enable", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            getLastLocation();
        } else {

            getLocation();
        }

    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(PaymentPreSetupActivity.this, MainActivity.class));
    }
    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void getLastLocation() {
        try {
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, false);
            if (provider != null) {
                Location location = locationManager.getLastKnownLocation(provider);

            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void getLocation() {
        try {
            if (canGetLocation) {

                if (isGPS) {

                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (loc != null)
                            updateUI(loc);
                    }
                } else if (isNetwork) {

                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (loc != null)
                            updateUI(loc);
                    }
                } else {
                    loc.setLatitude(0);
                    loc.setLongitude(0);
                    updateUI(loc);
                }
            } else {
                Log.d("TAG", "Can't get location");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void updateUI(Location loc) {

        lat = Double.toString(loc.getLatitude());
        lon = Double.toString(loc.getLongitude());
        tvLatitude.setText(lat+"");
        tvLongitude.setText(lon+"");

        Toast.makeText(this, "lat: " + lat + "\n long:" + lon, Toast.LENGTH_SHORT).show();
    }
}