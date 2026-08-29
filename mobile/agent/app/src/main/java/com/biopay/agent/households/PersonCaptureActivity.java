package com.biopay.agent.households;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.CaptureCallback;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.face.FaceCaptureActivity;
import com.biopay.agent.face.FaceRecognitionEngine;
import com.biopay.agent.face.FaceRecognitionException;
import com.biopay.agent.face.MlKitFaceRecognitionEngine;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Captures a person's chosen biometric method(s) -- fingerprint, face, or both -- and ties each
 * capture to a household head or alternate via the existing {@code beneficiaryId}/{@code
 * beneficiaryType} convention ({@link Beneficiary}). This is the first real caller of {@link
 * BiometricDevice#startCapture} (enrollment) and {@link FaceDao#savePending}/{@link
 * FingerprintDao#save} from an actual registration flow -- all three existed fully implemented
 * but were never invoked outside a diagnostic test screen before this. Reached from {@link
 * HouseholdFormActivity} after saving a new household (for the head), or from its "Captured
 * people" section when editing an existing one (for the head if still missing, or a new/updated
 * alternate).
 */
public class PersonCaptureActivity extends BaseActivity {

    private static final String TAG = "PersonCaptureActivity";
    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_BENEFICIARY_ID = "beneficiary_id";
    private static final String EXTRA_BENEFICIARY_TYPE = "beneficiary_type";
    private static final String EXTRA_PERSON_NAME = "person_name";
    private static final String EXTRA_METHOD = "method";

