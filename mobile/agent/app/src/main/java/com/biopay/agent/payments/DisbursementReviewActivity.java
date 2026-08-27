package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.sync.SyncScheduler;
import com.biopay.agent.ui.BaseActivity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Final summary before recording the disbursement -- confirming here calls the exact same
 *  {@link PaymentDao#recordFieldPayment} the old single-screen flow called immediately on a
 *  successful verify; only the host UI moved. */
public class DisbursementReviewActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_HOUSEHOLD_NAME = "household_name";
    private static final String EXTRA_AMOUNT = "amount";
    private static final String EXTRA_PERSON_NAME = "person_name";
    private static final String EXTRA_METHOD = "method";
    private static final String EXTRA_MATCHED_FINGERPRINT_UUID = "matched_fingerprint_uuid";
    private static final String EXTRA_MATCHED_FACE_UUID = "matched_face_uuid";
    private static final String EXTRA_INTERVENTION_TYPE = "intervention_type";

    public static Intent intentFor(Context context, String householdNumber, String householdName, double amount,
            String personName, String method, String matchedFingerprintUuid, String matchedFaceUuid, String interventionType) {
        Intent intent = new Intent(context, DisbursementReviewActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_HOUSEHOLD_NAME, householdName);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_METHOD, method);
        intent.putExtra(EXTRA_MATCHED_FINGERPRINT_UUID, matchedFingerprintUuid);
        intent.putExtra(EXTRA_MATCHED_FACE_UUID, matchedFaceUuid);
        intent.putExtra(EXTRA_INTERVENTION_TYPE, interventionType);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disbursement_review);
        setupBackToolbar(R.id.toolbar);

        Intent intent = getIntent();
        String householdNumber = intent.getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        String householdName = intent.getStringExtra(EXTRA_HOUSEHOLD_NAME);
        double amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0);
        String personName = intent.getStringExtra(EXTRA_PERSON_NAME);
        String method = intent.getStringExtra(EXTRA_METHOD);
        Location location = LocationHelper.getLastKnownLocation(this);
        String locationText = location == null ? "—"
                : String.format(Locale.US, "%.4f, %.4f", location.getLatitude(), location.getLongitude());

        android.widget.LinearLayout container = findViewById(R.id.containerSummary);
        LayoutInflater inflater = LayoutInflater.from(this);
        addRow(container, inflater, R.string.disbursement_review_household, householdName);
        addRow(container, inflater, R.string.disbursement_review_member, personName);
        addRow(container, inflater, R.string.disbursement_review_programme, getString(R.string.programme_default_label));
        addRow(container, inflater, R.string.disbursement_review_value, NumberFormat.getNumberInstance().format(amount));
        addRow(container, inflater, R.string.disbursement_review_verification, method);
        addRow(container, inflater, R.string.disbursement_review_location, locationText);
        addRow(container, inflater, R.string.disbursement_review_connectivity, getString(R.string.disbursement_review_connectivity_offline));
        addRow(container, inflater, R.string.disbursement_review_officer, new SessionManager(this).getFullName());
        addRow(container, inflater, R.string.disbursement_review_datetime,
                new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(new Date()));

        findViewById(R.id.btnConfirm).setOnClickListener(v ->
                confirm(householdNumber, householdName, amount, personName, method,
                        intent.getStringExtra(EXTRA_MATCHED_FINGERPRINT_UUID), intent.getStringExtra(EXTRA_MATCHED_FACE_UUID),
                        intent.getStringExtra(EXTRA_INTERVENTION_TYPE), location, locationText));
    }

    private void addRow(android.widget.LinearLayout container, LayoutInflater inflater, int labelRes, String value) {
        android.view.View row = inflater.inflate(R.layout.item_summary_row, container, false);
        ((TextView) row.findViewById(R.id.tvSummaryLabel)).setText(labelRes);
        ((TextView) row.findViewById(R.id.tvSummaryValue)).setText(value);
        container.addView(row);
    }

    private void confirm(String householdNumber, String householdName, double amount, String personName, String method,
            String matchedFingerprintUuid, String matchedFaceUuid, String interventionType, Location location, String locationText) {
        SessionManager sessionManager = new SessionManager(this);
        String latitude = location == null ? null : String.valueOf(location.getLatitude());
        String longitude = location == null ? null : String.valueOf(location.getLongitude());
        String transactionUuid = UUID.randomUUID().toString();

        new PaymentDao(this).recordFieldPayment(String.valueOf(sessionManager.getUserId()), sessionManager.getPartnerCode(),
                householdNumber, householdName, amount, matchedFingerprintUuid, matchedFaceUuid,
                latitude, longitude, transactionUuid, interventionType);
        SyncScheduler.triggerNow(this);

        String[] labels = {
                getString(R.string.transaction_receipt_id),
                getString(R.string.transaction_receipt_datetime),
                getString(R.string.transaction_receipt_programme),
                getString(R.string.transaction_receipt_verification),
                getString(R.string.transaction_receipt_location),
                getString(R.string.transaction_receipt_officer),
        };
        String[] values = {
                transactionUuid.substring(0, 8).toUpperCase(Locale.ROOT),
                new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(new Date()),
                getString(R.string.programme_default_label),
                method,
                locationText,
                sessionManager.getFullName(),
        };
        Intent success = VerifySuccessActivity.intentFor(this, VerifySuccessActivity.MODE_TRANSACTION,
                getString(R.string.transaction_success_title),
                NumberFormat.getNumberInstance().format(amount) + " · " + personName + " · " + householdName,
                labels, values, getString(R.string.transaction_done), null);
        startActivity(success);
        finish();
    }
}
