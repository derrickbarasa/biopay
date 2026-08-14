package com.biopay.agent.biometric;

/** Callbacks for {@link BiometricDevice#startVerify}. Always invoked on the main thread. */
public interface VerifyCallback {

    /** A human-readable prompt from the sensor, same vocabulary as {@link CaptureCallback#onProgress}. */
    void onProgress(String message);

    /** The live scan matched the candidate template. */
    void onMatched(int matchScore);

    /** The live scan did not match the candidate template (not an error -- a normal outcome). */
    void onNoMatch();

    /** Verification itself failed (device error, timeout) -- distinct from a clean no-match. */
    void onError(int errorCode, String message);
}
