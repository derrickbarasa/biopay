package com.biopay.agent.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only {@link FaceRecognitionEngine} implementation in this app. Detection (find a single,
 * well-framed face) is real and fully functional via ML Kit -- a free, on-device, Google-shipped
 * SDK. The identity-matching half is not: Google does not publicly distribute a face-
 * identification (embedding) model (only detection and landmark/mesh models), and no unvalidated
 * community model may be silently wired in as if it were production-ready (see the explicit
 * decision recorded in progress.md). {@link #createEmbedding} therefore runs the real detection
 * validation first, and only then fails with a clearly-labelled, typed error -- it never fabricates
 * a fake embedding.
 */
public class MlKitFaceRecognitionEngine implements FaceRecognitionEngine {

    /** Not a real production model tag -- deliberately unambiguous that no matching model backs
     *  it, so this string can never be mistaken for (or silently synced as) a real embedding model
     *  version if a caller ever ignores the thrown exception. */
    public static final String MODEL_VERSION = "mlkit-detection-only-v1-no-embedding";

    private static final long DETECT_TIMEOUT_SECONDS = 10;

    private final FaceDetector detector;

    public MlKitFaceRecognitionEngine() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        this.detector = FaceDetection.getClient(options);
    }

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public CaptureResult createEmbedding(byte[] encodedImage) throws FaceRecognitionException {
        DetectedFace detected = detectSingleUsableFace(encodedImage);

        // ---- Embedding integration point ----
        // Detection above is real; this is where a *vetted, approved* embedding model would run
        // (compute a fixed-length identity vector from `detected`, matched via FaceMatcher's
        // existing cosine comparison). No such model is configured -- see the class javadoc for
        // why one isn't simply downloaded here. Wire a real model in at this exact point once one
        // is sourced and approved; until then this must keep throwing, not fabricate a vector.
        throw new FaceRecognitionException(
                "Face detected and validated, but no approved face-matching model is configured. "
                        + "Identity verification is not available yet -- see "
                        + "MlKitFaceRecognitionEngine.createEmbedding() for the integration point.");
    }

    /** Runs real ML Kit detection + the {@link FaceDetectionHeuristics} quality gate; throws a
     *  specific, user-actionable {@link FaceRecognitionException} for each real capture problem
     *  (no face / multiple faces / face too small) before ever reaching the embedding stub above,
     *  so a caller can tell "bad capture, try again" apart from "feature not available yet". */
    private DetectedFace detectSingleUsableFace(byte[] encodedImage) throws FaceRecognitionException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(encodedImage, 0, encodedImage.length);
        if (bitmap == null) {
            throw new FaceRecognitionException("Captured image could not be decoded");
        }
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        List<Face> faces;
        try {
            faces = Tasks.await(detector.process(inputImage), DETECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            throw new FaceRecognitionException("Face detection failed", ex.getCause() != null ? ex.getCause() : ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FaceRecognitionException("Face detection was interrupted", ex);
        } catch (TimeoutException ex) {
            throw new FaceRecognitionException("Face detection timed out", ex);
        }

        Face single = faces.size() == 1 ? faces.get(0) : null;
        FaceDetectionHeuristics.Outcome outcome = FaceDetectionHeuristics.evaluate(
                faces.size(),
                single != null ? single.getBoundingBox().width() : 0,
                single != null ? single.getBoundingBox().height() : 0,
                bitmap.getWidth(),
                bitmap.getHeight());

        switch (outcome) {
            case NO_FACE:
                throw new FaceRecognitionException("No face was detected in the capture. Try again with better lighting and framing.");
            case MULTIPLE_FACES:
                throw new FaceRecognitionException("More than one face was detected. Only the beneficiary should be in frame.");
            case FACE_TOO_SMALL:
                throw new FaceRecognitionException("The detected face is too small or off-frame. Move closer and center the face.");
            case OK:
            default:
                return new DetectedFace(single);
        }
    }

    /** Marker wrapper -- keeps the ML Kit {@link Face} type from leaking past this file. */
    private static final class DetectedFace {
        @NonNull final Face face;
        DetectedFace(@NonNull Face face) { this.face = face; }
    }
}
