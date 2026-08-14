package com.flexmoney.nca.households;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.flexmoney.nca.AuthBfdCap;
import com.flexmoney.nca.MorphoTabletFPSensorDevice;
import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.SessionManager;
import com.morpho.morphosmart.sdk.ErrorCodes;

import java.util.UUID;

public class FingerprintsActivity extends AppCompatActivity implements AuthBfdCap {
    Button btnRightThumb, btnRightIndex, btnRightMiddle, btnRightRing, btnRightLittle,
            btnLeftThumb, btnLeftIndex, btnLeftMiddle, btnLeftRing, btnLeftLittle, btnFingerprintNext,
            btnFingerprintBack;
    ImageView ivHouseholdFingerprint;
    private MorphoTabletFPSensorDevice fpSensorCap;
    GlobalApplication globalApplication;
    SharedPreferences sPref;
    Handler mHandler;
    public static byte[] isoTemplate = null;
    boolean isWorking = false;
    int fingerCode;
    SessionManager sessionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprints);

        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();
        ivHouseholdFingerprint = findViewById(R.id.ivHouseholdFingerprint);
        sPref = FingerprintsActivity.this.getSharedPreferences("csp", Context.MODE_PRIVATE);

        btnRightThumb = findViewById(R.id.btnRightThumb);
        btnRightIndex = findViewById(R.id.btnRightIndex);
        btnRightMiddle = findViewById(R.id.btnRightMiddle);
        btnRightRing = findViewById(R.id.btnRightRing);
        btnRightLittle = findViewById(R.id.btnRightLittle);
        btnLeftThumb = findViewById(R.id.btnLeftThumb);
        btnLeftIndex = findViewById(R.id.btnLeftIndex);
        btnLeftMiddle = findViewById(R.id.btnLeftMiddle);
        btnLeftRing = findViewById(R.id.btnLeftRing);
        btnLeftLittle = findViewById(R.id.btnLeftLittle);

        initFP();
        populateFingerprints();
        btnFingerprintBack = findViewById(R.id.btnFingerprintBack);
        btnFingerprintBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FingerprintsActivity.this, PhotoActivity.class));
            }
        });
        btnFingerprintNext = findViewById(R.id.btnFingerprintNext);
        btnFingerprintNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int enrolledTemplates = populateFingerprints();

                GlobalDialogs.success(FingerprintsActivity.this, "Fingerprints Enrollment",
                        "Fingerprints enrolled are " + enrolledTemplates + ". Do you want to proceed",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(FingerprintsActivity.this, ReviewActivity.class));
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        }
                );
            }
        });
    }

    public int populateFingerprints() {
        Cursor cursor = null;
        int count = 0;
        String whereHousehold = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                count = cursor.getCount();
                if (count != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            int fNo = cursor.getInt(cursor.getColumnIndexOrThrow(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER));
                            updateUI(fNo);
                        } while (cursor.moveToNext());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
    }

    /**
     * To know status of sensor Working/ideal
     */
    public void setButtonEnabled(boolean enabled) {
        isWorking = !enabled;
    }

    /**
     * Initialize sensor
     */
    public void initFP() {
        fpSensorCap = new MorphoTabletFPSensorDevice(this);
        fpSensorCap.open(FingerprintsActivity.this);
        fpSensorCap.setViewToUpdate(ivHouseholdFingerprint);
    }

    public void btnEnrolFinger(View view) {
        int id = view.getId();
        if (id == R.id.btnRightThumb) {
            fingerCode = 1;
        } else if (id == R.id.btnRightIndex) {
            fingerCode = 2;
        } else if (id == R.id.btnRightMiddle) {
            fingerCode = 3;
        } else if (id == R.id.btnRightRing) {
            fingerCode = 4;
        } else if (id == R.id.btnRightLittle) {
            fingerCode = 5;
        } else if (id == R.id.btnLeftThumb) {
            fingerCode = 6;
        } else if (id == R.id.btnLeftIndex) {
            fingerCode = 7;
        } else if (id == R.id.btnLeftMiddle) {
            fingerCode = 8;
        } else if (id == R.id.btnLeftRing) {
            fingerCode = 9;
        } else if (id == R.id.btnLeftLittle) {
            fingerCode = 10;
        } else {
        }

        if (isWorking) {

            GlobalDialogs.error(FingerprintsActivity.this, "Fingerprints Enrollment",
                    "System detects the process is not complete. Please wait for the process to complete!!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        } else {
            setButtonEnabled(false);
            capture();
            Toast.makeText(FingerprintsActivity.this,
                            "Place your finger on the sensor ", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * To start capture
     */
    private void capture() {
        try {
            fpSensorCap.startCapture();
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "capture", e);
        }
    }

    @Override
    public void updateImageView(final ImageView imgPreview,
                                final Bitmap previewBitmap, String message,
                                final boolean flagComplete, final int captureError) {

        FingerprintsActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (imgPreview != null) {
                    imgPreview.setImageBitmap(previewBitmap);
                }
                if (captureError == ErrorCodes.MORPHOERR_TIMEOUT) {
                    Toast.makeText(FingerprintsActivity.this, "Capture Timeout", Toast.LENGTH_SHORT).show();
                    setButtonEnabled(true);
                    return;
                } else if (captureError == ErrorCodes.MORPHOERR_CMDE_ABORTED) {
                    setButtonEnabled(true);
                    return;
                }

                if (flagComplete && captureError == ErrorCodes.MORPHO_OK) {
                    setButtonEnabled(true);
                    //Check the household number availability
                    if (globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber()) == false) {
                        Toast.makeText(FingerprintsActivity.this, "Fingerprint successfully captured", Toast.LENGTH_SHORT).show();


                        ContentValues value = new ContentValues();
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getHouseholdNumber());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER, fingerCode);
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID, UUID.randomUUID().toString().trim());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT, Base64.encodeToString(fpSensorCap.templateBuffer, Base64.NO_WRAP));
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, "0");
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                        getContentResolver().insert(BiometricsContract.FingerprintEntry.CONTENT_URI, value);
                        updateUI(fingerCode);
                    } else {
                        GlobalDialogs.error(FingerprintsActivity.this, "Fingerprints Capture",
                                "Household not found. Please choose the household from edit field",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startActivity(new Intent(FingerprintsActivity.this, LocationActivity.class));
                                    }
                                });
                    }
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(FingerprintsActivity.this, PhotoActivity.class));
    }

    private void updateUI(int fingerNumber) {
        switch (fingerNumber) {
            case 1:
                btnRightThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnRightThumb.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 2:
                btnRightIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnRightIndex.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 3:
                btnRightMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnRightMiddle.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 4:
                btnRightRing.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnRightRing.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 5:
                btnRightLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnRightLittle.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 6:
                btnLeftThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnLeftThumb.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 7:
                btnLeftIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnLeftIndex.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 8:
                btnLeftMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnLeftMiddle.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 9:
                btnLeftRing.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnLeftRing.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            case 10:
                btnLeftLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnLeftLittle.setTextColor(ContextCompat.getColor(FingerprintsActivity.this, R.color.black));
                break;
            default:
                break;
        }
    }
}