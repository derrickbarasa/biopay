package com.alphabank.dca.households;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.alphabank.dca.R;
import com.alphabank.dca.alternates.AlternatesActivity;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.payments.PaymentHouseholdsActivity;

public class ReviewActivity extends AppCompatActivity {
    TextView tvState, tvCounty, tvPayam, tvBoma,
            tvHouseholdName, tvAge, tvIdNo, tvIncomeSource, tvAverageIncome, tvPhonenumber, tvSex, tvLegalStatus,
            tvMaritalStatus, tvSpouseName, tvHouseholdSize, tvMaleDependants, tvFemaleDependants, tvZeroFive,
            tvSixSeventeen, tvEighteenFortyFive, tvFortyFiveSixtyFive, tvSixtySixPlus, tvFingerprints,
            tvSelectionCriteria,tvSelectionReason;
    ImageView ivReviewPhoto;
    Button btnReviewHousehold,btnReviewBack;

    GlobalApplication globalApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        globalApplication = GlobalApplication.getInstance();

        tvState = findViewById(R.id.tvState);
        tvCounty = findViewById(R.id.tvCounty);
        tvPayam = findViewById(R.id.tvPayam);
        tvBoma = findViewById(R.id.tvBoma);
        tvHouseholdName = findViewById(R.id.tvHouseholdName);
        tvAge = findViewById(R.id.tvAge);
        tvIdNo = findViewById(R.id.tvIdNo);
        tvIncomeSource = findViewById(R.id.tvIncomeSource);
        tvAverageIncome = findViewById(R.id.tvAverageIncome);
        tvPhonenumber = findViewById(R.id.tvPhonenumber);
        tvLegalStatus = findViewById(R.id.tvLegalStatus);
        tvSex = findViewById(R.id.tvSex);
        tvMaritalStatus = findViewById(R.id.tvMaritalStatus);
        tvSpouseName = findViewById(R.id.tvSpouseName);
        tvHouseholdSize = findViewById(R.id.tvHouseholdSize);
        tvMaleDependants = findViewById(R.id.tvMaleDependants);
        tvFemaleDependants = findViewById(R.id.tvFemaleDependants);
        tvZeroFive = findViewById(R.id.tvZeroFive);
        tvSixSeventeen = findViewById(R.id.tvSixSeventeen);
        tvEighteenFortyFive = findViewById(R.id.tvEighteenFortyFive);
        tvFortyFiveSixtyFive = findViewById(R.id.tvFortyFiveSixtyFive);
        tvSixtySixPlus = findViewById(R.id.tvSixtySixPlus);
        tvFingerprints = findViewById(R.id.tvFingerprints);
        tvSelectionCriteria= findViewById(R.id.tvSelectionCriteria);
        tvSelectionReason= findViewById(R.id.tvSelectionReason);

        ivReviewPhoto = findViewById(R.id.ivReviewPhoto);
        populateHousehold();
        populateImages();
        populateFingers();
        btnReviewBack = findViewById(R.id.btnReviewBack);
        btnReviewBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReviewActivity.this, FingerprintsActivity.class));
            }
        });
        btnReviewHousehold = findViewById(R.id.btnReviewHousehold);
        btnReviewHousehold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder dialog = new AlertDialog.Builder(ReviewActivity.this);
                dialog.setTitle("Review and Complete Registration");
                dialog.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.images, null));
                dialog.setMessage("Do you want to complete the Household registration process. Do you want to proceed to Alternate Registration");

                dialog.setPositiveButton("Alternate Registration", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(ReviewActivity.this, AlternatesActivity.class));
                    }
                });
                dialog.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                dialog.setNegativeButton("Pay Beneficiary", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(ReviewActivity.this, PaymentHouseholdsActivity.class));
                    }
                });
                AlertDialog alert = dialog.create();
                alert.setCanceledOnTouchOutside(false);
                alert.show();
            }
        });
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(ReviewActivity.this, FingerprintsActivity.class));
    }
    private void populateImages() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) // data exist
                {
                    if (cursor.moveToFirst()) {
                        String encodedImage = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL));
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivReviewPhoto.setImageBitmap(decodedByte);
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
    }

    private void populateFingers() {
        Cursor cursor = null;
        String finger = "";
        String whereHousehold = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) // data exist
                {
                    if (cursor.moveToFirst()) {
                        do {
                            int fNo = cursor.getInt(cursor.getColumnIndexOrThrow(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER));
                            finger += "Finger " + fNo + " taken   ";
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
        tvFingerprints.setText(finger);
    }

    private void populateHousehold() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) // data exist
                {
                    if (cursor.moveToFirst()) {
                        String hName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME));
                        String hNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                        String hAge = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE));
                        String hIncomeSource = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD));
                        String hAvIncome = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME));
                        String hPhone = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER));
                        String hIdNo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER));
                        String hGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER));
                        String hLegality = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS));
                        String hMarital = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS));
                        String hSpouse = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME));
                        String hhSize = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE));
                        String hMaleDep = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS));
                        String hFemaleDep = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS));
                        String hZeroFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_FIVE));
                        String hSixSeventeen = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_EIGHTEEN));
                        String hEightFortyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_NINETEEN_FORTY_FIVE));
                        String hFSSixtyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_FORTY_SIX_SIXTY_FIVE));
                        String hSixtySixPlus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_SIX));
                        String hCriteria = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA));
                        String hReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON));


                        tvState.setText("State: " + globalApplication.getStateName());
                        tvCounty.setText("County: " + globalApplication.getCountyName());
                        tvPayam.setText("Payam: " + globalApplication.getPayamName());
                        tvBoma.setText("Boma: " + globalApplication.getBomaName());
                        tvHouseholdName.setText("Household Name: " + globalApplication.getHouseholdName());
                        tvAge.setText("Age: " + hAge);
                        tvIdNo.setText("Id Number: " + hIdNo);
                        tvIncomeSource.setText("Source of Income: " + hIncomeSource);
                        tvAverageIncome.setText("Average Income: " + hAvIncome);
                        tvPhonenumber.setText("Phonenumber: " + hPhone);
                        tvSex.setText("Gender: " + hGender);
                        tvLegalStatus.setText("Legal Status: " + hLegality);
                        tvMaritalStatus.setText("Marital Status: " + hMarital);
                        tvSpouseName.setText("Spouse Name: " + hSpouse);
                        tvHouseholdSize.setText("Household Size: " + hhSize);
                        tvMaleDependants.setText("Male Dependants: " + hMaleDep);
                        tvFemaleDependants.setText("Female Dependants: " + hFemaleDep);
                        tvZeroFive.setText("0-5 yrs Old: " + hZeroFive);
                        tvSixSeventeen.setText("6-17 yrs Old: " + hSixSeventeen);
                        tvEighteenFortyFive.setText("18-45 yrs Old: " + hEightFortyFive);
                        tvFortyFiveSixtyFive.setText("46-65 yrs Old: " + hFSSixtyFive);
                        tvSixtySixPlus.setText("66+ yrs Old: " + hSixtySixPlus);
                        tvSelectionCriteria.setText("Selection Criteria: " + hCriteria);
                        tvSelectionReason.setText("Selection Reason: " + hReason);
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
    }
}