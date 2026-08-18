package com.biopay.agent.face;

/** Pure cosine comparison for embeddings produced by the exact same recognition model version. */
public final class FaceMatcher {
    private FaceMatcher() { }

    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length < 2 || left.length != right.length) {
            throw new IllegalArgumentException("Embeddings must have equal dimensions");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            if (!Float.isFinite(left[i]) || !Float.isFinite(right[i])) {
                throw new IllegalArgumentException("Embeddings must contain finite values");
            }
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm < 1e-12 || rightNorm < 1e-12) {
            throw new IllegalArgumentException("Embeddings must have non-zero magnitude");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** The threshold must come from acceptance testing for the selected model and target devices. */
    public static boolean matches(float[] probe, float[] enrolled, double threshold) {
        if (!Double.isFinite(threshold) || threshold < -1 || threshold > 1) {
            throw new IllegalArgumentException("Threshold must be between -1 and 1");
        }
        return cosineSimilarity(probe, enrolled) >= threshold;
    }
}
