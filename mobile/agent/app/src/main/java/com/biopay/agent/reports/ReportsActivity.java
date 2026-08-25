package com.biopay.agent.reports;

import android.os.Bundle;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.home.SimpleDonutChartView;
import com.biopay.agent.ui.BaseActivity;

import java.text.NumberFormat;
import java.util.Arrays;

/** Offline operational report built entirely from the device database. */
public class ReportsActivity extends BaseActivity {
    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private PaymentDao paymentDao;
    private DatabaseHelper databaseHelper;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        setupBackToolbar(R.id.toolbar);
        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        paymentDao = new PaymentDao(this);
        databaseHelper = DatabaseHelper.get(this);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        int paidCount = paymentDao.countByStatus(PaymentDao.STATUS_PAID);
        int pendingCount = paymentDao.countByStatus(PaymentDao.STATUS_PENDING);
        int waitingSync = databaseHelper.countPendingSyncWork();
        NumberFormat number = NumberFormat.getNumberInstance();

        setText(R.id.tvPaidCount, String.valueOf(paidCount));
        setText(R.id.tvPendingCount, String.valueOf(pendingCount));
        setText(R.id.tvPaidAmount, number.format(paymentDao.totalAmountByStatus(PaymentDao.STATUS_PAID)));
        setText(R.id.tvPendingAmount, number.format(paymentDao.totalAmountByStatus(PaymentDao.STATUS_PENDING)));
        setText(R.id.tvHouseholdCount, String.valueOf(householdDao.countAll()));
        setText(R.id.tvAlternateCount, String.valueOf(alternateDao.countAll()));
        setText(R.id.tvWaitingSync, String.valueOf(waitingSync));

        SimpleDonutChartView chart = findViewById(R.id.chartPayments);
        chart.setSlices(Arrays.asList(
                new SimpleDonutChartView.Slice(getString(R.string.payment_paid), paidCount,
                        ContextCompat.getColor(this, R.color.bp_success)),
                new SimpleDonutChartView.Slice(getString(R.string.payment_pending), pendingCount,
                        ContextCompat.getColor(this, R.color.bp_secondary))),
                "Total");
    }

    private void setText(int viewId, String value) {
        ((TextView) findViewById(viewId)).setText(value);
    }
}
