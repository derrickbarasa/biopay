package com.flexmoney.nca.sync;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.flexmoney.nca.AccountGeneral;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.SessionManager;
import com.flexmoney.nca.objects.VolleyMultipartRequest;
import com.flexmoney.nca.objects.VolleySingleton;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@SuppressLint("Range")
public class BiometricsSyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "sync-sync";
    public static final String SYNC_COMPLETE_INTENT = "com.flexmoney.nca.intent.action.SYNC_COMPLETE";
    ContentResolver mContentResolver;
    GlobalApplication globalApplication;
    SessionManager sessionManager;
    private Context mContext;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public BiometricsSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();
        globalApplication = GlobalApplication.getInstance();
        sessionManager = new SessionManager(context);

        mContext = context;
        Log.d(TAG, "sync initialized");
    }

    public BiometricsSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs, ContentResolver mContentResolver) {
        super(context, autoInitialize, allowParallelSyncs);
        this.mContentResolver = mContentResolver;

    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        //do it as the last option
        sendPayments();
        sendAttendance();
        sendHouseholds();
        sendAlternates();
        sendFingerprints();

        System.out.println(sessionManager.getUsername());
        if (sessionManager.getUsername() != null) {
            reSyncData(sessionManager.getUsername());
        }
        //do it as the last option
        sendImages();

    }

    public static void performSync(Context context) {

        Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(AccountGeneral.getAccount(context),
                BiometricsContract.CONTENT_AUTHORITY, b);
    }


    public void sendHouseholds() {
        boolean rowHouseholds = true;
        while (rowHouseholds == true) {
            final ArrayList<HashMap<String, String>> houseHoldsArray;
            houseHoldsArray = new ArrayList<HashMap<String, String>>();
            Cursor cursor = null;
            String where = BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0"};
            try {
                cursor = mContentResolver.query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.HouseholdEntry._ID + " ASC " + " LIMIT 1");
                int getHouseholdRows = cursor.getCount();
                System.out.println("rows to upload--" + getHouseholdRows);
                if (getHouseholdRows != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            HashMap<String, String> map = new HashMap<String, String>();


                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PARTNER_CODE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_PARTNER_CODE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GROUP_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_GROUP_NUMBER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MEMBER_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_MEMBER_NUMBER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_ID_NUMBER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_PHONE_NUMBER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_AGE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_GENDER)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_MARITAL_STATUS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_SPOUSE_NAME)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_LEGAL_STATUS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_SELECTION_REASON)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATE_CODE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATE_CODE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_COUNTY_CODE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PAYAM_CODE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_PAYAM_CODE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_BOMA_CODE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_BOMA_CODE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LATITUDE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_LATITUDE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_LONGITUDE, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_LONGITUDE)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_CREATED_AT)));
                            map.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT)));

                            System.out.println("Household No: " + cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)) +
                                    "||||| Status: " + cursor.getString(cursor.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS)));
                            houseHoldsArray.add(map);

                            Gson gson = new GsonBuilder().create();
                            //Use GSON to serialize Array List to JSON
                            final String houseHold = gson.toJson(houseHoldsArray);
                            System.out.println("Household size: " + houseHold.getBytes().length);
                            System.out.println("url: " + "http://173.249.55.90:15000/nca/api/v1/households");

                            StringRequest str = new StringRequest(
                                    Request.Method.POST,
                                    "http://173.249.55.90:15000/nca/api/v1/households",
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {

                                            try {
                                                JSONObject res = new JSONObject(response);
                                                System.out.println("res" + res);
                                                boolean error = res.getBoolean("error");
                                                if (error == false) {
                                                    ContentValues valueUpdateHousehold = new ContentValues();

                                                    valueUpdateHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_STATUS, "1");

                                                    String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                                                    String whereUpdateHouseholdArgs[] = {houseHoldsArray.get(0).get(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)};
                                                    mContentResolver.update(BiometricsContract.HouseholdEntry.CONTENT_URI, valueUpdateHousehold, whereUpdateHousehold, whereUpdateHouseholdArgs);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();

                                            }

                                        }
                                    }, new Response.ErrorListener() {
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
                                    volleyError.printStackTrace();
                                }
                            }) {
                                protected Map<String, String> getParams() {
                                    Map<String, String> params = new HashMap<String, String>();
                                    params.put("data", houseHold);
                                    return params;
                                }
                            };
                            str.setRetryPolicy(new DefaultRetryPolicy(
                                    100000,
                                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

                            VolleySingleton.getInstance(this.getContext()).addToRequestQueue(str);
                            try {
                                Thread.sleep(1000);
                                System.out.println("1 seconds sleep enforced");
                            } catch (Exception e) {
                                System.out.println("exception 1 seconds sleep enforced");
                            }


                        } while (cursor.moveToNext());
                    }
                } else {
                    rowHouseholds = false;
                    Intent i = new Intent();
                    i.setType("households");
                    mContext.sendBroadcast(i);
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

    private void sendAlternates() {
        boolean rowAlternates = true;
        while (rowAlternates == true) {
            final ArrayList<HashMap<String, String>> alternatesArray;
            alternatesArray = new ArrayList<HashMap<String, String>>();

            Cursor cursor = null;
            String where = BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0"};
            try {
                cursor = mContentResolver.query(BiometricsContract.AlternateEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.AlternateEntry._ID + " ASC " + " LIMIT 1");
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        //do {

                        HashMap<String, String> map = new HashMap<String, String>();

                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_SUPERVISOR_ID)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_ID_NUMBER)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_RELATIONSHIP)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_PHONE_NUMBER)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_AGE)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_GENDER)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_CREATED_AT)));
                        map.put(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT)));

                        alternatesArray.add(map);

                        Gson gson = new GsonBuilder().create();
                        //Use GSON to serialize Array List to JSON
                        final String alternates = gson.toJson(alternatesArray);

                        System.out.println("url: " + "http://173.249.55.90:15000/nca/api/v1/alternates");
                        StringRequest altStr = new StringRequest(Request.Method.POST, "http://173.249.55.90:15000/nca/api/v1/alternates",
                                new Response.Listener<String>() {
                                    @Override
                                    public void onResponse(String response) {

                                        try {
                                            JSONObject res = new JSONObject(response);
                                            System.out.println("res" + res);
                                            boolean error = res.getBoolean("error");
                                            if (error == false) {
                                                ContentValues valueUpdateAlternate = new ContentValues();

                                                valueUpdateAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_STATUS, "1");

                                                String whereUpdateAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                                                String whereUpdateAlternateArgs[] = {alternatesArray.get(0).get(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER)};
                                                mContentResolver.update(BiometricsContract.AlternateEntry.CONTENT_URI, valueUpdateAlternate, whereUpdateAlternate, whereUpdateAlternateArgs);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();

                                        }

                                    }
                                }, new Response.ErrorListener() {
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
                                volleyError.printStackTrace();

                            }
                        }) {
                            protected Map<String, String> getParams() {
                                Map<String, String> params = new HashMap<String, String>();
                                params.put("data", alternates);
                                return params;
                            }

                        };
                        altStr.setRetryPolicy(new DefaultRetryPolicy(
                                100000,
                                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                        VolleySingleton.getInstance(this.getContext()).addToRequestQueue(altStr);
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                            System.out.println("exception 1 second sleep enforced");
                        }
                    }
                } else {
                    rowAlternates = false;
                    Intent i = new Intent();
                    i.setType("alternates");
                    mContext.sendBroadcast(i);
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

    private void sendAttendance() {
        boolean rowAttendance = true;
        while (rowAttendance == true) {
            final ArrayList<HashMap<String, String>> attendanceArray;
            attendanceArray = new ArrayList<HashMap<String, String>>();
            Cursor cursor = null;
            String where = BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0"};
            try {
                cursor = mContentResolver.query(BiometricsContract.AttendanceEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.AttendanceEntry._ID + " ASC " + " LIMIT 1");
                int getAttendanceRows = cursor.getCount();
                System.out.println("rows to upload--" + getAttendanceRows);
                if (getAttendanceRows != 0) {

                    if (cursor.moveToFirst()) {
                        do {
                            HashMap<String, String> map = new HashMap<String, String>();

                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_SUPERVISOR_ID)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_BENEFICIARY_TYPE, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_BENEFICIARY_TYPE)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_BENEFICIARY_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_BENEFICIARY_ID)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_CLOCK, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_CLOCK)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_TIME, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_TIME)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_ATTENDANCE_DATE, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_ATTENDANCE_DATE)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_UUID, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_UUID)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_CREATED_AT)));
                            map.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.AttendanceEntry.COLUMN_NAME_UPDATED_AT)));

                            attendanceArray.add(map);
                        } while (cursor.moveToNext());
                    }
                } else {
                    rowAttendance = false;
                    Intent i = new Intent();
                    i.setType("attendance");
                    mContext.sendBroadcast(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (attendanceArray.size() != 0) {
                Gson gson = new GsonBuilder().create();
                //Use GSON to serialize Array List to JSON
                final String attendanceFinal = gson.toJson(attendanceArray);

                System.out.println("Attendance size: " + attendanceFinal.getBytes().length);
                System.out.println("http://173.249.55.90:15000/nca/api/v1/attendance");
                StringRequest strAtt = new StringRequest(Request.Method.POST, "http://173.249.55.90:15000/nca/api/v1/attendance",
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {

                                try {

                                    JSONObject res = new JSONObject(response);
                                    boolean error = res.getBoolean("error");
                                    if (error == false) {
                                        //update local database
                                        for (int uploaded = 0; uploaded < attendanceArray.size(); uploaded++) {
                                            ContentValues valueUpdateAttendance = new ContentValues();

                                            valueUpdateAttendance.put(BiometricsContract.AttendanceEntry.COLUMN_NAME_STATUS, "1");

                                            String whereUpdateAttendance = BiometricsContract.AttendanceEntry.COLUMN_NAME_UUID + "=?";
                                            String whereUpdateAttendanceArgs[] = {attendanceArray.get(uploaded).get(BiometricsContract.AttendanceEntry.COLUMN_NAME_UUID)};
                                            mContentResolver.update(BiometricsContract.AttendanceEntry.CONTENT_URI, valueUpdateAttendance, whereUpdateAttendance, whereUpdateAttendanceArgs);
                                        }

                                    } else {
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();

                                }

                            }
                        }, new Response.ErrorListener() {
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
                        volleyError.printStackTrace();


                    }
                }) {
                    protected Map<String, String> getParams() {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("data", attendanceFinal);
                        return params;
                    }
                };
                strAtt.setRetryPolicy(new DefaultRetryPolicy(
                        100000,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

                VolleySingleton.getInstance(this.getContext()).addToRequestQueue(strAtt);
                try {
                    Thread.sleep(3000);
                } catch (Exception e) {
                    System.out.println("3 seconds sleep enforced");
                }
            }
        }
    }

    private void sendPayments() {
        boolean rowPayments = true;
        while (rowPayments == true) {
            final ArrayList<HashMap<String, String>> paymentArray;
            paymentArray = new ArrayList<HashMap<String, String>>();
            Cursor cursor = null;
            String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC + "=? AND " +
                    BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0", "1"};
            try {
                cursor = mContentResolver.query(BiometricsContract.PaymentsEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.PaymentsEntry._ID + " ASC " + " LIMIT 1");
                int getPaymentsRows = cursor.getCount();
                System.out.println("rows to upload--" + getPaymentsRows);
                if (getPaymentsRows != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            HashMap<String, String> map = new HashMap<String, String>();

                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_ENROLMENT_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_ENROLMENT_NUMBER)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAID_TO, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAID_TO)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_AMOUNT, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_AMOUNT)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_ATTENDANCE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_ATTENDANCE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_EXCHANGE_RATE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_EXCHANGE_RATE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_MATCHED_FP, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_MATCHED_FP)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LATITUDE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_LATITUDE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LONGITUDE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_LONGITUDE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_CREATED_AT)));
                            map.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_UPDATED_AT)));

                            paymentArray.add(map);
                        } while (cursor.moveToNext());
                    }
                } else {
                    rowPayments = false;
                    Intent i = new Intent();
                    i.setType("payments");
                    mContext.sendBroadcast(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            if (paymentArray.size() != 0) {
                Gson gson = new GsonBuilder().create();
                //Use GSON to serialize Array List to JSON
                final String payments = gson.toJson(paymentArray);
                System.out.println("payments size: " + payments.getBytes().length);

                StringRequest str = new StringRequest(Request.Method.POST, "http://173.249.55.90:15000/nca/api/v1/paid",
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {

                                try {
                                    System.out.println(response);
                                    JSONObject res = new JSONObject(response);
                                    boolean error = res.getBoolean("error");
                                    if (error == false) {
                                        //update local database
                                        for (int paid = 0; paid < paymentArray.size(); paid++) {
                                            ContentValues valueUpdatePayment = new ContentValues();

                                            valueUpdatePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC, "1");

                                            String whereUpdatePayment = BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID + "=?";
                                            String whereUpdatePaymentArgs[] = {paymentArray.get(paid).get(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID)};
                                            mContentResolver.update(BiometricsContract.PaymentsEntry.CONTENT_URI, valueUpdatePayment, whereUpdatePayment, whereUpdatePaymentArgs);
                                        }

                                    } else {
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();

                                }

                            }
                        }, new Response.ErrorListener() {
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
                        volleyError.printStackTrace();

                    }
                }) {
                    protected Map<String, String> getParams() {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("data", payments);
                        return params;
                    }
                };
                str.setRetryPolicy(new DefaultRetryPolicy(
                        100000,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

                VolleySingleton.getInstance(this.getContext()).addToRequestQueue(str);
                try {
                    Thread.sleep(3000);
                } catch (Exception e) {
                    System.out.println("3 seconds sleep enforced");
                }
            }
        }
    }

    public void sendFingerprints() {
        boolean rowFingerprints = true;
        while (rowFingerprints == true) {
            final ArrayList<HashMap<String, String>> fingerprintsArray;
            fingerprintsArray = new ArrayList<HashMap<String, String>>();
            Cursor cursor = null;
            String where = BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0"};
            try {
                cursor = mContentResolver.query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.FingerprintEntry._ID + " ASC " + " LIMIT 1");
                int getFingerprintRows = cursor.getCount();
                System.out.println("rows to upload--" + getFingerprintRows);
                if (getFingerprintRows != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            HashMap<String, String> map = new HashMap<String, String>();


                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT)));
                            map.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT)));

                            System.out.println("Ben No: " + cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID)) +
                                    "||||| Status: " + cursor.getString(cursor.getColumnIndex(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS)));
                            fingerprintsArray.add(map);

                            Gson gson = new GsonBuilder().create();
                            //Use GSON to serialize Array List to JSON
                            final String fingerprints = gson.toJson(fingerprintsArray);
                            System.out.println("Fingerprints size: " + fingerprints.getBytes().length);

                            System.out.println("url: " + "http://173.249.55.90:15000/nca/api/v1/fingerprints");
                            StringRequest str = new StringRequest(
                                    Request.Method.POST,
                                    "http://173.249.55.90:15000/nca/api/v1/fingerprints",
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {

                                            try {
                                                JSONObject res = new JSONObject(response);
                                                System.out.println("res" + res);
                                                boolean error = res.getBoolean("error");
                                                if (error == false) {
                                                    ContentValues valueUpdateFingerprints = new ContentValues();

                                                    valueUpdateFingerprints.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, "1");

                                                    String whereUpdateFingerprints = BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID + "=?";
                                                    String whereUpdateFingerprintsArgs[] = {fingerprintsArray.get(0).get(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID)};
                                                    mContentResolver.update(BiometricsContract.FingerprintEntry.CONTENT_URI, valueUpdateFingerprints, whereUpdateFingerprints, whereUpdateFingerprintsArgs);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();

                                            }

                                        }
                                    }, new Response.ErrorListener() {
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
                                    volleyError.printStackTrace();
                                }
                            }) {
                                protected Map<String, String> getParams() {
                                    Map<String, String> params = new HashMap<String, String>();
                                    params.put("data", fingerprints);
                                    return params;
                                }

                            };
                            str.setRetryPolicy(new DefaultRetryPolicy(
                                    100000,
                                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                            //Volley.newRequestQueue(this.getContext()).add(volleyMultipartRequest);
                            VolleySingleton.getInstance(this.getContext()).addToRequestQueue(str);
                            try {
                                Thread.sleep(5000);
                                System.out.println("5 seconds sleep enforced");
                            } catch (Exception e) {
                                System.out.println("exception 5 second sleep enforced");
                            }


                        } while (cursor.moveToNext());
                    }
                } else {
                    rowFingerprints = false;
                    Intent i = new Intent();
                    i.setType("fingerprints");
                    mContext.sendBroadcast(i);
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

    private void sendImages() {
        boolean rowImages = true;
        while (rowImages == true) {
            final ArrayList<HashMap<String, String>> imagesArray;
            imagesArray = new ArrayList<HashMap<String, String>>();

            Cursor cursor = null;
            String where = BiometricsContract.ImageEntry.COLUMN_NAME_STATUS + "=?";
            String[] whereArgs = {"0"};
            try {
                cursor = mContentResolver.query(BiometricsContract.ImageEntry.CONTENT_URI, null, where, whereArgs, BiometricsContract.ImageEntry._ID + " ASC " + " LIMIT 1");
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        //do {

                        HashMap<String, String> map = new HashMap<String, String>();

                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_SUPERVISOR_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_SUPERVISOR_ID)));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID)));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE)));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID)) + "-photo.png");
                        final String photoURL = cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_PHOTO_URL));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS)));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_CREATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_CREATED_AT)));
                        map.put(BiometricsContract.ImageEntry.COLUMN_NAME_UPDATED_AT, cursor.getString(cursor.getColumnIndex(BiometricsContract.ImageEntry.COLUMN_NAME_UPDATED_AT)));

                        imagesArray.add(map);

                        Gson gson = new GsonBuilder().create();
                        //Use GSON to serialize Array List to JSON
                        final String images = gson.toJson(imagesArray);
                        System.out.println("passport size: " + images.getBytes().length);
                        System.out.println("url: " + "http://173.249.55.90:15000/nca/api/v1/images");
                        VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, "http://173.249.55.90:15000/nca/api/v1/images",
                                new Response.Listener<NetworkResponse>() {
                                    @Override
                                    public void onResponse(NetworkResponse response) {

                                        try {
                                            JSONObject res = new JSONObject(new String(response.data));
                                            System.out.println("res" + res);
                                            boolean error = res.getBoolean("error");
                                            if (error == false) {
                                                System.out.println("BenId: " + imagesArray.get(0).get(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID));
                                                ContentValues valueUpdateImages = new ContentValues();
                                                valueUpdateImages.put(BiometricsContract.ImageEntry.COLUMN_NAME_STATUS, "1");

                                                String whereUpdateImages = BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
                                                String whereUpdateImagesArgs[] = {imagesArray.get(0).get(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID)};
                                                mContentResolver.update(BiometricsContract.ImageEntry.CONTENT_URI, valueUpdateImages, whereUpdateImages, whereUpdateImagesArgs);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();

                                        }

                                    }
                                }, new Response.ErrorListener() {
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
                                volleyError.printStackTrace();

                            }
                        }) {
                            protected Map<String, String> getParams() {
                                Map<String, String> params = new HashMap<String, String>();
                                params.put("data", images);
                                return params;
                            }

                            @Override
                            protected Map<String, DataPart> getByteData() {
                                Map<String, DataPart> params = new HashMap<>();
                                byte[] bytePhoto = Base64.decode(photoURL, Base64.DEFAULT);
                                params.put("photo", new DataPart(imagesArray.get(0).get(BiometricsContract.ImageEntry.COLUMN_NAME_BENEFICIARY_ID) + "-photo.png",
                                        bytePhoto));

                                return params;
                            }
                        };
                        volleyMultipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                                100000,
                                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                        VolleySingleton.getInstance(this.getContext()).addToRequestQueue(volleyMultipartRequest);
                        try {
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            System.out.println("exception 5 seconds sleep enforced");
                        }
                    }
                } else {
                    rowImages = false;
                    Intent i = new Intent();
                    i.setType("images");
                    mContext.sendBroadcast(i);
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

    public void reSyncData(String username) {
        JsonObjectRequest jor = new JsonObjectRequest(Request.Method.GET, "http://173.249.55.90:15000/nca/api/v1/supervisor/" + username, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {

                            if (response.getBoolean("error")) {
                                System.out.println("user not found");
                            } else {
                                JSONArray supervisors = response.getJSONArray("supervisors");
                                JSONArray households = response.getJSONArray("households");
                                JSONArray alternates = response.getJSONArray("alternates");
                                JSONArray regions = response.getJSONArray("regions");
                                JSONArray states = response.getJSONArray("states");
                                JSONArray counties = response.getJSONArray("counties");
                                JSONArray payams = response.getJSONArray("payams");
                                JSONArray bomas = response.getJSONArray("bomas");
                                JSONArray payments = response.getJSONArray("payments");
                                JSONArray fingerprints = response.getJSONArray("fingerprints");

                                System.out.println("------start downloads--");
                                if (bomas.length() != 0) {
                                    for (int j = 0; j < bomas.length(); j++) {
                                        try {
                                            ContentValues valueBomas = new ContentValues();

                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_STATE_CODE, bomas.getJSONObject(j).get("state_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_COUNTY_CODE, bomas.getJSONObject(j).get("county_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_PAYAM_CODE, bomas.getJSONObject(j).get("payam_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_CODE, bomas.getJSONObject(j).get("boma_code").toString().trim());
                                            valueBomas.put(BiometricsContract.BomasEntry.COLUMN_NAME_BOMA_NAME, bomas.getJSONObject(j).get("boma_name").toString().trim());

                                            mContentResolver.insert(BiometricsContract.BomasEntry.CONTENT_URI, valueBomas);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------end states start--" + bomas.length());
                                if (states.length() != 0) {

                                    for (int g = 0; g < states.length(); g++) {
                                        try {

                                            ContentValues valueStates = new ContentValues();

                                            valueStates.put(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_CODE, states.getJSONObject(g).get("state_code").toString().trim());
                                            valueStates.put(BiometricsContract.StatesEntry.COLUMN_NAME_STATE_NAME, states.getJSONObject(g).get("state_name").toString().trim());

                                            mContentResolver.insert(BiometricsContract.StatesEntry.CONTENT_URI, valueStates);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------end counties start--" + states.length());
                                if (counties.length() != 0) {

                                    for (int h = 0; h < counties.length(); h++) {
                                        try {

                                            ContentValues valueCounties = new ContentValues();

                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_STATE_CODE, counties.getJSONObject(h).get("state_code").toString().trim());
                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_CODE, counties.getJSONObject(h).get("county_code").toString().trim());
                                            valueCounties.put(BiometricsContract.CountiesEntry.COLUMN_NAME_COUNTY_NAME, counties.getJSONObject(h).get("county_name").toString().trim());

                                            mContentResolver.insert(BiometricsContract.CountiesEntry.CONTENT_URI, valueCounties);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------end payams start--" + counties.length());
                                if (payams.length() != 0) {

                                    for (int i = 0; i < payams.length(); i++) {
                                        try {

                                            ContentValues valuePayams = new ContentValues();

                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_STATE_CODE, payams.getJSONObject(i).get("state_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_COUNTY_CODE, payams.getJSONObject(i).get("county_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_CODE, payams.getJSONObject(i).get("payam_code").toString().trim());
                                            valuePayams.put(BiometricsContract.PayamsEntry.COLUMN_NAME_PAYAM_NAME, payams.getJSONObject(i).get("payam_name").toString().trim());

                                            mContentResolver.insert(BiometricsContract.PayamsEntry.CONTENT_URI, valuePayams);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------end supervisor start--" + payams.length());
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
                                            cursorSupervisor = mContentResolver.query(BiometricsContract.SupervisorsEntry.CONTENT_URI, null, selection, selectionArgs, null);
                                            if (cursorSupervisor.getCount() != 0) {
                                                if (cursorSupervisor.moveToFirst()) {
                                                    String serverTime = supervisors.getJSONObject(a).get("updated_at").toString().trim();
                                                    String localTime = cursorSupervisor.getString(cursorSupervisor.getColumnIndex(BiometricsContract.SupervisorsEntry.COLUMN_NAME_UPDATED_AT));
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
                                                                mContentResolver.update(BiometricsContract.SupervisorsEntry.CONTENT_URI, valueUpdateSupervisor, whereUpdateSupervisor, whereUpdateSupervisorArgs);
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

                                                mContentResolver.insert(BiometricsContract.SupervisorsEntry.CONTENT_URI, value);

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
                                System.out.println("------end households start--" + supervisors.length());
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
                                            cursorHouseholds = mContentResolver.query(BiometricsContract.HouseholdEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorHouseholds.getCount() != 0) {
                                                if (cursorHouseholds.moveToFirst()) {

                                                    String serverTime = households.getJSONObject(b).get("updated_at").toString().trim();
                                                    String localTime = cursorHouseholds.
                                                            getString(cursorHouseholds.getColumnIndex(BiometricsContract.HouseholdEntry.COLUMN_NAME_UPDATED_AT));

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
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO, households.getJSONObject(b).get("zero_two").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE, households.getJSONObject(b).get("three_five").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN, households.getJSONObject(b).get("six_seventeen").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE, households.getJSONObject(b).get("eighteen_thirty_five").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR, households.getJSONObject(b).get("thirty_six_sixty_four").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS, households.getJSONObject(b).get("sixty_five_plus").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE, households.getJSONObject(b).get("income_source").toString().trim());
                                                                valueHouseholdUpdate.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE, households.getJSONObject(b).get("other_income_source").toString().trim());
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
                                                                mContentResolver.update(BiometricsContract.HouseholdEntry.CONTENT_URI, valueHouseholdUpdate, whereUpdateHousehold, whereUpdateHouseholdArgs);
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
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO, households.getJSONObject(b).get("zero_two").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE, households.getJSONObject(b).get("three_five").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN, households.getJSONObject(b).get("six_seventeen").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE, households.getJSONObject(b).get("eighteen_thirty_five").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR, households.getJSONObject(b).get("thirty_six_sixty_four").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS, households.getJSONObject(b).get("sixty_five_plus").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_INCOME_SOURCE, households.getJSONObject(b).get("income_source").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE, households.getJSONObject(b).get("other_income_source").toString().trim());
                                                valueHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME, households.getJSONObject(b).get("average_household").toString().trim());
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

                                                mContentResolver.insert(BiometricsContract.HouseholdEntry.CONTENT_URI, valueHousehold);
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
                                System.out.println("------end alternates start--" + households.length());
                                if (alternates.length() != 0) {
                                    for (int c = 0; c < alternates.length(); c++) {
                                        Cursor cursorAlternates = null;
                                        try {
                                            String selection = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                                            String[] selectionArgs = {alternates.getJSONObject(c).get("alternate_number").toString().trim()};
                                            cursorAlternates = mContentResolver.query(BiometricsContract.AlternateEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorAlternates.getCount() != 0) {
                                                if (cursorAlternates.moveToFirst()) {

                                                    String serverTime = alternates.getJSONObject(c).get("updated_at").toString().trim();
                                                    String localTime = cursorAlternates.
                                                            getString(cursorAlternates.getColumnIndex(BiometricsContract.AlternateEntry.COLUMN_NAME_UPDATED_AT));
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
                                                                mContentResolver.update(BiometricsContract.AlternateEntry.CONTENT_URI, valueAlternateUpdate, whereUpdateAlternate, whereUpdateAlternateArgs);
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

                                                mContentResolver.insert(BiometricsContract.AlternateEntry.CONTENT_URI, valueAlternate);
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
                                System.out.println("------end payments start--" + alternates.length());
                                if (payments.length() != 0) {

                                    for (int f = 0; f < payments.length(); f++) {
                                        try {
                                            System.out.println("Payment started!!!" + payments.getJSONObject(f).get("household_number").toString().trim());
                                            ContentValues valuePayments = new ContentValues();

                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID, payments.getJSONObject(f).get("supervisor_id").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, payments.getJSONObject(f).get("household_number").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_ENROLMENT_NUMBER, payments.getJSONObject(f).get("enrolment_number").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE, payments.getJSONObject(f).get("beneficiary_type").toString().trim());

                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAID_TO, payments.getJSONObject(f).get("paid_to").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_EXCHANGE_RATE, payments.getJSONObject(f).get("exchange_rate").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_ATTENDANCE, payments.getJSONObject(f).get("attendance").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS, payments.getJSONObject(f).get("pounds").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_AMOUNT, payments.getJSONObject(f).get("amount").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID, payments.getJSONObject(f).get("uuid").toString().trim());

                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE, payments.getJSONObject(f).get("payment_centre").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_MATCHED_FP, payments.getJSONObject(f).get("matched_fp").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LATITUDE, payments.getJSONObject(f).get("latitude").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LONGITUDE, payments.getJSONObject(f).get("longitude").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC, payments.getJSONObject(f).get("sync").toString().trim());

                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS, payments.getJSONObject(f).get("status").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_CREATED_AT, payments.getJSONObject(f).get("created_at").toString().trim());
                                            valuePayments.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UPDATED_AT, payments.getJSONObject(f).get("updated_at").toString().trim());

                                            mContentResolver.insert(BiometricsContract.PaymentsEntry.CONTENT_URI, valuePayments);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {

                                        }
                                    }
                                }
                                System.out.println("------end regions start--" + payments.length());
                                if (regions.length() != 0) {
                                    for (int l = 0; l < regions.length(); l++) {

                                        Cursor cursorRegions = null;
                                        try {
                                            String selection = BiometricsContract.RegionsEntry.COLUMN_NAME_SUPERVISOR_ID + "=? AND " +
                                                    BiometricsContract.RegionsEntry.COLUMN_NAME_BOMA + "=?";
                                            String[] selectionArgs = {regions.getJSONObject(l).get("supervisor_id").toString().trim(),
                                                    regions.getJSONObject(l).get("boma").toString().trim()};
                                            cursorRegions = mContentResolver.query(BiometricsContract.RegionsEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorRegions.getCount() != 0) {
                                                if (cursorRegions.moveToFirst()) {

                                                    String serverTime = regions.getJSONObject(l).get("updated_at").toString().trim();
                                                    String localTime = cursorRegions.
                                                            getString(cursorRegions.getColumnIndex(BiometricsContract.RegionsEntry.COLUMN_NAME_UPDATED_AT));
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
                                                                mContentResolver.update(BiometricsContract.RegionsEntry.CONTENT_URI, valueRegionsUpdate, whereUpdateRegions, whereUpdateRegionsArgs);
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

                                                mContentResolver.insert(BiometricsContract.RegionsEntry.CONTENT_URI, valueRegions);
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
                                System.out.println("------end regions fingerprints start--" + fingerprints.length());
                                if (fingerprints.length() != 0) {
                                    for (int m = 0; m < fingerprints.length(); m++) {

                                        Cursor cursorFingerprints = null;
                                        try {
                                            String selection =
                                                    BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID + "=? ";
                                            String[] selectionArgs = {fingerprints.getJSONObject(m).get("uuid").toString().trim()};
                                            System.out.println("fps: " + fingerprints.getJSONObject(m).get("beneficiary_id").toString().trim());
                                            cursorFingerprints = mContentResolver.query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, selection, selectionArgs, null);

                                            if (cursorFingerprints.getCount() != 0) {
                                                continue;
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

                                                mContentResolver.insert(BiometricsContract.FingerprintEntry.CONTENT_URI, valueFingerprints);
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
                                System.out.println("---------------------end fingerprints-----------------" + fingerprints.length());

                                Log.d(TAG, "--------------------------sync complete--------------------------");

                                Intent i = new Intent();
                                i.setAction(SYNC_COMPLETE_INTENT);
                                mContext.sendBroadcast(i);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
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
                        volleyError.printStackTrace();
                    }
                }
        );
        jor.setRetryPolicy(new DefaultRetryPolicy(
                600000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(this.getContext()).addToRequestQueue(jor);
    }
}
