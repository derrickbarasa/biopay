package com.alphabank.dca.objects;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class GlobalApplication extends Application {
    private static final String TAG = "GlobalApplication";
    private static GlobalApplication singleton;

    public static GlobalApplication getInstance() {
        return singleton;
    }

    public String stateCode, stateName, countyCode, countyName, payamCode, payamName, bomaCode, bomaName;
    public String householdNumber, householdName, alternateNumber, alternateName, level;
    public String paymentCentre, latitude, longitude;
    public String img, transId;

    public void onCreate() {
        super.onCreate();
        singleton = this;
    }

    public boolean isNetworkAvailable(Context context) {
        boolean connected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(context.CONNECTIVITY_SERVICE);
            NetworkInfo nInfo = cm.getActiveNetworkInfo();
            connected = nInfo != null && nInfo.isAvailable() && nInfo.isConnected();
            return connected;
        } catch (Exception e) {
            Log.e("Connectivity Exception", e.getMessage());
        }
        return connected;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public String getCountyCode() {
        return countyCode;
    }

    public void setCountyCode(String countyCode) {
        this.countyCode = countyCode;
    }

    public String getCountyName() {
        return countyName;
    }

    public void setCountyName(String countyName) {
        this.countyName = countyName;
    }

    public String getPayamCode() {
        return payamCode;
    }

    public void setPayamCode(String payamCode) {
        this.payamCode = payamCode;
    }

    public String getPayamName() {
        return payamName;
    }

    public void setPayamName(String payamName) {
        this.payamName = payamName;
    }

    public String getBomaCode() {
        return bomaCode;
    }

    public void setBomaCode(String bomaCode) {
        this.bomaCode = bomaCode;
    }

    public String getBomaName() {
        return bomaName;
    }

    public void setBomaName(String bomaName) {
        this.bomaName = bomaName;
    }

    public String getHouseholdNumber() {
        return householdNumber;
    }

    public void setHouseholdNumber(String householdNumber) {
        this.householdNumber = householdNumber;
    }

    public String getHouseholdName() {
        return householdName;
    }

    public void setHouseholdName(String householdName) {
        this.householdName = householdName;
    }

    public String getAlternateNumber() {
        return alternateNumber;
    }

    public void setAlternateNumber(String alternateNumber) {
        this.alternateNumber = alternateNumber;
    }

    public String getAlternateName() {
        return alternateName;
    }

    public void setAlternateName(String alternateName) {
        this.alternateName = alternateName;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getPaymentCentre() {
        return paymentCentre;
    }

    public void setPaymentCentre(String paymentCentre) {
        this.paymentCentre = paymentCentre;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getTransId() {
        return transId;
    }

    public void setTransId(String transId) {
        this.transId = transId;
    }

    public boolean isNullOrEmpty(String str) {
        if (str != null && !str.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean hasText(TextInputEditText editText, String error_message) {
        String text = editText.getText().toString().trim();
        editText.setError(null);
        // length 0 means there is no text
        if (text.length() == 0) {
            editText.setError(error_message);
            return false;
        }
        return true;
    }

    public boolean ageCheck(TextInputEditText editText, String error_message) {
        int text = Integer.parseInt(editText.getText().toString().trim());
        editText.setError(null);
        // length 0 means there is no text
        if (text == 0) {
            editText.setError(error_message);
            return false;
        }
        return true;
    }

    public boolean checkPhonenumber(TextInputEditText editText, String error_message) {

        String phoneNo = editText.getText().toString().trim();
        editText.setError(null);

        if (phoneNo.length() != 10 ) {
            editText.setError(error_message);
            return false;
        }
        return true;
    }
    public boolean hasRequiredLength(TextInputEditText editText, String error_message) {
        String text = editText.getText().toString().trim();
        editText.setError(null);
        String splitText[] = text.split(" ");
        int wrds = splitText.length;
        editText.setError(null);
        if (wrds >= 3) {
            for (int i = 0; i < wrds; i++) {
                if (splitText[i].length() < 3) {
                    editText.setError("Enter a minimum of three letters per word");
                    return false;
                }
            }
        } else {
            editText.setError(error_message);
            return false;
        }
        return true;
    }

    public boolean isSpinnerSelected(Spinner spinner, String errorMessage) {
        int position = spinner.getSelectedItemPosition();
        ((TextView) spinner.getSelectedView()).setError(null);

        if (position == 0) {
            ((TextView) spinner.getSelectedView()).setError(errorMessage);
            return false;
        }
        return true;
    }

    public List sequenceNumbers(String title) {
        List sequences = new ArrayList<>();
        sequences.clear();
        sequences.add(title);
        for (int i = 0; i <= 100; i++) {
            sequences.add(Integer.toString(i));
        }
        return sequences;
    }

    public String todaysDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = Calendar.getInstance().getTime();
        String leo = sdf.format(date);
        return leo;
    }
}
