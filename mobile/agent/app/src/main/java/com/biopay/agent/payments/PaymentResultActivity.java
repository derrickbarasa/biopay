package com.biopay.agent.payments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import com.biopay.agent.ui.BaseActivity;

import java.text.NumberFormat;

/** Shared success/failure outcome for payment verification with explicit next actions. */
public class PaymentResultActivity extends BaseActivity {
    private static final String EXTRA_SUCCESS = "success";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_METHOD = "method";
    private static final String EXTRA_AMOUNT = "amount";
    private static final String EXTRA_HOUSEHOLD_NAME = "household_name";

    public static Intent successIntent(Context context, String personName, String householdName,
            double amount, String method) {
        return new Intent(context, PaymentResultActivity.class)
                .putExtra(EXTRA_SUCCESS, true)
                .putExtra(EXTRA_MESSAGE, context.getString(R.string.payment_result_success_message,
                        personName, householdName))
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_HOUSEHOLD_NAME, householdName)
                .putExtra(EXTRA_METHOD, method);
    }

    public static Intent failureIntent(Context context, String message, String householdName, double amount) {
        return new Intent(context, PaymentResultActivity.class)
                .putExtra(EXTRA_SUCCESS, false)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_HOUSEHOLD_NAME, householdName);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_result);
        boolean success = getIntent().getBooleanExtra(EXTRA_SUCCESS, false);
        ((TextView) findViewById(R.id.tvResultTitle)).setText(
                success ? R.string.payment_result_success_title : R.string.payment_result_failure_title);
        ((TextView) findViewById(R.id.tvResultMessage)).setText(getIntent().getStringExtra(EXTRA_MESSAGE));
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        ((TextView) findViewById(R.id.tvResultAmount)).setText(getString(
                success ? R.string.payment_result_amount_paid : R.string.payment_result_amount_not_paid,
                NumberFormat.getNumberInstance().format(amount)));
        ((TextView) findViewById(R.id.tvResultHousehold)).setText(
                getIntent().getStringExtra(EXTRA_HOUSEHOLD_NAME));
        TextView method = findViewById(R.id.tvResultMethod);
        if (success) {
            method.setText(getString(R.string.payment_result_verified_using,
                    getIntent().getStringExtra(EXTRA_METHOD)));
        } else {
            method.setText(R.string.payment_result_failure_help);
            method.setBackground(null);
            method.setTextColor(ContextCompat.getColor(this, R.color.bp_text_secondary));
            ((TextView) findViewById(R.id.tvResultAmount)).setTextColor(
                    ContextCompat.getColor(this, R.color.bp_error));
            ImageView icon = findViewById(R.id.ivResultIcon);
            icon.setImageResource(R.drawable.ic_close);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.bp_error));
            findViewById(R.id.resultIconContainer).setBackgroundResource(R.drawable.bg_error_banner);
        }

        findViewById(R.id.btnAnotherPayment).setOnClickListener(v -> {
            startActivity(new Intent(this, GeneratePaymentActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        findViewById(R.id.btnPaymentHome).setOnClickListener(v -> finishToHome());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishToHome(); }
        });
    }

    private void finishToHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
