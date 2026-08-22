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
 * Owns the ONNX Runtime session lifecycle for the prototype VirtuoTuring face-embedding model --
 * see {@code assets/face/README.md} for provenance and its explicit unvalidated-prototype status.
 *
 * <p>Loads from files copied out of APK assets into internal storage: ONNX Runtime needs a real
 * file path (not an AssetManager stream) so the model's external-data weights file resolves
 * relative to it. The copy is a one-time, best-effort presence check (not a re-verified checksum
 * on every load) -- acceptable for a prototype reachable only from the hidden Settings test
 * screen, not for a production path.
 */
final class OnnxFaceEmbedder {

    private static final String TAG = "OnnxFaceEmbedder";
    static final String MODEL_ASSET_DIR = "face";
    static final String MODEL_FILE = "virtuoturing.onnx";
    static final String WEIGHTS_FILE = "best_embedder.onnx.data";

    /** Confirmed by inspecting the graph directly (see progress.md) -- not assumed. The graph has
     *  exactly one output ("embedding"), so it's read back by index below rather than by name --
     *  {@link OrtSession.Result#get(String)} returns {@code java.util.Optional}, which needs API
     *  24+; this app's minSdk is 21. */
    private static final String INPUT_NAME = "input";
    private static final int INPUT_SIZE = FaceAligner.OUTPUT_SIZE;
    static final int EMBEDDING_DIMENSIONS = 512;

    private final OrtEnvironment environment;
    private final OrtSession session;

    OnnxFaceEmbedder(Context context) throws FaceRecognitionException {
        try {
            File modelFile = copyAssetIfNeeded(context, MODEL_FILE);
            copyAssetIfNeeded(context, WEIGHTS_FILE); // must sit alongside modelFile for external data
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

    /** NCHW, RGB, pixels scaled to [-1,1] per the model card (see assets/face/README.md). */
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
            r[i] = (((pixel >> 16) & 0xFF) / 127.5f) - 1f;
            g[i] = (((pixel >> 8) & 0xFF) / 127.5f) - 1f;
            b[i] = ((pixel & 0xFF) / 127.5f) - 1f;
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
