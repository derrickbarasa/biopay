package com.biopay.agent.payments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.ui.BaseActivity;

import java.util.List;

/** Filterable offline payment register. */
public class PaymentsActivity extends BaseActivity {
    private PaymentDao paymentDao;
    private PaymentListAdapter adapter;
    private Integer selectedStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payments);
        setupBackToolbar(R.id.toolbar);
        paymentDao = new PaymentDao(this);
        adapter = new PaymentListAdapter(payment ->
                startActivity(PaymentVerificationActivity.intentFor(this, payment.householdNumber, payment.amount)));
        RecyclerView recycler = findViewById(R.id.recyclerPayments);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.filterAll).setOnClickListener(v -> selectStatus(null));
        findViewById(R.id.filterPaid).setOnClickListener(v -> selectStatus(PaymentDao.STATUS_PAID));
        findViewById(R.id.filterPending).setOnClickListener(v -> selectStatus(PaymentDao.STATUS_PENDING));
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void selectStatus(Integer status) {
        selectedStatus = status;
        load();
    }

    private void load() {
        List<PaymentDao.LocalPayment> payments = paymentDao.listByStatus(selectedStatus);
        adapter.submitList(payments);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.payment_summary, payments.size()));
        findViewById(R.id.emptyState).setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerPayments).setVisibility(payments.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
