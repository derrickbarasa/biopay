package com.biopay.agent.face;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

/**
 * Exercises only the pure AES-GCM core ({@code encryptWithKey}/{@code decryptWithKey}), not the
 * public {@code encrypt}/{@code decrypt} entry points -- those go through Android Keystore, which
 * doesn't exist on a plain JVM unit test. The cipher math is identical either way; only where the
 * key comes from differs (see the class javadoc).
 */
public class FaceEmbeddingCipherTest {

    private static SecretKey aesKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test
    public void roundTripsPlaintext() throws GeneralSecurityException {
        SecretKey key = aesKey();
        byte[] plaintext = "[0.12,-0.45,0.98]".getBytes();

        byte[] combined = FaceEmbeddingCipher.encryptWithKey(key, plaintext);
        byte[] decrypted = FaceEmbeddingCipher.decryptWithKey(key, combined);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    public void ciphertextDoesNotContainPlaintext() throws GeneralSecurityException {
        SecretKey key = aesKey();
        byte[] plaintext = "sensitive-embedding-data".getBytes();

        byte[] combined = FaceEmbeddingCipher.encryptWithKey(key, plaintext);

        assertNotEquals(new String(plaintext), new String(combined));
    }

    @Test
    public void eachEncryptionUsesAFreshIv() throws GeneralSecurityException {
        SecretKey key = aesKey();
        byte[] plaintext = "same-input-both-times".getBytes();

        byte[] first = FaceEmbeddingCipher.encryptWithKey(key, plaintext);
        byte[] second = FaceEmbeddingCipher.encryptWithKey(key, plaintext);

        // Random IV means two encryptions of the same plaintext must not be byte-identical.
        boolean identical = Arrays.equals(first, second);
        assertNotEquals(true, identical);
    }

    @Test
    public void wrongKeyFailsToDecrypt() throws GeneralSecurityException {
        SecretKey key = aesKey();
        SecretKey otherKey = aesKey();
        byte[] combined = FaceEmbeddingCipher.encryptWithKey(key, "payload".getBytes());

        assertThrows(GeneralSecurityException.class,
                () -> FaceEmbeddingCipher.decryptWithKey(otherKey, combined));
    }

    @Test
    public void tooShortCiphertextIsRejected() {
        SecretKey key;
        try {
            key = aesKey();
        } catch (GeneralSecurityException ex) {
            throw new AssertionError(ex);
        }
        assertThrows(GeneralSecurityException.class,
                () -> FaceEmbeddingCipher.decryptWithKey(key, new byte[]{1, 2, 3}));
    }
}
