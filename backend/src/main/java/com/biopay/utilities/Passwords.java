package com.biopay.utilities;

import at.favre.lib.crypto.bcrypt.BCrypt;

/** BCrypt password hashing (salt rounds 12, per the security requirement). */
public final class Passwords {

    private static final int COST = 12;

    private Passwords() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    public static boolean verify(String plainPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash).verified;
    }
}
