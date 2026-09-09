package com.biopay.agent.households;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.CaptureCallback;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.ImageDao;
import com.biopay.agent.face.FaceCaptureActivity;
import com.biopay.agent.face.FaceMatchConfig;
import com.biopay.agent.face.FaceMatcher;
import com.biopay.agent.face.FaceRecognitionEngine;
import com.biopay.agent.face.FaceRecognitionException;
import com.biopay.agent.face.MlKitFaceRecognitionEngine;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.OutcomeFeedback;
import com.biopay.agent.ui.ThumbnailLoader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private ImageDao imageDao;

    private String householdNumber;
    private String method;
    private String beneficiaryId;
    private int beneficiaryType;
    private String personName;

    private boolean needsFingerprint;
    private boolean needsFace;
    private boolean fingerprintCaptured;
    private boolean faceCaptured;
    private final Set<Integer> capturedFingers = new HashSet<>();

    private TextView tvPersonName;
    private TextView tvPersonSubtitle;
    private View rowFingerprint;
    private View rowFace;
    private TextView tvFingerprintRowStatus;
    private TextView tvFaceRowStatus;
    private View doneSection;
    private TextView tvDoneMessage;

    /** {@code fingerViews[i]} is the tappable circle for {@link FingerPosition#RIGHT_HAND}[0..4]
     *  then {@link FingerPosition#LEFT_HAND}[0..4], i.e. index i holds finger position i+1. */
    private View[] fingerViews;
    private ImageView ivPersonPhoto;
    private TextView tvPhotoRowStatus;
    private MaterialButton btnCapturePhoto;

    private final ActivityResultLauncher<Intent> faceCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(FaceCaptureActivity.EXTRA_RESULT_IMAGE_PATH);
                if (path != null) embedFace(path);
            });

    private final ActivityResultLauncher<Intent> photoCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(PhotoCaptureActivity.EXTRA_RESULT_IMAGE_PATH);
                if (path != null) savePhoto(path);
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    photoCaptureLauncher.launch(new Intent(this, PhotoCaptureActivity.class));
                } else {
                    OutcomeFeedback.error(this, R.string.photo_capture_permission_denied);
                }
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
        imageDao = new ImageDao(this);

        tvPersonName = findViewById(R.id.tvPersonName);
        tvPersonSubtitle = findViewById(R.id.tvPersonSubtitle);
        rowFingerprint = findViewById(R.id.rowFingerprint);
        rowFace = findViewById(R.id.rowFace);
        tvFingerprintRowStatus = findViewById(R.id.tvFingerprintRowStatus);
        tvFaceRowStatus = findViewById(R.id.tvFaceRowStatus);
        doneSection = findViewById(R.id.doneSection);
        tvDoneMessage = findViewById(R.id.tvDoneMessage);

        fingerViews = new View[]{
                findViewById(R.id.finger1), findViewById(R.id.finger2), findViewById(R.id.finger3),
                findViewById(R.id.finger4), findViewById(R.id.finger5), findViewById(R.id.finger6),
                findViewById(R.id.finger7), findViewById(R.id.finger8), findViewById(R.id.finger9),
                findViewById(R.id.finger10)};
        for (int i = 0; i < fingerViews.length; i++) {
            int fingerPosition = i + 1;
            fingerViews[i].setOnClickListener(v -> onFingerTapped(fingerPosition));
        }

        ivPersonPhoto = findViewById(R.id.ivPersonPhoto);
        tvPhotoRowStatus = findViewById(R.id.tvPhotoRowStatus);
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto);
        btnCapturePhoto.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                photoCaptureLauncher.launch(new Intent(this, PhotoCaptureActivity.class));
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        findViewById(R.id.btnCaptureFace).setOnClickListener(v ->
                faceCaptureLauncher.launch(new Intent(this, FaceCaptureActivity.class)
                        .putExtra(FaceCaptureActivity.EXTRA_GUIDANCE_TEXT_RES, R.string.face_capture_guidance_enroll)));
        findViewById(R.id.btnAddPerson).setOnClickListener(v -> showAddAlternateDialog());
        findViewById(R.id.btnFinish).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        findViewById(R.id.btnFinishLater).setOnClickListener(v -> {
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
        capturedFingers.clear();
        capturedFingers.addAll(fingerprintDao.capturedFingerNumbers(beneficiaryId));
        fingerprintCaptured = needsFingerprint && !capturedFingers.isEmpty();
        faceCaptured = needsFace && faceDao.existsForBeneficiary(beneficiaryId);

        tvPersonName.setText(personName);
        tvPersonSubtitle.setText(beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD
                ? R.string.person_capture_subtitle_head : R.string.person_capture_subtitle_alternate);
        rowFingerprint.setVisibility(needsFingerprint ? View.VISIBLE : View.GONE);
        rowFace.setVisibility(needsFace ? View.VISIBLE : View.GONE);
        for (int i = 0; i < fingerViews.length; i++) {
            fingerViews[i].setSelected(capturedFingers.contains(i + 1));
        }
        updateFingerprintStatusText();
        tvFaceRowStatus.setText(faceCaptured ? R.string.person_capture_captured : R.string.person_capture_not_captured);
        refreshPhoto();
        doneSection.setVisibility(View.GONE);
        updateDoneState();
    }

    private void updateFingerprintStatusText() {
        tvFingerprintRowStatus.setText(capturedFingers.isEmpty()
                ? getString(R.string.person_capture_not_captured)
                : getString(R.string.person_capture_fingerprint_count, capturedFingers.size()));
    }

    private void refreshPhoto() {
        String path = imageDao.latestLocalPathForBeneficiary(beneficiaryId);
        boolean hasPhoto = ThumbnailLoader.loadInto(ivPersonPhoto, path, 72);
        if (!hasPhoto) ivPersonPhoto.setImageResource(R.drawable.ic_profile);
        ThumbnailLoader.makeExpandable(ivPersonPhoto, path, hasPhoto);
        tvPhotoRowStatus.setText(hasPhoto ? R.string.person_capture_captured : R.string.person_capture_not_captured);
        btnCapturePhoto.setText(hasPhoto ? R.string.person_capture_photo_retake_action : R.string.person_capture_photo_action);
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

    /** Tapping a finger on the hand-picker: fresh capture goes straight to the scanner; an
     *  already-captured (green) finger asks for confirmation first since scanning replaces its
     *  stored template. */
    private void onFingerTapped(int fingerPosition) {
        if (capturedFingers.contains(fingerPosition)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.person_capture_recapture_title)
                    .setMessage(getString(R.string.person_capture_recapture_message, FingerPosition.fullLabel(fingerPosition)))
                    .setNegativeButton(R.string.person_capture_recapture_cancel, null)
                    .setPositiveButton(R.string.person_capture_recapture_confirm,
                            (dialog, which) -> captureFingerprint(fingerPosition))
                    .show();
        } else {
            captureFingerprint(fingerPosition);
        }
    }

    private void captureFingerprint(int fingerPosition) {
        BiometricDevice device = BiometricDeviceFactory.create();
        try {
            device.open(this, null);
        } catch (BiometricDeviceException ex) {
            OutcomeFeedback.error(this, R.string.attendance_verify_error);
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
            OutcomeFeedback.error(this, R.string.attendance_verify_error);
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        TextView progress = content.findViewById(R.id.tvVerifyProgress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(FingerPosition.fullLabel(fingerPosition))
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (ignored, which) -> {
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();

        device.startCapture(fingerPosition, new CaptureCallback() {
            @Override public void onProgress(String message) { progress.setText(message); }
            @Override public void onPreviewFrame(Bitmap frame) { }

            @Override public void onCaptured(byte[] template, Bitmap finalImage) {
                progress.setText(R.string.person_capture_checking_duplicate);
                checkDuplicateThenSave(device, fingerPosition, template, dialog);
            }

            @Override public void onError(int errorCode, String message) {
                device.close();
                if (!isAliveForUi()) return;
                dialog.dismiss();
                OutcomeFeedback.error(PersonCaptureActivity.this,
                        getString(R.string.person_capture_failed, message));
            }
        });
    }

    /** A live fingerprint scan or the offline duplicate-check that follows it can take several
     *  seconds; if the officer backgrounds the app (screen lock, task switch, "don't keep
     *  activities") during that window, this activity's window can already be torn down by the
     *  time the async result arrives. Dismissing a dialog or touching views on a torn-down
     *  activity throws {@code IllegalArgumentException: ... not attached to window manager} and
     *  crashes the app -- confirmed on-device, see the 2026-09-08 progress.md entry. Every
     *  callback that reaches the UI after crossing a thread/async boundary checks this first. */
    private boolean isAliveForUi() {
        return !isFinishing() && !isDestroyed();
    }

    /** Runs after a fresh capture, before it's persisted: compares the new template against
     *  every other beneficiary's stored fingerprint (offline, no extra live scan) so the same
     *  finger can't be enrolled under two different people. Reused the {@code device} connection
     *  the capture just opened rather than reopening it. */
    private void checkDuplicateThenSave(BiometricDevice device, int fingerPosition, byte[] template, AlertDialog dialog) {
        List<FingerprintDao.BeneficiaryTemplate> others = fingerprintDao.templatesExcludingBeneficiary(beneficiaryId);
        new Thread(() -> {
            boolean duplicate = false;
            boolean checkFailed = false;
            for (FingerprintDao.BeneficiaryTemplate other : others) {
                BiometricDevice.MatchResult result = device.templatesMatch(template, other.template);
                if (result == BiometricDevice.MatchResult.MATCHED) {
                    duplicate = true;
                    break;
                } else if (result == BiometricDevice.MatchResult.ERROR) {
                    checkFailed = true;
                    break;
                }
            }
            boolean isDuplicate = duplicate;
            boolean hadError = checkFailed;
            runOnUiThread(() -> {
                device.close();
                if (!isAliveForUi()) return;
                dialog.dismiss();
                if (isDuplicate) {
                    OutcomeFeedback.error(this, R.string.person_capture_fingerprint_duplicate);
                } else if (hadError) {
                    OutcomeFeedback.error(this, R.string.person_capture_fingerprint_check_failed);
                } else {
                    // Replaces this exact finger's previous template (if any) rather than
                    // accumulating a second row for the same finger position -- a no-op delete
                    // on a fresh capture.
                    fingerprintDao.deleteForBeneficiaryAndFinger(beneficiaryId, fingerPosition);
                    fingerprintDao.save(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                            beneficiaryType, beneficiaryId, fingerPosition, UUID.randomUUID().toString(), template,
                            device.getDeviceId());
                    capturedFingers.add(fingerPosition);
                    fingerViews[fingerPosition - 1].setSelected(true);
                    updateFingerprintStatusText();
                    fingerprintCaptured = true;
                    updateDoneState();
                    OutcomeFeedback.success(this, R.string.person_capture_fingerprint_success);
                }
            });
        }).start();
    }

    // ---- Photo capture (not used for verification -- see PhotoCaptureActivity) -----------

    private void savePhoto(String cachePath) {
        String targetBeneficiaryId = beneficiaryId;
        int targetBeneficiaryType = beneficiaryType;
        new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), "photos");
                if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
                    throw new IOException("Could not create photo storage directory");
                }
                File dest = new File(dir, targetBeneficiaryId + "_" + System.currentTimeMillis() + ".jpg");
                copyFile(new File(cachePath), dest);
                new File(cachePath).delete();
                imageDao.save(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                        targetBeneficiaryType, targetBeneficiaryId, dest.getAbsolutePath());
                runOnUiThread(() -> {
                    if (!isAliveForUi()) return;
                    // The person shown may have changed (e.g. "Add another person") while the
                    // photo was being copied off the UI thread -- only refresh if it's still theirs.
                    if (targetBeneficiaryId.equals(beneficiaryId)) refreshPhoto();
                    OutcomeFeedback.success(this, R.string.person_capture_photo_success);
                });
            } catch (IOException ex) {
                Log.e(TAG, "Failed to save person photo", ex);
                runOnUiThread(() -> {
                    if (isAliveForUi()) OutcomeFeedback.error(this, R.string.person_capture_photo_failed);
                });
            }
        }).start();
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    // ---- Face capture (real embedding pipeline; still an explicitly unvalidated prototype --
    // see MlKitFaceRecognitionEngine's javadoc) ---------------------------------------------

    /** Compares the new embedding against every other beneficiary's stored face (offline, same
     *  cross-beneficiary duplicate check {@link #checkDuplicateThenSave} already does for
     *  fingerprints) before persisting it, so the same face can't be enrolled under two different
     *  people. A capture/decode/decrypt problem on any one existing record is treated the same as
     *  a real match failure -- silently skipping it would let a duplicate through undetected. */
    private void embedFace(String imagePath) {
        new Thread(() -> {
            MlKitFaceRecognitionEngine engine = new MlKitFaceRecognitionEngine(this);
            try {
                byte[] bytes = readFile(imagePath);
                FaceRecognitionEngine.CaptureResult result = engine.createEmbedding(bytes);

                boolean duplicate = false;
                boolean checkFailed = false;
                for (FaceDao.FaceRecord other : faceDao.listOtherBeneficiaries(beneficiaryId, engine.modelVersion())) {
                    try {
                        if (FaceMatcher.matches(result.embedding, toFloatArray(new JSONArray(other.embedding)),
                                FaceMatchConfig.UNCALIBRATED_PLACEHOLDER_THRESHOLD)) {
                            duplicate = true;
                            break;
                        }
                    } catch (org.json.JSONException | IllegalArgumentException ex) {
                        checkFailed = true;
                        break;
                    }
                }

                if (duplicate) {
                    runOnUiThread(() -> {
                        if (isAliveForUi()) OutcomeFeedback.error(this, R.string.person_capture_face_duplicate);
                    });
                    return;
                }
                if (checkFailed) {
                    runOnUiThread(() -> {
                        if (isAliveForUi()) OutcomeFeedback.error(this, R.string.person_capture_face_check_failed);
                    });
                    return;
                }

                JSONArray embeddingJson = new JSONArray();
                for (float v : result.embedding) embeddingJson.put(v);
                faceDao.savePending(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                        beneficiaryType, beneficiaryId, UUID.randomUUID().toString(), embeddingJson,
                        engine.modelVersion(), result.qualityScore);
                runOnUiThread(() -> {
                    if (!isAliveForUi()) return;
                    faceCaptured = true;
                    tvFaceRowStatus.setText(R.string.person_capture_captured);
                    updateDoneState();
                    OutcomeFeedback.success(this, R.string.person_capture_face_success);
                });
            } catch (FaceRecognitionException | IOException | org.json.JSONException ex) {
                String detail = ex.getMessage();
                runOnUiThread(() -> {
                    if (isAliveForUi()) {
                        OutcomeFeedback.error(PersonCaptureActivity.this,
                                getString(R.string.person_capture_failed, detail));
                    }
                });
            } finally {
                engine.close();
                new File(imagePath).delete();
            }
        }).start();
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
        AutoCompleteTextView etRelationship = content.findViewById(R.id.etAlternateRelationship);
        EditText etAge = content.findViewById(R.id.etAlternateAge);
        EditText etPhone = content.findViewById(R.id.etAlternatePhone);
        AutoCompleteTextView genderField = content.findViewById(R.id.spinnerAlternateGender);
        String[] genderOptions = {getString(R.string.gender_male), getString(R.string.gender_female)};
        genderField.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, genderOptions));
        String[] relationshipOptions = getResources().getStringArray(R.array.alternate_relationship_options);
        etRelationship.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, relationshipOptions));
        etRelationship.setOnItemClickListener((parent, view, position, id) -> {
            String inferred = RelationshipGender.infer(relationshipOptions[position]);
            genderField.setText(inferred == null ? "" : inferred, false);
        });

        AlertDialog addPersonDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.person_capture_add_another)
                .setView(content)
                .setCancelable(beneficiaryId != null)
                .setNegativeButton(R.string.person_capture_add_cancel, (dialog, which) -> {
                    // Launched via addPersonIntent() with nobody to show yet -- cancelling
                    // the only thing this screen can do leaves nothing to show.
                    if (beneficiaryId == null) finish();
                })
                .setPositiveButton(R.string.person_capture_add_confirm, null)
                .create();
        addPersonDialog.setOnShowListener(ignored -> addPersonDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        OutcomeFeedback.error(this, R.string.field_alternate_name);
                        return;
                    }
                    String relationship = etRelationship.getText().toString().trim();
                    if (relationship.isEmpty()) {
                        OutcomeFeedback.error(this, R.string.alternate_relationship_required);
                        return;
                    }
                    String gender = genderField.getText().toString().trim();
                    if (gender.isEmpty()) {
                        OutcomeFeedback.error(this, R.string.alternate_gender_required);
                        return;
                    }
                    String alternateNumber = "ALT" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.US);
                    ContentValues values = new ContentValues();
                    values.put("alternate_number", alternateNumber);
                    values.put("household_number", householdNumber);
                    values.put("supervisor_id", String.valueOf(sessionManager.getUserId()));
                    values.put("partner_code", sessionManager.getPartnerCode());
                    values.put("alternate_name", name);
                    values.put("relationship", relationship);
                    values.put("gender", gender);
                    values.put("age", parseIntOrNull(etAge.getText().toString()));
                    values.put("phone_number", etPhone.getText().toString().trim());
                    values.put("registration_method", method);
                    alternateDao.insert(values);

                    applyPerson(alternateNumber, Beneficiary.TYPE_ALTERNATE, name);
                    addPersonDialog.dismiss();
                }));
        addPersonDialog.show();
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
