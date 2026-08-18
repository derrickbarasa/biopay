package com.biopay.agent.face;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FaceMatcherTest {
    @Test
    public void identicalVectorsHaveMaximumSimilarity() {
        assertEquals(1.0, FaceMatcher.cosineSimilarity(
                new float[]{0.2f, -0.5f, 0.1f}, new float[]{0.2f, -0.5f, 0.1f}), 0.000001);
    }

    @Test
    public void thresholdControlsMatchDecision() {
        float[] enrolled = new float[]{1f, 0f};
        float[] probe = new float[]{0.8f, 0.6f};
        assertTrue(FaceMatcher.matches(probe, enrolled, 0.75));
        assertFalse(FaceMatcher.matches(probe, enrolled, 0.85));
    }

    @Test
    public void rejectsDimensionMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.cosineSimilarity(new float[]{1f, 0f}, new float[]{1f, 0f, 0f}));
    }
}
