package com.biopay.agent.face;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FaceAlignerTest {

    private static final double DELTA = 1e-6;

    @Test
    public void identityMappingWhenSourceMatchesDestination() {
        FaceAligner.Transform t = FaceAligner.similarityTransform(10, 20, 30, 20, 10, 20, 30, 20);
        assertEquals(1.0, t.a, DELTA);
        assertEquals(0.0, t.b, DELTA);
        assertEquals(0.0, t.tx, DELTA);
        assertEquals(0.0, t.ty, DELTA);
    }

    @Test
    public void pureTranslation() {
        // Same eye geometry, shifted by (5, 7).
        FaceAligner.Transform t = FaceAligner.similarityTransform(0, 0, 20, 0, 5, 7, 25, 7);
        assertEquals(1.0, t.a, DELTA);
        assertEquals(0.0, t.b, DELTA);
        assertEquals(5.0, t.tx, DELTA);
        assertEquals(7.0, t.ty, DELTA);
    }

    @Test
    public void pureScale() {
        // Destination eye distance is double the source's, same orientation.
        FaceAligner.Transform t = FaceAligner.similarityTransform(0, 0, 10, 0, 0, 0, 20, 0);
        assertEquals(2.0, t.a, DELTA);
        assertEquals(0.0, t.b, DELTA);
        assertEquals(0.0, t.tx, DELTA);
        assertEquals(0.0, t.ty, DELTA);
    }

    @Test
    public void rotationMapsSourcePointsOntoDestinationPoints() {
        // Source eyes are level; destination eyes are the same pair rotated 90 degrees.
        double srcLeftX = 0, srcLeftY = 0, srcRightX = 10, srcRightY = 0;
        double dstLeftX = 0, dstLeftY = 0, dstRightX = 0, dstRightY = 10;

        FaceAligner.Transform t = FaceAligner.similarityTransform(
                srcLeftX, srcLeftY, srcRightX, srcRightY, dstLeftX, dstLeftY, dstRightX, dstRightY);

        assertEquals(dstLeftX, apply(t, srcLeftX, srcLeftY)[0], DELTA);
        assertEquals(dstLeftY, apply(t, srcLeftX, srcLeftY)[1], DELTA);
        assertEquals(dstRightX, apply(t, srcRightX, srcRightY)[0], DELTA);
        assertEquals(dstRightY, apply(t, srcRightX, srcRightY)[1], DELTA);
    }

    @Test
    public void coincidentSourceEyesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FaceAligner.similarityTransform(5, 5, 5, 5, 0, 0, 10, 0));
    }

    private static double[] apply(FaceAligner.Transform t, double x, double y) {
        return new double[]{t.a * x - t.b * y + t.tx, t.b * x + t.a * y + t.ty};
    }
}
