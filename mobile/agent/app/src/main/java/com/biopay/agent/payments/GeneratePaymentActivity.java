package com.biopay.agent.payments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.biopay.agent.R;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.sync.SyncManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.NumberFormat;
import java.util.List;

/** Starts a payment from payroll-generated, unpaid entitlements cached on this device. */
public class GeneratePaymentActivity extends BaseActivity {

    private PaymentDao paymentDao;
    private MaterialAutoCompleteTextView search;
    private View details;
    private View emptyState;
    private MaterialButton proceed;
    private MaterialButton syncPayments;
    private ProgressBar syncProgress;
    private PaymentDao.LocalPayment selectedPayment;
    private boolean refreshing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_payment);
        setupMainNavigation(R.id.bottomNavigation, R.id.navPayment);
        setupPaymentFab(R.id.fabPayment);

        paymentDao = new PaymentDao(this);
        search = findViewById(R.id.searchPendingPayments);
        details = findViewById(R.id.paymentDetails);
        emptyState = findViewById(R.id.paymentEmptyState);
        proceed = findViewById(R.id.btnProceedVerification);
        syncPayments = findViewById(R.id.btnSyncPayments);
        syncProgress = findViewById(R.id.paymentSyncProgress);

        search.setOnItemClickListener((parent, view, position, id) ->
                selectPayment((PaymentDao.LocalPayment) parent.getItemAtPosition(position)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (selectedPayment != null && !selectedPayment.toString().contentEquals(s)) clearSelection();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        proceed.setOnClickListener(v -> {
            if (selectedPayment == null || selectedPayment.remoteId == null) return;
            startActivity(PaymentVerificationActivity.intentFor(this, selectedPayment.remoteId,
                    selectedPayment.householdNumber, selectedPayment.amount));
        });
        syncPayments.setOnClickListener(v -> refreshPayments());
        clearSelection();
        refreshPayments();
    }

    @Override protected void onResume() {
        super.onResume();
        loadPendingPayments();
    }

    private void loadPendingPayments() {
        List<PaymentDao.LocalPayment> payments = paymentDao.listPendingGeneratedPayments();
        ArrayAdapter<PaymentDao.LocalPayment> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, payments);
        search.setAdapter(adapter);
        search.setEnabled(!refreshing && !payments.isEmpty());
        emptyState.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvPendingCount)).setText(
                getResources().getQuantityString(R.plurals.generated_payment_count, payments.size(), payments.size()));

        if (selectedPayment != null) {
            boolean stillPending = false;
            for (PaymentDao.LocalPayment payment : payments) {
                if (payment.remoteId.equals(selectedPayment.remoteId)) {
                    stillPending = true;
                    break;
                }
            }
            if (!stillPending) {
                search.setText("");
                clearSelection();
            }
        }
    }

    private void selectPayment(PaymentDao.LocalPayment payment) {
        selectedPayment = payment;
        search.setText(payment.toString(), false);
        ((TextView) findViewById(R.id.fieldHouseholdName)).setText(payment.householdName);
        ((TextView) findViewById(R.id.fieldRegistrationNumber)).setText(payment.householdNumber);
        ((TextView) findViewById(R.id.fieldVillage)).setText(
                payment.village == null || payment.village.trim().isEmpty()
                        ? getString(R.string.generated_payment_village_unknown) : payment.village);
        ((TextView) findViewById(R.id.fieldAmount)).setText(
                NumberFormat.getNumberInstance().format(payment.amount));
        details.setVisibility(View.VISIBLE);
        proceed.setEnabled(true);
    }

    private void clearSelection() {
        selectedPayment = null;
        ((TextView) findViewById(R.id.fieldHouseholdName)).setText(R.string.generated_payment_placeholder);
        ((TextView) findViewById(R.id.fieldRegistrationNumber)).setText(R.string.generated_payment_placeholder);
        ((TextView) findViewById(R.id.fieldVillage)).setText(R.string.generated_payment_placeholder);
        ((TextView) findViewById(R.id.fieldAmount)).setText(R.string.generated_payment_placeholder);
        details.setVisibility(View.VISIBLE);
        proceed.setEnabled(false);
    }

    private void refreshPayments() {
        if (refreshing) return;
        refreshing = true;
        syncProgress.setVisibility(View.VISIBLE);
        syncPayments.setEnabled(false);
        search.setEnabled(false);
        new Thread(() -> {
            boolean success = new SyncManager(this).syncPaymentCatalogue();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                refreshing = false;
                syncProgress.setVisibility(View.GONE);
                syncPayments.setEnabled(true);
                loadPendingPayments();
                if (!success) {
                    Toast.makeText(this, R.string.generated_payment_sync_failed, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
