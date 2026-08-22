package com.biopay.agent.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only {@link FaceRecognitionEngine} implementation in this app. Detection (find a single,
 * well-framed face) is real and fully functional via ML Kit -- a free, on-device, Google-shipped
 * SDK. Google does not publicly distribute a face-identification (embedding) model, so the
 * embedding half is backed by a separate, explicitly-labeled <b>prototype</b>: the
 * VirtuoTuring/virtuoturing-face-embedder ONNX model run via {@link OnnxFaceEmbedder} -- see
 * {@code assets/face/README.md} and {@code progress.md} for its provenance, license, and why it
 * must not be mistaken for a validated production model (zero community adoption, ~23,660-image
 * training set, no published accuracy benchmark). The intended production path is IDEMIA
 * MorphoKit, pending separate licensing; this prototype exists to prove out the
 * capture-&gt;align-&gt;embed-&gt;match pipeline in the meantime and is expected to be replaced,
 * not extended, once that licensing is settled.
 */
public class MlKitFaceRecognitionEngine implements FaceRecognitionEngine {

    /** Flags this as the unvalidated prototype so nothing downstream can mistake it for a
     *  production-grade model tag if it's ever synced/compared against a real one later. */
    public static final String MODEL_VERSION = "virtuoturing-embedder-v1-PROTOTYPE-unvalidated";

    private static final long DETECT_TIMEOUT_SECONDS = 10;

    private final Context appContext;
    private final FaceDetector detector;
    private OnnxFaceEmbedder embedder;

    public MlKitFaceRecognitionEngine(Context context) {
        this.appContext = context.getApplicationContext();
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // alignment needs eye positions
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
        Bitmap bitmap = BitmapFactory.decodeByteArray(encodedImage, 0, encodedImage.length);
        if (bitmap == null) {
            throw new FaceRecognitionException("Captured image could not be decoded");
        }

        Face face = detectSingleUsableFace(bitmap);
        PointF leftEye = landmarkPosition(face, FaceLandmark.LEFT_EYE);
        PointF rightEye = landmarkPosition(face, FaceLandmark.RIGHT_EYE);
        if (leftEye == null || rightEye == null) {
            throw new FaceRecognitionException(
                    "Could not locate both eyes in the capture. Face the camera directly and try again.");
        }

        Bitmap aligned = FaceAligner.align(bitmap, leftEye, rightEye);
        float[] embedding = embedder().embed(aligned);
        double qualityScore = faceQualityScore(face, bitmap.getWidth(), bitmap.getHeight());

        // Liveness is explicitly out of scope for this prototype -- see the class javadoc and
        // progress.md. Never report true without a real liveness signal behind it.
        return new CaptureResult(embedding, qualityScore, /*live=*/false);
    }

    private OnnxFaceEmbedder embedder() throws FaceRecognitionException {
        if (embedder == null) {
            embedder = new OnnxFaceEmbedder(appContext);
        }
        return embedder;
    }

    /** Releases the ONNX Runtime session if one was loaded. Safe to call even if never used. */
    public void close() {
        if (embedder != null) {
            embedder.close();
            embedder = null;
        }
    }

    private static PointF landmarkPosition(Face face, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        return landmark != null ? landmark.getPosition() : null;
    }

    /** Coarse, non-rigorous heuristic (relative face size in frame) -- not a calibrated
     *  face-image-quality metric. Good enough to surface on the Settings test screen; not to be
     *  used as an acceptance gate without real validation. */
    private static double faceQualityScore(Face face, int imageWidth, int imageHeight) {
        int shorterSide = Math.min(imageWidth, imageHeight);
        if (shorterSide <= 0) return 0;
        int smallerBoxSide = Math.min(face.getBoundingBox().width(), face.getBoundingBox().height());
        return Math.max(0.0, Math.min(1.0, smallerBoxSide / (double) shorterSide));
    }

    /** Runs real ML Kit detection + the {@link FaceDetectionHeuristics} quality gate; throws a
     *  specific, user-actionable {@link FaceRecognitionException} for each real capture problem
     *  (no face / multiple faces / face too small). */
    private Face detectSingleUsableFace(Bitmap bitmap) throws FaceRecognitionException {
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
                return single;
        }
    }
}
