package com.alphabank.dca.households;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Service;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.GlobalDialogs;
import com.alphabank.dca.objects.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PersonalInfoActivity extends AppCompatActivity implements LocationListener {

    TextInputEditText tietHouseholdName, tietAge, tietIdNumber, tietPhonenumber, tietIncomeSource;
    Button btnPersonalInfo;
    RadioGroup rgGender;
    RadioButton rbMale, rbFemale;
    GlobalApplication globalApplication;
    SessionManager sessionManager;

    String lat, lon;
    private final static int ALL_PERMISSIONS_RESULT = 101;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 25;
    private static final long MIN_TIME_BW_UPDATES = 1000;
    LocationManager locationManager;
    Location loc;
    boolean isGPS = false;
    boolean isNetwork = false;
    boolean canGetLocation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_info);
        globalApplication = GlobalApplication.getInstance();
        sessionManager = new SessionManager(getApplicationContext());

        tietHouseholdName = findViewById(R.id.tietHouseholdName);
        tietAge = findViewById(R.id.tietAge);
        tietIdNumber = findViewById(R.id.tietIdNumber);
        tietPhonenumber = findViewById(R.id.tietPhonenumber);
        tietIncomeSource = findViewById(R.id.tietIncomeSource);
        rgGender = findViewById(R.id.rgGender);
        rbMale = findViewById(R.id.rbMale);
        rbFemale = findViewById(R.id.rbFemale);
        if (globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber()) == false) {
            populateInfo();
        } else {
            //showConsentLetter();
        }
        gpsImplementation();
        btnPersonalInfo = findViewById(R.id.btnPersonalInfo);
        btnPersonalInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateInformation();
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
            GlobalDialogs.success(PersonalInfoActivity.this, "GPS not Enabled",
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
        startActivity(new Intent(PersonalInfoActivity.this, LocationActivity.class));
    }

    private void populateInfo() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {

                    String hName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME));
                    String hNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                    String hAge = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE));
                    String hIncomeSource = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD));
                    String hAvIncome = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME));
                    String hPhone = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER));
                    String hIdNo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER));
                    String hSpouse = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME));

                    String hLegality = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS));
                    String hMaritalStatus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS));
                    String hCriteria = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA));
                    String hReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON));
                    String hGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER));


                    tietHouseholdName.setText(hName);
                    tietAge.setText(hAge);
                    tietPhonenumber.setText(hPhone);
                    tietIdNumber.setText(hIdNo);
                    tietIncomeSource.setText(hIncomeSource);

                    if (hGender.trim().equals("Male")) {
                        rbFemale.setChecked(false);
                        rbMale.setChecked(true);
                    } else {
                        rbMale.setChecked(false);
                        rbFemale.setChecked(true);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void validateInformation() {
        if (globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            //Insert New Data
            if (validateInputFields()) {
                    store();
            }

        } else {
            //Update displayed data
            if (validateInputFields()) {
                    update();
            }
        }
    }


    private boolean validateInputFields() {
        boolean check = true;
        if (!globalApplication.hasText(tietHouseholdName, "Enter Household Name")) check = false;
        if (!globalApplication.hasText(tietAge, "Enter Age")) check = false;
        if (!globalApplication.hasText(tietIdNumber, "Enter Id Number")) check = false;
        if (!globalApplication.hasText(tietPhonenumber, "Enter Phonenumber")) check = false;
        if (!globalApplication.hasText(tietIncomeSource, "Enter Start of Work")) check = false;
        if (!globalApplication.hasRequiredLength(tietHouseholdName, "Enter a minimum of three letters and words required!"))
            check = false;
        if (!globalApplication.ageCheck(tietAge, "Enter Age!"))
            check = false;
        if (!globalApplication.checkPhonenumber(tietPhonenumber, "Enter the a 10 digit phonenumber correctly"))
            check = false;
        return check;
    }

    private void store() {
        //setups
        globalApplication.setHouseholdNumber(UUID.randomUUID().toString().trim());
        String householdName = tietHouseholdName.getText().toString().trim();
        if (householdName.length() < 3) {
            GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                    "Household name is not fully registered. Kindly write three names",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else {

            globalApplication.setHouseholdName(householdName);
            int rgId = rgGender.getCheckedRadioButtonId();
            String gender = "", spouseName = "";
            if (rgId == rbMale.getId()) {
                gender = "Male";
            } else {
                gender = "Female";
            }


                if (globalApplication.isNullOrEmpty(globalApplication.getBomaCode()) == false) {
                    //inserts
                    ContentValues value = new ContentValues();
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, globalApplication.getHouseholdName());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, tietIdNumber.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, tietPhonenumber.getText().toString().trim());

                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, tietAge.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, gender);
                      value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, spouseName);
                      value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD, tietIncomeSource.getText().toString().trim());
                     value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATE_CODE, globalApplication.getStateCode());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE, globalApplication.getCountyCode());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PAYAM_CODE, globalApplication.getPayamCode());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BOMA_CODE, globalApplication.getBomaCode());

                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LONGITUDE, lon);
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LATITUDE, lat);
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, "0");
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                    Uri uri = getContentResolver().insert(BiometricsContract.HouseholdEntry.CONTENT_URI, value);

                    GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                            "Personal Information has been saved successfully. Move to the NEXT page",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(PersonalInfoActivity.this, PhotoActivity.class));
                                }
                            });
                } else {
                    GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                            "Location has not been set. Kindly set the location",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(PersonalInfoActivity.this, LocationActivity.class));
                                }
                            });
                }

        }
    }

    private void update() {
        //setups

        String householdName = tietHouseholdName.getText().toString().trim();
        if (householdName.length() < 3) {
            GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                    "Household name is not fully registered. Kindly write three names",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else {
            globalApplication.setHouseholdName(householdName);
            int rgId = rgGender.getCheckedRadioButtonId();
            String gender = "", spouseName = "";
            if (rgId == rbMale.getId()) {
                gender = "Male";
            } else {
                gender = "Female";
            }


                //inserts
                ContentValues value = new ContentValues();
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, globalApplication.getHouseholdName());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, tietIdNumber.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, tietPhonenumber.getText().toString().trim());


                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, tietAge.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, gender);
                 value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, spouseName);
                  value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD, tietIncomeSource.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, "0");
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                String whereUpdateHouseholdArgs[] = {globalApplication.getHouseholdNumber()};

                getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
                GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                        "Personal Information has been updated successfully. Move to the NEXT page",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(PersonalInfoActivity.this, PhotoActivity.class));
                            }
                        });
            }

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
                    Toast.makeText(PersonalInfoActivity.this, "Network not available to update location", Toast.LENGTH_SHORT).show();
                    if (loc != null)
                        updateUI(loc);
                }
            } else {
                Log.d("TAG", "Can't get location");
                Toast.makeText(PersonalInfoActivity.this, "Location not found. Kindly move 2 steps to capture GPS", Toast.LENGTH_SHORT).show();

            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void updateUI(Location loc) {

        lat = Double.toString(loc.getLatitude());
        lon = Double.toString(loc.getLongitude());

        Toast.makeText(this, "lat: " + lat + "\n long:" + lon, Toast.LENGTH_SHORT).show();
    }
}