package com.biopay.utilities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fast, non-salted digest for opaque bearer tokens (refresh tokens, OTP
 * codes) that need an exact-match DB lookup -- unlike login passwords
 * (see {@link Passwords}), these are already high-entropy random values, so
 * BCrypt's slow, salted comparison buys nothing and would make every lookup
 * a full-table scan (can't index a per-row-salted hash).
 */
public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
