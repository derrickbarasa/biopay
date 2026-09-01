package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.face.FaceCaptureActivity;
import com.biopay.agent.face.FaceMatchConfig;
import com.biopay.agent.face.FaceMatcher;
import com.biopay.agent.face.FaceRecognitionEngine;
import com.biopay.agent.face.FaceRecognitionException;
import com.biopay.agent.face.MlKitFaceRecognitionEngine;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Selects a beneficiary and enrolled method, then completes one generated payment after a match. */
public class PaymentVerificationActivity extends BaseActivity {
    private static final String EXTRA_PAYMENT_ID = "payment_id";
    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_AMOUNT = "amount";

    public static Intent intentFor(Context context, Integer paymentId, String householdNumber, double amount) {
        Intent intent = new Intent(context, PaymentVerificationActivity.class);
        if (paymentId != null) intent.putExtra(EXTRA_PAYMENT_ID, paymentId);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_AMOUNT, amount);
        return intent;
    }

    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private Integer paymentId;
    private String householdNumber;
    private String householdName;
    private double amount;
    private Beneficiary selectedBeneficiary;
    private boolean selectedFingerprint;
    private Beneficiary pendingFaceBeneficiary;
    private Beneficiary pendingFingerprintBeneficiary;
    private MaterialButton scanButton;

    private final ActivityResultLauncher<Intent> fingerprintVerifyLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == FingerprintVerifyActivity.RESULT_VERIFY_FAILED) {
                    showFailure(getString(R.string.payment_result_fingerprint_failed));
                    return;
                }
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String matchedUuid = result.getData().getStringExtra(FingerprintVerifyActivity.EXTRA_RESULT_MATCHED_UUID);
                Beneficiary beneficiary = pendingFingerprintBeneficiary;
                pendingFingerprintBeneficiary = null;
                if (beneficiary != null && matchedUuid != null) {
                    completePayment(beneficiary, getString(R.string.verify_method_fingerprint_label), matchedUuid, null);
                }
            });

    private final ActivityResultLauncher<Intent> faceCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Beneficiary beneficiary = pendingFaceBeneficiary;
                pendingFaceBeneficiary = null;
                if (beneficiary == null || result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(FaceCaptureActivity.EXTRA_RESULT_IMAGE_PATH);
                if (path != null) matchFace(beneficiary, path);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_verification);
        setupBackToolbar(R.id.toolbar);
        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        paymentId = getIntent().hasExtra(EXTRA_PAYMENT_ID) ? getIntent().getIntExtra(EXTRA_PAYMENT_ID, -1) : null;
        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);

        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        householdName = household != null && household.householdName != null && !household.householdName.isEmpty()
                ? household.householdName : householdNumber;
        ((TextView) findViewById(R.id.tvHouseholdName)).setText(householdName);
        ((TextView) findViewById(R.id.tvAmount)).setText(
                getString(R.string.payment_amount, NumberFormat.getNumberInstance().format(amount)));

        scanButton = findViewById(R.id.btnScanPayment);
        PaymentBeneficiaryAdapter adapter = new PaymentBeneficiaryAdapter((beneficiary, fingerprint) -> {
            selectedBeneficiary = beneficiary;
            selectedFingerprint = fingerprint;
            scanButton.setEnabled(true);
            scanButton.setText(fingerprint ? R.string.payment_scan_fingerprint : R.string.payment_scan_face);
            scanButton.setIconResource(fingerprint ? R.drawable.ic_fingerprint : R.drawable.ic_face);
        });
        RecyclerView recycler = findViewById(R.id.recyclerBeneficiaries);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        List<PaymentBeneficiaryAdapter.Row> beneficiaries = buildBeneficiaries(household);
        adapter.submitList(beneficiaries);
        findViewById(R.id.verificationEmptyState).setVisibility(
                beneficiaries.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        scanButton.setOnClickListener(v -> startSelectedVerification());
    }

    private List<PaymentBeneficiaryAdapter.Row> buildBeneficiaries(HouseholdDao.Household household) {
        List<PaymentBeneficiaryAdapter.Row> rows = new ArrayList<>();
        if (household != null) {
            rows.add(toRow(new Beneficiary(household.householdNumber, household.householdNumber,
                    Beneficiary.TYPE_HOUSEHOLD_HEAD, household.householdName,
                    getString(R.string.attendance_beneficiary_head))));
        }
        for (AlternateDao.Alternate alternate : alternateDao.findByHousehold(householdNumber)) {
            rows.add(toRow(new Beneficiary(alternate.alternateNumber, householdNumber,
                    Beneficiary.TYPE_ALTERNATE, alternate.alternateName,
                    getString(R.string.alternate_detail,
                            getString(R.string.attendance_beneficiary_alternate), alternate.relationship))));
        }
        return rows;
    }

    private PaymentBeneficiaryAdapter.Row toRow(Beneficiary beneficiary) {
        return new PaymentBeneficiaryAdapter.Row(beneficiary,
                fingerprintDao.countForBeneficiary(beneficiary.beneficiaryId) > 0,
                faceDao.existsForBeneficiary(beneficiary.beneficiaryId));
    }

    private void startSelectedVerification() {
        if (selectedBeneficiary == null) return;
        if (selectedFingerprint) {
            pendingFingerprintBeneficiary = selectedBeneficiary;
            fingerprintVerifyLauncher.launch(FingerprintVerifyActivity.intentFor(this, householdNumber,
                    selectedBeneficiary.beneficiaryId, selectedBeneficiary.name, selectedBeneficiary.subtitle));
        } else {
            pendingFaceBeneficiary = selectedBeneficiary;
            faceCaptureLauncher.launch(new Intent(this, FaceCaptureActivity.class));
        }
    }

    private void matchFace(Beneficiary beneficiary, String imagePath) {
        scanButton.setEnabled(false);
        scanButton.setText(R.string.payment_verifying_face);
        new Thread(() -> {
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                FaceRecognitionEngine.CaptureResult probe = engine.createEmbedding(readFile(imagePath));
                FaceDao.FaceRecord matched = null;
                for (FaceDao.FaceRecord record : faceDao.listForBeneficiary(
                        beneficiary.beneficiaryId, engine.modelVersion())) {
                    if (FaceMatcher.matches(probe.embedding, toFloatArray(new JSONArray(record.embedding)),
                            FaceMatchConfig.UNCALIBRATED_PLACEHOLDER_THRESHOLD)) {
                        matched = record;
                        break;
                    }
                }
                FaceDao.FaceRecord result = matched;
                runOnUiThread(() -> {
                    if (result == null) {
                        showFailure(getString(R.string.payment_result_face_failed));
                    } else {
                        new com.biopay.agent.data.VerificationEventDao(this)
                                .record(householdNumber, beneficiary.beneficiaryId, beneficiary.name, "Face");
                        completePayment(beneficiary, getString(R.string.settings_face_title), null, result.uuid);
                    }
                });
            } catch (FaceRecognitionException | IOException | org.json.JSONException | IllegalArgumentException ex) {
                runOnUiThread(() -> showFailure(getString(R.string.payment_result_face_error)));
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
        }).start();
    }

    private void completePayment(Beneficiary beneficiary, String method, String fingerprintUuid, String faceUuid) {
        SessionManager session = new SessionManager(this);
        Location location = LocationHelper.getLastKnownLocation(this);
        String latitude = location == null ? null : String.valueOf(location.getLatitude());
        String longitude = location == null ? null : String.valueOf(location.getLongitude());
        PaymentDao dao = new PaymentDao(this);
        boolean recorded;
        if (paymentId != null && paymentId >= 0) {
            recorded = dao.completeGeneratedPayment(paymentId, String.valueOf(session.getUserId()), householdName,
                    fingerprintUuid, faceUuid, latitude, longitude) == 1;
        } else {
            recorded = dao.recordFieldPayment(String.valueOf(session.getUserId()), session.getPartnerCode(),
                    householdNumber, householdName, amount, fingerprintUuid, faceUuid, latitude, longitude,
                    UUID.randomUUID().toString(), "CASH") != -1;
        }
        if (!recorded) {
            showFailure(getString(R.string.payment_result_unavailable));
            return;
        }
        SyncScheduler.triggerNow(this);
        startActivity(PaymentResultActivity.successIntent(this, beneficiary.name, householdName, amount, method));
        finish();
    }

    private void showFailure(String message) {
        // Only a generated payment has a remote row to fail -- an ad-hoc field payment with no
        // paymentId never reached the server, so there's nothing there to mark FAILED.
        if (paymentId != null && paymentId >= 0) {
            new PaymentDao(this).markGeneratedPaymentFailed(paymentId);
            SyncScheduler.triggerNow(this);
        }
        startActivity(PaymentResultActivity.failureIntent(this, message, householdName, amount));
    }

    private static float[] toFloatArray(JSONArray array) throws org.json.JSONException {
        float[] result = new float[array.length()];
        for (int i = 0; i < array.length(); i++) result[i] = (float) array.getDouble(i);
        return result;
    }

    private static byte[] readFile(String path) throws IOException {
        File file = new File(path);
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = in.read(bytes, offset, bytes.length - offset)) >= 0) offset += read;
        }
        return bytes;
    }
}
