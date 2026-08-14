package com.alphabank.dca.login;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.home.MainActivity;
import com.alphabank.dca.objects.GlobalDialogs;
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
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {

    ProgressBar pBFpHouseholds, pBPayments, pBFpAlternates, pBImageAlternates;
    TextView tvFpHouseholds, tvPayments, tvFpAlternates, tvImageAlternates;
    int maxHouseholds, maxAlternates;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mHandler = new Handler();
        pBFpHouseholds = findViewById(R.id.pBFpHouseholds);
        pBFpAlternates = findViewById(R.id.pBFpAlternates);
        pBPayments = findViewById(R.id.pBPayments);
        //pBImageAlternates = findViewById(R.id.pBImageAlternates);

        tvFpHouseholds = findViewById(R.id.tvFpHouseholds);
        tvFpAlternates = findViewById(R.id.tvFpAlternates);
        tvPayments = findViewById(R.id.tvPayments);
        tvImageAlternates = findViewById(R.id.tvImageAlternates);

        maxHouseholds = countHouseholds("0");
        pBFpHouseholds.setMax(maxHouseholds);

        maxAlternates = countAlternates("0");
        pBFpAlternates.setMax(maxAlternates);

        //initiateComponents();
        getHousehold();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.splash, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_splash_background) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(SplashActivity.this);
            dialog.setTitle("Download fingerprints");
            dialog.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.images, null));
            dialog.setMessage("Do you want to download the fingerprints or check the report");

            dialog.setPositiveButton("Download Household Fingerprints", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            getHousehold();
                        }
                    }).start();
                }
            });
            dialog.setNeutralButton("Download Alternate Fingerprints", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            getAlternate();
                        }
                    }).start();
                }
            });
            dialog.setNegativeButton("Go to Home", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                }
            });
            AlertDialog alert = dialog.create();
            alert.setCanceledOnTouchOutside(false);
            alert.show();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("Range")
    private void getHousehold() {
        Cursor cursor = null;
        try {
            String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER};
            String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_PICKED + "=?";
            String whereHouseholdArgs[] = {"0"};
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, whereHousehold, whereHouseholdArgs, BiometricsContract.HouseholdEntry._ID + " DESC LIMIT 400");
            JSONArray bioNumbers = new JSONArray();
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String bioNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                            bioNumbers.put(bioNumber);
                        } while (cursor.moveToNext());

                       // storeFingerprint(1, bioNumbers.toString());
                    }
                } else {
                    getAlternate();
                }
            } else {
                getAlternate();
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
    private void getAlternate() {
        Cursor cursor = null;
        try {
            String[] selection = {BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER};
            String whereAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_PICKED + "=?";
            String whereAlternateArgs[] = {"0"};
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, selection, whereAlternate, whereAlternateArgs, BiometricsContract.AlternateEntry._ID + " DESC LIMIT 400");
            JSONArray bioNumbers = new JSONArray();
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String bioNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER));
                            bioNumbers.put(bioNumber);
                        } while (cursor.moveToNext());
                       // storeFingerprint(2, bioNumbers.toString());
                    }
                } else {
                    GlobalDialogs.success(SplashActivity.this, "Fingerprints Download", "All fingerprints download have been completed. Kindly Login",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                                }
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                }
            } else {
                GlobalDialogs.success(SplashActivity.this, "Fingerprints Download", "All fingerprints download have been completed. Kindly Login",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private int countHouseholds(String picked) {
        Cursor cursor = null;
        int count = 0;

        try {
            String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER};
            if (picked != "0") {
                String whereHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_PICKED + "=?";
                String whereHouseholdArgs[] = {picked};
                cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, whereHousehold, whereHouseholdArgs, null);
            } else {
                cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, null, null, null);
            }
            count = cursor.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
    }

    private int countAlternates(String picked) {
        Cursor cursor = null;
        int count = 0;
        try {

            String[] selection = {BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER};
            if (picked != "0") {
                String whereAlternateArgs[] = {picked};
                String whereAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_PICKED + "=?";
                cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, selection, whereAlternate, whereAlternateArgs, null);
            } else {
                cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, selection, null, null, null);
            }
            count = cursor.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
    }

    /*private void storeFingerprint(int benType, String benNumbers) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int countHouseholds = countHouseholds("1");
                int percentCountHouseholds = countHouseholds * 100 / maxHouseholds;
                tvFpHouseholds.setText("Process ongoing. Downloaded Household Fingerprints " + countHouseholds + "/" + maxHouseholds + " (" + percentCountHouseholds + "%)");
                pBFpHouseholds.setProgress(countHouseholds);

                int countAlternates = countAlternates("1");
                int percentCountAlternates = countAlternates * 100 / maxAlternates;
                tvFpAlternates.setText("Process ongoing. Downloaded Alternate Fingerprints " + countAlternates + "/" + maxAlternates + " (" + percentCountAlternates + "%)");
                pBFpAlternates.setProgress(countAlternates);

            }
        });


        System.out.println(benNumbers);
        System.out.println("http://15.236.31.134:50232/dca/api/v1/fetch_fps_batch");
        StringRequest str = new StringRequest(Request.Method.POST, "http://15.236.31.134:50232/dca/api/v1/fetch_fps_batch",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String res) {
                        try {
                            JSONObject response = new JSONObject(res);
                            if (!response.getBoolean("error")) {
                                JSONArray fingerprints = response.getJSONArray("data");
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
                                    JSONArray benIds = new JSONArray(benNumbers);
                                    for (int benId = 0; benId < benIds.length(); benId++) {
                                        if (benType == 1) {
                                            //update household
                                            ContentValues valueUpdateHousehold = new ContentValues();
                                            valueUpdateHousehold.put(BiometricsContract.HouseholdEntry.COLUMN_NAME_PICKED, "1");
                                            String whereUpdateHousehold = BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
                                            String whereUpdateHouseholdArgs[] = {benIds.getString(benId).trim()};
                                            getContentResolver().update(BiometricsContract.HouseholdEntry.CONTENT_URI, valueUpdateHousehold, whereUpdateHousehold, whereUpdateHouseholdArgs);

                                        } else {
                                            //update alternate
                                            ContentValues valueUpdateAlternate = new ContentValues();
                                            valueUpdateAlternate.put(BiometricsContract.AlternateEntry.COLUMN_NAME_PICKED, "1");
                                            String whereUpdateAlternate = BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + "=?";
                                            String whereUpdateAlternateArgs[] = {benIds.getString(benId).trim()};
                                            getContentResolver().update(BiometricsContract.AlternateEntry.CONTENT_URI, valueUpdateAlternate, whereUpdateAlternate, whereUpdateAlternateArgs);

                                        }
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                int countHouseholds = countHouseholds("1");
                                                pBFpHouseholds.setProgress(countHouseholds);

                                                int countAlternates = countAlternates("1");
                                                pBFpAlternates.setProgress(countAlternates);
                                            }
                                        });

                                    }
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            int countHouseholds = countHouseholds("1");
                                            int percentCountHouseholds = countHouseholds * 100 / maxHouseholds;
                                            tvFpHouseholds.setText("Household batch download completed. Press download again " + countHouseholds + "/" + maxHouseholds + " (" + percentCountHouseholds + "%)");
                                            pBFpHouseholds.setProgress(countHouseholds);

                                            int countAlternates = countAlternates("1");
                                            int percentCountAlternates = countAlternates * 100 / maxAlternates;
                                            tvFpAlternates.setText("Alternate batch download completed. Press download again " + countAlternates + "/" + maxAlternates + " (" + percentCountAlternates + "%)");
                                            pBFpAlternates.setProgress(countAlternates);
                                        }
                                    });
                                    getHousehold();
                                }

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
                params.put("data", benNumbers);
                return params;
            }
        };
        str.setRetryPolicy(new DefaultRetryPolicy(
                100000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(this).addToRequestQueue(str);
        try {
            Thread.sleep(8000);
        } catch (Exception e) {
            System.out.println("8 seconds sleep enforced");
        }

    }*/
}