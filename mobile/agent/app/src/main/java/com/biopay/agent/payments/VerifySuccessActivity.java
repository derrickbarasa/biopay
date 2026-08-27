package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * The full-bleed teal success screen shared by both moments in the flow: right after a
 * fingerprint/face match ("Identity Verified", continues to Disbursement) and after a payment is
 * actually recorded ("Disbursement Complete", a receipt, and Done). Which one is driven by
 * {@link #EXTRA_MODE}; the receipt/summary rows are passed as parallel label/value arrays rather
 * than a bespoke screen per mode, since the layout (title, subtitle, one content card, one
 * button) is otherwise identical.
 */
public class VerifySuccessActivity extends BaseActivity {

    public static final String MODE_VERIFICATION = "VERIFICATION";
    public static final String MODE_TRANSACTION = "TRANSACTION";

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_SUBTITLE = "subtitle";
    private static final String EXTRA_LABELS = "labels";
    private static final String EXTRA_VALUES = "values";
    private static final String EXTRA_BUTTON_LABEL = "button_label";
    private static final String EXTRA_NEXT_INTENT = "next_intent";

    public static Intent intentFor(Context context, String mode, String title, String subtitle,
            String[] labels, String[] values, String buttonLabel, Intent nextIntent) {
        Intent intent = new Intent(context, VerifySuccessActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_LABELS, labels);
        intent.putExtra(EXTRA_VALUES, values);
        intent.putExtra(EXTRA_BUTTON_LABEL, buttonLabel);
        if (nextIntent != null) intent.putExtra(EXTRA_NEXT_INTENT, nextIntent);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partial_success_state);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // Success screens are a one-way door -- back should not return into a completed
                // verification/disbursement step and risk re-triggering it.
                finishToHome();
            }
        });

        Intent intent = getIntent();
        boolean isTransaction = MODE_TRANSACTION.equals(intent.getStringExtra(EXTRA_MODE));
        ((TextView) findViewById(R.id.tvSuccessTitle)).setText(intent.getStringExtra(EXTRA_TITLE));
        ((TextView) findViewById(R.id.tvSuccessSubtitle)).setText(intent.getStringExtra(EXTRA_SUBTITLE));

        String[] labels = intent.getStringArrayExtra(EXTRA_LABELS);
        String[] values = intent.getStringArrayExtra(EXTRA_VALUES);
        android.widget.LinearLayout content = findViewById(R.id.contentContainer);
        MaterialCardView card = buildContentCard(content, labels, values, isTransaction);
        content.addView(card);

        if (isTransaction) {
            TextView note = new TextView(this);
            note.setText(R.string.transaction_saved_offline);
            note.setTextColor(ContextCompat.getColor(this, R.color.bp_on_primary));
            note.setAlpha(0.9f);
            note.setTextSize(12.5f);
            note.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(14);
            note.setLayoutParams(params);
            content.addView(note);
        }

        MaterialButton primary = findViewById(R.id.btnSuccessPrimary);
        primary.setText(intent.getStringExtra(EXTRA_BUTTON_LABEL));
        Intent nextIntent = intent.getParcelableExtra(EXTRA_NEXT_INTENT);
        primary.setOnClickListener(v -> {
            if (nextIntent != null) {
                startActivity(nextIntent);
                finish();
            } else {
                finishToHome();
            }
        });
    }

    private MaterialCardView buildContentCard(android.view.ViewGroup parent, String[] labels, String[] values, boolean isTransaction) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bp_surface));
        android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardParams);

        android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(16);
        rows.setPadding(pad, pad, pad, pad);
        LayoutInflater inflater = LayoutInflater.from(this);
        if (labels != null) {
            for (int i = 0; i < labels.length; i++) {
                android.view.View row = inflater.inflate(R.layout.item_summary_row, rows, false);
                ((TextView) row.findViewById(R.id.tvSummaryLabel)).setText(labels[i]);
                ((TextView) row.findViewById(R.id.tvSummaryValue)).setText(i < values.length ? values[i] : "");
                if (isTransaction) {
                    ((TextView) row.findViewById(R.id.tvSummaryLabel)).setTextColor(ContextCompat.getColor(this, R.color.bp_text_secondary));
                }
                rows.addView(row);
            }
        }
        card.addView(rows);
        return card;
    }

    private void finishToHome() {
        Intent intent = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
