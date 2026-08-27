package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

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
import com.biopay.agent.face.FaceCaptureActivity;
import com.biopay.agent.face.FaceMatchConfig;
import com.biopay.agent.face.FaceMatcher;
import com.biopay.agent.face.FaceRecognitionEngine;
import com.biopay.agent.face.FaceRecognitionException;
import com.biopay.agent.face.MlKitFaceRecognitionEngine;
import com.biopay.agent.ui.BaseActivity;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks which household member is collecting a pending field payment -- by whichever method
 * (fingerprint and/or face) that specific person actually has enrolled (see
 * {@link #buildBeneficiaries}) -- then hands off to the redesigned verify -> disburse -> review
 * -> success wizard ({@link FingerprintVerifyActivity} / face match here -> {@link
 * VerifySuccessActivity} -> {@link DisbursementActivity} -> {@link DisbursementReviewActivity}).
 * This screen itself is unchanged from before the redesign -- it's the destination of each
 * row's tap that changed, from an inline dialog/immediate-record to that full flow.
 */
public class PaymentVerificationActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_AMOUNT = "amount";

    public static Intent intentFor(Context context, String householdNumber, double amount) {
        Intent intent = new Intent(context, PaymentVerificationActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_AMOUNT, amount);
        return intent;
    }

    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;

    private String householdNumber;
    private String householdName;
    private double amount;
    private Beneficiary pendingFaceBeneficiary;
    private Beneficiary pendingFingerprintBeneficiary;

    private final ActivityResultLauncher<Intent> fingerprintVerifyLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String matchedUuid = result.getData().getStringExtra(FingerprintVerifyActivity.EXTRA_RESULT_MATCHED_UUID);
                Beneficiary beneficiary = pendingFingerprintBeneficiary;
                pendingFingerprintBeneficiary = null;
                if (beneficiary != null && matchedUuid != null) {
                    proceedToSuccess(beneficiary, getString(R.string.verify_method_fingerprint_label), matchedUuid, null);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_verification);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);

        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);

        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        householdName = household != null && household.householdName != null && !household.householdName.isEmpty()
                ? household.householdName : householdNumber;
        ((TextView) findViewById(R.id.tvHouseholdName)).setText(householdName);
        ((TextView) findViewById(R.id.tvAmount)).setText(
                getString(R.string.payment_amount, NumberFormat.getNumberInstance().format(amount)));

        PaymentBeneficiaryAdapter adapter = new PaymentBeneficiaryAdapter(new PaymentBeneficiaryAdapter.OnVerifyListener() {
            @Override public void onVerifyFingerprint(Beneficiary beneficiary) {
                pendingFingerprintBeneficiary = beneficiary;
                fingerprintVerifyLauncher.launch(FingerprintVerifyActivity.intentFor(
                        PaymentVerificationActivity.this, householdNumber, beneficiary.beneficiaryId, beneficiary.name, beneficiary.subtitle));
            }
            @Override public void onVerifyFace(Beneficiary beneficiary) { startFaceCapture(beneficiary); }
        });
        RecyclerView recyclerView = findViewById(R.id.recyclerBeneficiaries);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.submitList(buildBeneficiaries(household));
    }

    /** Checks what each person actually has captured (not the stored registration_method label --
     *  same principle as HouseholdFormActivity's "Captured people" section) so only real, usable
     *  verification methods are offered. */
    private List<PaymentBeneficiaryAdapter.Row> buildBeneficiaries(HouseholdDao.Household household) {
        List<PaymentBeneficiaryAdapter.Row> rows = new ArrayList<>();
        if (household != null) {
            Beneficiary head = new Beneficiary(household.householdNumber, household.householdNumber,
                    Beneficiary.TYPE_HOUSEHOLD_HEAD, household.householdName,
                    getString(R.string.attendance_beneficiary_head));
            rows.add(toRow(head));
        }
        for (AlternateDao.Alternate alternate : alternateDao.findByHousehold(householdNumber)) {
            Beneficiary alt = new Beneficiary(alternate.alternateNumber, householdNumber,
                    Beneficiary.TYPE_ALTERNATE, alternate.alternateName,
                    getString(R.string.alternate_detail,
                            getString(R.string.attendance_beneficiary_alternate), alternate.relationship));
            rows.add(toRow(alt));
        }
        return rows;
    }

    private PaymentBeneficiaryAdapter.Row toRow(Beneficiary beneficiary) {
        boolean hasFingerprint = fingerprintDao.countForBeneficiary(beneficiary.beneficiaryId) > 0;
        boolean hasFace = faceDao.existsForBeneficiary(beneficiary.beneficiaryId);
        return new PaymentBeneficiaryAdapter.Row(beneficiary, hasFingerprint, hasFace);
    }

    // ---- Face verify (first live face verification anywhere in the app) --------------------

    private void startFaceCapture(Beneficiary beneficiary) {
        pendingFaceBeneficiary = beneficiary;
        faceCaptureLauncher.launch(new Intent(this, FaceCaptureActivity.class));
    }

    private void matchFace(Beneficiary beneficiary, String imagePath) {
        new Thread(() -> {
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                byte[] bytes = readFile(imagePath);
                FaceRecognitionEngine.CaptureResult probeResult = engine.createEmbedding(bytes);
                List<FaceDao.FaceRecord> enrolled = faceDao.listForBeneficiary(beneficiary.beneficiaryId, engine.modelVersion());
                FaceDao.FaceRecord matched = null;
                for (FaceDao.FaceRecord record : enrolled) {
                    float[] storedEmbedding = toFloatArray(new JSONArray(record.embedding));
                    if (FaceMatcher.matches(probeResult.embedding, storedEmbedding, FaceMatchConfig.UNCALIBRATED_PLACEHOLDER_THRESHOLD)) {
                        matched = record;
                        break;
                    }
                }
                FaceDao.FaceRecord finalMatched = matched;
                runOnUiThread(() -> {
                    if (finalMatched != null) {
                        new com.biopay.agent.data.VerificationEventDao(this)
                                .record(householdNumber, beneficiary.beneficiaryId, beneficiary.name, "Face");
                        proceedToSuccess(beneficiary, getString(R.string.settings_face_title), null, finalMatched.uuid);
                    } else {
                        Toast.makeText(this, R.string.payment_verify_no_face_match, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (FaceRecognitionException | IOException | org.json.JSONException | IllegalArgumentException ex) {
                String detail = ex.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.person_capture_failed, detail), Toast.LENGTH_LONG).show());
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
        }).start();
    }

    private static float[] toFloatArray(JSONArray array) throws org.json.JSONException {
        float[] result = new float[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = (float) array.getDouble(i);
        }
        return result;
    }

    private static byte[] readFile(String path) throws IOException {
        File file = new File(path);
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = in.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += read;
            }
        }
        return bytes;
    }

    // ---- Hand off to the shared success screen, then Disbursement --------------------------

    private void proceedToSuccess(Beneficiary beneficiary, String methodLabel, String matchedFingerprintUuid, String matchedFaceUuid) {
        Intent disbursement = DisbursementActivity.intentFor(this, householdNumber, householdName, amount,
                beneficiary.name, methodLabel, matchedFingerprintUuid, matchedFaceUuid);
        Intent success = VerifySuccessActivity.intentFor(this, VerifySuccessActivity.MODE_VERIFICATION,
                getString(R.string.verify_success_title), beneficiary.name + " · " + householdName,
                new String[]{getString(R.string.verify_success_verified_using, methodLabel)}, new String[]{""},
                getString(R.string.verify_success_continue), disbursement);
        startActivity(success);
    }
}
