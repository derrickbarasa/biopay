package com.flexmoney.nca.households;

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
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

public class PersonalInfoActivity extends AppCompatActivity implements LocationListener {

    TextInputEditText tietHouseholdName, tietAge, tietIdNumber, tietPhonenumber,
            tietAverageIncome, tietSpouseName, tietOtherIncomeSource;
    TextInputLayout tilSpouseName, tilOtherIncomeSource;
    Button btnPersonalInfo;
    AutoCompleteTextView actvLiteracy,actvLegalStatus, actvMaritalStatus, actvSelectionCriteria, actvIncomeSource, actvHouseholdHeadRelationship, actvGender;
    MultiAutoCompleteTextView mactvSelectionReason;

    GlobalApplication globalApplication;
    SessionManager sessionManager;
    ArrayAdapter<String> adapterLiteracy,adapterLegalStatus, adapterMaritalStatus, adapterSelectionCriteria, adapterSelectionReason,
            adapterHouseholdHeadRelationship, adapterIncomeSource, adapterGender;

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
        tietAverageIncome = findViewById(R.id.tietAverageIncome);
        tietSpouseName = findViewById(R.id.tietSpouseName);
        tietSpouseName.setVisibility(View.INVISIBLE);
        tietOtherIncomeSource = findViewById(R.id.tietOtherIncomeSource);
        tietOtherIncomeSource.setVisibility(View.INVISIBLE);

        tilSpouseName = findViewById(R.id.tilSpouseName);
        tilSpouseName.setVisibility(View.INVISIBLE);
        tilOtherIncomeSource = findViewById(R.id.tilOtherIncomeSource);
        tilOtherIncomeSource.setVisibility(View.INVISIBLE);

        actvLiteracy = findViewById(R.id.actvLiteracy);
        actvLegalStatus = findViewById(R.id.actvLegalStatus);
        actvMaritalStatus = findViewById(R.id.actvMaritalStatus);
        actvSelectionCriteria = findViewById(R.id.actvSelectionCriteria);
        actvIncomeSource = findViewById(R.id.actvIncomeSource);
        actvHouseholdHeadRelationship = findViewById(R.id.actvHouseholdHeadRelationship);
        actvGender = findViewById(R.id.actvGender);

        mactvSelectionReason = findViewById(R.id.mactvSelectionReason);


