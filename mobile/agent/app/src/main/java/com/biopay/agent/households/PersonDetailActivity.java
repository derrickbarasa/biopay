package com.biopay.agent.households;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.ImageDao;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.ThumbnailLoader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.Set;

/**
 * Read-first "Person Profile" screen for one household member (the head or an alternate) --
 * reached by tapping their row on {@link HouseholdDetailActivity}. Shows their registered
 * details, their photo large (not the small identity-chip circle used everywhere else), and
 * exactly which fingers/face/photo are already captured, with a single **Capture more** action
 * that hands off to the existing {@link PersonCaptureActivity} for the actual scan/photo work --
 * this screen never talks to the biometric device itself, it only reports status.
 */
public class PersonDetailActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_BENEFICIARY_ID = "beneficiary_id";
    private static final String EXTRA_BENEFICIARY_TYPE = "beneficiary_type";
    private static final String EXTRA_PERSON_NAME = "person_name";
    private static final String EXTRA_ROLE = "role";
    private static final String EXTRA_METHOD = "method";

    public static Intent intent(Context context, String householdNumber, String beneficiaryId,
            int beneficiaryType, String personName, String role, String registrationMethod) {
        Intent intent = new Intent(context, PersonDetailActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_BENEFICIARY_ID, beneficiaryId);
        intent.putExtra(EXTRA_BENEFICIARY_TYPE, beneficiaryType);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_ROLE, role);
        intent.putExtra(EXTRA_METHOD, registrationMethod);
        return intent;
    }

    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private ImageDao imageDao;

    private String householdNumber;
    private String beneficiaryId;
    private int beneficiaryType;
    private String personName;
    private String role;
    private String registrationMethod;

    private View[] fingerViews;

    private final ActivityResultLauncher<Intent> captureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> render());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_detail);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        imageDao = new ImageDao(this);

        fingerViews = new View[]{
                findViewById(R.id.finger1), findViewById(R.id.finger2), findViewById(R.id.finger3),
                findViewById(R.id.finger4), findViewById(R.id.finger5), findViewById(R.id.finger6),
                findViewById(R.id.finger7), findViewById(R.id.finger8), findViewById(R.id.finger9),
                findViewById(R.id.finger10)};

        Intent intent = getIntent();
        householdNumber = intent.getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        beneficiaryId = intent.getStringExtra(EXTRA_BENEFICIARY_ID);
        beneficiaryType = intent.getIntExtra(EXTRA_BENEFICIARY_TYPE, Beneficiary.TYPE_HOUSEHOLD_HEAD);
        personName = intent.getStringExtra(EXTRA_PERSON_NAME);
        role = intent.getStringExtra(EXTRA_ROLE);
        registrationMethod = intent.getStringExtra(EXTRA_METHOD);

        MaterialButton btnCapture = findViewById(R.id.btnDetailCapture);
        btnCapture.setOnClickListener(v -> captureLauncher.launch(PersonCaptureActivity.captureIntent(
                this, householdNumber, beneficiaryId, beneficiaryType, personName, registrationMethod)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.tvDetailName)).setText(personName);
        ((TextView) findViewById(R.id.tvDetailRole)).setText(role);

        String genderValue;
        Integer ageValue;
        String phoneValue;
        String idNumberValue;
        if (beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD) {
            HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
            genderValue = household == null ? null : household.gender;
            ageValue = household == null ? null : household.age;
            phoneValue = household == null ? null : household.phoneNumber;
            idNumberValue = household == null ? null : household.idNumber;
        } else {
            AlternateDao.Alternate alternate = alternateDao.findByNumber(beneficiaryId);
            genderValue = alternate == null ? null : alternate.gender;
            ageValue = alternate == null ? null : alternate.age;
            phoneValue = alternate == null ? null : alternate.phoneNumber;
            idNumberValue = null; // Not collected for alternates -- see AlternateDao.Alternate.
        }
        ((TextView) findViewById(R.id.tvDetailGender)).setText(displayOrPlaceholder(genderValue));
        ((TextView) findViewById(R.id.tvDetailAge)).setText(ageValue == null ? getString(R.string.person_detail_not_recorded) : String.valueOf(ageValue));
        ((TextView) findViewById(R.id.tvDetailPhone)).setText(displayOrPlaceholder(phoneValue));
        View idRow = findViewById(R.id.rowDetailIdNumber);
        View idDivider = findViewById(R.id.dividerDetailIdNumber);
        boolean showIdRow = idNumberValue != null && !idNumberValue.trim().isEmpty();
        idRow.setVisibility(showIdRow ? View.VISIBLE : View.GONE);
        idDivider.setVisibility(showIdRow ? View.VISIBLE : View.GONE);
        if (showIdRow) ((TextView) findViewById(R.id.tvDetailIdNumber)).setText(idNumberValue);

        Set<Integer> capturedFingers = fingerprintDao.capturedFingerNumbers(beneficiaryId);
        for (int i = 0; i < fingerViews.length; i++) {
            fingerViews[i].setSelected(capturedFingers.contains(i + 1));
        }
        ((TextView) findViewById(R.id.tvDetailFingerprintStatus)).setText(capturedFingers.isEmpty()
                ? getString(R.string.household_person_not_captured)
                : getString(R.string.person_capture_fingerprint_count, capturedFingers.size()));

        boolean hasFace = faceDao.existsForBeneficiary(beneficiaryId);
        ((TextView) findViewById(R.id.tvDetailFaceStatus)).setText(
                hasFace ? R.string.person_capture_captured : R.string.household_person_not_captured);

        ShapeableImageView photo = findViewById(R.id.ivDetailPhoto);
        String photoPath = imageDao.latestLocalPathForBeneficiary(beneficiaryId);
        boolean hasPhoto = ThumbnailLoader.loadInto(photo, photoPath, 900);
        if (!hasPhoto) photo.setImageResource(R.drawable.ic_profile);
        ThumbnailLoader.makeExpandable(photo, photoPath, hasPhoto);
    }

    private String displayOrPlaceholder(String value) {
        return value == null || value.trim().isEmpty() ? getString(R.string.person_detail_not_recorded) : value;
    }
}
