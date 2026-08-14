package com.biopay.agent.households;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.biopay.agent.R;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;

import java.util.Locale;

/** Add or edit a household -- the form behind {@code activity_household_form.xml}. */
public class HouseholdFormActivity extends AppCompatActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String[] GENDER_OPTIONS = {"Male", "Female"};

    public static Intent editIntent(Context context, String householdNumber) {
        Intent intent = new Intent(context, HouseholdFormActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        return intent;
    }

    private HouseholdDao householdDao;
    private SessionManager sessionManager;
    private String editingHouseholdNumber;

    private EditText etHouseholdName;
    private EditText etIdNumber;
    private EditText etPhoneNumber;
    private EditText etAge;
    private Spinner spinnerGender;
    private EditText etStateCode;
    private EditText etBomaCode;
    private EditText etHouseholdSize;
    private EditText etMaleDependants;
    private EditText etFemaleDependants;
    private CheckBox cbDisabledMembers;
    private CheckBox cbLiteracy;
    private CheckBox cbEligible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_form);

        householdDao = new HouseholdDao(this);
        sessionManager = new SessionManager(this);
        editingHouseholdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);

        etHouseholdName = findViewById(R.id.etHouseholdName);
        etIdNumber = findViewById(R.id.etIdNumber);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etAge = findViewById(R.id.etAge);
        spinnerGender = findViewById(R.id.spinnerGender);
        etStateCode = findViewById(R.id.etStateCode);
        etBomaCode = findViewById(R.id.etBomaCode);
        etHouseholdSize = findViewById(R.id.etHouseholdSize);
        etMaleDependants = findViewById(R.id.etMaleDependants);
        etFemaleDependants = findViewById(R.id.etFemaleDependants);
        cbDisabledMembers = findViewById(R.id.cbDisabledMembers);
        cbLiteracy = findViewById(R.id.cbLiteracy);
        cbEligible = findViewById(R.id.cbEligible);

        spinnerGender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, GENDER_OPTIONS));

        if (editingHouseholdNumber != null) {
            populateForEdit(editingHouseholdNumber);
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    private void populateForEdit(String householdNumber) {
        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        if (household == null) {
            return;
        }
        etHouseholdName.setText(household.householdName);
        etIdNumber.setText(household.idNumber);
        etPhoneNumber.setText(household.phoneNumber);
        etAge.setText(household.age == null ? "" : String.valueOf(household.age));
        int genderIndex = household.gender == null ? -1
                : java.util.Arrays.asList(GENDER_OPTIONS).indexOf(household.gender);
        if (genderIndex >= 0) {
            spinnerGender.setSelection(genderIndex);
        }
        etStateCode.setText(household.stateCode);
        etBomaCode.setText(household.bomaCode);
        etHouseholdSize.setText(household.householdSize == null ? "" : String.valueOf(household.householdSize));
        etMaleDependants.setText(household.maleDependants == null ? "" : String.valueOf(household.maleDependants));
        etFemaleDependants.setText(household.femaleDependants == null ? "" : String.valueOf(household.femaleDependants));
        cbDisabledMembers.setChecked(household.disabledMembers);
        cbLiteracy.setChecked(household.literate);
        cbEligible.setChecked(household.eligible);
    }

    private void save() {
        String householdName = etHouseholdName.getText().toString().trim();
        if (householdName.isEmpty()) {
            etHouseholdName.setError(getString(R.string.field_household_name));
            return;
        }

        ContentValues values = new ContentValues();
        values.put("supervisor_id", String.valueOf(sessionManager.getUserId()));
        values.put("partner_code", sessionManager.getPartnerCode());
        values.put("household_name", householdName);
        values.put("id_number", etIdNumber.getText().toString().trim());
        values.put("phone_number", etPhoneNumber.getText().toString().trim());
        values.put("age", parseIntOrNull(etAge.getText().toString()));
        values.put("gender", (String) spinnerGender.getSelectedItem());
        values.put("state_code", etStateCode.getText().toString().trim());
        values.put("boma_code", etBomaCode.getText().toString().trim());
        values.put("household_size", parseIntOrNull(etHouseholdSize.getText().toString()));
        values.put("male_dependants", parseIntOrNull(etMaleDependants.getText().toString()));
        values.put("female_dependants", parseIntOrNull(etFemaleDependants.getText().toString()));
        values.put("disabled_members", cbDisabledMembers.isChecked() ? 1 : 0);
        values.put("literacy", cbLiteracy.isChecked() ? "Y" : "N");
        values.put("eligibility", cbEligible.isChecked() ? "Y" : "N");

        Location location = LocationHelper.getLastKnownLocation(this);
        if (location != null) {
            values.put("latitude", String.valueOf(location.getLatitude()));
            values.put("longitude", String.valueOf(location.getLongitude()));
        }

        if (editingHouseholdNumber != null) {
            householdDao.update(editingHouseholdNumber, values);
        } else {
            values.put("household_number", generateHouseholdNumber());
            householdDao.insert(values);
        }

        Toast.makeText(this, R.string.household_save_continue, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Offline-generated -- the server assigns its own canonical number once this record syncs. */
    private static String generateHouseholdNumber() {
        return "HH" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.US);
    }
}