        setUpAdapters();
        if (globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber()) == false) {
            populateInfo();
        } else {
            showConsentLetter();
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

    private void showConsentLetter() {

        AlertDialog.Builder builder = new AlertDialog.Builder(PersonalInfoActivity.this);
        builder.setTitle("CONSENT FOR USE OF PERSONAL DATA BY UNOPS IN SNSOP");
        builder.setMessage(
                "UNOPS is implementing the SNSOP on behalf of the Government of\n" +
                        "South Sudan. The objective of the project is to respond to the threat posed by the locust outbreak and\n" +
                        "to strengthen systems for preparedness. To facilitate the project implementation and achieve the\n" +
                        "project objectives, the project will collect data including personal data for assistance, and monitoring\n" +
                        "and evaluation.\n" +
                        "In order to fulfill these purposes, UNOPS will process your personal data including Name, Gender,\n" +
                        "Marital status, Residential Address, Age, Biometrics (fingerprints) and passport size photo.\n\n" +
                        "Purpose of Collection\n" +
                        "UNOPS may share your personal data with its partners, humanitarian organizations, private sector\n" +
                        "entities and the Government for the following purposes\n" +
                        "●Eligibility to be included in the program;\n" +
                        "●Payment of wages;\n" +
                        "●Monitoring and evaluation of programs;\n" +
                        "●Other purposes (please specify)\n" +
                        "In that regard, do you grant permission to UNOPS and any authorized person or entity acting on behalf\n" +
                        "of UNOPS to process your data, and OR share it with other partners?\n\n" +
                        "YES……………………………………………………………………NO…………………………………………………………………………………\n" +
                        "\n" +
                        "Data Protection\n" +
                        "Your data will be kept confidential in conformity with UNOPS data policy which binds any third party\n" +
                        "UNOPS shares your personal data with to keep your information safe and confidential. UNOPS will seek\n" +
                        "to have contractual arrangements in place that require the third party to adhere to relevant\n" +
                        "international data protection best practices.\n" +
                        "UNOPS stores the information you provide about yourself using appropriate systems (MIS and Biometric\n" +
                        "systems). UNOPS reviews information security measures regularly to mitigate risks, including accidental\n" +
                        "or unlawful destruction, loss, alteration, unauthorized disclosure of, or access to your personal data.\n" +
                        "In case you are not willing to give consent, UNOPS will not be able to render assistance to your\n" +
                        "household..\n" +
                        "If you have any concerns about the safety of your data, please contact UNOPS community mobilization\n" +
                        "clerk or call the hotline (XXXX).\n\n" +
                        "Declaration\n" +
                        "I ...............................................................give UNOPS unfettered access to my personal data (name,address, biometrics etc).\n");


        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(PersonalInfoActivity.this, LocationActivity.class));
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
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
                    String hHeadRelationship = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_HEAD_RELATIONSHIP));
                    String hNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                    String hAge = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE));
                    String hIncomeSource = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE));
                    String hOtherIncomeSource = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE));
                    String hAvIncome = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME));
                    String hPhone = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER));
                    String hIdNo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER));
                    String hSpouse = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME));

                    String hLegality = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS));
                    String hMaritalStatus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS));
                    String hCriteria = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA));
                    String hReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON));
                    String hGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER));
                    String literacy = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_LITERACY));


                    tietHouseholdName.setText(hName);
                    actvHouseholdHeadRelationship.setText(hHeadRelationship, false);
                    tietAge.setText(hAge);
                    tietPhonenumber.setText(hPhone);
                    tietIdNumber.setText(hIdNo);
                    actvIncomeSource.setText(hIncomeSource, false);
                    tietOtherIncomeSource.setText(hOtherIncomeSource);
                    tietAverageIncome.setText(hAvIncome);
                    tietSpouseName.setText(hSpouse);
                    actvMaritalStatus.setText(hMaritalStatus, false);
                    actvLiteracy.setText(literacy, false);
                    actvLegalStatus.setText(hLegality, false);
                    actvGender.setText(hGender, false);
                    actvSelectionCriteria.setText(hCriteria, false);
                    mactvSelectionReason.setText(hReason, false);

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
                if (validateAutoCompleteTextView()) {
                    store();
                }
            }

        } else {
            //Update displayed data
            if (validateInputFields()) {
                if (validateAutoCompleteTextView()) {
                    update();
                }
            }
        }
    }

    private boolean validateAutoCompleteTextView() {
        boolean check = true;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvMaritalStatus, "Select Marital Status"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvLegalStatus, "Select Legal Status"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvSelectionCriteria, "Select Selection Criteria"))
            check = false;
        if (!globalApplication.isMultiAutoCompleteTextViewChecked(mactvSelectionReason, "Select Selection Reason"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvLiteracy, "Select Literacy Level"))
            check = false;
        if (!globalApplication.checkAgeReasonably(mactvSelectionReason, tietAge)) check = false;

        return check;
    }

    private boolean validateInputFields() {
        boolean check = true;
        if (!globalApplication.hasText(tietHouseholdName, "Enter Household Name")) check = false;
        if (!globalApplication.hasText(tietAge, "Enter Age")) check = false;
        if (!globalApplication.hasText(tietIdNumber, "Enter Id Number")) check = false;
        if (!globalApplication.hasText(tietPhonenumber, "Enter Phonenumber")) check = false;
        if (!globalApplication.hasText(tietAverageIncome, "Enter Average Income")) check = false;
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
            String gender = "", spouseName = "", selectionReason = "";
            gender = actvGender.getText().toString().trim();

            selectionReason = mactvSelectionReason.getText().toString().trim();
            if (selectionReason.contains("Female-headed") && gender.equals("Male")) {
                GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                        "Household cannot be Male and the selection reason contains Female-headed reason",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            } else {

                if (tietSpouseName.getText().toString().trim().length() == 0) {
                    spouseName = "0";
                } else {
                    spouseName = tietSpouseName.getText().toString().trim();
                }


                if (globalApplication.isNullOrEmpty(globalApplication.getBomaCode()) == false) {
                    //inserts
                    ContentValues value = new ContentValues();
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, globalApplication.getHouseholdName());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_HEAD_RELATIONSHIP, actvHouseholdHeadRelationship.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, tietIdNumber.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, tietPhonenumber.getText().toString().trim());

                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, tietAge.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, gender);
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS, actvMaritalStatus.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, spouseName);
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LITERACY, actvLiteracy.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS, actvLegalStatus.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, tietAverageIncome.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE, actvIncomeSource.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE, tietOtherIncomeSource.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA, actvSelectionCriteria.getText().toString().trim());
                    value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON, mactvSelectionReason.getText().toString().trim());
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
                                    startActivity(new Intent(PersonalInfoActivity.this, HouseholdBreakdownActivity.class));
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

            String gender = "", spouseName = "", selectionReason = "";
            gender = actvGender.getText().toString().trim();
            selectionReason = mactvSelectionReason.getText().toString().trim();
            if (selectionReason.contains("Female-headed") && gender.equals("Male")) {
                GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                        "Household cannot be Male and the selection reason contains Female-headed reason",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            } else {
                if (tietSpouseName.getText().toString().trim().length() == 0) {
                    spouseName = "0";
                } else {
                    spouseName = tietSpouseName.getText().toString().trim();
                }

                //inserts
                ContentValues value = new ContentValues();
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, globalApplication.getHouseholdName());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_HEAD_RELATIONSHIP, actvHouseholdHeadRelationship.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, tietIdNumber.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, tietPhonenumber.getText().toString().trim());

                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA, actvSelectionCriteria.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON, mactvSelectionReason.getText().toString().trim());

                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, tietAge.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, gender);
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS, actvMaritalStatus.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, spouseName);
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LITERACY, actvLiteracy.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS, actvLegalStatus.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, tietAverageIncome.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE, actvIncomeSource.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE, tietOtherIncomeSource.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, "0");
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                String[] whereUpdateHouseholdArgs = {globalApplication.getHouseholdNumber()};

                getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
                GlobalDialogs.error(PersonalInfoActivity.this, "Personal Information",
                        "Personal Information has been updated successfully. Move to the NEXT page",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(PersonalInfoActivity.this, HouseholdBreakdownActivity.class));
                            }
                        });
            }
        }
    }

    private void setUpAdapters() {

        String[] genderList = getResources().getStringArray(R.array.gender_list);
        adapterGender = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, genderList);
        actvGender.setThreshold(1);
        actvGender.setAdapter(adapterGender);

        String[] relationship = getResources().getStringArray(R.array.relationship);
        adapterHouseholdHeadRelationship = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, relationship);
        actvHouseholdHeadRelationship.setThreshold(1);
        actvHouseholdHeadRelationship.setAdapter(adapterHouseholdHeadRelationship);

        String[] decisioning = getResources().getStringArray(R.array.decisioning);
        adapterLiteracy = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, decisioning);
        actvLiteracy.setThreshold(1);
        actvLiteracy.setAdapter(adapterLiteracy);

        String[] legality = getResources().getStringArray(R.array.legality);
        adapterLegalStatus = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, legality);
        actvLegalStatus.setThreshold(1);
        actvLegalStatus.setAdapter(adapterLegalStatus);

        String[] incomeList = getResources().getStringArray(R.array.income_list);
        adapterIncomeSource = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, incomeList);
        actvIncomeSource.setThreshold(1);
        actvIncomeSource.setAdapter(adapterIncomeSource);
        actvIncomeSource.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String selectedItem = (String) adapterView.getItemAtPosition(i);
                if (selectedItem.equalsIgnoreCase("Other (specify)")) {
                    tietOtherIncomeSource.setVisibility(View.VISIBLE);
                    tilOtherIncomeSource.setVisibility(View.VISIBLE);
                } else {
                    tietOtherIncomeSource.setVisibility(View.INVISIBLE);
                    tilOtherIncomeSource.setVisibility(View.INVISIBLE);
                }
            }

        });

        String[] marital = getResources().getStringArray(R.array.marital);
        adapterMaritalStatus = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, marital);
        actvMaritalStatus.setThreshold(1);
        actvMaritalStatus.setAdapter(adapterMaritalStatus);
        actvMaritalStatus.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0) {
                    tietSpouseName.setVisibility(View.VISIBLE);
                    tilSpouseName.setVisibility(View.VISIBLE);
                } else {
                    tietSpouseName.setVisibility(View.INVISIBLE);
                    tilSpouseName.setVisibility(View.INVISIBLE);
                }
            }

        });


        String[] selectionCriteria = getResources().getStringArray(R.array.selection_criteria);
        adapterSelectionCriteria = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, selectionCriteria);
        actvSelectionCriteria.setThreshold(1);
        actvSelectionCriteria.setAdapter(adapterSelectionCriteria);
        actvSelectionCriteria.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String[] reasonsList;
                if (i == 2) {
                    reasonsList = getResources().getStringArray(R.array.dis);
                } else {
                    reasonsList = getResources().getStringArray(R.array.pw);
                }

                adapterSelectionReason = new ArrayAdapter<>(PersonalInfoActivity.this, android.R.layout.simple_list_item_1, reasonsList);
                mactvSelectionReason.setAdapter(adapterSelectionReason);
                mactvSelectionReason.setThreshold(1);
                mactvSelectionReason.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
            }
        });

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