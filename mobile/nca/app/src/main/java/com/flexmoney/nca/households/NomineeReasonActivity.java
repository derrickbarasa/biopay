package com.flexmoney.nca.households;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;

import androidx.appcompat.app.AppCompatActivity;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class NomineeReasonActivity extends AppCompatActivity {
    GlobalApplication globalApplication;
    MultiAutoCompleteTextView mactvNomineeReason;
    TextInputLayout tilNomineeReason, tilNomineeOtherReason;
    TextInputEditText tietNomineeOtherReason;
    Button btnNomineeReasonBack, btnNomineeReasonNext;
    ArrayAdapter<String> adapterNomineeReason;
    String firstNominee, lastNominee, hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nominee_reason);

        globalApplication = GlobalApplication.getInstance();
        mactvNomineeReason = findViewById(R.id.mactvNomineeReason);
        tilNomineeReason = findViewById(R.id.tilNomineeReason);

        firstNominee = getFirstNomineeName();
        lastNominee = getLastNomineeName();
        if (firstNominee.matches(lastNominee)) {
            hint = "What was the main reason for nominating " + firstNominee +
                    " first for this new component?";
        } else {
            hint = "What was the main reason for nominating " + firstNominee +
                    " over the other household members for this new component? (You can choose more than one)";
        }
        mactvNomineeReason.setHint(hint);
        tilNomineeReason.setHint(hint);

        tilNomineeOtherReason = findViewById(R.id.tilNomineeOtherReason);
        tietNomineeOtherReason = findViewById(R.id.tietNomineeOtherReason);


        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            initialiseAdapters();
            populateNomineeReasons();
        }

        btnNomineeReasonBack = findViewById(R.id.btnNomineeReasonBack);
        btnNomineeReasonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NomineeReasonActivity.this, NomineeActivity.class));
            }
        });
        btnNomineeReasonNext = findViewById(R.id.btnNomineeReasonNext);
        btnNomineeReasonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                update();
            }
        });
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(NomineeReasonActivity.this, NomineeActivity.class));
    }
    private void update() {
        if (validateAutoCompleteTextView()) {
            //Update

            ContentValues value = new ContentValues();
            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_NOMINEE_REASON, mactvNomineeReason.getText().toString().trim());
            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_NOMINEE_OTHER_REASON, tietNomineeOtherReason.getText().toString().trim());

            String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
            String whereUpdateHouseholdArgs[] = {globalApplication.getHouseholdNumber()};

            int i = getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
            if (i != 0) {
                GlobalDialogs.error(NomineeReasonActivity.this, "Nominee Reason",
                        globalApplication.getHouseholdName() + " Nominee Reason has been updated successfully. Move to the NEXT page",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(NomineeReasonActivity.this, PhotoActivity.class));
                            }
                        });
            } else {
                GlobalDialogs.error(NomineeReasonActivity.this, "Nominee Reason",
                        globalApplication.getHouseholdName() + " Nominee Reason update failed",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }

        } else {
            GlobalDialogs.error(NomineeReasonActivity.this, "Nominee Reason",
                    globalApplication.getHouseholdName() + " Kindly check select all fields",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }

    }

    private boolean validateAutoCompleteTextView() {
        boolean check = true;

        if (!globalApplication.isAutoCompleteTextViewSelected(mactvNomineeReason, "Select Nominee Reason"))
            check = false;
        return check;
    }

    private void populateNomineeReasons() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {


                    String nomineeReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_NOMINEE_REASON));
                    String nomineeOtherReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_NOMINEE_OTHER_REASON));

                    mactvNomineeReason.setText(nomineeReason, false);
                    tietNomineeOtherReason.setText(nomineeOtherReason);
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

    private void initialiseAdapters() {

        String[] nomineeReason = getResources().getStringArray(R.array.nominee_reason);
        String[] formattedNomineeReason = globalApplication.replaceStringArray(nomineeReason, firstNominee);
        adapterNomineeReason = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, formattedNomineeReason);
        mactvNomineeReason.setThreshold(1);
        mactvNomineeReason.setAdapter(adapterNomineeReason);
        mactvNomineeReason.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        mactvNomineeReason.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String otherReason = adapterView.getItemAtPosition(i).toString().trim();
                if (otherReason.contains("Other (Specify)")) {
                    tilNomineeOtherReason.setVisibility(View.VISIBLE);
                } else {
                    tilNomineeOtherReason.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private String getFirstNomineeName() {
        String nomineeName = "";
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.NomineeEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, BiometricsContract.NomineeEntry._ID + " ASC");
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    nomineeName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NAME));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return nomineeName;
    }

    private String getLastNomineeName() {
        String nomineeName = "";
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.NomineeEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, BiometricsContract.NomineeEntry._ID + " DESC");
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    nomineeName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NAME));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return nomineeName;
    }
}