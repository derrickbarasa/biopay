package com.biopay.agent.payments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.sync.SyncManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Starts a payment from payroll-generated, unpaid entitlements cached on this device. */
public class GeneratePaymentActivity extends BaseActivity {

    private PaymentDao paymentDao;
    private TextInputLayout searchFieldLayout;
    private TextInputEditText search;
    private View details;
    private View emptyState;
    private MaterialButton proceed;
    private MaterialButton syncPayments;
    private ProgressBar syncProgress;
    private PaymentDao.LocalPayment selectedPayment;
    private List<PaymentDao.LocalPayment> pendingPayments = new ArrayList<>();
    private boolean refreshing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_payment);
        setupBackToolbar(R.id.toolbar);

        paymentDao = new PaymentDao(this);
        searchFieldLayout = findViewById(R.id.searchFieldLayout);
        search = findViewById(R.id.searchPendingPayments);
        details = findViewById(R.id.paymentDetails);
        emptyState = findViewById(R.id.paymentEmptyState);
        proceed = findViewById(R.id.btnProceedVerification);
        syncPayments = findViewById(R.id.btnSyncPayments);
        syncProgress = findViewById(R.id.paymentSyncProgress);

        search.setOnClickListener(v -> openHouseholdPicker());
        searchFieldLayout.setEndIconOnClickListener(v -> openHouseholdPicker());
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
        pendingPayments = paymentDao.listPendingGeneratedPayments();
        search.setEnabled(!refreshing);
        emptyState.setVisibility(pendingPayments.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvPendingCount)).setText(getResources().getQuantityString(
                R.plurals.generated_payment_count, pendingPayments.size(), pendingPayments.size()));

        if (selectedPayment != null) {
            boolean stillPending = false;
            for (PaymentDao.LocalPayment payment : pendingPayments) {
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

    private void openHouseholdPicker() {
        if (!search.isEnabled()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottomsheet_household_picker);

        RecyclerView recyclerView = dialog.findViewById(R.id.recyclerHouseholdPicker);
        TextView emptyText = dialog.findViewById(R.id.tvPickerEmpty);
        TextInputEditText searchInput = dialog.findViewById(R.id.searchHouseholdInput);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        HouseholdPickerAdapter adapter = new HouseholdPickerAdapter(payment -> {
            selectPayment(payment);
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);

        Runnable applyFilter = () -> {
            String query = searchInput.getText() == null ? "" : searchInput.getText().toString();
            List<PaymentDao.LocalPayment> filtered = filterHouseholds(pendingPayments, query);
            adapter.submit(filtered);
            boolean empty = filtered.isEmpty();
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
            emptyText.setText(pendingPayments.isEmpty()
                    ? R.string.household_picker_empty_none_pending
                    : R.string.household_picker_empty_no_match);
        };
        applyFilter.run();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.show();
    }

    private static List<PaymentDao.LocalPayment> filterHouseholds(List<PaymentDao.LocalPayment> source, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.getDefault());
        if (query.isEmpty()) return new ArrayList<>(source);
        List<PaymentDao.LocalPayment> matches = new ArrayList<>();
        for (PaymentDao.LocalPayment payment : source) {
            if (contains(payment.householdName, query) || contains(payment.householdNumber, query)
                    || contains(payment.village, query)) {
                matches.add(payment);
            }
        }
        return matches;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void selectPayment(PaymentDao.LocalPayment payment) {
        selectedPayment = payment;
        search.setText(payment.toString());
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

    private interface OnHouseholdPicked {
        void onPick(PaymentDao.LocalPayment payment);
    }

    private static class HouseholdPickerAdapter extends RecyclerView.Adapter<HouseholdPickerAdapter.RowHolder> {
        private final OnHouseholdPicked onPick;
        private final List<PaymentDao.LocalPayment> items = new ArrayList<>();

        HouseholdPickerAdapter(OnHouseholdPicked onPick) {
            this.onPick = onPick;
        }

        void submit(List<PaymentDao.LocalPayment> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override public RowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_household_dropdown, parent, false);
            return new RowHolder(row);
        }

        @Override public void onBindViewHolder(RowHolder holder, int position) {
            holder.bind(items.get(position), onPick);
        }

        @Override public int getItemCount() {
            return items.size();
        }

        static class RowHolder extends RecyclerView.ViewHolder {
            private final TextView name;
            private final TextView subtitle;

            RowHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvDropdownName);
                subtitle = itemView.findViewById(R.id.tvDropdownSubtitle);
            }

            void bind(PaymentDao.LocalPayment payment, OnHouseholdPicked onPick) {
                name.setText(payment.householdName);
                String subtitleText = payment.village == null || payment.village.trim().isEmpty()
                        ? payment.householdNumber
                        : payment.village + "  ·  " + payment.householdNumber;
                subtitle.setText(subtitleText);
                itemView.setOnClickListener(v -> onPick.onPick(payment));
            }
        }
    }
}
