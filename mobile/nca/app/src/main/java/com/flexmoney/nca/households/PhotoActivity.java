package com.flexmoney.nca.households;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.GlobalDialogs;
import com.flexmoney.nca.objects.SessionManager;

import org.json.JSONObject;

public class PhotoActivity extends AppCompatActivity {
    ImageView ivHouseholdPhoto;
    Bitmap bitmap;
    Button btnPhotoNext, btnPhotoBack, btnCaptureHouseholdPhoto;
    private static final int CAM_PHOTO = 1;
    GlobalApplication globalApplication;
    SessionManager sessionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        globalApplication = GlobalApplication.getInstance();
        sessionManager = new SessionManager(getApplicationContext());

        ivHouseholdPhoto = findViewById(R.id.ivHouseholdPhoto);
        if (!globalApplication.isNullOrEmpty(globalApplication.getHouseholdNumber())) {
            populatePhoto();
        }
        btnCaptureHouseholdPhoto = findViewById(R.id.btnCaptureHouseholdPhoto);
        btnCaptureHouseholdPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, CAM_PHOTO);
            }
        });

        btnPhotoBack = findViewById(R.id.btnPhotoBack);
        btnPhotoBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                    startActivity(new Intent(PhotoActivity.this, EditHouseholdActivity.class));

            }
        });
        btnPhotoNext = findViewById(R.id.btnPhotoNext);
        btnPhotoNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int photoTaken = populatePhoto();
                if (photoTaken != 0) {
                    GlobalDialogs.error(PhotoActivity.this, "Photo Taking",
                            "Photo has already been taken. It is not editable, do you want to proceed?",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(PhotoActivity.this, FingerprintsActivity.class));
                                }
                            });
                } else {
                    store();
                }
            }
        });
    }



    private int populatePhoto() {
        Cursor cursor = null;
        int count = 0;
        String whereHousehold = BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                count = cursor.getCount();
                if (count != 0) {
                    if (cursor.moveToFirst()) {

                        String encodedImage = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL));
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivHouseholdPhoto.setImageBitmap(decodedByte);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(PhotoActivity.this, EditHouseholdActivity.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAM_PHOTO && resultCode == RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");
            ivHouseholdPhoto.setImageBitmap(bitmap);
        } else {
            GlobalDialogs.error(PhotoActivity.this, "Photo Taking", "Household photo not taken",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }
    }

    private void store() {
        String imageByte = "";
        imageByte = (bitmap != null) ? GlobalDialogs.getStringImage(bitmap) : "";

        if (!globalApplication.isNullOrEmpty(imageByte)) {

            ContentValues valuePhoto = new ContentValues();
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getHouseholdNumber());
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL, imageByte);
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS, "0");
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
            valuePhoto.put(BiometricsContract.ImageEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

            getContentResolver().insert(BiometricsContract.ImageEntry.CONTENT_URI, valuePhoto);
            GlobalDialogs.error(PhotoActivity.this, "Photo Taking",
                    globalApplication.getHouseholdName() + " photo has been collected successfully. Move to the NEXT page",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(PhotoActivity.this, FingerprintsActivity.class));
                        }
                    });
        } else {

            GlobalDialogs.error(PhotoActivity.this, "Photo Taking", "Photo image not found, please capture again",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
        }

    }
}