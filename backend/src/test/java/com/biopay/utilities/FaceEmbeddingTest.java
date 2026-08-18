package com.biopay.utilities;

import io.vertx.core.json.JsonArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaceEmbeddingTest {
    @Test
    void acceptsFiniteNonZeroVectorWithMatchingDimensions() {
        assertDoesNotThrow(() -> FaceEmbedding.validate(new JsonArray().add(0.25).add(-0.5).add(0.1), 3));
    }

    @Test
    void rejectsDimensionMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> FaceEmbedding.validate(new JsonArray().add(0.25).add(-0.5), 3));
    }

    @Test
    void rejectsZeroVector() {
        assertThrows(IllegalArgumentException.class,
                () -> FaceEmbedding.validate(new JsonArray().add(0.0).add(0.0), 2));
    }
}
