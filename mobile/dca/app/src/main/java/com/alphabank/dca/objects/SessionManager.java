package com.alphabank.dca.objects;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context smContext;
    int PRIVATE_MODE = 0;

    // Sharedpref file name
    private static final String PREF_NAME = "SESSION_ID";
    // User name (make variable public to access from outside)
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_USER_NAME = "username";
    public static final String KEY_ROLE_ID = "roleId";

    public SessionManager(Context context) {
        this.smContext = context;
        pref = smContext.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }
    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }
    public String getRoleId() {
        return pref.getString(KEY_ROLE_ID, null);
    }

    public String getUsername() {
        return pref.getString(KEY_USER_NAME, null);
    }

    public void setUserSession(String userId, String username, String roleId) {
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, username);
        editor.putString(KEY_ROLE_ID, roleId);
        editor.commit();
    }


    public void deleteUserSession() {
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_NAME);
        editor.remove(KEY_ROLE_ID);
        editor.commit();
    }

}
