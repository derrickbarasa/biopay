package com.biopay.agent.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.biopay.agent.BuildConfig;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Durable, device-local proof that a field officer recently authenticated online.
 *
 * <p>The password itself is never stored. A salted PBKDF2 verifier allows the same officer to
 * sign in while the device has no connection. That verifier is useful only for sixty days after
 * the server last confirmed both the account and its anchor subscription. A server-confirmed
 * archived subscription always wins and remains locked until a later online check sees renewal.
 */
public final class OfflineAccessManager {

    private static final String PREFS_NAME = "biopay_offline_access";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD_SALT = "password_salt";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_ANCHOR_ID = "anchor_id";
    private static final String KEY_PARTNER_CODE = "partner_code";
    private static final String KEY_VERIFICATION_METHOD = "verification_method";
    private static final String KEY_SUBSCRIPTION_STATUS = "subscription_status";
    private static final String KEY_VALIDATED_AT = "validated_at";
    private static final String KEY_OFFLINE_ACCESS_DAYS = "offline_access_days";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;

    public enum Decision {
        ALLOWED,
        LOCKED,
        ONLINE_REQUIRED,
        INVALID_CREDENTIALS
    }

    public static final class CachedProfile {
        public final int userId;
        public final String email;
        public final String firstName;
        public final String lastName;
        public final Integer anchorId;
        public final String partnerCode;
        public final String verificationMethod;

        private CachedProfile(int userId, String email, String firstName, String lastName,
                Integer anchorId, String partnerCode, String verificationMethod) {
            this.userId = userId;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.anchorId = anchorId;
            this.partnerCode = partnerCode;
            this.verificationMethod = verificationMethod;
        }
    }

    // PBKDF2 at PBKDF2_ITERATIONS is deliberately slow (that's the point, as a local password
    // verifier) but that means it can run for a noticeable stretch on the underpowered CPUs of
    // the embedded terminals this app targets. Neither call site (post-login, post-password-change)
    // needs the result before moving on -- it's a durable cache for a *future* offline login -- so
    // it's dispatched here off the caller's thread rather than blocking it (the main thread, at
    // both call sites) until it completes.
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();

    private final SharedPreferences prefs;

    public OfflineAccessManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Saves/rotates the local password verifier after the server accepts the credentials.
     * Runs the PBKDF2 derivation and the preference write on a background thread; returns
     * immediately since no caller needs to wait on the result before proceeding. */
    public void cacheAuthenticatedIdentity(String password, int userId, String email,
            String firstName, String lastName, Integer anchorId, String partnerCode,
            String verificationMethod, int offlineAccessDays) {
        BACKGROUND.execute(() -> cacheAuthenticatedIdentityBlocking(password, userId, email,
                firstName, lastName, anchorId, partnerCode, verificationMethod, offlineAccessDays));
    }

