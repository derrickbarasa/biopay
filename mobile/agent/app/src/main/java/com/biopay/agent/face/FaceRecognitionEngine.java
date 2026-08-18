package com.biopay.agent.face;

/**
 * Boundary for the approved on-device recognition/liveness SDK.
 *
 * <p>Face detection alone must never implement this interface: an implementation must produce
 * stable identity embeddings and report a liveness decision from the same capture session.
 */
public interface FaceRecognitionEngine {
    String modelVersion();

    CaptureResult createEmbedding(byte[] encodedImage) throws FaceRecognitionException;

    final class CaptureResult {
        public final float[] embedding;
        public final double qualityScore;
        public final boolean live;

        public CaptureResult(float[] embedding, double qualityScore, boolean live) {
            this.embedding = embedding;
            this.qualityScore = qualityScore;
            this.live = live;
        }
    }
}
