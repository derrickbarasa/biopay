package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.VerificationEventDao;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;

/**
 * Full-screen fingerprint verification -- hosts the exact capture sequence that used to live in
 * a modal dialog inside PaymentVerificationActivity (device.open/startVerify/cancelLiveAcquisition
 * /close, the same VerifyCallback contract), moved verbatim rather than reimplemented.
 */
public class FingerprintVerifyActivity extends BaseActivity {

    private static final String TAG = "FingerprintVerify";
    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_BENEFICIARY_ID = "beneficiary_id";
    private static final String EXTRA_PERSON_NAME = "person_name";
    private static final String EXTRA_SUBTITLE = "subtitle";

    public static Intent intentFor(Context context, String householdNumber, String beneficiaryId, String personName, String subtitle) {
        Intent intent = new Intent(context, FingerprintVerifyActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_BENEFICIARY_ID, beneficiaryId);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        return intent;
    }

    public static final String EXTRA_RESULT_MATCHED_UUID = "matched_uuid";

    private FingerprintDao fingerprintDao;
    private BiometricDevice device;
    private String householdNumber;
    private String beneficiaryId;
    private String personName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_verify);
        setupBackToolbar(R.id.toolbar);

        fingerprintDao = new FingerprintDao(this);
        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        beneficiaryId = getIntent().getStringExtra(EXTRA_BENEFICIARY_ID);
        personName = getIntent().getStringExtra(EXTRA_PERSON_NAME);
        ((TextView) findViewById(R.id.tvPersonName)).setText(personName);
        ((TextView) findViewById(R.id.tvPersonSubtitle)).setText(getIntent().getStringExtra(EXTRA_SUBTITLE));

        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            if (device != null) {
                device.cancelLiveAcquisition();
                device.close();
            }
            finish();
        });

        startVerify();
    }

    private void startVerify() {
        List<FingerprintDao.StoredTemplate> templates = fingerprintDao.templatesWithUuidForBeneficiary(beneficiaryId);
        if (templates.isEmpty()) {
            Toast.makeText(this, R.string.attendance_no_enrolled_fingerprint, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        device = BiometricDeviceFactory.create();
        try {
            device.open(this, null);
        } catch (BiometricDeviceException ex) {
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        } catch (Throwable ex) {
            // See PersonCaptureActivity's matching fix -- a missing/mismatched vendor native
            // library throws an unchecked UnsatisfiedLinkError, not the checked exception open()
            // declares; caught broadly here for the same reason.
            Log.e(TAG, "BiometricDevice.open() failed unexpectedly", ex);
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        attemptVerify(templates, 0);
    }

    private void attemptVerify(List<FingerprintDao.StoredTemplate> templates, int index) {
        TextView status = findViewById(R.id.tvVerifyStatus);
        if (index >= templates.size()) {
            device.close();
            Toast.makeText(this, R.string.attendance_no_match, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        device.startVerify(templates.get(index).template, new VerifyCallback() {
            @Override public void onProgress(String message) { status.setText(message); }

            @Override public void onMatched(int score) {
                device.close();
                new VerificationEventDao(FingerprintVerifyActivity.this)
                        .record(householdNumber, beneficiaryId, personName, "Fingerprint");
                Intent result = new Intent();
                result.putExtra(EXTRA_RESULT_MATCHED_UUID, templates.get(index).uuid);
                setResult(RESULT_OK, result);
                finish();
            }

            @Override public void onNoMatch() {
                attemptVerify(templates, index + 1);
            }

            @Override public void onError(int code, String message) {
                device.close();
                Toast.makeText(FingerprintVerifyActivity.this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
