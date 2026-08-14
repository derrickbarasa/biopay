package com.flexmoney.nca.households;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.google.android.material.textfield.TextInputEditText;

public class HouseholdBreakdownActivity extends AppCompatActivity {
    GlobalApplication globalApplication;

    TextInputEditText tietHouseholdSize;
    AutoCompleteTextView actvMaleDependants, actvFemaleDependants,
            actvZeroTwo, actvThreeFive, actvSixSeventeen, actvEighteenThirtyFive,
            actvThirtySixSixtyFour, actvSixtyFivePlus;

    Button btnHouseholdBreakdownBack, btnHouseholdBreakdownNext;
    ArrayAdapter<String> adapterMaleDependants, adapterFemaleDependants,
            adapterZeroTwo, adapterThreeFive, adapterSixSeventeen,
            adapterEighteenThirtyFive, adapterThirtySixSixtyFour, adapterSixtyFivePlus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_breakdown);

        globalApplication = GlobalApplication.getInstance();

        tietHouseholdSize = findViewById(R.id.tietHouseholdSize);
        actvMaleDependants = findViewById(R.id.actvMaleDependants);
        actvFemaleDependants = findViewById(R.id.actvFemaleDependants);

        actvZeroTwo = findViewById(R.id.actvZeroTwo);
        actvThreeFive = findViewById(R.id.actvThreeFive);
        actvSixSeventeen = findViewById(R.id.actvSixSeventeen);
        actvEighteenThirtyFive = findViewById(R.id.actvEighteenThirtyFive);
        actvThirtySixSixtyFour = findViewById(R.id.actvThirtySixSixtyFour);
        actvSixtyFivePlus = findViewById(R.id.actvSixtyFivePlus);

        initialiseAdapters();
        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            populateBreakdown();
        }
        btnHouseholdBreakdownBack = findViewById(R.id.btnHouseholdBreakdownBack);
        btnHouseholdBreakdownBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HouseholdBreakdownActivity.this, PersonalInfoActivity.class));
            }
        });
        btnHouseholdBreakdownNext = findViewById(R.id.btnHouseholdBreakdownNext);
        btnHouseholdBreakdownNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (globalApplication.hasText(tietHouseholdSize, "Enter Household Size")) {
                    update();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(HouseholdBreakdownActivity.this, PersonalInfoActivity.class));
    }


    private void initialiseAdapters() {

        adapterMaleDependants = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvMaleDependants.setThreshold(1);
        actvMaleDependants.setAdapter(adapterMaleDependants);

        adapterFemaleDependants = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvFemaleDependants.setThreshold(1);
        actvFemaleDependants.setAdapter(adapterFemaleDependants);

        adapterZeroTwo = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvZeroTwo.setThreshold(1);
        actvZeroTwo.setAdapter(adapterZeroTwo);

        adapterThreeFive = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvThreeFive.setThreshold(1);
        actvThreeFive.setAdapter(adapterThreeFive);

        adapterSixSeventeen = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvSixSeventeen.setThreshold(1);
        actvSixSeventeen.setAdapter(adapterSixSeventeen);

        adapterEighteenThirtyFive = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvEighteenThirtyFive.setThreshold(1);
        actvEighteenThirtyFive.setAdapter(adapterEighteenThirtyFive);

        adapterThirtySixSixtyFour = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvThirtySixSixtyFour.setThreshold(1);
        actvThirtySixSixtyFour.setAdapter(adapterThirtySixSixtyFour);

        adapterSixtyFivePlus = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(100));
        actvSixtyFivePlus.setThreshold(1);
        actvSixtyFivePlus.setAdapter(adapterSixtyFivePlus);
    }

    private boolean validateAutoCompleteTextView() {
        boolean check = true;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvMaleDependants, "Select Male Dependants"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvFemaleDependants, "Select Female Dependants"))
            check = false;

        if (!globalApplication.isAutoCompleteTextViewSelected(actvZeroTwo, "Select 0-2 yrs Old"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvThreeFive, "Select 3-5 yrs Old"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvSixSeventeen, "Select 6-17 yrs Old"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvEighteenThirtyFive, "Select 18-35 yrs Old"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvThirtySixSixtyFour, "Select 36-64 yrs Old"))
            check = false;
        if (!globalApplication.isAutoCompleteTextViewSelected(actvSixtyFivePlus, "Select 65+ yrs Old"))
            check = false;
        return check;
    }

    private void update() {
        if (validateAutoCompleteTextView()) {
            if (calculateAccuracy()) {
                //Update
                ContentValues value = new ContentValues();
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE, tietHouseholdSize.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS, actvMaleDependants.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS, actvFemaleDependants.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO, actvZeroTwo.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE, actvThreeFive.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN, actvSixSeventeen.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE, actvEighteenThirtyFive.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR, actvThirtySixSixtyFour.getText().toString().trim());
                value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS, actvSixtyFivePlus.getText().toString().trim());

                String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                String whereUpdateHouseholdArgs[] = {globalApplication.getHouseholdNumber()};

                int i = getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
                if (i != 0) {
                    GlobalDialogs.error(HouseholdBreakdownActivity.this, "Household Breakdown",
                            globalApplication.getHouseholdName() + " household breakdown has been updated successfully. Move to the NEXT page",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(HouseholdBreakdownActivity.this, VulnerabilityStatusActivity.class));
                                }
                            });
                } else {
                    GlobalDialogs.error(HouseholdBreakdownActivity.this, "Household Breakdown",
                            globalApplication.getHouseholdName() + " household breakdown update failed",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                }
            }
        }

    }


    private boolean calculateAccuracy() {

        int hhSize = Integer.parseInt(tietHouseholdSize.getText().toString().trim());
        int mD = Integer.parseInt(actvMaleDependants.getText().toString().trim());
        int fD = Integer.parseInt(actvFemaleDependants.getText().toString().trim());
        int zTwo = Integer.parseInt(actvZeroTwo.getText().toString().trim());
        int tFive = Integer.parseInt(actvThreeFive.getText().toString().trim());
        int sSeventeen = Integer.parseInt(actvSixSeventeen.getText().toString().trim());
        int eThirtyFive = Integer.parseInt(actvEighteenThirtyFive.getText().toString().trim());
        int tSixSixtyFour = Integer.parseInt(actvThirtySixSixtyFour.getText().toString().trim());
        int plus = Integer.parseInt(actvSixtyFivePlus.getText().toString().trim());

        if (hhSize != (mD + fD)) {
            GlobalDialogs.error(HouseholdBreakdownActivity.this, "Household Breakdown",
                    "Household size and summation of male and female dependants do not match. Kindly review the selection!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            return false;
        } else {
            if (hhSize != (zTwo + tFive + sSeventeen + eThirtyFive + tSixSixtyFour + plus)) {
                GlobalDialogs.error(HouseholdBreakdownActivity.this, "Household Breakdown",
                        "Household size and summation of 0-2,3-5, 6-17, 18-35, 36-64 and over 65 yrs old breakdown does not match. Kindly review the selection!",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                return false;
            } else {
                return true;
            }
        }

    }

    private void populateBreakdown() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {


                    String hhSize = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE));
                    String hMD = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS));
                    String hFD = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS));
                    String hZeroTwo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO));
                    String hThreeFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE));
                    String hSixSeventeen = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN));
                    String hEighteenThirtyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE));
                    String hThirtySixSixtyFour = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR));
                    String hSixtyFivePlus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS));


                    tietHouseholdSize.setText(hhSize);

                    actvMaleDependants.setText(hMD, false);
                    actvFemaleDependants.setText(hFD, false);


                    actvZeroTwo.setText(hZeroTwo, false);
                    actvThreeFive.setText(hThreeFive, false);
                    actvSixSeventeen.setText(hSixSeventeen, false);
                    actvEighteenThirtyFive.setText(hEighteenThirtyFive, false);
                    actvThirtySixSixtyFour.setText(hThirtySixSixtyFour, false);
                    actvSixtyFivePlus.setText(hSixtyFivePlus, false);

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