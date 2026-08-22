package com.biopay.agent.attendance;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.AttendanceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Household head + alternates for one household, each with Clock In / Clock Out actions that
 * drive a live fingerprint verify. This is the first screen in the app to actually call
 * {@link BiometricDevice#startVerify} -- see the class javadoc on backend's {@code Biometric} for
 * why 1:1 verify is the only matching mode available (no vendor 1:N identify wired in), which is
 * also why a beneficiary with several enrolled fingers means sequential re-scan attempts here
 * rather than a single scan matched against all of them at once.
 */
public class AttendanceBeneficiariesActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";

    public static Intent intentFor(Context context, String householdNumber) {
        Intent intent = new Intent(context, AttendanceBeneficiariesActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        return intent;
    }

    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private FingerprintDao fingerprintDao;
    private AttendanceDao attendanceDao;
    private SessionManager sessionManager;
    private String householdNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_beneficiaries);
        setupBackToolbar(R.id.toolbar);

        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        fingerprintDao = new FingerprintDao(this);
        attendanceDao = new AttendanceDao(this);
        sessionManager = new SessionManager(this);
        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);

        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        ((TextView) findViewById(R.id.tvHouseholdName))
                .setText(household != null ? household.householdName : householdNumber);

        AttendanceBeneficiaryAdapter adapter = new AttendanceBeneficiaryAdapter(this::startVerify);
        RecyclerView recyclerView = findViewById(R.id.recyclerBeneficiaries);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.submitList(buildBeneficiaries(household));
    }

    private List<Beneficiary> buildBeneficiaries(HouseholdDao.Household household) {
        List<Beneficiary> beneficiaries = new ArrayList<>();
        if (household != null) {
            beneficiaries.add(new Beneficiary(household.householdNumber, household.householdNumber,
                    Beneficiary.TYPE_HOUSEHOLD_HEAD, household.householdName,
                    getString(R.string.attendance_beneficiary_head)));
        }
        for (AlternateDao.Alternate alternate : alternateDao.findByHousehold(householdNumber)) {
            beneficiaries.add(new Beneficiary(alternate.alternateNumber, householdNumber,
                    Beneficiary.TYPE_ALTERNATE, alternate.alternateName,
                    getString(R.string.alternate_detail,
                            getString(R.string.attendance_beneficiary_alternate), alternate.relationship)));
        }
        return beneficiaries;
    }

    private void startVerify(Beneficiary beneficiary, String clock) {
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
            // A missing/mismatched vendor native library throws an unchecked UnsatisfiedLinkError,
            // not the checked exception open() declares -- confirmed on-device (see PersonCaptureActivity's
            // matching fix). Caught broadly so a hardware/library problem degrades to the same
            // honest message instead of crashing the app.
            android.util.Log.e("AttendanceBeneficiaries", "BiometricDevice.open() failed unexpectedly", ex);
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        }

        android.view.View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        TextView tvProgress = dialogView.findViewById(R.id.tvVerifyProgress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.attendance_people_title)
                .setView(dialogView)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (d, which) -> {
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();

        attemptVerify(device, templates, 0, tvProgress, dialog, beneficiary, clock);
    }

    private void attemptVerify(BiometricDevice device, List<FingerprintDao.StoredTemplate> templates, int index,
            TextView tvProgress, AlertDialog dialog, Beneficiary beneficiary, String clock) {
        if (index >= templates.size()) {
            device.close();
            dialog.dismiss();
            Toast.makeText(this, R.string.attendance_no_match, Toast.LENGTH_SHORT).show();
            return;
        }

        device.startVerify(templates.get(index).template, new VerifyCallback() {
            @Override
            public void onProgress(String message) {
                tvProgress.setText(message);
            }

            @Override
            public void onMatched(int matchScore) {
                device.close();
                dialog.dismiss();
                recordAttendance(beneficiary, clock, templates.get(index).uuid);
            }

            @Override
            public void onNoMatch() {
                attemptVerify(device, templates, index + 1, tvProgress, dialog, beneficiary, clock);
            }

            @Override
            public void onError(int errorCode, String message) {
                device.close();
                dialog.dismiss();
                Toast.makeText(AttendanceBeneficiariesActivity.this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recordAttendance(Beneficiary beneficiary, String clock, String matchedFingerprintUuid) {
        Location location = LocationHelper.getLastKnownLocation(this);
        String latitude = location == null ? null : String.valueOf(location.getLatitude());
        String longitude = location == null ? null : String.valueOf(location.getLongitude());

        attendanceDao.record(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                beneficiary.householdNumber, beneficiary.beneficiaryType, beneficiary.beneficiaryId,
                matchedFingerprintUuid, clock, null, latitude, longitude, UUID.randomUUID().toString());
        Toast.makeText(this, R.string.attendance_recorded, Toast.LENGTH_SHORT).show();
    }
}
