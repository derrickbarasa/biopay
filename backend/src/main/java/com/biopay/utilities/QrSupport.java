package com.biopay.utilities;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Generic QR-code rendering for arbitrary text, backed by the ZXing core/javase
 * libraries already on the classpath (declared for the TOTP enrollment QR). Used
 * to stamp a scannable household reference onto printable payment vouchers.
 *
 * <p>Distinct from {@link TotpSupport#qrCodeDataUri}, which only ever encodes an
 * {@code otpauth://} enrollment URI via dev.samstevens.totp's specialised
 * generator; this one encodes any string.
 */
public final class QrSupport {

    private QrSupport() {
    }

    /** A ready-to-embed {@code data:image/png;base64,...} URI encoding the given text. */
    public static String dataUri(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate QR code", ex);
        }
    }

    public static String dataUri(String text) {
        return dataUri(text, 220);
    }
}
