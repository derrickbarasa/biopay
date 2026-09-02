package com.biopay.agent.households;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.GeoDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Add or edit a household -- the form behind {@code activity_household_form.xml}. Registering a
 * new household saves the base record locally, then offers {@link PersonCaptureActivity}
 * to capture the household head's chosen biometric method(s), which may be completed later -- previously
 * "registration method" was just a stored label with no real capture step anywhere in the app.
 * Editing an existing household additionally shows who's been captured so far and lets the
 * officer complete a missing capture or add another person, via the same activity.
 */
public class HouseholdFormActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String[] GENDER_OPTIONS = {"Male", "Female"};
    private static final String[] LEGAL_STATUS_LABELS = {
            "Not recorded", "Citizen", "Refugee", "Internally displaced person (IDP)",
            "Asylum seeker", "Returnee", "Stateless", "Other"};
    private static final String[] LEGAL_STATUS_CODES = {
            "", "CITIZEN", "REFUGEE", "IDP", "ASYLUM_SEEKER", "RETURNEE", "STATELESS", "OTHER"};

    private static final String OPTION_FINGERPRINT = "FINGERPRINT";
    private static final String OPTION_FACE = "FACE";
    private static final String OPTION_BOTH = "FINGERPRINT_AND_FACE";

    public static Intent editIntent(Context context, String householdNumber) {
        Intent intent = new Intent(context, HouseholdFormActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        return intent;
    }

    private HouseholdDao householdDao;
    private GeoDao geoDao;
    private AlternateDao alternateDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private SessionManager sessionManager;
    private String editingHouseholdNumber;

    /** Which registration methods this screen offers, gated by the organisation's
     *  verificationMethod setting (see SessionManager.getVerificationMethod()) -- extended from
     *  the fingerprint-only lockdown once face capture had a real, working embedding pipeline to
     *  offer (still an explicitly unvalidated prototype, hence the accuracy notice in the UI). */
    private String[] registrationOptions;
    private String[] registrationLabels;

    private final ActivityResultLauncher<Intent> personCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (editingHouseholdNumber != null) {
                    populateCapturedPeople(editingHouseholdNumber);
                } else {
                    // New-household flow: capturing the head (or choosing not to add more people)
                    // completes registration.
                    Toast.makeText(this, R.string.household_saved, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

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
    private EditText etStateName;
    private EditText etCountyName;
    private EditText etPayamName;
    private EditText etBomaName;
    private String selectedStateCode;
    private String selectedCountyCode;
    private String selectedPayamCode;
    private String selectedBomaCode;

    private EditText etHouseholdSize;
    private EditText etMaleDependants;
    private EditText etFemaleDependants;
    private CheckBox cbDisabledMembers;
    private CheckBox cbElderlyHeaded;
    private CheckBox cbChildHeaded;
    private CheckBox cbChronicIllness;
    private CheckBox cbPregnantLactating;
    private CheckBox cbSingleCaregiver;
    private CheckBox cbLiteracy;
    private CheckBox cbEligible;
    private AutoCompleteTextView spinnerLegalStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_form);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        geoDao = new GeoDao(this);
        alternateDao = new AlternateDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        sessionManager = new SessionManager(this);
        editingHouseholdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        buildRegistrationOptions();

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
        etStateName = findViewById(R.id.etStateName);
        etCountyName = findViewById(R.id.etCountyName);
        etPayamName = findViewById(R.id.etPayamName);
        etBomaName = findViewById(R.id.etBomaName);
        etHouseholdSize = findViewById(R.id.etHouseholdSize);
        etMaleDependants = findViewById(R.id.etMaleDependants);
        etFemaleDependants = findViewById(R.id.etFemaleDependants);
        cbDisabledMembers = findViewById(R.id.cbDisabledMembers);
        cbElderlyHeaded = findViewById(R.id.cbElderlyHeaded);
        cbChildHeaded = findViewById(R.id.cbChildHeaded);
        cbChronicIllness = findViewById(R.id.cbChronicIllness);
        cbPregnantLactating = findViewById(R.id.cbPregnantLactating);
        cbSingleCaregiver = findViewById(R.id.cbSingleCaregiver);
        cbLiteracy = findViewById(R.id.cbLiteracy);
        cbEligible = findViewById(R.id.cbEligible);
        spinnerLegalStatus = findViewById(R.id.spinnerLegalStatus);

        spinnerGender.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, GENDER_OPTIONS));
        spinnerGender.setText(GENDER_OPTIONS[0], false);

        spinnerLegalStatus.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, LEGAL_STATUS_LABELS));
        spinnerLegalStatus.setText(LEGAL_STATUS_LABELS[0], false);

        spinnerRegistrationMethod.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, registrationLabels));
        spinnerRegistrationMethod.setText(registrationLabels[0], false);
        spinnerRegistrationMethod.setEnabled(registrationLabels.length > 1);
        spinnerRegistrationMethod.setOnItemClickListener((parent, view, position, id) ->
                updateFaceAccuracyNoticeVisibility());
        updateFaceAccuracyNoticeVisibility();

        loadStates();
        // No hierarchy synced for this anchor yet (or the device has never been online) -- default
        // to manual name entry so registration is never blocked on it.
        setManualEntryVisible(!geoDao.hasAnyStates());
        tvToggleManualEntry.setOnClickListener(v ->
                setManualEntryVisible(locationManualGroup.getVisibility() != View.VISIBLE));

        ((TextView) findViewById(R.id.tvFormTitle)).setText(editingHouseholdNumber == null
                ? R.string.household_form_new_title : R.string.household_form_edit_title);

        MaterialButton btnSave = findViewById(R.id.btnSave);
        if (editingHouseholdNumber != null) {
            populateForEdit(editingHouseholdNumber);
            populateCapturedPeople(editingHouseholdNumber);
            btnSave.setText(R.string.household_save_continue);
        } else {
            btnSave.setText(R.string.household_continue_to_capture);
        }
        btnSave.setOnClickListener(v -> save());
    }

    /** BIOMETRIC -> fingerprint only; FACIAL -> face only; BOTH -> all three choices, including
     *  enrolling both for the same person. "FINGERPRINT_AND_FACE" previously existed in this
     *  exact form before being stripped down to fingerprint-only on 2026-08-18 (see git history)
     *  -- reviving a known value, not inventing one. */
    private void buildRegistrationOptions() {
        String orgMethod = sessionManager.getVerificationMethod();
        if ("FACIAL".equals(orgMethod)) {
            registrationOptions = new String[]{OPTION_FACE};
            registrationLabels = new String[]{"Face"};
        } else if ("BOTH".equals(orgMethod)) {
            registrationOptions = new String[]{OPTION_FINGERPRINT, OPTION_FACE, OPTION_BOTH};
            registrationLabels = new String[]{"Fingerprint", "Face", "Fingerprint and face"};
        } else {
            registrationOptions = new String[]{OPTION_FINGERPRINT};
            registrationLabels = new String[]{"Fingerprint"};
        }
    }

    private void updateFaceAccuracyNoticeVisibility() {
        boolean facePossible = OPTION_FACE.equals(selectedRegistrationCode()) || OPTION_BOTH.equals(selectedRegistrationCode());
        findViewById(R.id.tvFaceAccuracyNotice).setVisibility(facePossible ? View.VISIBLE : View.GONE);
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
        int registrationIndex = household.registrationMethod == null ? -1
                : java.util.Arrays.asList(registrationOptions).indexOf(household.registrationMethod);
        spinnerRegistrationMethod.setText(registrationLabels[Math.max(0, registrationIndex)], false);
        updateFaceAccuracyNoticeVisibility();
        populateLocationForEdit(household);
        etHouseholdSize.setText(household.householdSize == null ? "" : String.valueOf(household.householdSize));
        etMaleDependants.setText(household.maleDependants == null ? "" : String.valueOf(household.maleDependants));
        etFemaleDependants.setText(household.femaleDependants == null ? "" : String.valueOf(household.femaleDependants));
        cbDisabledMembers.setChecked(household.disabledMembers
                || hasCsvCode(household.vulnerabilityStatuses, "DISABILITY"));
        cbElderlyHeaded.setChecked(hasCsvCode(household.vulnerabilityStatuses, "ELDERLY_HEADED"));
        cbChildHeaded.setChecked(hasCsvCode(household.vulnerabilityStatuses, "CHILD_HEADED"));
        cbChronicIllness.setChecked(hasCsvCode(household.vulnerabilityStatuses, "CHRONIC_ILLNESS"));
        cbPregnantLactating.setChecked(hasCsvCode(household.vulnerabilityStatuses, "PREGNANT_OR_LACTATING"));
        cbSingleCaregiver.setChecked(hasCsvCode(household.vulnerabilityStatuses, "SINGLE_CAREGIVER"));
        cbLiteracy.setChecked(household.literate);
        cbEligible.setChecked(household.eligible);
        int legalIndex = household.legalStatus == null ? 0
                : java.util.Arrays.asList(LEGAL_STATUS_CODES).indexOf(household.legalStatus);
        spinnerLegalStatus.setText(LEGAL_STATUS_LABELS[Math.max(0, legalIndex)], false);
    }

    /**
     * Always keeps the manual name fields populated with the household's raw stored values (so
     * nothing is ever silently dropped from view), then attempts to resolve and cascade-select
     * matching names in the picker as far down the hierarchy as the locally synced data allows.
     * If any level can't be resolved (captured before the hierarchy existed, or belongs to a
     * different/unsynced anchor), falls back to showing the manual fields instead of a picker
     * that can't represent the saved value.
     */
    private void populateLocationForEdit(HouseholdDao.Household household) {
        boolean resolved = true;

        String stateName = geoDao.findStateName(household.stateCode);
        etStateName.setText(stateName != null ? stateName : household.stateCode);
        if (stateName != null) {
            selectedStateCode = household.stateCode;
            spinnerState.setText(stateName, false);
            loadCounties(household.stateCode);
        } else {
            resolved = false;
        }

        String countyLookup = geoDao.findCountyName(household.countyCode);
        etCountyName.setText(countyLookup != null ? countyLookup : household.countyCode);
        String countyName = resolved ? countyLookup : null;
        if (resolved && countyName != null) {
            selectedCountyCode = household.countyCode;
            spinnerCounty.setText(countyName, false);
            loadLocations(household.countyCode);
        } else {
            resolved = false;
        }

        String payamLookup = geoDao.findPayamName(household.payamCode);
        etPayamName.setText(payamLookup != null ? payamLookup : household.payamCode);
        String payamName = resolved ? payamLookup : null;
        if (resolved && payamName != null) {
            selectedPayamCode = household.payamCode;
            spinnerLocation.setText(payamName, false);
            loadVillages(household.payamCode);
        } else {
            resolved = false;
        }

        String bomaLookup = geoDao.findBomaName(household.bomaCode);
        etBomaName.setText(bomaLookup != null ? bomaLookup : household.bomaCode);
        String bomaName = resolved ? bomaLookup : null;
        if (resolved && bomaName != null) {
            selectedBomaCode = household.bomaCode;
            spinnerVillage.setText(bomaName, false);
        } else {
            resolved = false;
        }

        boolean anyValuePresent = hasText(household.stateCode) || hasText(household.countyCode)
                || hasText(household.payamCode) || hasText(household.bomaCode);
        if (anyValuePresent && !resolved) {
            setManualEntryVisible(true);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean hasCsvCode(String csv, String code) {
        if (!hasText(csv)) return false;
        for (String value : csv.split(",")) {
            if (code.equals(value.trim())) return true;
        }
        return false;
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
        // Manual entry stores the place name the officer typed directly -- state_code/county_code/
        // payam_code/boma_code are loosely-typed free text columns (no FK to the geo hierarchy, see
        // database/migrations/006_geo_hierarchy.sql), so a name is just as valid a value as a code
        // and is what most officers actually know for their area.
        boolean manualEntry = locationManualGroup.getVisibility() == View.VISIBLE;
        String manualState = etStateName.getText().toString().trim();
        String manualCounty = etCountyName.getText().toString().trim();
        String manualPayam = etPayamName.getText().toString().trim();
        String manualBoma = etBomaName.getText().toString().trim();
        values.put("state_code", manualEntry ? manualState : selectedStateCode);
        values.put("county_code", manualEntry ? manualCounty : selectedCountyCode);
        values.put("payam_code", manualEntry ? manualPayam : selectedPayamCode);
        values.put("boma_code", manualEntry ? manualBoma : selectedBomaCode);
        if (manualEntry) {
            geoDao.rememberManualHierarchy(manualState, manualCounty, manualPayam, manualBoma);
            loadStates();
        }
        values.put("household_size", parseIntOrNull(etHouseholdSize.getText().toString()));
        values.put("male_dependants", parseIntOrNull(etMaleDependants.getText().toString()));
        values.put("female_dependants", parseIntOrNull(etFemaleDependants.getText().toString()));
        values.put("disabled_members", cbDisabledMembers.isChecked() ? 1 : 0);
        List<String> vulnerabilityStatuses = new ArrayList<>();
        if (cbDisabledMembers.isChecked()) vulnerabilityStatuses.add("DISABILITY");
        if (cbElderlyHeaded.isChecked()) vulnerabilityStatuses.add("ELDERLY_HEADED");
        if (cbChildHeaded.isChecked()) vulnerabilityStatuses.add("CHILD_HEADED");
        if (cbChronicIllness.isChecked()) vulnerabilityStatuses.add("CHRONIC_ILLNESS");
        if (cbPregnantLactating.isChecked()) vulnerabilityStatuses.add("PREGNANT_OR_LACTATING");
        if (cbSingleCaregiver.isChecked()) vulnerabilityStatuses.add("SINGLE_CAREGIVER");
        values.put("vulnerability_statuses", android.text.TextUtils.join(",", vulnerabilityStatuses));
        int legalIndex = java.util.Arrays.asList(LEGAL_STATUS_LABELS)
                .indexOf(spinnerLegalStatus.getText().toString());
        values.put("legal_status", LEGAL_STATUS_CODES[Math.max(0, legalIndex)]);
        values.put("literacy", cbLiteracy.isChecked() ? "Y" : "N");
        values.put("eligibility", cbEligible.isChecked() ? "Y" : "N");

        Location location = LocationHelper.getLastKnownLocation(this);
        if (location != null) {
            values.put("latitude", String.valueOf(location.getLatitude()));
            values.put("longitude", String.valueOf(location.getLongitude()));
        }

        if (editingHouseholdNumber != null) {
            householdDao.update(editingHouseholdNumber, values);
            Toast.makeText(this, R.string.household_saved, Toast.LENGTH_SHORT).show();
            populateCapturedPeople(editingHouseholdNumber);
        } else {
            String householdNumber = generateHouseholdNumber();
            values.put("household_number", householdNumber);
            householdDao.insert(values);
            // The base record is complete and durable at this point. Capture is an optional next
            // step; PersonCaptureActivity exposes "Finish and capture later" for interruptions.
            personCaptureLauncher.launch(PersonCaptureActivity.captureIntent(this, householdNumber,
                    householdNumber, Beneficiary.TYPE_HOUSEHOLD_HEAD, householdName, selectedRegistrationCode()));
        }
    }

    private String selectedRegistrationCode() {
        String selected = spinnerRegistrationMethod.getText().toString();
        int index = java.util.Arrays.asList(registrationLabels).indexOf(selected);
        return registrationOptions[Math.max(0, index)];
    }

    /** Populates the edit-only "Captured people" list -- the household head (always shown, with
     *  a capture affordance if still missing) plus any alternates -- and wires "Add another
     *  person" to {@link PersonCaptureActivity#addPersonIntent}. */
    private void populateCapturedPeople(String householdNumber) {
        LinearLayout list = findViewById(R.id.capturedPeopleList);
        list.removeAllViews();

        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        String headName = household != null && hasText(household.householdName)
                ? household.householdName : householdNumber;
        addCapturedPersonRow(list, householdNumber, Beneficiary.TYPE_HOUSEHOLD_HEAD, headName);

        for (AlternateDao.Alternate alternate : alternateDao.findByHousehold(householdNumber)) {
            addCapturedPersonRow(list, alternate.alternateNumber, Beneficiary.TYPE_ALTERNATE, alternate.alternateName);
        }

        findViewById(R.id.capturedPeopleSection).setVisibility(View.VISIBLE);
        findViewById(R.id.btnAddPersonFromEdit).setOnClickListener(v -> personCaptureLauncher.launch(
                PersonCaptureActivity.addPersonIntent(this, householdNumber, selectedRegistrationCode())));
    }

    private void addCapturedPersonRow(LinearLayout container, String beneficiaryId, int beneficiaryType, String name) {
        boolean fingerprintDone = fingerprintDao.countForBeneficiary(beneficiaryId) > 0;
        boolean faceDone = faceDao.existsForBeneficiary(beneficiaryId);

        View row = LayoutInflater.from(this).inflate(R.layout.item_captured_person, container, false);
        ((TextView) row.findViewById(R.id.tvCapturedPersonName)).setText(
                beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD
                        ? getString(R.string.household_person_head) + " - " + name
                        : getString(R.string.household_person_alternate) + " - " + name);
        ((TextView) row.findViewById(R.id.tvCapturedPersonStatus)).setText(capturedStatusText(fingerprintDone, faceDone));
        row.findViewById(R.id.btnCapturePersonRow).setOnClickListener(v -> personCaptureLauncher.launch(
                PersonCaptureActivity.captureIntent(this, editingHouseholdNumber, beneficiaryId, beneficiaryType,
                        name, selectedRegistrationCode())));
        container.addView(row);
    }

    private String capturedStatusText(boolean fingerprintDone, boolean faceDone) {
        List<String> captured = new ArrayList<>();
        if (fingerprintDone) captured.add(getString(R.string.person_capture_fingerprint_label));
        if (faceDone) captured.add(getString(R.string.person_capture_face_label));
        return captured.isEmpty() ? getString(R.string.household_person_not_captured)
                : android.text.TextUtils.join(", ", captured);
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
