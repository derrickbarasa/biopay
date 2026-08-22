package com.biopay.agent.face;

/**
 * Holds the cosine-similarity accept threshold for {@link FaceMatcher#matches}.
 *
 * <p><b>{@link #UNCALIBRATED_PLACEHOLDER_THRESHOLD} must never be trusted for a real accept/reject
 * decision.</b> It is the model card's suggested starting point for the prototype embedder, not a
 * value derived from acceptance testing against this deployment's actual beneficiaries/devices/
 * lighting conditions -- exactly the calibration {@link FaceMatcher#matches} already documents as
 * a prerequisite. Until that testing exists, this constant exists only to let the Settings test
 * screen show a match/no-match result, and must not back any real payment/attendance decision.
 */
public final class FaceMatchConfig {
    private FaceMatchConfig() { }

    public static final double UNCALIBRATED_PLACEHOLDER_THRESHOLD = 0.93;
}
