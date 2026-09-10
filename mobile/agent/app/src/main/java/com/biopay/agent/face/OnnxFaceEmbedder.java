package com.biopay.agent.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Owns the ONNX Runtime session lifecycle for the SFace face-embedding model (OpenCV Zoo,
 * Apache 2.0) -- see {@code assets/face/README.md} for provenance, benchmark, and why it replaced
 * the earlier VirtuoTuring prototype (no published benchmark, near-zero adoption).
 *
 * <p>Shared by both product flavors, which pin different onnxruntime-android versions (see
 * build.gradle.kts's dependencies block) -- morphoSmart642 uses the current release, morphoSmart615
 * (old Android 5/6 tablets) is pinned to 1.18.0, the last release supporting its API-21 floor. The
 * {@code ai.onnxruntime.*} API surface used below is identical across both versions.
 *
 * <p>Loads from a file copied out of APK assets into internal storage: ONNX Runtime needs a real
 * file path (not an AssetManager stream). The copy is a one-time, best-effort presence check (not
 * a re-verified checksum on every load) -- acceptable for a model reachable only from the hidden
 * Settings test screen, not for a production path.
 */
final class OnnxFaceEmbedder {

    private static final String TAG = "OnnxFaceEmbedder";
    static final String MODEL_ASSET_DIR = "face";
    static final String MODEL_FILE = "sface.onnx";

    /** Confirmed by loading the graph directly with Python's onnx package (see progress.md and
     *  assets/face/README.md) -- not assumed from docs, which for this model are unreliable (the
     *  official OpenCV doc pages 403; the Python demo only exposes the opaque cv.FaceRecognizerSF
     *  wrapper, not the raw tensor names). The graph has exactly one output ("fc1"), so it's read
     *  back by index below rather than by name -- {@link OrtSession.Result#get(String)} returns
     *  {@code java.util.Optional}, which needs API 24+; morphoSmart615's minSdk is 21. */
    private static final String INPUT_NAME = "data";
    private static final int INPUT_SIZE = FaceAligner.OUTPUT_SIZE;
    static final int EMBEDDING_DIMENSIONS = 128;

    private final OrtEnvironment environment;
    private final OrtSession session;

    OnnxFaceEmbedder(Context context) throws FaceRecognitionException {
        try {
            File modelFile = copyAssetIfNeeded(context, MODEL_FILE);
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
        } catch (OrtException | IOException ex) {
            Log.e(TAG, "Failed to load face-embedding model", ex);
            throw new FaceRecognitionException("Failed to load face-embedding model: " + ex.getMessage(), ex);
        }
    }

    float[] embed(Bitmap alignedFace) throws FaceRecognitionException {
        if (alignedFace.getWidth() != INPUT_SIZE || alignedFace.getHeight() != INPUT_SIZE) {
            throw new FaceRecognitionException("Aligned face must be " + INPUT_SIZE + "x" + INPUT_SIZE);
        }
        FloatBuffer input = toNchwFloatBuffer(alignedFace);
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result result = session.run(Collections.singletonMap(INPUT_NAME, tensor))) {
            if (result.size() != 1) {
                throw new FaceRecognitionException("Unexpected embedder output count: " + result.size());
            }
            OnnxValue outputValue = result.get(0);
            float[][] output = (float[][]) outputValue.getValue();
            if (output.length != 1 || output[0].length != EMBEDDING_DIMENSIONS) {
                throw new FaceRecognitionException("Unexpected embedder output shape");
            }
            return output[0];
        } catch (OrtException ex) {
            Log.e(TAG, "Face embedding inference failed", ex);
            throw new FaceRecognitionException("Face embedding inference failed: " + ex.getMessage(), ex);
        }
    }

    void close() {
        try {
            session.close();
        } catch (OrtException ignored) {
            // Nothing recoverable to do on teardown.
        }
    }

    /** NCHW, RGB, raw pixel values 0-255 -- confirmed from OpenCV's face_recognize.cpp, which
     *  feeds this model via {@code blobFromImage(scalefactor=1, mean=(0,0,0), swapRB=true)}; no
     *  scaling to [0,1] or [-1,1] like the old VirtuoTuring model (see assets/face/README.md). */
    private static FloatBuffer toNchwFloatBuffer(Bitmap bitmap) {
        int size = INPUT_SIZE;
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);

        int channelStride = size * size;
        float[] r = new float[channelStride];
        float[] g = new float[channelStride];
        float[] b = new float[channelStride];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            r[i] = (pixel >> 16) & 0xFF;
            g[i] = (pixel >> 8) & 0xFF;
            b[i] = pixel & 0xFF;
        }
        FloatBuffer buffer = FloatBuffer.allocate(3 * channelStride);
        buffer.put(r).put(g).put(b);
        buffer.rewind();
        return buffer;
    }

    private static File copyAssetIfNeeded(Context context, String assetName) throws IOException {
        File dir = new File(context.getFilesDir(), MODEL_ASSET_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create " + dir);
        }
        File dest = new File(dir, assetName);
        if (dest.exists() && dest.length() > 0) {
            return dest;
        }
        try (InputStream in = context.getAssets().open(MODEL_ASSET_DIR + "/" + assetName);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return dest;
    }
}
