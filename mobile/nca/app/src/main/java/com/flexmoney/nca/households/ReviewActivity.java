package com.flexmoney.nca.households;

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

import com.flexmoney.nca.R;
import com.flexmoney.nca.alternates.AlternatesActivity;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.payments.PaymentHouseholdsActivity;

public class ReviewActivity extends AppCompatActivity {
    TextView tvState, tvCounty, tvPayam, tvBoma,
            tvHouseholdName, tvAge, tvIdNo, tvIncomeSource, tvAverageIncome, tvPhonenumber, tvSex, tvLegalStatus,
            tvMaritalStatus, tvSpouseName, tvHouseholdSize, tvMaleDependants, tvFemaleDependants, tvZeroTwo,tvThreeFive,
            tvSixSeventeen, tvEighteenThirtyFive, tvThirtySixSixtyFour, tvSixtyFivePlus, tvFingerprints,
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
        tvZeroTwo = findViewById(R.id.tvZeroTwo);
        tvSixSeventeen = findViewById(R.id.tvSixSeventeen);
        tvEighteenThirtyFive = findViewById(R.id.tvEighteenThirtyFive);
        tvThirtySixSixtyFour = findViewById(R.id.tvThirtySixSixtyFour);
        tvSixtyFivePlus = findViewById(R.id.tvSixtyFivePlus);
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
                dialog.setNeutralButton("Pay Beneficiary", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(ReviewActivity.this, PaymentHouseholdsActivity.class));
                    }
                });
                dialog.setNegativeButton("Household Registration", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(ReviewActivity.this, EditHouseholdActivity.class));
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
                        String hIncomeSource = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE));
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
                        String hZeroTwo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO));
                        String hThreeFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE));
                        String hSixSeventeen = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN));
                        String hEightThirtyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE));
                        String hFSSixtyFour = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR));
                        String hSixtyFivePlus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS));
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
                        tvZeroTwo.setText("0-2 yrs Old: " + hZeroTwo);
                        tvSixSeventeen.setText("6-17 yrs Old: " + hSixSeventeen);
                        tvEighteenThirtyFive.setText("18-35 yrs Old: " + hEightThirtyFive);
                        tvThirtySixSixtyFour.setText("36-64 yrs Old: " + hFSSixtyFour);
                        tvSixtyFivePlus.setText("65+ yrs Old: " + hSixtyFivePlus);
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