package com.biopay.utilities;

import io.vertx.core.json.JsonArray;

/** Validation shared by face-enrolment boundaries; this class does not perform recognition. */
public final class FaceEmbedding {
    public static final int MAX_DIMENSIONS = 2048;

    private FaceEmbedding() { }

    public static void validate(JsonArray embedding, int declaredDimensions) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding is required");
        }
        if (embedding.size() != declaredDimensions) {
            throw new IllegalArgumentException("embeddingDimensions does not match embedding length");
        }
        if (declaredDimensions < 2 || declaredDimensions > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("embeddingDimensions is outside the supported range");
        }

        double squaredNorm = 0;
        for (Object value : embedding) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("embedding must contain only numbers");
            }
            double component = ((Number) value).doubleValue();
            if (!Double.isFinite(component)) {
                throw new IllegalArgumentException("embedding contains a non-finite number");
            }
            squaredNorm += component * component;
        }
        if (squaredNorm < 1e-12) {
            throw new IllegalArgumentException("embedding must have a non-zero magnitude");
        }
    }
}