    /** @param method one of "FINGERPRINT", "FACE", or "FINGERPRINT_AND_FACE". Re-entrant: if the
     *  person already has some (but not all) of the needed captures -- e.g. resuming after an
     *  earlier attempt was interrupted -- only what's still missing is asked for. */
    public static Intent captureIntent(Context context, String householdNumber, String beneficiaryId,
            int beneficiaryType, String personName, String method) {
        Intent intent = new Intent(context, PersonCaptureActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_BENEFICIARY_ID, beneficiaryId);
        intent.putExtra(EXTRA_BENEFICIARY_TYPE, beneficiaryType);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_METHOD, method);
        return intent;
    }

    /** Launches straight into "Add another person" with no initial target -- used by the
     *  household edit screen's own "Add another person" action, which has no existing alternate
     *  to capture yet (unlike {@link #captureIntent}, which always targets someone specific). */
    public static Intent addPersonIntent(Context context, String householdNumber, String method) {
        Intent intent = new Intent(context, PersonCaptureActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_METHOD, method);
        return intent;
    }

    private SessionManager sessionManager;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private AlternateDao alternateDao;

    private String householdNumber;
    private String method;
    private String beneficiaryId;
    private int beneficiaryType;
    private String personName;

    private boolean needsFingerprint;
    private boolean needsFace;
    private boolean fingerprintCaptured;
    private boolean faceCaptured;

    private TextView tvPersonName;
    private TextView tvPersonSubtitle;
    private View rowFingerprint;
    private View rowFace;
    private TextView tvFingerprintRowStatus;
    private TextView tvFaceRowStatus;
    private View doneSection;
    private TextView tvDoneMessage;

    private final ActivityResultLauncher<Intent> faceCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(FaceCaptureActivity.EXTRA_RESULT_IMAGE_PATH);
                if (path != null) embedFace(path);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_capture);
        setupBackToolbar(R.id.toolbar);

        sessionManager = new SessionManager(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        alternateDao = new AlternateDao(this);

        tvPersonName = findViewById(R.id.tvPersonName);
        tvPersonSubtitle = findViewById(R.id.tvPersonSubtitle);
        rowFingerprint = findViewById(R.id.rowFingerprint);
        rowFace = findViewById(R.id.rowFace);
        tvFingerprintRowStatus = findViewById(R.id.tvFingerprintRowStatus);
        tvFaceRowStatus = findViewById(R.id.tvFaceRowStatus);
        doneSection = findViewById(R.id.doneSection);
        tvDoneMessage = findViewById(R.id.tvDoneMessage);

        findViewById(R.id.btnCaptureFingerprint).setOnClickListener(v -> captureFingerprint());
        findViewById(R.id.btnCaptureFace).setOnClickListener(v ->
                faceCaptureLauncher.launch(new Intent(this, FaceCaptureActivity.class)));
        findViewById(R.id.btnAddPerson).setOnClickListener(v -> showAddAlternateDialog());
        findViewById(R.id.btnFinish).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        Intent intent = getIntent();
        householdNumber = intent.getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        method = intent.getStringExtra(EXTRA_METHOD);
        String initialBeneficiaryId = intent.getStringExtra(EXTRA_BENEFICIARY_ID);
        if (initialBeneficiaryId != null) {
            applyPerson(initialBeneficiaryId, intent.getIntExtra(EXTRA_BENEFICIARY_TYPE, Beneficiary.TYPE_HOUSEHOLD_HEAD),
                    intent.getStringExtra(EXTRA_PERSON_NAME));
        } else {
            // addPersonIntent(): nothing to show until a new alternate exists.
            showAddAlternateDialog();
        }
    }

    /** (Re)targets the screen at a person -- used for the initial launch and again, in place,
     *  when "Add another person" creates a new alternate to capture next. Re-entrant: checks
     *  what this person already has captured (e.g. resuming after an earlier interruption, or
     *  completing a still-missing method under a BOTH policy) rather than always starting fresh. */
    private void applyPerson(String newBeneficiaryId, int newBeneficiaryType, String newPersonName) {
        beneficiaryId = newBeneficiaryId;
        beneficiaryType = newBeneficiaryType;
        personName = newPersonName;

        needsFingerprint = "FINGERPRINT".equals(method) || "FINGERPRINT_AND_FACE".equals(method);
        needsFace = "FACE".equals(method) || "FINGERPRINT_AND_FACE".equals(method);
        fingerprintCaptured = needsFingerprint && fingerprintDao.countForBeneficiary(beneficiaryId) > 0;
        faceCaptured = needsFace && faceDao.existsForBeneficiary(beneficiaryId);

        tvPersonName.setText(personName);
        tvPersonSubtitle.setText(beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD
                ? R.string.person_capture_subtitle_head : R.string.person_capture_subtitle_alternate);
        rowFingerprint.setVisibility(needsFingerprint ? View.VISIBLE : View.GONE);
        rowFace.setVisibility(needsFace ? View.VISIBLE : View.GONE);
        tvFingerprintRowStatus.setText(fingerprintCaptured ? R.string.person_capture_captured : R.string.person_capture_not_captured);
        tvFaceRowStatus.setText(faceCaptured ? R.string.person_capture_captured : R.string.person_capture_not_captured);
        doneSection.setVisibility(View.GONE);
        updateDoneState();
    }

    private void updateDoneState() {
        boolean fingerprintDone = !needsFingerprint || fingerprintCaptured;
        boolean faceDone = !needsFace || faceCaptured;
        if (fingerprintDone && faceDone) {
            tvDoneMessage.setText(getString(R.string.person_capture_done, personName));
            doneSection.setVisibility(View.VISIBLE);
        }
    }

    // ---- Fingerprint capture --------------------------------------------------------------

    private void captureFingerprint() {
        BiometricDevice device = BiometricDeviceFactory.create();
        try {
            device.open(this, null);
        } catch (BiometricDeviceException ex) {
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        } catch (Throwable ex) {
            // The vendor SDK's open() only declares BiometricDeviceException, but a missing/
            // mismatched native library on a given device throws an unchecked UnsatisfiedLinkError
            // instead (confirmed on-device: "libNativeMorphoSmartSDK_6.42.0.0.so not found") --
            // caught broadly here so a hardware/library problem degrades to the same honest
            // message rather than crashing the app. (The pre-existing verify() call sites in
            // VoucherRedemptionActivity/AttendanceBeneficiariesActivity only catch the checked
            // exception and share this same latent risk -- out of scope to fix here, but worth
            // hardening the same way if this recurs there.)
            Log.e(TAG, "BiometricDevice.open() failed unexpectedly", ex);
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        TextView progress = content.findViewById(R.id.tvVerifyProgress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.person_capture_fingerprint_action)
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (ignored, which) -> {
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();

        device.startCapture(1, new CaptureCallback() {
            @Override public void onProgress(String message) { progress.setText(message); }
            @Override public void onPreviewFrame(Bitmap frame) { }

            @Override public void onCaptured(byte[] template, Bitmap finalImage) {
                device.close();
                dialog.dismiss();
                fingerprintDao.save(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                        beneficiaryType, beneficiaryId, 1, UUID.randomUUID().toString(), template,
                        device.getDeviceId());
                fingerprintCaptured = true;
                tvFingerprintRowStatus.setText(R.string.person_capture_captured);
                updateDoneState();
            }

            @Override public void onError(int errorCode, String message) {
                device.close();
                dialog.dismiss();
                Toast.makeText(PersonCaptureActivity.this,
                        getString(R.string.person_capture_failed, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---- Face capture (real embedding pipeline; still an explicitly unvalidated prototype --
    // see MlKitFaceRecognitionEngine's javadoc) ---------------------------------------------

    private void embedFace(String imagePath) {
        new Thread(() -> {
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                byte[] bytes = readFile(imagePath);
                FaceRecognitionEngine.CaptureResult result = engine.createEmbedding(bytes);
                JSONArray embeddingJson = new JSONArray();
                for (float v : result.embedding) embeddingJson.put(v);
                faceDao.savePending(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                        beneficiaryType, beneficiaryId, UUID.randomUUID().toString(), embeddingJson,
                        engine.modelVersion(), result.qualityScore);
                runOnUiThread(() -> {
                    faceCaptured = true;
                    tvFaceRowStatus.setText(R.string.person_capture_captured);
                    updateDoneState();
                });
            } catch (FaceRecognitionException | IOException | org.json.JSONException ex) {
                String detail = ex.getMessage();
                runOnUiThread(() -> Toast.makeText(PersonCaptureActivity.this,
                        getString(R.string.person_capture_failed, detail), Toast.LENGTH_LONG).show());
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
        }).start();
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

    // ---- Add another person -- the app's first "create alternate" UI; AlternateDao and its
    // sync/backend path (UPLOAD_ALTERNATE_BIO) already existed end to end with no caller ------

    private void showAddAlternateDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_add_alternate, null);
        EditText etName = content.findViewById(R.id.etAlternateName);
        EditText etRelationship = content.findViewById(R.id.etAlternateRelationship);
        EditText etAge = content.findViewById(R.id.etAlternateAge);
        EditText etPhone = content.findViewById(R.id.etAlternatePhone);

        AlertDialog addPersonDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.person_capture_add_another)
                .setView(content)
                .setCancelable(beneficiaryId != null)
                .setNegativeButton(R.string.person_capture_add_cancel, (dialog, which) -> {
                    // Launched via addPersonIntent() with nobody to show yet -- cancelling
                    // the only thing this screen can do leaves nothing to show.
                    if (beneficiaryId == null) finish();
                })
                .setPositiveButton(R.string.person_capture_add_confirm, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.field_alternate_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String alternateNumber = "ALT" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.US);
                    ContentValues values = new ContentValues();
                    values.put("alternate_number", alternateNumber);
                    values.put("household_number", householdNumber);
                    values.put("supervisor_id", String.valueOf(sessionManager.getUserId()));
                    values.put("partner_code", sessionManager.getPartnerCode());
                    values.put("alternate_name", name);
                    values.put("relationship", etRelationship.getText().toString().trim());
                    values.put("age", parseIntOrNull(etAge.getText().toString()));
                    values.put("phone_number", etPhone.getText().toString().trim());
                    values.put("registration_method", method);
                    alternateDao.insert(values);

                    applyPerson(alternateNumber, Beneficiary.TYPE_ALTERNATE, name);
                })
                .show();
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
