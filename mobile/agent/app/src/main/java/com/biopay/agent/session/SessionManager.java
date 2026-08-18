package com.biopay.agent.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the logged-in officer's session -- JWT access/refresh tokens and
 * the profile fields every screen needs for scoping (organisationCode,
 * anchorId). Replaces the old per-app SessionManager, which only tracked a
 * local userId/role against the on-device SQLite `supervisors` table (no
 * server-issued token existed before this app talked to a real backend).
 */
public class SessionManager {

    private static final String PREFS_NAME = "biopay_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_ANCHOR_ID = "anchor_id";
    private static final String KEY_PARTNER_CODE = "partner_code";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String refreshToken, int userId, String email,
            String firstName, String lastName, Integer anchorId, String partnerCode) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_FIRST_NAME, firstName);
        editor.putString(KEY_LAST_NAME, lastName);
        editor.putInt(KEY_ANCHOR_ID, anchorId == null ? -1 : anchorId);
        editor.putString(KEY_PARTNER_CODE, partnerCode);
        editor.apply();
    }

    /** Called after a successful token refresh -- only the token pair changes. */
    public void updateTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getFirstName() {
        return prefs.getString(KEY_FIRST_NAME, "");
    }

    public String getLastName() {
        return prefs.getString(KEY_LAST_NAME, "");
    }

    public String getFullName() {
        String name = (getFirstName() + " " + getLastName()).trim();
        return name.isEmpty() ? getEmail() : name;
    }

    public void updateProfile(String firstName, String lastName) {
        prefs.edit()
                .putString(KEY_FIRST_NAME, firstName)
                .putString(KEY_LAST_NAME, lastName)
                .apply();
    }

    public Integer getAnchorId() {
        int stored = prefs.getInt(KEY_ANCHOR_ID, -1);
        return stored == -1 ? null : stored;
    }

    public String getPartnerCode() {
        return prefs.getString(KEY_PARTNER_CODE, null);
    }
}
