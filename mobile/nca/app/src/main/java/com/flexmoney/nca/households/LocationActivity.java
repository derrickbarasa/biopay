package com.flexmoney.nca.households;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.home.MainActivity;
import com.flexmoney.nca.objects.TargetLocation;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;

import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.ArrayList;

public class LocationActivity extends AppCompatActivity {
    GlobalApplication globalApplication;
    Button btnLocation;
    Spinner spStates, spCounties, spPayams, spBomas;
    ArrayList<TargetLocation> stateArray = new ArrayList<>();
    ArrayList<TargetLocation> countyArray = new ArrayList<>();
    ArrayList<TargetLocation> payamArray = new ArrayList<>();
    ArrayList<TargetLocation> bomaArray = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        globalApplication = GlobalApplication.getInstance();
        globalApplication.setBomaCode(null);
        globalApplication.setHouseholdNumber(null);
        globalApplication.setHouseholdName(null);

        spStates = findViewById(R.id.spStates);
        spCounties = findViewById(R.id.spCounties);
        spPayams = findViewById(R.id.spPayams);
        spBomas = findViewById(R.id.spBomas);
        populateStates();
        initialLocationHeaders();

        btnLocation = findViewById(R.id.btnLocation);
        btnLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (globalApplication.getBomaCode() != null) {
                    GlobalDialogs.success(LocationActivity.this, "Registration Setup",
                            "You have successfully set up " + globalApplication.getStateName() +
                                    " state, " + globalApplication.getCountyName() + " county, " +
                                    globalApplication.getPayamName() + " payam, " + globalApplication.getBomaName() +
                                    " boma. You can now proceed to register all households in this boma",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    startActivity(new Intent(LocationActivity.this, PersonalInfoActivity.class));
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                } else {
                    GlobalDialogs.error(LocationActivity.this, "Registration Setup",
                            "Please select all the locations properly", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                }
            }
        });

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(LocationActivity.this, MainActivity.class));
    }

    private void initialLocationHeaders() {
        countyArray.clear();
        countyArray.add(new TargetLocation("0", "Select County"));
        ArrayAdapter adapterCounties = new ArrayAdapter(LocationActivity.this, android.R.layout.simple_spinner_item, countyArray);
        adapterCounties.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCounties.setAdapter(adapterCounties);

        payamArray.clear();
        payamArray.add(new TargetLocation("0", "Select Payam"));
        ArrayAdapter adapterPayam = new ArrayAdapter(LocationActivity.this, android.R.layout.simple_spinner_item, payamArray);
        adapterPayam.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPayams.setAdapter(adapterPayam);

        bomaArray.clear();
        bomaArray.add(new TargetLocation("0", "Select Boma"));
        ArrayAdapter adapterBoma = new ArrayAdapter(LocationActivity.this, android.R.layout.simple_spinner_item, bomaArray);
        adapterBoma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBomas.setAdapter(adapterBoma);
    }

    private void populateStates() {
        Cursor cursor = null;

        stateArray.clear();
        stateArray.add(new TargetLocation("0", "Select State"));

        try {
            cursor = getContentResolver().query(BiometricsContract.StatesEntry.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String stateName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_NAME));
                            String stateCode = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_CODE));
                            stateArray.add(new TargetLocation(stateCode, stateName));

                        } while (cursor.moveToNext());
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


        ArrayAdapter adapterState = new ArrayAdapter(LocationActivity.this, android.R.layout.simple_spinner_item, stateArray);
        adapterState.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spStates.setAdapter(adapterState);
        adapterState.notifyDataSetChanged();

        spStates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (position != 0) {
                    TargetLocation targetLocation = (TargetLocation) parent.getSelectedItem();
                    String selStateCode = targetLocation.getTargetLocationId();
                    String selStateName = targetLocation.getTargetLocationName();

                    globalApplication.setStateCode(selStateCode);
                    globalApplication.setStateName(selStateName);

                    populateCounties(selStateCode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void populateCounties(String stateCode) {
        Cursor cursor = null;

        countyArray.clear();
        countyArray.add(new TargetLocation("0", "Select County"));

        String where = BiometricsContract.CountiesEntry.COLUMN_NAME_STATE_CODE + "=?";
        String whereArgs[] = {stateCode};
        try {
            cursor = getContentResolver().query(BiometricsContract.CountiesEntry.CONTENT_URI, null, where, whereArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String countyCode = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_CODE));
                            String countyName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_NAME));
                            countyArray.add(new TargetLocation(countyCode, countyName));

                        } while (cursor.moveToNext());
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


        ArrayAdapter<TargetLocation> adapterCounty = new ArrayAdapter<TargetLocation>(this, android.R.layout.simple_spinner_item, countyArray);
        adapterCounty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spCounties.setAdapter(adapterCounty);
        adapterCounty.notifyDataSetChanged();

        spCounties.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (position != 0) {
                    TargetLocation targetLocation = (TargetLocation) parent.getSelectedItem();
                    String selCountyCode = targetLocation.getTargetLocationId();
                    String selCountyName = targetLocation.getTargetLocationName();

                    globalApplication.setCountyCode(selCountyCode);
                    globalApplication.setCountyName(selCountyName);

                    populatePayams(selCountyCode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void populatePayams(String countyCode) {
        Cursor cursor = null;
        payamArray.clear();
        payamArray.add(new TargetLocation("0", "Select Payam"));
        String where = BiometricsContract.PayamsEntry.COLUMN_NAME_COUNTY_CODE + "=?";
        String whereArgs[] = {countyCode};
        try {
            cursor = getContentResolver().query(BiometricsContract.PayamsEntry.CONTENT_URI, null, where, whereArgs, null);

            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String payamCode = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_CODE));
                            String payamName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_NAME));
                            payamArray.add(new TargetLocation(payamCode, payamName));

                        } while (cursor.moveToNext());
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


        ArrayAdapter<TargetLocation> adapterPayams = new ArrayAdapter<TargetLocation>(this, android.R.layout.simple_spinner_item, payamArray);
        adapterPayams.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spPayams.setAdapter(adapterPayams);
        adapterPayams.notifyDataSetChanged();

        spPayams.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (position != 0) {
                    TargetLocation targetLocation = (TargetLocation) parent.getSelectedItem();
                    String selPayamCode = targetLocation.getTargetLocationId();
                    String selPayamName = targetLocation.getTargetLocationName();

                    globalApplication.setPayamCode(selPayamCode);
                    globalApplication.setPayamName(selPayamName);

                    populateBomas(selPayamCode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void populateBomas(String payamCode) {
        Cursor cursor = null;

        bomaArray.clear();
        bomaArray.add(new TargetLocation("0", "Select Boma"));
        String where = BiometricsContract.BomasEntry.COLUMN_NAME_PAYAM_CODE + "=?";
        String whereArgs[] = {payamCode};
        try {
            cursor = getContentResolver().query(BiometricsContract.BomasEntry.CONTENT_URI, null, where, whereArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String bomaCode = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_CODE));
                            String bomaName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_NAME));
                            bomaArray.add(new TargetLocation(bomaCode, bomaName));
                        } while (cursor.moveToNext());
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


        ArrayAdapter<TargetLocation> adapterBomas = new ArrayAdapter<TargetLocation>(this, android.R.layout.simple_spinner_item, bomaArray);
        adapterBomas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBomas.setAdapter(adapterBomas);
        adapterBomas.notifyDataSetChanged();

        spBomas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (position != 0) {
                    spBomas.setSelection(position, true);
                    TargetLocation targetLocation = (TargetLocation) parent.getSelectedItem();
                    String selBomaCode = targetLocation.getTargetLocationId();
                    String selBomaName = targetLocation.getTargetLocationName();

                    globalApplication.setBomaCode(selBomaCode);
                    globalApplication.setBomaName(selBomaName);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.house_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            startActivity(new Intent(LocationActivity.this, EditHouseholdActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }
}