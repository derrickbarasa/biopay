package com.alphabank.dca.alternates;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.GlobalDialogs;
import com.alphabank.dca.objects.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.UUID;

public class AlternatesActivity extends AppCompatActivity {
    TextInputEditText tietAlternateHousehold, tietAlternateName, tietAlternateAge, tietAlternateIdNumber,
            tietAlternatePhonenumber;
    Spinner spAlternateRelationship;
    ArrayAdapter adapterRelationship;
    RadioGroup rgAlternatesGender;
    RadioButton rbAlternateFemale, rbAlternateMale;
    Button btnAlternateNext, btnAlternateCapturePhoto;

    private static final int CAM_PHOTO = 1;
    ImageView ivAlternatePhoto;
    Bitmap bitmap;
    GlobalApplication globalApplication;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alternates);

        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();

        tietAlternateHousehold = findViewById(R.id.tietAlternateHousehold);
        tietAlternateHousehold.setText(globalApplication.getHouseholdName());
        tietAlternateHousehold.setEnabled(false);
        tietAlternateHousehold.setFocusable(false);

        tietAlternateName = findViewById(R.id.tietAlternateName);
        tietAlternateName.setFocusable(true);
        tietAlternateAge = findViewById(R.id.tietAlternateAge);
        tietAlternateIdNumber = findViewById(R.id.tietAlternateIdNumber);
        tietAlternatePhonenumber = findViewById(R.id.tietAlternatePhonenumber);

        spAlternateRelationship = findViewById(R.id.spAlternateRelationship);

        rgAlternatesGender = findViewById(R.id.rgAlternateGender);
        rbAlternateMale = findViewById(R.id.rbAlternateMale);
        rbAlternateFemale = findViewById(R.id.rbAlternateFemale);

        ivAlternatePhoto = findViewById(R.id.ivAlternatePhoto);

        btnAlternateCapturePhoto = findViewById(R.id.btnAlternateCapturePhoto);
        btnAlternateNext = findViewById(R.id.btnAlternateNext);
        setUpAdapters();
        if (checkAlternateNumber() > 2) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration",
                    "Limit Reached!! This household already has TWO(2) alternates. You cannot register more!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(AlternatesActivity.this, AlternatesHouseholdsActivity.class));
                        }
                    });
        } else {
            if (globalApplication.isNullOrEmpty(globalApplication.getAlternateNumber()) == false) {
                populateInfo();
                populateAlternatePhoto();
            }
            onClicks();
        }

    }

    private void setUpAdapters() {
        adapterRelationship = ArrayAdapter.createFromResource(this,
                R.array.relationship, android.R.layout.simple_spinner_item);
        adapterRelationship.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAlternateRelationship.setAdapter(adapterRelationship);
    }

    private void populateInfo() {
        Cursor cursor = null;
        String whereAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
        String whereAlternateArgs[] = {globalApplication.getAlternateNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, whereAlternate, whereAlternateArgs, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {

                    String altHouseNo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                    String altAge = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE));
                    String altPhone = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER));
                    String altIdNo = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER));
                    String altRelationship = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP));
                    String altGender = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER));
                    populateHousehold(altHouseNo);
                    tietAlternateName.setText(globalApplication.getAlternateName());
                    tietAlternateAge.setText(altAge);
                    tietAlternatePhonenumber.setText(altPhone);
                    tietAlternateIdNumber.setText(altIdNo);
                    if (altGender.trim().equals("Male")) {
                        rgAlternatesGender.check(R.id.rbAlternateMale);
                    } else {
                        rgAlternatesGender.check(R.id.rbAlternateFemale);
                    }
                    spAlternateRelationship.setSelection(adapterRelationship.getPosition(altRelationship));

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

    private void populateHousehold(String householdNumber) {
        Cursor cursor = null;
        String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME,
                BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER
        };
        String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {householdNumber};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        String hName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME));
                        String bioNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));

                        globalApplication.setHouseholdNumber(bioNumber);
                        globalApplication.setHouseholdName(hName);
                        tietAlternateHousehold.setText(hName);

                    }


                } else {
                    GlobalDialogs.error(AlternatesActivity.this, "Edit Alternates",
                            "Household data cannot be found", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });

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

    private void populateAlternatePhoto() {
        Cursor cursor = null;
        int count = 0;
        String whereAlternate = BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereAlternateArgs[] = {globalApplication.getAlternateNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, whereAlternate, whereAlternateArgs, null);
            if (cursor != null) {
                count = cursor.getCount();
                if (count != 0) {
                    if (cursor.moveToFirst()) {
                        String encodedImage = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL));
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivAlternatePhoto.setImageBitmap(bitmap);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(AlternatesActivity.this, AlternatesHouseholdsActivity.class));
    }

    private void onClicks() {
        btnAlternateCapturePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, CAM_PHOTO);
            }
        });
        btnAlternateNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validate();
            }
        });
    }

    public int checkAlternateNumber() {
        Cursor cursor = null;
        int count = 0;
        String whereAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereAlternateArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, whereAlternate, whereAlternateArgs, null);
            if (cursor != null) {
                count = cursor.getCount();
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

    private void validate() {
        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            //Insert New Data
            if (validateInputFields()) {
                if (globalApplication.isSpinnerSelected(spAlternateRelationship, "Select the Alternate Relationship")) {
                    if (globalApplication.isNullOrEmpty(globalApplication.getAlternateNumber())) {
                        store();
                    } else {
                        update();
                    }
                }
            }
        } else {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration", "Household not found please, select a household",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(AlternatesActivity.this, AlternatesHouseholdsActivity.class));
                        }
                    });
        }
    }

    private void store() {
        int rgId = rgAlternatesGender.getCheckedRadioButtonId();
        String gender = "";
        if (rgId == rbAlternateMale.getId()) {
            gender = "Male";
        } else {
            gender = "Female";
        }
        String altRelationship = spAlternateRelationship.getSelectedItem().toString().trim();
        if (altRelationship.contains("Daughter") && gender.equals("Male")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a daughter and male",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else if (altRelationship.contains("Sister") && gender.equals("Male")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a sister and male",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else if (altRelationship.contains("Son") && gender.equals("Female")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a son and female",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else {
            globalApplication.setAlternateNumber(UUID.randomUUID().toString());
            globalApplication.setAlternateName(tietAlternateName.getText().toString().trim());
            String imageByte = GlobalDialogs.getStringImage(bitmap);
            if (!globalApplication.isNullOrEmpty(imageByte)) {

                ContentValues value = new ContentValues();
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER, globalApplication.getAlternateNumber());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME, globalApplication.getAlternateName());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER, tietAlternateIdNumber.getText().toString().trim());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER, tietAlternatePhonenumber.getText().toString().trim());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE, tietAlternateAge.getText().toString().trim());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP, spAlternateRelationship.getSelectedItem().toString().trim());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER, gender);
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, "0");
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                value.put(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                getContentResolver().insert(BiometricsContract.AlternateEntry.CONTENT_URI, value);

                ContentValues valuePhoto = new ContentValues();
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE, "2");
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getAlternateNumber());
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL, imageByte);
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS, "0");
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                getContentResolver().insert(BiometricsContract.ImageEntry.CONTENT_URI, valuePhoto);
                GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration",
                        "Alternate details & passport photo saved successfully. Move to the NEXT page for fingerprint capture",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(AlternatesActivity.this, AlternateFingerprintsActivity.class));
                            }
                        });
            } else {
                GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration", "Photo not taken, please capture a photo",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        }
    }

    private void update() {
        Log.e("Alt No.", globalApplication.getAlternateNumber());

        int rgId = rgAlternatesGender.getCheckedRadioButtonId();
        String gender = "";
        if (rgId == rbAlternateMale.getId()) {
            gender = "Male";
        } else {
            gender = "Female";
        }
        String altRelationship = spAlternateRelationship.getSelectedItem().toString().trim();
        if (altRelationship.contains("Daughter") && gender.equals("Male")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a daughter and male",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else if (altRelationship.contains("Sister") && gender.equals("Male")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a sister and male",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else if (altRelationship.contains("Son") && gender.equals("Female")) {
            GlobalDialogs.error(AlternatesActivity.this, "Alternate Information",
                    "Alternate cannot be a son and female",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        } else {
            String imageByte = GlobalDialogs.getStringImage(bitmap);
            if (!globalApplication.isNullOrEmpty(imageByte)) {

                ContentValues valueUpdate = new ContentValues();
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER, globalApplication.getAlternateNumber());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME, globalApplication.getAlternateName());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER, tietAlternateIdNumber.getText().toString().trim());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER, tietAlternatePhonenumber.getText().toString().trim());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE, tietAlternateAge.getText().toString().trim());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP, spAlternateRelationship.getSelectedItem().toString().trim());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER, gender);
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, "0");
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                valueUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());


                String whereUpdateAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                String whereUpdateAlternateArgs[] = {globalApplication.getAlternateNumber()};

                getContentResolver().update(BiometricsContract.AlternateEntry.CONTENT_URI, valueUpdate, whereUpdateAlternate, whereUpdateAlternateArgs);


                ContentValues valuePhotoUpdate = new ContentValues();
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE, "2");
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getAlternateNumber());
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL, imageByte);
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS, "0");
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                valuePhotoUpdate.put(BiometricsContract.ImageEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());


                String whereUpdatePhotoAlternate = BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
                String whereUpdatePhotoAlternateArgs[] = {globalApplication.getAlternateNumber()};

                getContentResolver().update(BiometricsContract.ImageEntry.CONTENT_URI, valuePhotoUpdate, whereUpdatePhotoAlternate, whereUpdatePhotoAlternateArgs);

                GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration",
                        "Alternate details & passport photo updated successfully. Move to the NEXT page for fingerprint capture",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(AlternatesActivity.this, AlternateFingerprintsActivity.class));
                            }
                        });
            } else {
                GlobalDialogs.error(AlternatesActivity.this, "Alternate Registration", "Photo not taken, please capture a photo",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        }
    }


    private boolean validateInputFields() {
        boolean check = true;
        if (!globalApplication.hasText(tietAlternateName, "Enter Alternate Name"))
            check = false;
        if (!globalApplication.hasText(tietAlternateAge, "Enter Alternate Age")) check = false;
        if (!globalApplication.hasText(tietAlternateIdNumber, "Enter Alternate Id Number"))
            check = false;
        if (!globalApplication.hasText(tietAlternatePhonenumber, "Enter Alternate Phonenumber"))
            check = false;
        if (!globalApplication.hasRequiredLength(tietAlternateName, "Enter a minimum of three letters and words required!"))
            check = false;
        if (!globalApplication.ageCheck(tietAlternateAge, "Enter Age!"))
            check = false;
        return check;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAM_PHOTO && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");
            ivAlternatePhoto.setImageBitmap(bitmap);
        } else {
            GlobalDialogs.error(AlternatesActivity.this, "Photo Taking", "Alternate photo not taken",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }
    }
}