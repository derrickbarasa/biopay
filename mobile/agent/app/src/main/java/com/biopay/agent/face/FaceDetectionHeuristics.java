package com.biopay.agent.face;

/**
 * Pure, JVM-testable validation rules for "is this a usable single-face capture" -- deliberately
 * separated from ML Kit's {@code Face}/{@code InputImage} types (which need an Android/Play
 * Services runtime and can't be constructed in a plain unit test) so the actual decision logic
 * has real test coverage instead of only being exercised on a device.
 */
public final class FaceDetectionHeuristics {
    private FaceDetectionHeuristics() { }

    /** A face's bounding box must cover at least this fraction of the shorter image dimension to
     *  be considered close/large enough to use -- rejects faces that are present but too small
     *  (e.g. someone in the background) to capture usefully. */
    private static final double MIN_FACE_FRACTION = 0.25;

    public enum Outcome {
        OK,
        NO_FACE,
        MULTIPLE_FACES,
        FACE_TOO_SMALL
    }

    /**
     * @param faceCount        number of faces ML Kit detected in the frame
     * @param faceBoxWidth     bounding box width of the single detected face (ignored if faceCount != 1)
     * @param faceBoxHeight    bounding box height of the single detected face (ignored if faceCount != 1)
     * @param imageWidth       source image width
     * @param imageHeight      source image height
     */
    public static Outcome evaluate(int faceCount, int faceBoxWidth, int faceBoxHeight,
            int imageWidth, int imageHeight) {
        if (faceCount == 0) return Outcome.NO_FACE;
        if (faceCount > 1) return Outcome.MULTIPLE_FACES;
        if (imageWidth <= 0 || imageHeight <= 0) return Outcome.FACE_TOO_SMALL;

        int shorterSide = Math.min(imageWidth, imageHeight);
        int smallerBoxSide = Math.min(faceBoxWidth, faceBoxHeight);
        if (smallerBoxSide < shorterSide * MIN_FACE_FRACTION) return Outcome.FACE_TOO_SMALL;

        return Outcome.OK;
    }
}
