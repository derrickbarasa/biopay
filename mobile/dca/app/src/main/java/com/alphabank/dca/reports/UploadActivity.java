package com.alphabank.dca.reports;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.home.MainActivity;
import com.alphabank.dca.login.LoginActivity;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.SessionManager;

public class UploadActivity extends AppCompatActivity {
    Account mAccount;
    GlobalApplication globalApplication;
    SessionManager sessionManager;

    String userId = null, userName = null, roleId = null;
    public static final int SYNC_SECONDS = 60 * 60 * 1;
    TextView tvUploadHouseholdsCaptured, tvUploadedHousehold, tvUnuploadedHousehold,
            tvUploadAlternatesCaptured, tvUploadedAlternates, tvUnuploadedAlternates,
            tvUploadAttendanceCaptured, tvUploadedAttendance, tvUnuploadedAttendance,
            tvUploadDownloadedPayments, tvUploadPaymentsPaid, tvUploadPaymentsUnpaid,
            tvUploadPaymentsSynced, tvUploadPaymentsUnsynced, tvHouseholdHeader,
            tvUploadFingerprintsCaptured, tvUploadedFingerprints, tvUnuploadedFingerprints,
            tvUploadImagesCaptured, tvUploadedImages, tvUnuploadedImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        Toolbar toolbar = findViewById(R.id.toolbarUpload);
        setSupportActionBar(toolbar);
        globalApplication = GlobalApplication.getInstance();
        sessionManager = new SessionManager(getApplicationContext());

        tvHouseholdHeader = findViewById(R.id.tvHouseholdHeader);
        tvUploadHouseholdsCaptured = findViewById(R.id.tvUploadHouseholdsCaptured);
        tvUploadedHousehold = findViewById(R.id.tvUploadedHousehold);
        tvUnuploadedHousehold = findViewById(R.id.tvUnuploadedHousehold);
        tvUploadAlternatesCaptured = findViewById(R.id.tvUploadAlternatesCaptured);
        tvUploadedAlternates = findViewById(R.id.tvUploadedAlternates);
        tvUnuploadedAlternates = findViewById(R.id.tvUnuploadedAlternates);

        tvUploadDownloadedPayments = findViewById(R.id.tvUploadDownloadedPayments);
        tvUploadPaymentsPaid = findViewById(R.id.tvUploadPaymentsPaid);
        tvUploadPaymentsUnpaid = findViewById(R.id.tvUploadPaymentsUnpaid);
        tvUploadPaymentsSynced = findViewById(R.id.tvUploadPaymentsSynced);
        tvUploadPaymentsUnsynced = findViewById(R.id.tvUploadPaymentsUnsynced);

        tvUploadFingerprintsCaptured = findViewById(R.id.tvUploadFingerprintsCaptured);
        tvUploadedFingerprints = findViewById(R.id.tvUploadedFingerprints);
        tvUnuploadedFingerprints = findViewById(R.id.tvUnuploadedFingerprints);

        tvUploadImagesCaptured = findViewById(R.id.tvUploadImagesCaptured);
        tvUploadedImages = findViewById(R.id.tvUploadedImages);
        tvUnuploadedImages = findViewById(R.id.tvUnuploadedImages);


        mAccount = CreateSyncAccount(this);

