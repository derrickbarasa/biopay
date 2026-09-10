package com.biopay.agent.vouchers;

import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.BiometricDeviceFactory;
import com.biopay.agent.biometric.VerifyCallback;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.VoucherDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.BeneficiaryTone;
import com.biopay.agent.ui.OutcomeFeedback;
import com.biopay.agent.ui.SearchViewHelper;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline voucher ledger; issued rows can continue into IDEMIA 1:1 fingerprint redemption. */
public class VoucherRedemptionActivity extends BaseActivity {
    private VoucherDao voucherDao;
    private FingerprintDao fingerprintDao;
    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private VoucherListAdapter adapter;
    private View voucherEmptyState;
    private TextView voucherEmptyBody;
    private TextView voucherSummary;
    private List<VoucherDao.Voucher> allVouchers = new ArrayList<>();
    private int checkedFilterId = R.id.chipVoucherAll;
    private String currentQuery = "";
    private final ExecutorService scannerOpenExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onDestroy() {
        super.onDestroy();
        scannerOpenExecutor.shutdownNow();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_voucher_redemption);
        setupMainNavigation(R.id.bottomNavigation, R.id.navVouchers);
        voucherDao = new VoucherDao(this);
        fingerprintDao = new FingerprintDao(this);
        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // The toolbar already carries the title; repeating it here just burns vertical space
            // a short landscape viewport can't spare (see the household list's landscape fix).
            findViewById(R.id.tvScreenTitle).setVisibility(View.GONE);
            findViewById(R.id.tvScreenDescription).setVisibility(View.GONE);
        }

        voucherEmptyState = findViewById(R.id.voucherEmptyState);
        voucherEmptyBody = findViewById(R.id.tvVoucherEmptyBody);
        voucherSummary = findViewById(R.id.tvVoucherSummary);
        adapter = new VoucherListAdapter(this::chooseBeneficiary);
        RecyclerView voucherList = findViewById(R.id.recyclerVouchers);
        voucherList.setLayoutManager(new LinearLayoutManager(this));
        voucherList.setAdapter(adapter);

        SearchView voucherSearch = findViewById(R.id.searchVouchers);
        SearchViewHelper.makeFullyClickable(voucherSearch);
        voucherSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                currentQuery = query == null ? "" : query.trim();
                renderVoucherList();
                return true;
            }

            @Override public boolean onQueryTextChange(String query) {
                currentQuery = query == null ? "" : query.trim();
                renderVoucherList();
                return true;
            }
        });

        ChipGroup filters = findViewById(R.id.voucherStatusFilters);
        filters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            checkedFilterId = checkedIds.isEmpty() ? R.id.chipVoucherAll : checkedIds.get(0);
            renderVoucherList();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        loadVouchers();
    }

    private void loadVouchers() {
        allVouchers = voucherDao.listAll();
        renderVoucherList();
    }

    private void renderVoucherList() {
        List<VoucherDao.Voucher> visible = new ArrayList<>();
        for (VoucherDao.Voucher voucher : allVouchers) {
            boolean statusMatches = checkedFilterId == R.id.chipVoucherAll
                    || (checkedFilterId == R.id.chipVoucherAvailable && "ISSUED".equalsIgnoreCase(voucher.status))
                    || (checkedFilterId == R.id.chipVoucherRedeemed && "REDEEMED".equalsIgnoreCase(voucher.status))
                    || (checkedFilterId == R.id.chipVoucherVoided && "VOID".equalsIgnoreCase(voucher.status));
            String query = currentQuery.toLowerCase(java.util.Locale.getDefault());
            boolean householdMatches = query.isEmpty()
                    || contains(voucher.householdName, query)
                    || contains(voucher.householdNumber, query);
            if (statusMatches && householdMatches) {
                visible.add(voucher);
            }
        }
        adapter.submitList(visible);
        voucherSummary.setText(getResources().getQuantityString(
                R.plurals.voucher_list_summary, visible.size(), visible.size()));
        boolean empty = visible.isEmpty();
        voucherEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerVouchers).setVisibility(empty ? View.GONE : View.VISIBLE);
        voucherEmptyBody.setText(allVouchers.isEmpty()
                ? R.string.voucher_empty
                : currentQuery.isEmpty() ? R.string.voucher_filter_empty : R.string.voucher_search_empty);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(java.util.Locale.getDefault()).contains(query);
    }

    private void chooseBeneficiary(VoucherDao.Voucher voucher) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_voucher_beneficiary_picker, null);
        AlertDialog picker = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.voucher_choose_person_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        VoucherBeneficiaryAdapter peopleAdapter = new VoucherBeneficiaryAdapter(person -> {
            picker.dismiss();
            verify(voucher, person);
        });
        RecyclerView peopleList = content.findViewById(R.id.recyclerBeneficiaries);
        peopleList.setLayoutManager(new LinearLayoutManager(this));
        peopleList.setAdapter(peopleAdapter);
        List<Beneficiary> people = buildVerifiableBeneficiaries(voucher.householdNumber);
        peopleAdapter.submitList(people);
        View emptyState = content.findViewById(R.id.emptyState);
        emptyState.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        SearchView searchView = content.findViewById(R.id.searchView);
        SearchViewHelper.makeFullyClickable(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                return filterPeople(peopleAdapter, emptyState, query);
            }
            @Override public boolean onQueryTextChange(String newText) {
                return filterPeople(peopleAdapter, emptyState, newText);
            }
        });
        picker.show();
    }

    private boolean filterPeople(VoucherBeneficiaryAdapter peopleAdapter, View emptyState, String query) {
        peopleAdapter.filter(query);
        emptyState.setVisibility(peopleAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        return true;
    }

    private List<Beneficiary> buildVerifiableBeneficiaries(String householdNumber) {
        List<Beneficiary> people = new ArrayList<>();
        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        if (household != null && fingerprintDao.countForBeneficiary(householdNumber) > 0) {
            people.add(new Beneficiary(householdNumber, householdNumber,
                    Beneficiary.TYPE_HOUSEHOLD_HEAD, household.householdName,
                    getString(R.string.beneficiary_head_detail, genderLabel(household.gender)), household.gender));
        }
        for (AlternateDao.Alternate alternate : alternateDao.findByHousehold(householdNumber)) {
            if (fingerprintDao.countForBeneficiary(alternate.alternateNumber) > 0) {
                people.add(new Beneficiary(alternate.alternateNumber, householdNumber,
                        Beneficiary.TYPE_ALTERNATE, alternate.alternateName,
                        getString(R.string.beneficiary_alternate_detail,
                                relationshipLabel(alternate.relationship), genderLabel(alternate.gender)),
                        alternate.gender));
            }
        }
        return people;
    }

    private String genderLabel(String gender) {
        return gender == null || gender.trim().isEmpty()
                ? getString(R.string.gender_not_recorded) : gender.trim();
    }

    private String relationshipLabel(String relationship) {
        return relationship == null || relationship.trim().isEmpty()
                ? getString(R.string.attendance_beneficiary_alternate) : relationship.trim();
    }

    private void verify(VoucherDao.Voucher voucher, Beneficiary beneficiary) {
        List<FingerprintDao.StoredTemplate> templates =
                fingerprintDao.templatesWithUuidForBeneficiary(beneficiary.beneficiaryId);
        if (templates.isEmpty()) {
            OutcomeFeedback.error(this, R.string.voucher_no_fingerprint);
            return;
        }
        BiometricDevice device = BiometricDeviceFactory.create();

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_verify_progress, null);
        content.setBackgroundColor(ContextCompat.getColor(this, BeneficiaryTone.background(beneficiary)));
        TextView progress = content.findViewById(R.id.tvVerifyProgress);
        progress.setText(R.string.fingerprint_connecting_scanner);
        boolean[] cancelled = {false};
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.verify_method_title, beneficiary.name))
                .setView(content)
                .setCancelable(false)
                .setNegativeButton(R.string.attendance_cancel, (ignored, which) -> {
                    cancelled[0] = true;
                    device.cancelLiveAcquisition();
                    device.close();
                })
                .create();
        dialog.show();

        // Morpho open() powers the embedded sensor, waits for its AIDL service and retries USB
        // enumeration -- that can take several seconds and must never run on Android's UI thread
        // (blocking it there starves Android's own USB-permission dialog, so the very first
        // capture/verify attempt on a device -- before that permission is granted -- always
        // failed). See FingerprintVerifyActivity.startVerify() for the same pattern.
        scannerOpenExecutor.execute(() -> {
            BiometricDeviceException openError = null;
            Throwable unexpectedError = null;
            try {
                device.open(this, null);
            } catch (BiometricDeviceException error) {
                openError = error;
            } catch (Throwable error) {
                unexpectedError = error;
            }
            BiometricDeviceException finalOpenError = openError;
            Throwable finalUnexpectedError = unexpectedError;
            runOnUiThread(() -> {
                if (cancelled[0]) return;
                if (finalOpenError != null || finalUnexpectedError != null) {
                    if (finalUnexpectedError != null) {
                        android.util.Log.e("VoucherRedemption", "BiometricDevice.open() failed unexpectedly", finalUnexpectedError);
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        dialog.dismiss();
                        OutcomeFeedback.error(this, R.string.attendance_verify_error);
                    }
                    return;
                }
                if (isFinishing() || isDestroyed()) {
                    device.close();
                    return;
                }
                attempt(device, templates, 0, progress, dialog, voucher);
            });
        });
    }

    private void attempt(BiometricDevice device, List<FingerprintDao.StoredTemplate> templates,
            int index, TextView progress, AlertDialog dialog, VoucherDao.Voucher voucher) {
        if (index >= templates.size()) {
            device.close();
            dialog.dismiss();
            OutcomeFeedback.error(this, R.string.attendance_no_match);
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
                OutcomeFeedback.error(VoucherRedemptionActivity.this, R.string.attendance_verify_error);
            }
        });
    }

    private void queue(VoucherDao.Voucher voucher, String fingerprint) {
        Location location = LocationHelper.getLastKnownLocation(this);
        voucherDao.queueRedemption(voucher.code, fingerprint,
                location == null ? null : String.valueOf(location.getLatitude()),
                location == null ? null : String.valueOf(location.getLongitude()));
        OutcomeFeedback.success(this, R.string.voucher_redeemed_queued);
        loadVouchers();
        SyncScheduler.triggerAutomaticNow(this);
    }
}
