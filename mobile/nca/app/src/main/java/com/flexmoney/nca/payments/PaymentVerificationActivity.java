package com.flexmoney.nca.payments;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flexmoney.nca.AuthBfdCap;
import com.flexmoney.nca.MorphoTabletFPSensorDevice;
import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.SessionManager;
import com.morpho.morphosmart.sdk.ErrorCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaymentVerificationActivity extends AppCompatActivity implements AuthBfdCap {

    private MorphoTabletFPSensorDevice fpSensorCap;
    private ImageView ivPaymentVerification;
    private Button btnPaymentVerification;
    private TextView tvPaymentVerification;
    GlobalApplication globalApplication;
    SessionManager sessionManager;
    List<String> listFingerPrints;
    public String templateUsed;

    private boolean callForVerify = false;
    private boolean isWorking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_verification);

        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();
        initFP();
        listFingerPrints = new ArrayList<String>();

        ivPaymentVerification = findViewById(R.id.ivPaymentVerification);
        tvPaymentVerification = findViewById(R.id.tvPaymentVerification);

        btnPaymentVerification = findViewById(R.id.btnPaymentVerification);
        btnPaymentVerification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isWorking) {
                    GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                            "System detects the process is not complete. Please restart the process?",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = getIntent();
                                    finish();
                                    startActivity(intent);
                                }
                            }
                    );
                } else {
                    setWorkingEnabled(false);
                    verify();
                    callForVerify = true;
                }
            }
        });


    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
    }

    /**
     * Initialize sensor
     */
    public void initFP() {
        fpSensorCap = new MorphoTabletFPSensorDevice(this);
        fpSensorCap.open(PaymentVerificationActivity.this);
        fpSensorCap.setViewToUpdate(ivPaymentVerification);
    }

    private void verify() {
        ivPaymentVerification.setImageBitmap(null);
        tvPaymentVerification.setText("");
        getBeneficiaryFingerprints();
    }

    @SuppressLint("Range")
    private void getBeneficiaryFingerprints() {
        listFingerPrints.clear();
        String where = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "= ?";
        String whereArgs[] = {globalApplication.getAlternateNumber()};

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, where, whereArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String fingerprintByte = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT));
                            listFingerPrints.add(fingerprintByte);
                        } while (cursor.moveToNext());
                    }
                    verifyFingerprint();
                } else {

                    GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                            "No fingerprints found. Please select another beneficiary",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                                }
                            });
                }
            } else {
                GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                        "No fingerprints found. Please select another beneficiary",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                            }
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void verifyFingerprint() {
        byte[] retByte = null;

        retByte = (listFingerPrints.get(0).trim() == null) ? null : Base64.decode(listFingerPrints.get(0).trim(), Base64.NO_WRAP);
        templateUsed = null;
        templateUsed = listFingerPrints.get(0).trim();
        listFingerPrints.remove(0);
        fpSensorCap.verify(retByte);
        tvPaymentVerification.setText("Place your finger on the sensor");
        Toast.makeText(PaymentVerificationActivity.this, "Place your finger on the sensor", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void updateImageView(final ImageView im, final Bitmap bm,
                                String mesg, final boolean flagComplete, final int captureError) {

        PaymentVerificationActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (im != null) {
                    im.setImageBitmap(bm);
                }
                tvPaymentVerification.setText("Verification in progress");
                if (flagComplete) {
                    setWorkingEnabled(true);
                    if (captureError == ErrorCodes.MORPHOERR_TIMEOUT) {
                        Toast.makeText(PaymentVerificationActivity.this, "Verification timed out", Toast.LENGTH_SHORT).show();
                        tvPaymentVerification.setText("Verification timed out");
                        return;
                    } else if (captureError == ErrorCodes.MORPHOERR_CMDE_ABORTED) {
                        tvPaymentVerification.setText("Verification aborted");
                        return;
                    }

                    if (callForVerify) {
                        if (captureError == ErrorCodes.MORPHO_OK) {
                            tvPaymentVerification.setText("Biometric verification is successful!!");

                            Toast.makeText(PaymentVerificationActivity.this, "Biometric verification is successful", Toast.LENGTH_SHORT).show();
                            if (globalApplication.getHouseholdNumber() != null) {

                                ContentValues valueUpdatePayment = new ContentValues();
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC, "0");
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS, "1");
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE, globalApplication.getLevel());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_MATCHED_FP, templateUsed);
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID, UUID.randomUUID().toString());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE, globalApplication.getPaymentCentre());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LATITUDE, globalApplication.getLatitude());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LONGITUDE, globalApplication.getLongitude());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAID_TO, globalApplication.getAlternateNumber());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                                valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                                Uri uri = getContentResolver().insert(BiometricsContract.PaymentsEntry.CONTENT_URI, valueUpdatePayment);
                                // int u = getContentResolver().update(BiometricsContract.PaymentsEntry.CONTENT_URI, valueUpdatePayment, whereUpdatePayments, whereUpdatePaymentsArgs);


                                GlobalDialogs.warning(PaymentVerificationActivity.this, "Payment Verification",
                                        "Payment SUCCESSFULLY saved, please choose another household!",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
                                            }
                                        });

                            } else {
                                GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                                        "System could not find the Payment Batch (cycle). DO NOT PAY the beneficiary. Please direct the beneficiary to the relevant authorities!",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
                                            }
                                        });
                            }

                        } else {

                            if (listFingerPrints.isEmpty()) {
                                GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                                        "Fingerprints Verification failed. All stored fingerprints checked. This might not be the actual beneficiary. Choose an alternate!!",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                                            }
                                        });

                            } else {
                                byte[] byteValue = (listFingerPrints.get(0).trim() == null) ? null : Base64.decode(listFingerPrints.get(0).trim(), Base64.DEFAULT);

                                templateUsed = null;
                                templateUsed = listFingerPrints.get(0).trim();
                                listFingerPrints.remove(0);
                                fpSensorCap.verify(byteValue);
                                //Toast.makeText(PaymentFPActivity.this, "Place your finger on the sensor ", Toast.LENGTH_SHORT).show();

                            }
                        }
                    }
                }
            }
        });
    }

    private void setWorkingEnabled(boolean enabled) {
        isWorking = !enabled;
    }

    @Override
    public void onPause() {
        super.onPause();
        fpSensorCap.cancelLiveAcquisition();
    }

}