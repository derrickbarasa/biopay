package com.biopay.utilities;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.util.Base64;

/**
 * Authenticator-app (TOTP) enrollment and verification for login 2FA --
 * standard SHA1/6-digit/30s codes, the default every authenticator app
 * (Google Authenticator, Authy, 1Password, ...) expects. Secrets are
 * persisted encrypted at rest via {@link Crypto}, same as fingerprint
 * templates; this class only ever sees the decrypted plaintext secret.
 */
public final class TotpSupport {

    private static final CodeVerifier VERIFIER =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private static final QrGenerator QR_GENERATOR = new ZxingPngQrGenerator();

    private TotpSupport() {
    }

    public static String generateSecret() {
        return new DefaultSecretGenerator().generate();
    }

    public static boolean verify(String secret, String code) {
        if (secret == null || code == null || code.trim().isEmpty()) {
            return false;
        }
        return VERIFIER.isValidCode(secret, code.trim());
    }

    /** A ready-to-embed {@code data:image/png;base64,...} URI for an enrollment QR code. */
    public static String qrCodeDataUri(String secret, String accountLabel) {
        String issuer = Env.get().get("TOTP_ISSUER", "BioPay");
        QrData data = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] png = QR_GENERATOR.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("Failed to generate TOTP QR code", ex);
        }
    }
}
