package com.biopay.agent.biometric;

import android.graphics.Bitmap;

/** Callbacks for {@link BiometricDevice#startCapture}. Always invoked on the main thread. */
public interface CaptureCallback {

    /** A human-readable prompt from the sensor ("Place finger", "Move up", "Press harder", ...). */
    void onProgress(String message);

    /** A live low-resolution preview frame, if the SDK streams one. May never fire. */
    void onPreviewFrame(Bitmap frame);

    /** Capture succeeded: {@code template} is the raw (unencrypted) fingerprint template bytes. */
    void onCaptured(byte[] template, Bitmap finalImage);

    /** Capture failed or timed out. {@code errorCode} is the SDK's own error code, for logs only. */
    void onError(int errorCode, String message);
}
