package com.biopay.agent.vouchers;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import com.biopay.agent.R;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.VoucherDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** Offline voucher list; redemption is queued only after an IDEMIA 1:1 fingerprint match. */
public class VoucherRedemptionActivity extends BaseActivity {
    private VoucherDao voucherDao;
    private FingerprintDao fingerprintDao;
    private VoucherListAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_voucher_redemption);
        setupBackToolbar(R.id.toolbar);
        voucherDao = new VoucherDao(this);
        fingerprintDao = new FingerprintDao(this);
        adapter = new VoucherListAdapter(this::verify);
        RecyclerView list = findViewById(R.id.recyclerVouchers);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        showVouchers();
    }

    private void showVouchers() {
        List<VoucherDao.Voucher> rows = voucherDao.listIssued();
        adapter.submitList(rows);
        findViewById(R.id.emptyState).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerVouchers).setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void verify(VoucherDao.Voucher voucher) {
        List<FingerprintDao.StoredTemplate> templates =
                fingerprintDao.templatesWithUuidForBeneficiary(voucher.householdNumber);
        if (templates.isEmpty()) {
            Toast.makeText(this, R.string.voucher_no_fingerprint, Toast.LENGTH_SHORT).show();
            return;
        }
        BiometricDevice device = BiometricDeviceFactory.create();
        try {
            device.open(this, null);
        } catch (BiometricDeviceException error) {
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        } catch (Throwable error) {
            // A missing/mismatched vendor native library throws an unchecked UnsatisfiedLinkError,
            // not the checked exception open() declares -- confirmed on-device (see PersonCaptureActivity's
            // matching fix). Caught broadly so a hardware/library problem degrades to the same
            // honest message instead of crashing the app.
            android.util.Log.e("VoucherRedemption", "BiometricDevice.open() failed unexpectedly", error);
            Toast.makeText(this, R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        TextView progress = content.findViewById(R.id.tvVerifyProgress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.voucher_verify_title)
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (ignored, which) -> {
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();
        attempt(device, templates, 0, progress, dialog, voucher);
    }

    private void attempt(BiometricDevice device, List<FingerprintDao.StoredTemplate> templates,
            int index, TextView progress, AlertDialog dialog, VoucherDao.Voucher voucher) {
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
                queue(voucher, templates.get(index).uuid);
            }
            @Override public void onNoMatch() {
                attempt(device, templates, index + 1, progress, dialog, voucher);
            }
            @Override public void onError(int code, String message) {
                device.close();
                dialog.dismiss();
                Toast.makeText(VoucherRedemptionActivity.this,
                        R.string.attendance_verify_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void queue(VoucherDao.Voucher voucher, String fingerprint) {
        Location location = LocationHelper.getLastKnownLocation(this);
        voucherDao.queueRedemption(voucher.code, fingerprint,
                location == null ? null : String.valueOf(location.getLatitude()),
                location == null ? null : String.valueOf(location.getLongitude()));
        Toast.makeText(this, R.string.voucher_redeemed_queued, Toast.LENGTH_LONG).show();
        showVouchers();
        SyncScheduler.triggerAutomaticNow(this);
    }
}
