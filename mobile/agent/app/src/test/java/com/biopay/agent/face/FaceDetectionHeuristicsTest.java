package com.biopay.agent.face;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FaceDetectionHeuristicsTest {

    @Test
    public void noFacesIsRejected() {
        assertEquals(FaceDetectionHeuristics.Outcome.NO_FACE,
                FaceDetectionHeuristics.evaluate(0, 0, 0, 1000, 1000));
    }

    @Test
    public void multipleFacesIsRejected() {
        assertEquals(FaceDetectionHeuristics.Outcome.MULTIPLE_FACES,
                FaceDetectionHeuristics.evaluate(2, 400, 400, 1000, 1000));
    }

    @Test
    public void singleLargeCenteredFaceIsAccepted() {
        assertEquals(FaceDetectionHeuristics.Outcome.OK,
                FaceDetectionHeuristics.evaluate(1, 400, 450, 1000, 1200));
    }

    @Test
    public void singleTinyFaceIsRejectedAsTooSmall() {
        assertEquals(FaceDetectionHeuristics.Outcome.FACE_TOO_SMALL,
                FaceDetectionHeuristics.evaluate(1, 100, 110, 1000, 1200));
    }

    @Test
    public void boundaryFaceSizeIsAccepted() {
        // Exactly the minimum fraction (25%) of the shorter side should pass, not fail.
        assertEquals(FaceDetectionHeuristics.Outcome.OK,
                FaceDetectionHeuristics.evaluate(1, 250, 250, 1000, 1200));
    }

    @Test
    public void invalidImageDimensionsAreRejected() {
        assertEquals(FaceDetectionHeuristics.Outcome.FACE_TOO_SMALL,
                FaceDetectionHeuristics.evaluate(1, 400, 400, 0, 0));
    }
}
