package com.flexmoney.nca.households;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flexmoney.nca.R;
import com.flexmoney.nca.adapters.RecyclerNomineeAdapter;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.Nominee;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NomineeActivity extends AppCompatActivity {
    GlobalApplication globalApplication;
    AutoCompleteTextView actvEligibility, actvIneligibility;
    TextInputLayout tilIneligibility, tilIneligibilityOtherReason;
    TextInputEditText tietIneligibilityOtherReason;
    Button btnNomineeBack, btnNomineeNext;

    ArrayAdapter<String> adapterEligibility, adapterIneligibility;
    RecyclerNomineeAdapter nomineeAdapter;

    List<Nominee> nomineeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nominee);

        globalApplication = GlobalApplication.getInstance();

        tietIneligibilityOtherReason = findViewById(R.id.tietIneligibilityOtherReason);

        actvEligibility = findViewById(R.id.actvEligibility);
        actvIneligibility = findViewById(R.id.actvIneligibility);

        tilIneligibility = findViewById(R.id.tilIneligibility);
        tilIneligibilityOtherReason = findViewById(R.id.tilIneligibilityOtherReason);

        LinearLayoutManager lmNominees = new LinearLayoutManager(this);
        nomineeList = new ArrayList<>();
        RecyclerView rvNominees = findViewById(R.id.rvNominees);

        nomineeAdapter = new RecyclerNomineeAdapter(NomineeActivity.this, nomineeList);
        rvNominees.setAdapter(nomineeAdapter);
        rvNominees.setLayoutManager(lmNominees);


        initialiseAdapters();
        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            populateEligibility();
            populateNominees();
        }

        if (nomineeList.isEmpty()) {
            GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                    "I would like to tell you about a livelihood component that we’re introducing in addition to the cash transfer support (either through “Direct Income Support” or “Public Works”) that the program is already offering. This additional component wants to enhance economic opportunities for poor and vulnerable youth, with the goal of supporting them to become more productive. The new program component may include training in business, livelihoods, and soft skills, along with an additional conditional cash grant. Please note that this new program component will be introduced only to some households, so it is important to understand that the presence of this message does not necessarily mean that your household has been selected for the economic opportunities program. Only household members who are between 18-35 years old, and who can read and write are eligible for participation in this new component",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }
        btnNomineeBack = findViewById(R.id.btnNomineeBack);
        btnNomineeBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NomineeActivity.this, LiteracyActivity.class));
            }
        });
        btnNomineeNext = findViewById(R.id.btnNomineeNext);
        btnNomineeNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                update();
            }
        });

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(NomineeActivity.this, LiteracyActivity.class));
    }

    private void showNomineeAlertDialog(String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        // set the custom layout
        final View layoutNominee = getLayoutInflater().inflate(R.layout.content_nominee, null);
        builder.setView(layoutNominee);

        TextInputEditText tietNomineeName = layoutNominee.findViewById(R.id.tietNomineeName);
        TextInputEditText tietNomineeAge = layoutNominee.findViewById(R.id.tietNomineeAge);
        AutoCompleteTextView actvNomineeGender = layoutNominee.findViewById(R.id.actvNomineeGender);
        String[] gender = getResources().getStringArray(R.array.gender_list);
        ArrayAdapter<String> adapterNomineeGender = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, gender);
        actvNomineeGender.setThreshold(1);
        actvNomineeGender.setAdapter(adapterNomineeGender);

        AutoCompleteTextView actvNomineeRelationship = layoutNominee.findViewById(R.id.actvNomineeRelationship);
        String[] relationship = getResources().getStringArray(R.array.relationship);
        ArrayAdapter<String> adapterNomineeRelationship = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, relationship);
        actvNomineeRelationship.setThreshold(1);
        actvNomineeRelationship.setAdapter(adapterNomineeRelationship);

        TextInputLayout tilOtherNomineeOccupation = layoutNominee.findViewById(R.id.tilOtherNomineeOccupation);
        TextInputEditText tietOtherNomineeOccupation = layoutNominee.findViewById(R.id.tietOtherNomineeOccupation);

        AutoCompleteTextView actvNomineeOccupation = layoutNominee.findViewById(R.id.actvNomineeOccupation);
        String[] nomineeIncomeList = getResources().getStringArray(R.array.nominee_income_list);
        ArrayAdapter<String> adapterNomineeOccupation = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, nomineeIncomeList);
        actvNomineeOccupation.setThreshold(1);
        actvNomineeOccupation.setAdapter(adapterNomineeOccupation);
        actvNomineeOccupation.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String otherOccupation = adapterView.getItemAtPosition(i).toString().trim();
                if (otherOccupation.contains("Other (Specify)")) {
                    tilOtherNomineeOccupation.setVisibility(View.VISIBLE);
                } else {
                    tilOtherNomineeOccupation.setVisibility(View.INVISIBLE);
                }
            }
        });


        builder.setPositiveButton("NEXT", (dialog, which) -> {

            if (globalApplication.hasText(tietNomineeName, "Enter Nominee Name") ||
                    globalApplication.hasText(tietNomineeAge, "Enter Nominee Age")) {

                ContentValues value = new ContentValues();

                String sex = actvNomineeGender.getText().toString().trim();
                String oppositeSex = sex.equalsIgnoreCase("Male") ? "Female" : "Male";

                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NUMBER, UUID.randomUUID().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NAME, tietNomineeName.getText().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_AGE, tietNomineeAge.getText().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_GENDER, sex);
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_RELATIONSHIP, actvNomineeRelationship.getText().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_OCCUPATION, actvNomineeOccupation.getText().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_OTHER_OCCUPATION, tietOtherNomineeOccupation.getText().toString().trim());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_STATUS, "0");
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                value.put(BiometricsContract.NomineeEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                Uri uri = getContentResolver().insert(BiometricsContract.NomineeEntry.CONTENT_URI, value);
                populateNominees();
                List<String> genderList = checkIterations();
                int genderSize = genderList.size();
                String subsequentTitle = "";
                if (genderSize == 0) {
                    subsequentTitle = "";
                } else if (genderSize == 1) {
                    subsequentTitle = "Second Household Nominee";
                } else if (genderSize == 2) {
                    subsequentTitle = "Third Household Nominee";
                } else if (genderSize == 3) {
                    subsequentTitle = "Fourth Household Nominee";
                } else if (genderSize == 4) {
                    subsequentTitle = "Fifth Household Nominee";
                } else {
                    subsequentTitle = "Select Another Household Nominee";
                }
                String finalSubsequentTitle = subsequentTitle;
                if (genderSize < checkLiterateMembers()) {
                    if (genderList.contains("Male") && genderList.contains("Female")) {
                        GlobalDialogs.success(NomineeActivity.this, "Livelihood Component",
                                "Do you want to nominate another household member?",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        showNomineeAlertDialog(finalSubsequentTitle, "");
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                });
                    } else {
                        GlobalDialogs.success(NomineeActivity.this, "Livelihood Component",
                                "Would you like to nominate another household member who is between 18 and 35 years old and can read for this new component?",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        showNomineeAlertDialog(finalSubsequentTitle, "");
                                    }
                                }, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        GlobalDialogs.success(NomineeActivity.this, "Livelihood Component",
                                                "The project’s objective is to achieve a 50-50 percent distribution between male and female youth in the program. Consequently, there might be situations where nominating household members from both genders could increase the probability of selection into the program. Would you like to nominate a " + oppositeSex + " for the program?",
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        showNomineeAlertDialog(finalSubsequentTitle, "");
                                                    }
                                                },
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        dialog.cancel();
                                                    }
                                                });
                                    }
                                });
                    }
                } else {
                    GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                            "Maximum number of household members between ages of 18-35 years old has been achieved! Kindly proceed with the registration process.",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                }
            } else {
                GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                        "Kindly make sure all the fields are properly added!",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        });

        builder.setNeutralButton("CANCEL", (dialog, which) -> {
            dialog.cancel();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        Button ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        ok.setTextColor(Color.BLUE);
        Button neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        neutral.setTextColor(Color.MAGENTA);


        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        WindowManager.LayoutParams layoutParams;
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());

        // setting width to 90% of display
        layoutParams.width = (int) (displayMetrics.widthPixels * 0.9f);

        // setting height to 90% of display
        layoutParams.height = (int) (displayMetrics.heightPixels * 0.9f);
        dialog.getWindow().setAttributes(layoutParams);
    }


    private void initialiseAdapters() {

        String[] ineligibilityReasons = getResources().getStringArray(R.array.ineligibility_reasons);
        adapterIneligibility = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, ineligibilityReasons);
        actvIneligibility.setThreshold(1);
        actvIneligibility.setAdapter(adapterIneligibility);
        actvIneligibility.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String other = adapterView.getItemAtPosition(i).toString().toString();
                if (other.contains("Other (Specify)")) {
                    tietIneligibilityOtherReason.setVisibility(View.VISIBLE);
                    tilIneligibilityOtherReason.setVisibility(View.VISIBLE);
                } else {
                    tietIneligibilityOtherReason.setVisibility(View.INVISIBLE);
                    tilIneligibilityOtherReason.setVisibility(View.INVISIBLE);
                }
            }
        });


        String[] decisioning = getResources().getStringArray(R.array.decisioning);
        adapterEligibility = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, decisioning);
        actvEligibility.setThreshold(1);
        actvEligibility.setAdapter(adapterEligibility);
        actvEligibility.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i > 0) {
                    actvIneligibility.setVisibility(View.VISIBLE);
                    tilIneligibility.setVisibility(View.VISIBLE);
                } else {
                    actvIneligibility.setVisibility(View.INVISIBLE);
                    tilIneligibility.setVisibility(View.INVISIBLE);

                    List<String> genderList = checkIterations();
                    int genderSize = genderList.size();
                    String subsequentTitle = "";
                    if (genderSize == 0) {
                        subsequentTitle = "First Household Nominee";
                    } else if (genderSize == 1) {
                        subsequentTitle = "Second Household Nominee";
                    } else if (genderSize == 2) {
                        subsequentTitle = "Third Household Nominee";
                    } else if (genderSize == 3) {
                        subsequentTitle = "Fourth Household Nominee";
                    } else if (genderSize == 4) {
                        subsequentTitle = "Fifth Household Nominee";
                    } else {
                        subsequentTitle = "Select Another Household Nominee";
                    }

                    if (genderSize < checkLiterateMembers()) {
                        if (genderList.contains("Male") && genderList.contains("Female")) {
                            String finalSubsequentTitle = subsequentTitle;
                            GlobalDialogs.success(NomineeActivity.this, "Livelihood Component",
                                    "Do you want to nominate another household member?",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            showNomineeAlertDialog(finalSubsequentTitle, "");
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.cancel();
                                        }
                                    });
                        } else {
                            showNomineeAlertDialog(subsequentTitle, "Among the household members, including yourself, whom would you like to nominate for participation in this new component? Nominees must be between the ages of 18 to 35 and able to read and write");
                        }
                    } else {
                        GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                                "Maximum number of household members between ages of 18-35 years old has been achieved! Kindly proceed with the registration process.",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                });
                    }
                }

            }

        });
    }

    private void populateNominees() {
        nomineeList.clear();
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String[] whereHouseholdArgs = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.NomineeEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            assert cursor != null;
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    do {

                        String nomName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NAME));
                        String nomAge = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_AGE));
                        String nomGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_GENDER));
                        String nomRelationship = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_RELATIONSHIP));
                        String nomOccupation = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_OCCUPATION));
                        String nomOtherOccupation = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_OTHER_OCCUPATION));
                        Nominee nominee = new Nominee(nomName, nomAge, nomGender, nomRelationship, nomOccupation, nomOtherOccupation);
                        nomineeList.add(nominee);
                    } while (cursor.moveToNext());
                }
                nomineeAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private int checkLiterateMembers() {
        int literate = 0;
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String[] whereHouseholdArgs = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    String youthLiteracy = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_YOUTH_LITERACY));

                    literate = !youthLiteracy.isEmpty() ? Integer.parseInt(youthLiteracy) : 0;

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return literate;
    }

    private List<String> checkIterations() {
        List<String> genderList = new ArrayList<String>();
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String[] whereHouseholdArgs = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.NomineeEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            assert cursor != null;
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    do {
                        String nomGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_GENDER));
                        genderList.add(nomGender);
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return genderList;
    }


    private void populateEligibility() {
        Cursor cursor = null;
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String[] whereHouseholdArgs = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            assert cursor != null;
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {


                    String eligibility = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_ELIGIBILITY));
                    String ineligibility = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INELIGIBILITY));
                    String ineligibilityOtherReason = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_INELIGIBILITY_OTHER_REASON));

                    actvEligibility.setText(eligibility, false);
                    actvIneligibility.setText(ineligibility, false);
                    tietIneligibilityOtherReason.setText(ineligibilityOtherReason);

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
        boolean check = globalApplication.isAutoCompleteTextViewSelected(actvEligibility, "Decide Livelihood Component");
        return check;
    }

    private void update() {
        if (validateAutoCompleteTextView()) {
            //Update

            ContentValues value = new ContentValues();
            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ELIGIBILITY, actvEligibility.getText().toString().trim());
            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INELIGIBILITY, actvIneligibility.getText().toString().trim());
            value.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INELIGIBILITY_OTHER_REASON, tietIneligibilityOtherReason.getText().toString().trim());

            String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
            String[] whereUpdateHouseholdArgs = {globalApplication.getHouseholdNumber()};

            int i = getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, value, whereUpdateHousehold, whereUpdateHouseholdArgs);
            if (i != 0) {
                GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                        globalApplication.getHouseholdName() + " Nominee has been updated successfully. Move to the NEXT page",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (actvEligibility.getText().toString().trim().equalsIgnoreCase("No")) {
                                    startActivity(new Intent(NomineeActivity.this, PhotoActivity.class));
                                } else {
                                    startActivity(new Intent(NomineeActivity.this, NomineeReasonActivity.class));
                                }
                            }
                        });
            } else {
                GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                        globalApplication.getHouseholdName() + " Nominee update failed",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }

        } else {
            GlobalDialogs.error(NomineeActivity.this, "Livelihood Component",
                    globalApplication.getHouseholdName() + " Kindly check select all fields",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }
    }
}