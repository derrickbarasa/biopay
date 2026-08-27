package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.location.LocationHelper;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;

/**
 * Cash/Voucher/Food/In-kind selector (Cash pre-selected -- the only type this integration
 * actually records server-side today, see PaymentDao#recordFieldPayment's javadoc) plus the
 * pre-set entitlement amount and a readiness checklist, before Review Disbursement.
 */
public class DisbursementActivity extends BaseActivity {

    static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    static final String EXTRA_HOUSEHOLD_NAME = "household_name";
    static final String EXTRA_AMOUNT = "amount";
    static final String EXTRA_PERSON_NAME = "person_name";
    static final String EXTRA_METHOD = "method";
    static final String EXTRA_MATCHED_FINGERPRINT_UUID = "matched_fingerprint_uuid";
    static final String EXTRA_MATCHED_FACE_UUID = "matched_face_uuid";
    static final String EXTRA_INTERVENTION_TYPE = "intervention_type";

    public static Intent intentFor(Context context, String householdNumber, String householdName, double amount,
            String personName, String method, String matchedFingerprintUuid, String matchedFaceUuid) {
        Intent intent = new Intent(context, DisbursementActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_HOUSEHOLD_NAME, householdName);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_PERSON_NAME, personName);
        intent.putExtra(EXTRA_METHOD, method);
        intent.putExtra(EXTRA_MATCHED_FINGERPRINT_UUID, matchedFingerprintUuid);
        intent.putExtra(EXTRA_MATCHED_FACE_UUID, matchedFaceUuid);
        return intent;
    }

    private String selectedIntervention = "CASH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disbursement);
        setupBackToolbar(R.id.toolbar);

        Intent intent = getIntent();
        double amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0);
        ((TextView) findViewById(R.id.tvPersonName)).setText(
                intent.getStringExtra(EXTRA_PERSON_NAME) + " · " + intent.getStringExtra(EXTRA_HOUSEHOLD_NAME));
        ((TextView) findViewById(R.id.tvProgramme)).setText(R.string.programme_default_label);
        ((TextView) findViewById(R.id.tvEntitlement)).setText(NumberFormat.getNumberInstance().format(amount));
        ((TextInputEditText) findViewById(R.id.etAmount)).setText(NumberFormat.getNumberInstance().format(amount));

        setupChecklist(intent);
        setupInterventionSelector();

        findViewById(R.id.btnReview).setOnClickListener(v -> {
            Intent review = DisbursementReviewActivity.intentFor(this,
                    intent.getStringExtra(EXTRA_HOUSEHOLD_NUMBER), intent.getStringExtra(EXTRA_HOUSEHOLD_NAME),
                    amount, intent.getStringExtra(EXTRA_PERSON_NAME), intent.getStringExtra(EXTRA_METHOD),
                    intent.getStringExtra(EXTRA_MATCHED_FINGERPRINT_UUID), intent.getStringExtra(EXTRA_MATCHED_FACE_UUID),
                    selectedIntervention);
            startActivity(review);
        });
    }

    private void setupChecklist(Intent intent) {
        Location location = LocationHelper.getLastKnownLocation(this);
        bindChecklistRow(R.id.rowChecklistLocation, R.string.disbursement_checklist_location, location != null);
        bindChecklistRow(R.id.rowChecklistVerified, R.string.disbursement_checklist_verified, true);
        bindChecklistRow(R.id.rowChecklistOffline, R.string.disbursement_checklist_offline, true);
    }

    private void bindChecklistRow(int rowId, int labelRes, boolean satisfied) {
        android.view.View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.tvChecklistLabel)).setText(labelRes);
        ImageView icon = row.findViewById(R.id.ivChecklistStatus);
        icon.setColorFilter(ContextCompat.getColor(this, satisfied ? R.color.bp_success : R.color.bp_disabled));
    }

    private void setupInterventionSelector() {
        selectIntervention("CASH");
        findViewById(R.id.btnInterventionCash).setOnClickListener(v -> selectIntervention("CASH"));
        findViewById(R.id.btnInterventionVoucher).setOnClickListener(v -> selectIntervention("VOUCHER"));
        findViewById(R.id.btnInterventionFood).setOnClickListener(v -> selectIntervention("FOOD"));
        findViewById(R.id.btnInterventionInKind).setOnClickListener(v -> selectIntervention("IN_KIND"));
    }

    private void selectIntervention(String type) {
        selectedIntervention = type;
        applyInterventionState(R.id.btnInterventionCash, R.id.ivInterventionCash, R.id.tvInterventionCash, "CASH".equals(type));
        applyInterventionState(R.id.btnInterventionVoucher, R.id.ivInterventionVoucher, R.id.tvInterventionVoucher, "VOUCHER".equals(type));
        applyInterventionState(R.id.btnInterventionFood, R.id.ivInterventionFood, R.id.tvInterventionFood, "FOOD".equals(type));
        applyInterventionState(R.id.btnInterventionInKind, R.id.ivInterventionInKind, R.id.tvInterventionInKind, "IN_KIND".equals(type));
    }

    private void applyInterventionState(int containerId, int iconId, int labelId, boolean selected) {
        findViewById(containerId).setBackgroundResource(selected ? R.drawable.bg_status_info : R.drawable.bg_status_neutral);
        int color = ContextCompat.getColor(this, selected ? R.color.bp_primary : R.color.bp_text_secondary);
        ((ImageView) findViewById(iconId)).setColorFilter(color);
        ((TextView) findViewById(labelId)).setTextColor(color);
    }
}
