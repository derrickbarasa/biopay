package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.VerificationEventDao;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.BeneficiaryTone;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String EXTRA_BENEFICIARY_TYPE = "beneficiary_type";
    private static final String EXTRA_GENDER = "gender";

    public static Intent intentFor(Context context, String householdNumber, String beneficiaryId, String personName,
            String subtitle, int beneficiaryType, String gender) {
        Intent intent = new Intent(context, FingerprintVerifyActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_BENEFICIARY_ID, beneficiaryId);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_BENEFICIARY_TYPE, beneficiaryType);
        intent.putExtra(EXTRA_GENDER, gender);
        return intent;
    }

    public static final String EXTRA_RESULT_MATCHED_UUID = "matched_uuid";
    public static final String EXTRA_FAILURE_MESSAGE = "failure_message";
    public static final int RESULT_VERIFY_FAILED = RESULT_FIRST_USER;

    private FingerprintDao fingerprintDao;
    private BiometricDevice device;
    private final ExecutorService scannerConnectionExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean sessionEnding;
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
        findViewById(R.id.verificationRoot).setBackgroundColor(ContextCompat.getColor(this,
                BeneficiaryTone.background(getIntent().getIntExtra(EXTRA_BENEFICIARY_TYPE,
                        Beneficiary.TYPE_HOUSEHOLD_HEAD), getIntent().getStringExtra(EXTRA_GENDER))));

        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            sessionEnding = true;
            closeDeviceAsync(true);
            finish();
        });

        startVerify();
    }

    private void startVerify() {
        List<FingerprintDao.StoredTemplate> templates = fingerprintDao.templatesWithUuidForBeneficiary(beneficiaryId);
        if (templates.isEmpty()) {
            failAndFinish(getString(R.string.attendance_no_enrolled_fingerprint));
            return;
        }
        device = BiometricDeviceFactory.create();
        ((TextView) findViewById(R.id.tvVerifyStatus)).setText(R.string.fingerprint_connecting_scanner);

        // Morpho open() powers the embedded sensor, waits for its AIDL service and retries USB
        // enumeration. That can take several seconds and must never run on Android's UI thread.
        scannerConnectionExecutor.execute(() -> {
            try {
                device.open(this, null);
                if (sessionEnding) {
                    device.close();
                    return;
                }
                runOnUiThread(() -> {
                    if (!sessionEnding && !isFinishing() && !isDestroyed()) {
                        attemptVerify(templates, 0);
                    }
                });
            } catch (BiometricDeviceException ex) {
                Log.e(TAG, "Could not open biometric device", ex);
                postConnectionFailure();
            } catch (Throwable ex) {
                // A missing/mismatched vendor native library throws an unchecked Error.
                Log.e(TAG, "BiometricDevice.open() failed unexpectedly", ex);
                postConnectionFailure();
            }
        });
    }

    private void postConnectionFailure() {
        runOnUiThread(() -> {
            if (!sessionEnding && !isFinishing() && !isDestroyed()) {
                failAndFinish(getString(R.string.attendance_verify_error));
            }
        });
    }

    private void attemptVerify(List<FingerprintDao.StoredTemplate> templates, int index) {
        TextView status = findViewById(R.id.tvVerifyStatus);
        if (index >= templates.size()) {
            sessionEnding = true;
            device.close();
            failAndFinish(getString(R.string.payment_result_fingerprint_failed));
            return;
        }
        device.startVerify(templates.get(index).template, new VerifyCallback() {
            @Override public void onProgress(String message) {
                if (!sessionEnding) status.setText(message);
            }

            @Override public void onMatched(int score) {
                if (sessionEnding) return;
                sessionEnding = true;
                device.close();
                new VerificationEventDao(FingerprintVerifyActivity.this)
                        .record(new SessionManager(FingerprintVerifyActivity.this).getPartnerCode(),
                                householdNumber, beneficiaryId, personName, "Fingerprint");
                Intent result = new Intent();
                result.putExtra(EXTRA_RESULT_MATCHED_UUID, templates.get(index).uuid);
                setResult(RESULT_OK, result);
                finish();
            }

            @Override public void onNoMatch() {
                if (sessionEnding) return;
                attemptVerify(templates, index + 1);
            }

            @Override public void onError(int code, String message) {
                if (sessionEnding) return;
                sessionEnding = true;
                device.close();
                failAndFinish(message == null || message.trim().isEmpty()
                        ? getString(R.string.attendance_verify_error)
                        : getString(R.string.fingerprint_verify_error_detail, message));
            }
        });
    }

    private void closeDeviceAsync(boolean cancelFirst) {
        if (device == null || scannerConnectionExecutor.isShutdown()) return;
        scannerConnectionExecutor.execute(() -> {
            if (cancelFirst) device.cancelLiveAcquisition();
            device.close();
        });
    }

    private void failAndFinish(String message) {
        Intent result = new Intent().putExtra(EXTRA_FAILURE_MESSAGE, message);
        setResult(RESULT_VERIFY_FAILED, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        sessionEnding = true;
        closeDeviceAsync(true);
        // shutdown() lets a close queued behind an in-progress open run before releasing the
        // executor; the sessionEnding check prevents that completed open from starting a scan.
        scannerConnectionExecutor.shutdown();
        super.onDestroy();
    }
}
