package com.flexmoney.nca.alternates;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

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
import com.flexmoney.nca.home.MainActivity;
import com.flexmoney.nca.households.PersonalInfoActivity;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.SessionManager;
import com.morpho.morphosmart.sdk.ErrorCodes;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;

public class AlternateFingerprintsActivity extends AppCompatActivity implements AuthBfdCap {
    Button btnAlternateRightThumb, btnAlternateRightIndex, btnAlternateRightMiddle, btnAlternateRightRing,
            btnAlternateRightLittle, btnAlternateLeftThumb, btnAlternateLeftIndex, btnAlternateLeftMiddle,
            btnAlternateLeftRing, btnAlternateLeftLittle, btnAlternateSave;

    ImageView ivAlternateFingerprint;
    private MorphoTabletFPSensorDevice fpSensorCap;
    GlobalApplication globalApplication;
    SharedPreferences sPref;
    Handler mHandler;
    public static byte[] isoTemplate = null;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    boolean isWorking = false;
    int fingerCode;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alternate_fingerprints);

        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();
        ivAlternateFingerprint = findViewById(R.id.ivAlternateFingerprint);
        sPref = AlternateFingerprintsActivity.this.getSharedPreferences("csp", Context.MODE_PRIVATE);

        btnAlternateRightThumb = findViewById(R.id.btnAlternateRightThumb);
        btnAlternateRightIndex = findViewById(R.id.btnAlternateRightIndex);
        btnAlternateRightMiddle = findViewById(R.id.btnAlternateRightMiddle);
        btnAlternateRightRing = findViewById(R.id.btnAlternateRightRing);
        btnAlternateRightLittle = findViewById(R.id.btnAlternateRightLittle);
        btnAlternateLeftThumb = findViewById(R.id.btnAlternateLeftThumb);
        btnAlternateLeftIndex = findViewById(R.id.btnAlternateLeftIndex);
        btnAlternateLeftMiddle = findViewById(R.id.btnAlternateLeftMiddle);
        btnAlternateLeftRing = findViewById(R.id.btnAlternateLeftRing);
        btnAlternateLeftLittle = findViewById(R.id.btnAlternateLeftLittle);
        btnAlternateSave = findViewById(R.id.btnAlternateSave);

        initFP();
        populateFingerprints();

        btnAlternateSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int enrolledTemplates = populateFingerprints();
                AlertDialog.Builder dialog = new AlertDialog.Builder(AlternateFingerprintsActivity.this);
                dialog.setTitle("Fingerprints Enrollment");
                dialog.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.images, null));
                dialog.setMessage("Fingerprints enrolled are " + enrolledTemplates + ". Do you want to proceed");

                dialog.setPositiveButton("Register Another Alternate", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, AlternatesActivity.class));
                    }
                });
                dialog.setNeutralButton("Home", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, MainActivity.class));
                    }
                });
                dialog.setNegativeButton("Household Registration", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, PersonalInfoActivity.class));

                    }
                });

                AlertDialog alert = dialog.create();
                alert.setCanceledOnTouchOutside(false);
                alert.show();
            }
        });
    }

    public int populateFingerprints() {
        Log.e("Alt Fp No.", globalApplication.getAlternateNumber());
        Cursor cursor = null;
        int count = 0;
        String whereAlternate = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereAlternateArgs[] = {globalApplication.getAlternateNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, whereAlternate, whereAlternateArgs, null);
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
        fpSensorCap.open(AlternateFingerprintsActivity.this);
        fpSensorCap.setViewToUpdate(ivAlternateFingerprint);
    }

    public void btnAlternateEnrolFinger(View view) {

        int idNo = view.getId();

            if(idNo== R.id.btnAlternateRightThumb) {
                fingerCode = 1;
            }else if(idNo ==R.id.btnAlternateRightIndex){
                fingerCode = 2;
            }else if(idNo ==R.id.btnAlternateRightMiddle){
                fingerCode = 3;
            }else if(idNo ==R.id.btnAlternateRightRing){
                fingerCode = 4;
            }else if(idNo ==R.id.btnAlternateRightLittle){
                fingerCode = 5;
            }else if(idNo ==R.id.btnAlternateLeftThumb){
                fingerCode = 6;
            }else if(idNo ==R.id.btnAlternateLeftIndex){
                fingerCode = 7;
            }else if(idNo ==R.id.btnAlternateLeftMiddle){
                fingerCode = 8;
            }else if(idNo ==R.id.btnAlternateLeftRing){
                fingerCode = 9;
            }else if(idNo ==R.id.btnAlternateLeftLittle){
                fingerCode = 10;
            }else {
            }

        if (isWorking) {

            GlobalDialogs.error(AlternateFingerprintsActivity.this, "Fingerprints Enrollment",
                    "System detects the process is not complete. Please wiat for the process to complete!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        } else {
            setButtonEnabled(false);
            capture();
            Toast.makeText(AlternateFingerprintsActivity.this,
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

        AlternateFingerprintsActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (imgPreview != null) {
                    imgPreview.setImageBitmap(previewBitmap);
                }
                if (captureError == ErrorCodes.MORPHOERR_TIMEOUT) {
                    Toast.makeText(AlternateFingerprintsActivity.this, "Capture Timeout", Toast.LENGTH_SHORT).show();
                    setButtonEnabled(true);
                    return;
                } else if (captureError == ErrorCodes.MORPHOERR_CMDE_ABORTED) {
                    setButtonEnabled(true);
                    return;
                }

                if (flagComplete && captureError == ErrorCodes.MORPHO_OK) {
                    setButtonEnabled(true);
                    if (globalApplication.isNullOrEmpty(globalApplication.getAlternateNumber()) == false) {
                        Toast.makeText(AlternateFingerprintsActivity.this, "Fingerprint successfully captured", Toast.LENGTH_SHORT).show();


                        ContentValues value = new ContentValues();
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getAlternateNumber());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER, fingerCode);
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID, UUID.randomUUID().toString().trim());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT, Base64.encodeToString(fpSensorCap.templateBuffer, Base64.NO_WRAP));
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, "0");
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                        value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                        getContentResolver().insert(BiometricsContract.FingerprintEntry.CONTENT_URI, value);
                        updateUI(fingerCode);
                    } else {
                        GlobalDialogs.error(AlternateFingerprintsActivity.this, "Fingerprints Capture",
                                "Alternate not found. Please choose the alternate from edit field",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startActivity(new Intent(AlternateFingerprintsActivity.this, EditAlternateActivity.class));
                                    }
                                });
                    }
                }
            }
        });
    }

    private void updateUI(int fingerNumber) {
        switch (fingerNumber) {
            case 1:
                btnAlternateRightThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateRightThumb.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 2:
                btnAlternateRightIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateRightIndex.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 3:
                btnAlternateRightMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateRightMiddle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 4:
                btnAlternateRightRing.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateRightRing.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 5:
                btnAlternateRightLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateRightLittle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 6:
                btnAlternateLeftThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateLeftThumb.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 7:
                btnAlternateLeftIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateLeftIndex.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 8:
                btnAlternateLeftMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateLeftMiddle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 9:
                btnAlternateLeftRing.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateLeftRing.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            case 10:
                btnAlternateLeftLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                        getResources().getDrawable(R.drawable.finger_success), null, null);
                btnAlternateLeftLittle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(AlternateFingerprintsActivity.this, AlternatesActivity.class));
    }
}