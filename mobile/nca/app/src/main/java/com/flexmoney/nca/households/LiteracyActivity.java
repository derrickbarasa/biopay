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

import org.json.JSONObject;

public class LiteracyActivity extends AppCompatActivity {
    GlobalApplication globalApplication;
    AutoCompleteTextView actvYouthLiteracy;
    Button btnLiteracyBack, btnLiteracyNext;
    ArrayAdapter<String> adapterYouthLiteracy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_literacy);

        globalApplication = GlobalApplication.getInstance();

        actvYouthLiteracy = findViewById(R.id.actvYouthLiteracy);
        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            initialiseAdapters();
            populateLiteracy();
        }
        btnLiteracyBack = findViewById(R.id.btnLiteracyBack);
        btnLiteracyBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LiteracyActivity.this, VulnerabilityStatusActivity.class));
            }
        });
        btnLiteracyNext = findViewById(R.id.btnLiteracyNext);
        btnLiteracyNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                update();
            }
        });
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(LiteracyActivity.this, VulnerabilityStatusActivity.class));
    }

    private void initialiseAdapters() {



        adapterYouthLiteracy = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, globalApplication.sequenceNumbers(getYouthMembers()));
        actvYouthLiteracy.setThreshold(1);
        actvYouthLiteracy.setAdapter(adapterYouthLiteracy);
    }

    private int getYouthMembers() {
        int youthMembers = 0;
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=? AND "
                + BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber(), "1081"};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) // data exist
                {
                    if (cursor.moveToFirst()) {
                        String eighteenThirtyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE)).trim();
                        youthMembers = !eighteenThirtyFive.isEmpty() ? Integer.parseInt(eighteenThirtyFive) : 0;
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
        return youthMembers;
    }

    private void populateLiteracy() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {

                    String youthLiteracy = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_YOUTH_LITERACY));

                    actvYouthLiteracy.setText(youthLiteracy, false);
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

    private boolean validateAutoCompleteTextView() {
        boolean check = true;

        if (!globalApplication.isAutoCompleteTextViewSelected(actvYouthLiteracy, "Select Literate Youth Member (18-35)"))
            check = false;

        return check;
    }

    private void update() {
        if (validateAutoCompleteTextView()) {
            //Update

            String literateYouth = actvYouthLiteracy.getText().toString().trim();
            int youth = (!literateYouth.isEmpty()) ? Integer.parseInt(literateYouth) : 0;

            ContentValues value = new ContentValues();

            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_YOUTH_LITERACY, literateYouth);

            String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
            String whereUpdateHouseholdArgs[] = {globalApplication.getHouseholdNumber()};

            int i = getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
            if (i != 0) {
                GlobalDialogs.error(LiteracyActivity.this, "Household Literacy",
                        globalApplication.getHouseholdName() + " Household Literacy has been updated successfully. Move to the NEXT page",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (youth > 0 && eligibilityQuiz() >= 3) {
                                    startActivity(new Intent(LiteracyActivity.this, NomineeActivity.class));
                                } else {
                                    startActivity(new Intent(LiteracyActivity.this, PhotoActivity.class));
                                }
                            }
                        });
            } else {
                GlobalDialogs.error(LiteracyActivity.this, "Household Literacy",
                        globalApplication.getHouseholdName() + " Household Literacy update failed",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }

        } else {
            GlobalDialogs.error(LiteracyActivity.this, "Household Literacy",
                    globalApplication.getHouseholdName() + " Kindly select can you read and write",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }
    }

    private int eligibilityQuiz() {
        int eligibility = 0;
        try {
            JSONObject beneficiary = getBeneficiary();

            if (beneficiary.has("zeroTwo")) {
                int zeroTwo = Integer.parseInt(beneficiary.getString("zeroTwo"));
                int threeFive = Integer.parseInt(beneficiary.getString("threeFive"));
                int sixSeventeen = Integer.parseInt(beneficiary.getString("sixSeventeen"));
                int sixtyFivePlus = Integer.parseInt(beneficiary.getString("sixtyFivePlus"));
                int disabledMembers = Integer.parseInt(beneficiary.getString("disabledMembers"));
                int chronicallyIllMembers = Integer.parseInt(beneficiary.getString("chronicallyIllMembers"));

                eligibility = zeroTwo + threeFive + sixSeventeen + sixtyFivePlus + disabledMembers + chronicallyIllMembers;


            }
        } catch (Exception e) {
            System.err.println(e.getMessage());

        }
        return eligibility;
    }

    private JSONObject getBeneficiary() {
        JSONObject response = new JSONObject();
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=? AND "
                + BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber(), "1081"};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) // data exist
                {
                    if (cursor.moveToFirst()) {

                        String zeroTwo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO));
                        String threeFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE));
                        String sixSeventeen = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN));
                        String eighteenThirtyFive = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE));
                        String sixtyFivePlus = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS));
                        String disabledMembers = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_DISABLED_MEMBERS));
                        String chronicallyIllMembers = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_CHRONICALLY_ILL_MEMBERS));

                        response.put("zeroTwo", zeroTwo);
                        response.put("threeFive", threeFive);
                        response.put("sixSeventeen", sixSeventeen);
                        response.put("eighteenThirtyFive", eighteenThirtyFive);
                        response.put("sixtyFivePlus", sixtyFivePlus);
                        response.put("disabledMembers", disabledMembers);
                        response.put("chronicallyIllMembers", chronicallyIllMembers);
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
        return response;
    }

}