        if (mAccount == null) {
            Log.d("AutoSync", "<<<<Failed to create sync account.");
        } else {
            Log.d("AutoSync", "<<<<Adding periodic sync");
            //automaticSync();
        }
        if (sessionManager.getUserId() != null) {
            userId = sessionManager.getUserId();
            if (sessionManager.getUsername() != null) {
                userName = sessionManager.getUsername();
                if (sessionManager.getRoleId() != null) {

                    roleId = sessionManager.getRoleId();

                } else {
                    Toast.makeText(this, "Session has expired, please login", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                }
            } else {
                //Go back to the ReportActivity
                Toast.makeText(this, "Session has expired, please login", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
            }
        } else {
            Toast.makeText(UploadActivity.this, "User does not exist, please login", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(UploadActivity.this, LoginActivity.class));
        }
        getHousholdBomas();
        getAllHouseholds();
        getUploadedHouseholds();
        getUnUploadedHouseholds();

        getAllAlternates();
        getUnUploadedAlternates();
        getUploadedAlternates();

        getAllAttendance();
        getUnUploadedAttendance();
        getUploadedAttendance();

        getAllPayments();
        getPaidPayments();
        getUnPaidPayments();
        getSyncedPayments();
        getUnSyncedPayments();

        getAllFingerprints();
        getUploadedFingerprints();
        getUnUploadedFingerprints();

        getAllImages();
        getUploadedImages();
        getUnUploadedImages();
    }


    @Override
    public void onBackPressed() {
        startActivity(new Intent(UploadActivity.this, MainActivity.class));
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.upload, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_upload) {
            Toast.makeText(UploadActivity.this, "Syncing in progress", Toast.LENGTH_SHORT).show();
            if (globalApplication.isNetworkAvailable(UploadActivity.this)) {
                manualSync();
            } else {
                //showNetwork();
                Toast.makeText(UploadActivity.this, "Connect to the internet", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.action_report) {
            startActivity(new Intent(UploadActivity.this, ReportActivity.class));
        } else if (id == R.id.action_reset) {
            Toast.makeText(UploadActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
        }

        return super.onOptionsItemSelected(item);
    }


    private void manualSync() {

        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(
                ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        /*
         * Request the sync for the default account, authority, and
         * manual sync settings
         */
        ContentResolver.requestSync(mAccount, getResources().getString(R.string.content_authority), settingsBundle);

    }

    public static Account CreateSyncAccount(Context context) {
        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        ACCOUNT_SERVICE);
        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call context.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */
            Log.d("AccountManager", "<<<<New account added. Setting syncable.");
            /*ContentResolver.setIsSyncable(newAccount,  "ke.co.payconnect.joywo", 1);
            ContentResolver.setSyncAutomatically(newAccount,  "ke.co.payconnect.joywo", true);*/

        } else {
            /*
             * The account exists or some other error occurred. Log this, report it,
             * or handle it internally.
             */
            newAccount = null;
            Log.d("Sync Account", "creating sync account error");
        }
        return newAccount;
    }

    public void automaticSync() {

        if (globalApplication.isNetworkAvailable(UploadActivity.this)) {
            Log.e("AutoSync", "AutoSync started");
            ContentResolver resolver = getContentResolver();
            resolver.setIsSyncable(mAccount, getResources().getString(R.string.content_authority), 1);
            resolver.setSyncAutomatically(mAccount, getResources().getString(R.string.content_authority), true);

            ContentResolver.addPeriodicSync(
                    mAccount,
                    getResources().getString(R.string.content_authority),
                    Bundle.EMPTY,
                    SYNC_SECONDS);
        } else {
            // showNetwork();
        }

    }
    @SuppressLint("Range")
    private void getHousholdBomas() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(BiometricsContract.BomasEntry.CONTENT_URI, null, null, null, null);
            String bomas = "";
            int count = 0;
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            if (count > 0) {
                                bomas += "|";
                            }
                            String bomaName = cursor.getString(cursor.getColumnIndex(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_NAME));
                            bomas = bomas + bomaName;
                            count++;
                        } while (cursor.moveToNext());
                    }
                }
            }
            tvHouseholdHeader.setText("Households. Bomas: " + bomas);
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getAllHouseholds() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, null, null, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadHouseholdsCaptured.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUploadedHouseholds() {
        Cursor cursor = null;
        String where = BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadedHousehold.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnUploadedHouseholds() {
        Cursor cursor = null;
        String where = BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUnuploadedHousehold.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getAllAlternates() {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, null, null, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadAlternatesCaptured.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUploadedAlternates() {
        Cursor cursor = null;
        String where = BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadedAlternates.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnUploadedAlternates() {
        Cursor cursor = null;
        String where = BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUnuploadedAlternates.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getAllAttendance() {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(BiometricsContract.AttendanceEntry.CONTENT_URI, null, null, null, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadAttendanceCaptured.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUploadedAttendance() {
        Cursor cursor = null;
        String where = BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.AttendanceEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadedAttendance.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnUploadedAttendance() {
        Cursor cursor = null;
        String where = BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.AttendanceEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUnuploadedAttendance.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getAllPayments() {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, null, null, null);
            int getPaymentRows = cursor.getCount();
            tvUploadDownloadedPayments.setText(getPaymentRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getPaidPayments() {
        Cursor cursor = null;
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, where, whereArgs, null);
            int getPaidPayments = cursor.getCount();
            tvUploadPaymentsPaid.setText(getPaidPayments + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnPaidPayments() {
        Cursor cursor = null;
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, where, whereArgs, null);
            int getUnpaidPayments = cursor.getCount();
            tvUploadPaymentsUnpaid.setText(getUnpaidPayments + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getSyncedPayments() {
        Cursor cursor = null;
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, where, whereArgs, null);
            int getSyncedRows = cursor.getCount();
            tvUploadPaymentsSynced.setText(getSyncedRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnSyncedPayments() {
        Cursor cursor = null;
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, where, whereArgs, null);
            int getUnsyncedRows = cursor.getCount();
            tvUploadPaymentsUnsynced.setText(getUnsyncedRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @SuppressLint("Range")
    private void getAllFingerprints() {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, null, null, null);
            int getFingerprintsRows = cursor.getCount();
            tvUploadFingerprintsCaptured.setText(getFingerprintsRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUploadedFingerprints() {
        Cursor cursor = null;
        String where = BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadedFingerprints.setText(getHouseholdRows + "");

        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnUploadedFingerprints() {
        Cursor cursor = null;
        String where = BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUnuploadedFingerprints.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getAllImages() {
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, null, null, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadImagesCaptured.setText(getHouseholdRows + "");

        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUploadedImages() {
        Cursor cursor = null;
        String where = BiometricsContract.ImageEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"1"};
        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUploadedImages.setText(getHouseholdRows + "");

        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    @SuppressLint("Range")
    private void getUnUploadedImages() {
        Cursor cursor = null;
        String where = BiometricsContract.ImageEntry.COLUMN_NAME_STATUS + "=?";
        String[] whereArgs = {"0"};
        try {
            cursor = getContentResolver().query(BiometricsContract.ImageEntry.CONTENT_URI, null, where, whereArgs, null);
            int getHouseholdRows = cursor.getCount();
            tvUnuploadedImages.setText(getHouseholdRows + "");
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}