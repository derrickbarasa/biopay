package com.alphabank.dca.login;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.home.MainActivity;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.GlobalDialogs;
import com.alphabank.dca.objects.SessionManager;
import com.alphabank.dca.objects.VolleySingleton;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class LoginActivity extends AppCompatActivity {
    private boolean doubleBackToExitPressedOnce = false;
    private TextInputEditText tietPassword, tietUsername;
    private Button btnLogin;
    private SessionManager sessionManager;
    GlobalApplication globalApplication;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        globalApplication = GlobalApplication.getInstance();
        sessionManager = new SessionManager(getApplicationContext());

        tietUsername = findViewById(R.id.tietUsername);
        tietPassword = findViewById(R.id.tietPassword);
        btnLogin = findViewById(R.id.btnLogin);

        if (sessionManager.getUsername() != null) {
            tietUsername.setText(sessionManager.getUsername());
            tietUsername.setEnabled(false);
            tietUsername.setFocusable(false);
        }

        tietPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
    }

    @Override
    public void onBackPressed() {

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            System.exit(0);
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Tap again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    private void attemptLogin() {
        String username = tietUsername.getText().toString().trim();
        String password = tietPassword.getText().toString().trim();
        //check if users exists
        if (username.isEmpty()) {
            GlobalDialogs.error(LoginActivity.this, "Login", "Enter username",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        } else if (password.isEmpty()) {
            GlobalDialogs.error(LoginActivity.this, "Login", "Enter password",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        } else {

            checkUser(username, password);

        }
    }

    private int checkDownloadCompletion() {
        Cursor cursor = null;
        int count = 0;
        try {
            String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER};
            String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_PICKED + "=?";
            String whereHouseholdArgs[] = {"0"};

            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, whereHousehold, whereHouseholdArgs, null);
            count = cursor.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        System.out.println(count + " of households");
        if (count == 0) {
            count = checkAlternateDownloadCompletion();
        }
        return count;
    }

    private int checkAlternateDownloadCompletion() {
        Cursor cursor = null;
        int count = 0;
        try {
            String[] selection = {BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER};
            String whereAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_PICKED + "=?";
            String whereAlternateArgs[] = {"0"};

            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, selection, whereAlternate, whereAlternateArgs, null);
            count = cursor.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        System.out.println(count + " of alternates");
        return count;
    }

    @SuppressLint("Range")
    private void checkUser(String username, String password) {
        String where = BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME + "=?";
        String whereArgs[] = {username};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(BiometricsContract.SupervisorsEntry.CONTENT_URI, null, where, whereArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        String hashedPassword = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.SupervisorsEntry.COLUMN_NAME_PASSWORD));


                        MessageDigest md = MessageDigest.getInstance("MD5");
                        md.reset();
                        md.update(password.getBytes());
                        byte[] digest = md.digest();
                        BigInteger bigInt = new BigInteger(1, digest);
                        String digestPass = bigInt.toString(16);
                        while (digestPass.length() < 32) {
                            digestPass = "0" + digestPass;
                        }

                        if (digestPass.equals(hashedPassword.trim())) {
                            /*
                             * 1. Get User ID
                             * 2. Proceed with successful login
                             * */
                            sessionManager.setUserSession(cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.SupervisorsEntry.COLUMN_NAME_SUPERVISOR_ID)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.SupervisorsEntry.COLUMN_NAME_ROLE_ID)));

                            if (checkDownloadCompletion() != 0) {
                                finish();
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                intent.putExtra("username", username);
                                startActivity(intent);
                            } else {
                                GlobalDialogs.success(LoginActivity.this, "Login", "Login Successful",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                                finish();
                                            }
                                        },
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.cancel();
                                            }
                                        });
                            }
                        } else {
                            //Toast.makeText(LoginActivity.this, "Incorrect Password, please try again!", Toast.LENGTH_SHORT).show();
                            if (tietPassword.length() > 0) {
                                tietPassword.getText().clear();
                            }

                            GlobalDialogs.error(LoginActivity.this, "Login", "Incorrect Password, please try again!",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    });
                        }
                    } else {


                        if (globalApplication.isNetworkAvailable(LoginActivity.this)) {
                            getUnsyncedData(username);
                        } else {
                            GlobalDialogs.success(LoginActivity.this, "Login", "Internet connection failed. Kindly connect!",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    });
                        }

                    }
                } else {
                    if (globalApplication.isNetworkAvailable(LoginActivity.this)) {
                        getUnsyncedData(username);

                    } else {
                        GlobalDialogs.success(LoginActivity.this, "Login", "Internet connection failed. Kindly connect!",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                    }
                }
            } else {
                if (globalApplication.isNetworkAvailable(LoginActivity.this)) {
                    getUnsyncedData(username);

                } else {
                    GlobalDialogs.success(LoginActivity.this, "Login", "Internet connection failed. Kindly connect!",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    startActivityForResult(new Intent(android.provider.Settings.ACTION_SETTINGS), 0);
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
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

    @SuppressLint("Range")
    private void getUnsyncedData(String username) {

        final ProgressDialog pd = new ProgressDialog(LoginActivity.this);
        pd.setTitle("Login");
        pd.setMessage("downloading...");
        pd.setIcon(R.mipmap.ic_launcher);
        pd.show();

        JsonObjectRequest jor = new JsonObjectRequest(Request.Method.GET, "http://173.249.55.90:15000/dca/api/v1/supervisor/" + username, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.getBoolean("error")) {

                                GlobalDialogs.error(LoginActivity.this, "Login", response.getString("data"),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        });
                            } else {
                                JSONArray supervisors = response.getJSONArray("supervisors");
                                JSONArray households = response.getJSONArray("households");
                                JSONArray alternates = response.getJSONArray("alternates");
                                JSONArray regions = response.getJSONArray("regions");
                                JSONArray states = response.getJSONArray("states");
                                JSONArray counties = response.getJSONArray("counties");
                                JSONArray payams = response.getJSONArray("payams");
                                JSONArray bomas = response.getJSONArray("bomas");
                                JSONArray fingerprints = response.getJSONArray("fingerprints");

                                System.out.println("------start downloads-----");
                                System.out.println("------bomas size----" + bomas.length());
                                if (bomas.length() != 0) {
                                    for (int j = 0; j < bomas.length(); j++) {
                                        try {
                                            ContentValues valueBomas = new ContentValues();

                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_STATE_CODE, bomas.getJSONObject(j).get("state_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_COUNTY_CODE, bomas.getJSONObject(j).get("county_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_PAYAM_CODE, bomas.getJSONObject(j).get("payam_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_CODE, bomas.getJSONObject(j).get("boma_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_NAME, bomas.getJSONObject(j).get("boma_name").toString().trim());

                                            getContentResolver().insert(BiometricsContract.BomasEntry.CONTENT_URI, valueBomas);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------states start-----" + states.length());
                                if (states.length() != 0) {

                                    for (int g = 0; g < states.length(); g++) {
                                        try {

                                            ContentValues valueStates = new ContentValues();

                                            valueStates.put(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_CODE, states.getJSONObject(g).get("state_code").toString().trim());
                                            valueStates.put(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_NAME, states.getJSONObject(g).get("state_name").toString().trim());

                                            getContentResolver().insert(BiometricsContract.StatesEntry.CONTENT_URI, valueStates);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------counties start-----" + counties.length());
                                if (counties.length() != 0) {

                                    for (int h = 0; h < counties.length(); h++) {
                                        try {

                                            ContentValues valueCounties = new ContentValues();

                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_STATE_CODE, counties.getJSONObject(h).get("state_code").toString().trim());
                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_CODE, counties.getJSONObject(h).get("county_code").toString().trim());
                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_NAME, counties.getJSONObject(h).get("county_name").toString().trim());

                                            getContentResolver().insert(BiometricsContract.CountiesEntry.CONTENT_URI, valueCounties);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------payams start------" + payams.length());
                                if (payams.length() != 0) {

                                    for (int i = 0; i < payams.length(); i++) {
                                        try {

                                            ContentValues valuePayams = new ContentValues();

                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_STATE_CODE, payams.getJSONObject(i).get("state_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_COUNTY_CODE, payams.getJSONObject(i).get("county_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_CODE, payams.getJSONObject(i).get("payam_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_NAME, payams.getJSONObject(i).get("payam_name").toString().trim());

                                            getContentResolver().insert(BiometricsContract.PayamsEntry.CONTENT_URI, valuePayams);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------supervisor start------" + supervisors.length());
                                if (supervisors.length() != 0) {
                                    for (int a = 0; a < supervisors.length(); a++) {
                                        /**
                                         * A. ADDITION
                                         * B. EDITION
                                         * 1. Get the row by ID
                                         * 2. Check if the row exists if doesn't insert
                                         * 3. Check the timestamp updated_at if they are equal
                                         * 4. If not equal, update the row else skip the row
                                         */
                                        Cursor cursorSupervisor = null;
                                        try {
                                            String selection = BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME + "=?";
                                            String[] selectionArgs = {supervisors.getJSONObject(a).get("username").toString().trim()};
                                            cursorSupervisor = getContentResolver().query(BiometricsContract.SupervisorsEntry.CONTENT_URI, null, selection, selectionArgs, null);
                                            if (cursorSupervisor.getCount() != 0) {
                                                if (cursorSupervisor.moveToFirst()) {
                                                    String serverTime = supervisors.getJSONObject(a).get("updated_at").toString().trim();
                                                    String localTime = cursorSupervisor.getString(cursorSupervisor.getColumnIndexOrThrow(BiometricsContract.SupervisorsEntry.COLUMN_NAME_UPDATED_AT));
                                                    try {
                                                        Date dfServerTime = sdf.parse(serverTime);
                                                        Date dfLocalTime = sdf.parse(localTime);

                                                        if (dfLocalTime.after(dfServerTime)) {
                                                            continue;
                                                        } else {
                                                            if (dfLocalTime.equals(dfServerTime)) {
                                                                //nothing to change
                                                                continue;
                                                            } else {

                                                                ContentValues valueUpdateSupervisor = new ContentValues();

                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_SUPERVISOR_ID, supervisors.getJSONObject(a).get("supervisor_id").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME, supervisors.getJSONObject(a).get("username").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_PASSWORD, supervisors.getJSONObject(a).get("password").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_FIRSTNAME, supervisors.getJSONObject(a).get("firstname").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_OTHERNAMES, supervisors.getJSONObject(a).get("lastname").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_ROLE_ID, supervisors.getJSONObject(a).get("role").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_PARTNER_CODE, supervisors.getJSONObject(a).get("partner_code").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_ACTIVE, supervisors.getJSONObject(a).get("active").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_CREATED_AT, supervisors.getJSONObject(a).get("created_at").toString().trim());
                                                                valueUpdateSupervisor.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_UPDATED_AT, supervisors.getJSONObject(a).get("updated_at").toString().trim());

                                                                String whereUpdateSupervisor = BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME + "=?";
                                                                String whereUpdateSupervisorArgs[] = {supervisors.getJSONObject(a).get("username").toString().trim()};
                                                                getContentResolver().update(BiometricsContract.SupervisorsEntry.CONTENT_URI, valueUpdateSupervisor, whereUpdateSupervisor, whereUpdateSupervisorArgs);
                                                            }
                                                        }
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } else {
                                                //Insert
                                                ContentValues value = new ContentValues();
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_SUPERVISOR_ID, supervisors.getJSONObject(a).get("supervisor_id").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_USERNAME, supervisors.getJSONObject(a).get("username").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_PASSWORD, supervisors.getJSONObject(a).get("password").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_FIRSTNAME, supervisors.getJSONObject(a).get("firstname").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_OTHERNAMES, supervisors.getJSONObject(a).get("lastname").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_ROLE_ID, supervisors.getJSONObject(a).get("role").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_PARTNER_CODE, supervisors.getJSONObject(a).get("partner_code").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_ACTIVE, supervisors.getJSONObject(a).get("active").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_CREATED_AT, supervisors.getJSONObject(a).get("created_at").toString().trim());
                                                value.put(BiometricsContract.SupervisorsEntry.COLUMN_NAME_UPDATED_AT, supervisors.getJSONObject(a).get("updated_at").toString().trim());

                                                getContentResolver().insert(BiometricsContract.SupervisorsEntry.CONTENT_URI, value);

                                            }

                                        } catch (Exception e) {
                                            e.printStackTrace();

                                        } finally {
                                            if (cursorSupervisor != null) {
                                                cursorSupervisor.close();
                                            }
                                        }

                                    }

                                }
                                System.out.println("------households start------" + households.length());
                                if (households.length() != 0) {
                                    for (int b = 0; b < households.length(); b++) {
                                        /**
                                         * A. ADDITION
                                         * B. EDITION
                                         * 1. Get the row by ID
                                         * 2. Check if the row exists if doesn't insert
                                         * 3. Check the timestamp updated_at if they are equal
                                         * 4. If not equal, update the row else skip the row
                                         */
                                        Cursor cursorHouseholds = null;
                                        try {
                                            String selection = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                                            String[] selectionArgs = {households.getJSONObject(b).get("household_number").toString().trim()};
                                            cursorHouseholds = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorHouseholds.getCount() != 0) {
                                                if (cursorHouseholds.moveToFirst()) {

                                                    String serverTime = households.getJSONObject(b).get("updated_at").toString().trim();
                                                    String localTime = cursorHouseholds.
                                                            getString(cursorHouseholds.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT));

                                                    try {
                                                        Date dfServerTime = sdf.parse(serverTime);
                                                        Date dfLocalTime = sdf.parse(localTime);

                                                        if (dfLocalTime.after(dfServerTime)) {
                                                            continue;
                                                        } else {
                                                            if (dfLocalTime.equals(dfServerTime)) {
                                                                //nothing to change
                                                                continue;
                                                            } else {
                                                                ContentValues valueHouseholdUpdate = new ContentValues();

                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, households.getJSONObject(b).get("supervisor_id").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, households.getJSONObject(b).get("household_number").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, households.getJSONObject(b).get("household_name").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS, households.getJSONObject(b).get("marital_status").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, households.getJSONObject(b).get("spouse_name").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, households.getJSONObject(b).get("beneficiary_type").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GROUP_NUMBER, households.getJSONObject(b).get("group_number").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MEMBER_NUMBER, households.getJSONObject(b).get("member_number").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PARTNER_CODE, households.getJSONObject(b).get("partner_code").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, households.getJSONObject(b).get("age").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, households.getJSONObject(b).get("id_number").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, households.getJSONObject(b).get("phone_number").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE, households.getJSONObject(b).get("household_size").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, households.getJSONObject(b).get("gender").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS, households.getJSONObject(b).get("male_dependants").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS, households.getJSONObject(b).get("female_dependants").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_FIVE, households.getJSONObject(b).get("zero_five").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_EIGHTEEN, households.getJSONObject(b).get("six_eighteen").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_NINETEEN_FORTY_FIVE, households.getJSONObject(b).get("nineteen_forty_five").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_FORTY_SIX_SIXTY_FIVE, households.getJSONObject(b).get("forty_six_sixty_five").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_SIX, households.getJSONObject(b).get("sixty_six").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD, households.getJSONObject(b).get("income_household").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, households.getJSONObject(b).get("average_household").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATE_CODE, households.getJSONObject(b).get("state_code").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE, households.getJSONObject(b).get("county_code").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PAYAM_CODE, households.getJSONObject(b).get("payam_code").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BOMA_CODE, households.getJSONObject(b).get("boma_code").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LATITUDE, households.getJSONObject(b).get("latitude").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LONGITUDE, households.getJSONObject(b).get("longitude").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS, households.getJSONObject(b).get("legal_status").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, households.getJSONObject(b).get("status").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, households.getJSONObject(b).get("created_at").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, households.getJSONObject(b).get("updated_at").toString().trim());

                                                                String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                                                                String whereUpdateHouseholdArgs[] = {households.getJSONObject(b).get("household_number").toString().trim()};
                                                                getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, valueHouseholdUpdate, whereUpdateHousehold, whereUpdateHouseholdArgs);
                                                            }
                                                        }
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } else {
                                                ContentValues valueHousehold = new ContentValues();

                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, households.getJSONObject(b).get("supervisor_id").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, households.getJSONObject(b).get("household_number").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, households.getJSONObject(b).get("household_name").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS, households.getJSONObject(b).get("marital_status").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, households.getJSONObject(b).get("spouse_name").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, households.getJSONObject(b).get("beneficiary_type").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GROUP_NUMBER, households.getJSONObject(b).get("group_number").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MEMBER_NUMBER, households.getJSONObject(b).get("member_number").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PARTNER_CODE, households.getJSONObject(b).get("partner_code").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, households.getJSONObject(b).get("age").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, households.getJSONObject(b).get("id_number").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, households.getJSONObject(b).get("phone_number").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE, households.getJSONObject(b).get("household_size").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, households.getJSONObject(b).get("gender").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS, households.getJSONObject(b).get("male_dependants").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS, households.getJSONObject(b).get("female_dependants").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_FIVE, households.getJSONObject(b).get("zero_five").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_EIGHTEEN, households.getJSONObject(b).get("six_eighteen").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_NINETEEN_FORTY_FIVE, households.getJSONObject(b).get("nineteen_forty_five").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_FORTY_SIX_SIXTY_FIVE, households.getJSONObject(b).get("forty_six_sixty_five").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_SIX, households.getJSONObject(b).get("sixty_six").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_HOUSEHOLD, households.getJSONObject(b).get("income_household").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, households.getJSONObject(b).get("average_household").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA, households.getJSONObject(b).get("selection_criteria").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON, households.getJSONObject(b).get("selection_reason").toString().trim());

                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATE_CODE, households.getJSONObject(b).get("state_code").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE, households.getJSONObject(b).get("county_code").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PAYAM_CODE, households.getJSONObject(b).get("payam_code").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BOMA_CODE, households.getJSONObject(b).get("boma_code").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LATITUDE, households.getJSONObject(b).get("latitude").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LONGITUDE, households.getJSONObject(b).get("longitude").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS, households.getJSONObject(b).get("legal_status").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, households.getJSONObject(b).get("status").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, households.getJSONObject(b).get("created_at").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, households.getJSONObject(b).get("updated_at").toString().trim());

                                                getContentResolver().insert(BiometricsContract.HouseholdEntry.CONTENT_URI, valueHousehold);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (cursorHouseholds != null) {
                                                cursorHouseholds.close();
                                            }
                                        }
                                    }
                                }
                                System.out.println("------alternates start------" + alternates.length());
                                if (alternates.length() != 0) {
                                    for (int c = 0; c < alternates.length(); c++) {
                                        Cursor cursorAlternates = null;
                                        try {
                                            String selection = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                                            String[] selectionArgs = {alternates.getJSONObject(c).get("alternate_number").toString().trim()};
                                            cursorAlternates = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorAlternates.getCount() != 0) {
                                                if (cursorAlternates.moveToFirst()) {

                                                    String serverTime = alternates.getJSONObject(c).get("updated_at").toString().trim();
                                                    String localTime = cursorAlternates.
                                                            getString(cursorAlternates.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT));
                                                    try {
                                                        Date dfServerTime = sdf.parse(serverTime);
                                                        Date dfLocalTime = sdf.parse(localTime);

                                                        if (dfLocalTime.after(dfServerTime)) {
                                                            continue;
                                                        } else {
                                                            if (dfLocalTime.equals(dfServerTime)) {
                                                                //nothing to change
                                                                continue;
                                                            } else {
                                                                ContentValues valueAlternateUpdate = new ContentValues();

                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID, alternates.getJSONObject(c).get("supervisor_id").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, alternates.getJSONObject(c).get("household_number").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME, alternates.getJSONObject(c).get("alternate_name").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER, alternates.getJSONObject(c).get("alternate_number").toString().trim());

                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE, alternates.getJSONObject(c).get("age").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER, alternates.getJSONObject(c).get("id_number").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER, alternates.getJSONObject(c).get("phone_number").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER, alternates.getJSONObject(c).get("gender").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP, alternates.getJSONObject(c).get("relationship").toString().trim());

                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, alternates.getJSONObject(c).get("status").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT, alternates.getJSONObject(c).get("created_at").toString().trim());
                                                                valueAlternateUpdate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT, alternates.getJSONObject(c).get("updated_at").toString().trim());

                                                                String whereUpdateAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                                                                String whereUpdateAlternateArgs[] = {alternates.getJSONObject(c).get("alternate_number").toString().trim()};
                                                                getContentResolver().update(BiometricsContract.AlternateEntry.CONTENT_URI, valueAlternateUpdate, whereUpdateAlternate, whereUpdateAlternateArgs);
                                                            }
                                                        }
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } else {
                                                ContentValues valueAlternate = new ContentValues();

                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID, alternates.getJSONObject(c).get("supervisor_id").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, alternates.getJSONObject(c).get("household_number").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME, alternates.getJSONObject(c).get("alternate_name").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER, alternates.getJSONObject(c).get("alternate_number").toString().trim());

                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE, alternates.getJSONObject(c).get("age").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER, alternates.getJSONObject(c).get("id_number").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER, alternates.getJSONObject(c).get("phone_number").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER, alternates.getJSONObject(c).get("gender").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP, alternates.getJSONObject(c).get("relationship").toString().trim());

                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, alternates.getJSONObject(c).get("status").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT, alternates.getJSONObject(c).get("created_at").toString().trim());
                                                valueAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT, alternates.getJSONObject(c).get("updated_at").toString().trim());

                                                getContentResolver().insert(BiometricsContract.AlternateEntry.CONTENT_URI, valueAlternate);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (cursorAlternates != null) {
                                                cursorAlternates.close();
                                            }
                                        }
                                    }
                                }
                                System.out.println("------end alternates download-------");
                                System.out.println("------start regions-------" + regions.length());
                                if (regions.length() != 0) {
                                    for (int l = 0; l < regions.length(); l++) {

                                        Cursor cursorRegions = null;
                                        try {
                                            String selection = BiometricsContract.RegionsEntry.COLUMN_NAME_SUPERVISOR_ID + "=? AND " +
                                                    BiometricsContract.RegionsEntry.COLUMN_NAME_BOMA + "=?";
                                            String[] selectionArgs = {regions.getJSONObject(l).get("supervisor_id").toString().trim(),
                                                    regions.getJSONObject(l).get("boma").toString().trim()};
                                            cursorRegions = getContentResolver().query(BiometricsContract.RegionsEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorRegions.getCount() != 0) {
                                                if (cursorRegions.moveToFirst()) {

                                                    String serverTime = regions.getJSONObject(l).get("updated_at").toString().trim();
                                                    String localTime = cursorRegions.
                                                            getString(cursorRegions.getColumnIndexOrThrow(BiometricsContract.RegionsEntry.COLUMN_NAME_UPDATED_AT));
                                                    try {
                                                        Date dfServerTime = sdf.parse(serverTime);
                                                        Date dfLocalTime = sdf.parse(localTime);

                                                        if (dfLocalTime.after(dfServerTime)) {
                                                            continue;
                                                        } else {
                                                            if (dfLocalTime.equals(dfServerTime)) {
                                                                //nothing to change
                                                                continue;
                                                            } else {
                                                                ContentValues valueRegionsUpdate = new ContentValues();

                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_SUPERVISOR_ID, regions.getJSONObject(l).get("supervisor_id").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_STATE, regions.getJSONObject(l).get("state").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_COUNTY, regions.getJSONObject(l).get("county").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_PAYAM, regions.getJSONObject(l).get("payam").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_BOMA, regions.getJSONObject(l).get("boma").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_STATUS, regions.getJSONObject(l).get("status").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_CREATED_AT, regions.getJSONObject(l).get("created_at").toString().trim());
                                                                valueRegionsUpdate.put(BiometricsContract.RegionsEntry.COLUMN_NAME_UPDATED_AT, regions.getJSONObject(l).get("updated_at").toString().trim());

                                                                String whereUpdateRegions = BiometricsContract.RegionsEntry.COLUMN_NAME_SUPERVISOR_ID + "=? AND " +
                                                                        BiometricsContract.RegionsEntry.COLUMN_NAME_BOMA + "=?";
                                                                String whereUpdateRegionsArgs[] = {regions.getJSONObject(l).get("supervisor_id").toString().trim(),
                                                                        regions.getJSONObject(l).get("boma").toString().trim()};
                                                                getContentResolver().update(BiometricsContract.RegionsEntry.CONTENT_URI, valueRegionsUpdate, whereUpdateRegions, whereUpdateRegionsArgs);
                                                            }
                                                        }
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } else {
                                                ContentValues valueRegions = new ContentValues();

                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_SUPERVISOR_ID, regions.getJSONObject(l).get("supervisor_id").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_STATE, regions.getJSONObject(l).get("state").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_COUNTY, regions.getJSONObject(l).get("county").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_PAYAM, regions.getJSONObject(l).get("payam").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_BOMA, regions.getJSONObject(l).get("boma").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_STATUS, regions.getJSONObject(l).get("status").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_CREATED_AT, regions.getJSONObject(l).get("created_at").toString().trim());
                                                valueRegions.put(BiometricsContract.RegionsEntry.COLUMN_NAME_UPDATED_AT, regions.getJSONObject(l).get("updated_at").toString().trim());

                                                getContentResolver().insert(BiometricsContract.RegionsEntry.CONTENT_URI, valueRegions);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (cursorRegions != null) {
                                                cursorRegions.close();
                                            }
                                        }
                                    }
                                }
                                System.out.println("------end regions download-------");
                                System.out.println("------start fingerprints-------" + fingerprints.length());
                                if (fingerprints.length() != 0) {

                                    for (int m = 0; m < fingerprints.length(); m++) {
                                        Cursor cursorFingerprints = null;
                                        try {
                                            String selection = BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID + "=? ";
                                            String[] selectionArgs = {fingerprints.getJSONObject(m).get("uuid").toString().trim()};

                                            cursorFingerprints = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, selection, selectionArgs, null);
                                            if (cursorFingerprints.getCount() != 0) {
                                                //fingerprint exists
                                            } else {
                                                ContentValues valueFingerprints = new ContentValues();
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID, fingerprints.getJSONObject(m).get("uuid").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID, fingerprints.getJSONObject(m).get("supervisor_id").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE, fingerprints.getJSONObject(m).get("beneficiary_type").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID, fingerprints.getJSONObject(m).get("beneficiary_id").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER, fingerprints.getJSONObject(m).get("fingerprint_number").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT, fingerprints.getJSONObject(m).get("fingerprint").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, fingerprints.getJSONObject(m).get("status").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT, fingerprints.getJSONObject(m).get("created_at").toString().trim());
                                                valueFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT, fingerprints.getJSONObject(m).get("updated_at").toString().trim());

                                                getContentResolver().insert(BiometricsContract.FingerprintEntry.CONTENT_URI, valueFingerprints);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (cursorFingerprints != null) {
                                                cursorFingerprints.close();
                                            }
                                        }

                                    }
                                }
                                System.out.println("------end all downloads-------");

                                pd.dismiss();
                                //attemptLogin();

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            pd.dismiss();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        String message = null;
                        if (volleyError instanceof NetworkError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof ServerError) {
                            message = "The server could not be found. Please try again after some time!!";
                        } else if (volleyError instanceof AuthFailureError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof ParseError) {
                            message = "Parsing error! Please try again after some time!!";
                        } else if (volleyError instanceof NoConnectionError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof TimeoutError) {
                            message = "Connection TimeOut! Please check your internet connection.";
                        }
                        System.err.println(message);
                        pd.dismiss();

                    }
                }
        );
        jor.setRetryPolicy(new DefaultRetryPolicy(
                600000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(this).addToRequestQueue(jor);
    }

}