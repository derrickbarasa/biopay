package com.biopay.agent.households;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.biopay.agent.R;
import com.biopay.agent.data.GeoDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;
import java.util.Locale;

/** Add or edit a household -- the form behind {@code activity_household_form.xml}. */
public class HouseholdFormActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String[] GENDER_OPTIONS = {"Male", "Female"};
    // MlKitFaceRecognitionEngine now does real face detection, but its createEmbedding() always
    // throws -- no approved identity-matching model is configured (see that class's javadoc for
    // why one isn't just downloaded). Attendance/Voucher verification also only ever checks an
    // enrolled fingerprint. Offering FACE/BOTH here would let an officer register a household that
    // could never be verified again, so fingerprint stays the only option until a real embedding
    // model is wired into MlKitFaceRecognitionEngine.createEmbedding().
    private static final String[] REGISTRATION_OPTIONS = {"FINGERPRINT"};
    private static final String[] REGISTRATION_LABELS = {"Fingerprint"};

    public static Intent editIntent(Context context, String householdNumber) {
        Intent intent = new Intent(context, HouseholdFormActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        return intent;
    }

    private HouseholdDao householdDao;
    private GeoDao geoDao;
    private SessionManager sessionManager;
    private String editingHouseholdNumber;

    private EditText etHouseholdName;
    private EditText etIdNumber;
    private EditText etPhoneNumber;
    private EditText etAge;
    private AutoCompleteTextView spinnerGender;
    private AutoCompleteTextView spinnerRegistrationMethod;

    private View locationPickerGroup;
    private View locationManualGroup;
    private TextView tvToggleManualEntry;
    private AutoCompleteTextView spinnerState;
    private AutoCompleteTextView spinnerCounty;
    private AutoCompleteTextView spinnerLocation;
    private AutoCompleteTextView spinnerVillage;
    private EditText etStateCode;
    private EditText etCountyCode;
    private EditText etPayamCode;
    private EditText etBomaCode;
    private String selectedStateCode;
    private String selectedCountyCode;
    private String selectedPayamCode;
    private String selectedBomaCode;

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
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        geoDao = new GeoDao(this);
        sessionManager = new SessionManager(this);
        editingHouseholdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);

        etHouseholdName = findViewById(R.id.etHouseholdName);
        etIdNumber = findViewById(R.id.etIdNumber);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etAge = findViewById(R.id.etAge);
        spinnerGender = findViewById(R.id.spinnerGender);
        spinnerRegistrationMethod = findViewById(R.id.spinnerRegistrationMethod);

        locationPickerGroup = findViewById(R.id.locationPickerGroup);
        locationManualGroup = findViewById(R.id.locationManualGroup);
        tvToggleManualEntry = findViewById(R.id.tvToggleManualEntry);
        spinnerState = findViewById(R.id.spinnerState);
        spinnerCounty = findViewById(R.id.spinnerCounty);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        spinnerVillage = findViewById(R.id.spinnerVillage);
        etStateCode = findViewById(R.id.etStateCode);
        etCountyCode = findViewById(R.id.etCountyCode);
        etPayamCode = findViewById(R.id.etPayamCode);
        etBomaCode = findViewById(R.id.etBomaCode);
        etHouseholdSize = findViewById(R.id.etHouseholdSize);
        etMaleDependants = findViewById(R.id.etMaleDependants);
        etFemaleDependants = findViewById(R.id.etFemaleDependants);
        cbDisabledMembers = findViewById(R.id.cbDisabledMembers);
        cbLiteracy = findViewById(R.id.cbLiteracy);
        cbEligible = findViewById(R.id.cbEligible);

        spinnerGender.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, GENDER_OPTIONS));
        spinnerGender.setText(GENDER_OPTIONS[0], false);
        spinnerRegistrationMethod.setText(REGISTRATION_LABELS[0], false);
        spinnerRegistrationMethod.setEnabled(false);

        loadStates();
        // No hierarchy synced for this anchor yet (or the device has never been online) -- default
        // to manual code entry so registration is never blocked on it.
        setManualEntryVisible(!geoDao.hasAnyStates());
        tvToggleManualEntry.setOnClickListener(v ->
                setManualEntryVisible(locationManualGroup.getVisibility() != View.VISIBLE));

        ((TextView) findViewById(R.id.tvFormTitle)).setText(editingHouseholdNumber == null
                ? R.string.household_form_new_title : R.string.household_form_edit_title);

        if (editingHouseholdNumber != null) {
            populateForEdit(editingHouseholdNumber);
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    private void setManualEntryVisible(boolean visible) {
        locationManualGroup.setVisibility(visible ? View.VISIBLE : View.GONE);
        locationPickerGroup.setVisibility(visible ? View.GONE : View.VISIBLE);
        tvToggleManualEntry.setText(visible ? R.string.location_use_picker : R.string.location_enter_manually);
    }

    private void loadStates() {
        List<GeoDao.GeoNode> states = geoDao.listStates();
        spinnerState.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, states));
        spinnerState.setOnItemClickListener((parent, view, position, id) -> {
            GeoDao.GeoNode selected = states.get(position);
            selectedStateCode = selected.code;
            selectedCountyCode = null;
            selectedPayamCode = null;
            selectedBomaCode = null;
            spinnerCounty.setText("", false);
            spinnerLocation.setText("", false);
            spinnerVillage.setText("", false);
            loadCounties(selected.code);
        });
    }

    private void loadCounties(String stateCode) {
        List<GeoDao.GeoNode> counties = geoDao.listCounties(stateCode);
        spinnerCounty.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, counties));
        spinnerCounty.setOnItemClickListener((parent, view, position, id) -> {
            GeoDao.GeoNode selected = counties.get(position);
            selectedCountyCode = selected.code;
            selectedPayamCode = null;
            selectedBomaCode = null;
            spinnerLocation.setText("", false);
            spinnerVillage.setText("", false);
            loadLocations(selected.code);
        });
    }

    private void loadLocations(String countyCode) {
        List<GeoDao.GeoNode> locations = geoDao.listPayams(countyCode);
        spinnerLocation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, locations));
        spinnerLocation.setOnItemClickListener((parent, view, position, id) -> {
            GeoDao.GeoNode selected = locations.get(position);
            selectedPayamCode = selected.code;
            selectedBomaCode = null;
            spinnerVillage.setText("", false);
            loadVillages(selected.code);
        });
    }

    private void loadVillages(String payamCode) {
        List<GeoDao.GeoNode> villages = geoDao.listBomas(payamCode);
        spinnerVillage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, villages));
        spinnerVillage.setOnItemClickListener((parent, view, position, id) ->
                selectedBomaCode = villages.get(position).code);
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
            spinnerGender.setText(GENDER_OPTIONS[genderIndex], false);
        }
        int registrationIndex = household.registrationMethod == null ? 0
                : java.util.Arrays.asList(REGISTRATION_OPTIONS).indexOf(household.registrationMethod);
        spinnerRegistrationMethod.setText(REGISTRATION_LABELS[Math.max(0, registrationIndex)], false);
        populateLocationForEdit(household);
        etHouseholdSize.setText(household.householdSize == null ? "" : String.valueOf(household.householdSize));
        etMaleDependants.setText(household.maleDependants == null ? "" : String.valueOf(household.maleDependants));
        etFemaleDependants.setText(household.femaleDependants == null ? "" : String.valueOf(household.femaleDependants));
        cbDisabledMembers.setChecked(household.disabledMembers);
        cbLiteracy.setChecked(household.literate);
        cbEligible.setChecked(household.eligible);
    }

    /**
     * Always keeps the manual code fields populated with the household's raw stored codes (so
     * nothing is ever silently dropped from view), then attempts to resolve and cascade-select
     * matching names in the picker as far down the hierarchy as the locally synced data allows.
     * If any level can't be resolved (captured before the hierarchy existed, or belongs to a
     * different/unsynced anchor), falls back to showing the manual fields instead of a picker
     * that can't represent the saved value.
     */
    private void populateLocationForEdit(HouseholdDao.Household household) {
        etStateCode.setText(household.stateCode);
        etCountyCode.setText(household.countyCode);
        etPayamCode.setText(household.payamCode);
        etBomaCode.setText(household.bomaCode);

        boolean resolved = true;

        String stateName = geoDao.findStateName(household.stateCode);
        if (stateName != null) {
            selectedStateCode = household.stateCode;
            spinnerState.setText(stateName, false);
            loadCounties(household.stateCode);
        } else {
            resolved = false;
        }

        String countyName = resolved ? geoDao.findCountyName(household.countyCode) : null;
        if (resolved && countyName != null) {
            selectedCountyCode = household.countyCode;
            spinnerCounty.setText(countyName, false);
            loadLocations(household.countyCode);
        } else {
            resolved = false;
        }

        String payamName = resolved ? geoDao.findPayamName(household.payamCode) : null;
        if (resolved && payamName != null) {
            selectedPayamCode = household.payamCode;
            spinnerLocation.setText(payamName, false);
            loadVillages(household.payamCode);
        } else {
            resolved = false;
        }

        String bomaName = resolved ? geoDao.findBomaName(household.bomaCode) : null;
        if (resolved && bomaName != null) {
            selectedBomaCode = household.bomaCode;
            spinnerVillage.setText(bomaName, false);
        } else {
            resolved = false;
        }

        boolean anyCodePresent = hasText(household.stateCode) || hasText(household.countyCode)
                || hasText(household.payamCode) || hasText(household.bomaCode);
        if (anyCodePresent && !resolved) {
            setManualEntryVisible(true);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
        values.put("gender", spinnerGender.getText().toString());
        values.put("registration_method", selectedRegistrationCode());
        boolean manualEntry = locationManualGroup.getVisibility() == View.VISIBLE;
        values.put("state_code", manualEntry ? etStateCode.getText().toString().trim() : selectedStateCode);
        values.put("county_code", manualEntry ? etCountyCode.getText().toString().trim() : selectedCountyCode);
        values.put("payam_code", manualEntry ? etPayamCode.getText().toString().trim() : selectedPayamCode);
        values.put("boma_code", manualEntry ? etBomaCode.getText().toString().trim() : selectedBomaCode);
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

        Toast.makeText(this, R.string.household_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String selectedRegistrationCode() {
        String selected = spinnerRegistrationMethod.getText().toString();
        int index = java.util.Arrays.asList(REGISTRATION_LABELS).indexOf(selected);
        return REGISTRATION_OPTIONS[Math.max(0, index)];
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
