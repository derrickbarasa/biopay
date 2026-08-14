package com.biopay.utilities;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM at-rest encryption for fingerprint templates (spec: "Fingerprint
 * templates must be encrypted at rest"). Output is base64(iv || ciphertext);
 * the 12-byte IV is regenerated per call and doesn't need to be secret.
 */
public final class Crypto {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static volatile SecretKeySpec key;

    private Crypto() {
    }

    private static SecretKeySpec key() {
        if (key == null) {
            synchronized (Crypto.class) {
                if (key == null) {
                    Dotenv dotenv = Dotenv.load();
                    String base64Key = dotenv.get("FINGERPRINT_ENCRYPTION_KEY");
                    if (base64Key == null || base64Key.isBlank()) {
                        throw new IllegalStateException("FINGERPRINT_ENCRYPTION_KEY is not configured");
                    }
                    key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
                }
            }
        }
        return key;
    }

    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    public static String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Decryption failed", ex);
        }
    }
}
