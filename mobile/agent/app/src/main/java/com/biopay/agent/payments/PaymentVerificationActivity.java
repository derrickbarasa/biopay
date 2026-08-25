package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verifies whichever household member shows up to collect a pending field payment -- by
 * fingerprint and/or face, whichever that specific person actually has enrolled (see {@link
 * #buildBeneficiaries}) -- then records the disbursement via {@link PaymentDao#recordFieldPayment}.
 *
 * <p>This is the first real caller of {@code RECORD_FIELD_PAYMENT} from the mobile app: the
 * backend endpoint and {@code PaymentDao.recordFieldPayment} both existed fully implemented with
 * no UI ever calling them (progress.md's original plan even named this exact screen,
 * "PaymentVerificationActivity", years before it was actually built). The fingerprint path
 * mirrors {@code AttendanceBeneficiariesActivity}'s already-proven verify loop; the face path is
 * this app's first live face verification against a stored embedding -- until today only face
 * capture/enrollment existed. Face remains an explicitly unvalidated prototype (see {@link
 * MlKitFaceRecognitionEngine}'s javadoc) -- shown with the same accuracy warning used at capture
 * time, not hidden here just because money is involved.
 */
public class PaymentVerificationActivity extends BaseActivity {

    private static final String TAG = "PaymentVerification";
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
    private PaymentDao paymentDao;
    private SessionManager sessionManager;

    private String householdNumber;
    private String householdName;
    private double amount;
    private Beneficiary pendingFaceBeneficiary;

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
        paymentDao = new PaymentDao(this);
        sessionManager = new SessionManager(this);

        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);

        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        householdName = household != null && household.householdName != null && !household.householdName.isEmpty()
                ? household.householdName : householdNumber;
        ((TextView) findViewById(R.id.tvHouseholdName)).setText(householdName);
        ((TextView) findViewById(R.id.tvAmount)).setText(
                getString(R.string.payment_amount, NumberFormat.getNumberInstance().format(amount)));

        PaymentBeneficiaryAdapter adapter = new PaymentBeneficiaryAdapter(new PaymentBeneficiaryAdapter.OnVerifyListener() {
            @Override public void onVerifyFingerprint(Beneficiary beneficiary) { startFingerprintVerify(beneficiary); }
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

    // ---- Fingerprint verify (exact pattern proven in AttendanceBeneficiariesActivity) -----

    private void startFingerprintVerify(Beneficiary beneficiary) {
        List<FingerprintDao.StoredTemplate> templates = fingerprintDao.templatesWithUuidForBeneficiary(beneficiary.beneficiaryId);
        if (templates.isEmpty()) {
            Toast.makeText(this, R.string.attendance_no_enrolled_fingerprint, Toast.LENGTH_SHORT).show();
            return;
        }
        BiometricDevice device = BiometricDeviceFactory.create();
        try {
            device.open(this, null);
        } catch (BiometricDeviceException ex) {
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        } catch (Throwable ex) {
            // See PersonCaptureActivity's matching fix -- a missing/mismatched vendor native
            // library throws an unchecked UnsatisfiedLinkError, not the checked exception open()
            // declares; caught broadly here for the same reason.
            Log.e(TAG, "BiometricDevice.open() failed unexpectedly", ex);
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        TextView progress = content.findViewById(R.id.tvVerifyProgress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_verify_title)
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (d, which) -> {
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();
        com.biopay.agent.session.SessionTimeoutManager.keepAlive(dialog);
        attemptFingerprintVerify(device, templates, 0, progress, dialog, beneficiary);
    }

    private void attemptFingerprintVerify(BiometricDevice device, List<FingerprintDao.StoredTemplate> templates,
            int index, TextView progress, AlertDialog dialog, Beneficiary beneficiary) {
        if (index >= templates.size()) {
            device.close();
            dialog.dismiss();
            Toast.makeText(this, R.string.attendance_no_match, Toast.LENGTH_SHORT).show();
            return;
        }
        device.startVerify(templates.get(index).template, new VerifyCallback() {
            @Override public void onProgress(String message) { progress.setText(message); }

            @Override public void onMatched(int score) {
                device.close();
                dialog.dismiss();
                recordPayment(beneficiary, templates.get(index).uuid, null);
            }

            @Override public void onNoMatch() {
                attemptFingerprintVerify(device, templates, index + 1, progress, dialog, beneficiary);
            }

            @Override public void onError(int code, String message) {
                device.close();
                dialog.dismiss();
                Toast.makeText(PaymentVerificationActivity.this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Face verify (first live face verification anywhere in the app -- see class javadoc) --

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
                        recordPayment(beneficiary, null, finalMatched.uuid);
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

    // ---- Record the disbursement ------------------------------------------------------------

    private void recordPayment(Beneficiary beneficiary, String matchedFingerprintUuid, String matchedFaceUuid) {
        Location location = LocationHelper.getLastKnownLocation(this);
        String latitude = location == null ? null : String.valueOf(location.getLatitude());
        String longitude = location == null ? null : String.valueOf(location.getLongitude());

        paymentDao.recordFieldPayment(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                householdNumber, householdName, amount, matchedFingerprintUuid, matchedFaceUuid,
                latitude, longitude, UUID.randomUUID().toString());
        Toast.makeText(this, R.string.payment_verify_recorded, Toast.LENGTH_LONG).show();
        SyncScheduler.triggerNow(this);
        finish();
    }
}