    private boolean cacheAuthenticatedIdentityBlocking(String password, int userId, String email,
            String firstName, String lastName, Integer anchorId, String partnerCode,
            String verificationMethod, int offlineAccessDays) {
        try {
            String normalizedEmail = normalizeEmail(email);
            String previousEmail = prefs.getString(KEY_EMAIL, null);
            int previousAnchor = prefs.getInt(KEY_ANCHOR_ID, -1);
            int nextAnchor = anchorId == null ? -1 : anchorId;
            boolean identityChanged = previousEmail != null
                    && (!previousEmail.equals(normalizedEmail) || previousAnchor != nextAnchor);
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_EMAIL, normalizedEmail)
                    .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .putInt(KEY_USER_ID, userId)
                    .putString(KEY_FIRST_NAME, firstName)
                    .putString(KEY_LAST_NAME, lastName)
                    .putInt(KEY_ANCHOR_ID, nextAnchor)
                    .putString(KEY_PARTNER_CODE, partnerCode)
                    .putString(KEY_VERIFICATION_METHOD, verificationMethod)
                    .putInt(KEY_OFFLINE_ACCESS_DAYS, offlineAccessDays > 0
                            ? offlineAccessDays : BuildConfig.BIOPAY_OFFLINE_ACCESS_DAYS);
            if (identityChanged) {
                editor.remove(KEY_SUBSCRIPTION_STATUS).remove(KEY_VALIDATED_AT);
            }
            return editor.commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Records only a completed server subscription check, never a transport failure. */
    public void recordSubscriptionCheck(String status) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SUBSCRIPTION_STATUS, status == null ? "NONE" : status);
        if (!"ARCHIVED".equals(status)) {
            editor.putLong(KEY_VALIDATED_AT, System.currentTimeMillis());
        }
        editor.commit();
    }

    public Decision evaluateOfflineLogin(String email, String password) {
        if (!credentialsMatch(email, password)) {
            return Decision.INVALID_CREDENTIALS;
        }
        if (isKnownLocked()) {
            return Decision.LOCKED;
        }
        long validatedAt = prefs.getLong(KEY_VALIDATED_AT, -1L);
        return isLeaseValid(validatedAt, System.currentTimeMillis(),
                leaseMilliseconds(getOnlineRevalidationDays()))
                ? Decision.ALLOWED : Decision.ONLINE_REQUIRED;
    }

    public Decision evaluateCachedAuthorization() {
        if (isKnownLocked()) {
            return Decision.LOCKED;
        }
        long validatedAt = prefs.getLong(KEY_VALIDATED_AT, -1L);
        return isLeaseValid(validatedAt, System.currentTimeMillis(),
                leaseMilliseconds(getOnlineRevalidationDays()))
                ? Decision.ALLOWED : Decision.ONLINE_REQUIRED;
    }

    public CachedProfile getCachedProfile() {
        String email = prefs.getString(KEY_EMAIL, null);
        if (email == null) return null;
        int storedAnchor = prefs.getInt(KEY_ANCHOR_ID, -1);
        return new CachedProfile(
                prefs.getInt(KEY_USER_ID, -1), email,
                prefs.getString(KEY_FIRST_NAME, ""), prefs.getString(KEY_LAST_NAME, ""),
                storedAnchor == -1 ? null : storedAnchor,
                prefs.getString(KEY_PARTNER_CODE, null),
                prefs.getString(KEY_VERIFICATION_METHOD, "BIOMETRIC"));
    }

    private boolean isKnownLocked() {
        return "ARCHIVED".equals(prefs.getString(KEY_SUBSCRIPTION_STATUS, null));
    }

    private boolean credentialsMatch(String email, String password) {
        String storedEmail = prefs.getString(KEY_EMAIL, null);
        String saltValue = prefs.getString(KEY_PASSWORD_SALT, null);
        String hashValue = prefs.getString(KEY_PASSWORD_HASH, null);
        if (storedEmail == null || saltValue == null || hashValue == null
                || !storedEmail.equals(normalizeEmail(email))) {
            return false;
        }
        try {
            byte[] salt = Base64.decode(saltValue, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashValue, Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isLeaseValid(long validatedAt, long now, long leaseMs) {
        // A clock moved behind the server-validation time must not extend offline access.
        return validatedAt > 0L && now >= validatedAt && now - validatedAt <= leaseMs;
    }

    public int getOnlineRevalidationDays() {
        int stored = prefs.getInt(KEY_OFFLINE_ACCESS_DAYS, BuildConfig.BIOPAY_OFFLINE_ACCESS_DAYS);
        return stored > 0 ? stored : BuildConfig.BIOPAY_OFFLINE_ACCESS_DAYS;
    }

    static long leaseMilliseconds(int days) {
        return (long) days * 24L * 60L * 60L * 1000L;
    }

    private static byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        try {
            // HmacSHA1 is available across the app's full API 24+ range. PBKDF2's strength here
            // comes from a unique salt and high work factor; the output is only a local verifier.
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
