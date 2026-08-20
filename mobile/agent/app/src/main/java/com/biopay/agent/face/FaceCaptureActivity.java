package com.biopay.agent.face;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dumb capture-and-validate screen: shows a live front-camera preview, runs a fast ML Kit
 * detector on each frame purely for on-screen guidance (enable/disable the capture button, show
 * a status chip), and on capture writes a still JPEG to a cache file and returns its path.
 * Deliberately owns no embedding/storage/enrollment logic itself -- see {@link
 * MlKitFaceRecognitionEngine}, which re-validates the actual captured still authoritatively and
 * is the only place identity matching would ever be attempted.
 */
public class FaceCaptureActivity extends BaseActivity {

    public static final String EXTRA_RESULT_IMAGE_PATH = "face_capture_image_path";

    private PreviewView previewView;
    private TextView tvFaceStatus;
    private MaterialButton btnCapture;

    private ExecutorService cameraExecutor;
    private FaceDetector liveDetector;
    private ImageCapture imageCapture;
    private volatile boolean liveFrameOk = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Snackbar.make(previewView, R.string.face_capture_permission_denied, Snackbar.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_capture);
        setupBackToolbar(R.id.toolbar);

        previewView = findViewById(R.id.previewView);
        tvFaceStatus = findViewById(R.id.tvFaceStatus);
        btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(v -> capture());

        cameraExecutor = Executors.newSingleThreadExecutor();
        FaceDetectorOptions liveOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        liveDetector = FaceDetection.getClient(liveOptions);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindCamera(future.get());
            } catch (ExecutionException | InterruptedException ex) {
                Snackbar.make(previewView, R.string.face_capture_camera_unavailable, Snackbar.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        provider.unbindAll();
        provider.bindToLifecycle(this, selector, preview, analysis, imageCapture);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        liveDetector.process(inputImage)
                .addOnSuccessListener(faces -> onLiveFaces(faces, inputImage.getWidth(), inputImage.getHeight()))
                .addOnFailureListener(ex -> onLiveFaces(java.util.Collections.emptyList(), inputImage.getWidth(), inputImage.getHeight()))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onLiveFaces(List<Face> faces, int width, int height) {
        Face single = faces.size() == 1 ? faces.get(0) : null;
        FaceDetectionHeuristics.Outcome outcome = FaceDetectionHeuristics.evaluate(
                faces.size(),
                single != null ? single.getBoundingBox().width() : 0,
                single != null ? single.getBoundingBox().height() : 0,
                width, height);

        runOnUiThread(() -> {
            liveFrameOk = outcome == FaceDetectionHeuristics.Outcome.OK;
            btnCapture.setEnabled(liveFrameOk);
            tvFaceStatus.setText(statusTextFor(outcome));
            tvFaceStatus.setBackgroundResource(liveFrameOk ? R.drawable.bg_status_success : R.drawable.bg_status_warning);
            tvFaceStatus.setTextColor(ContextCompat.getColor(this, liveFrameOk ? R.color.bp_success : R.color.bp_warning));
        });
    }

    private int statusTextFor(FaceDetectionHeuristics.Outcome outcome) {
        switch (outcome) {
            case OK: return R.string.face_capture_status_ready;
            case MULTIPLE_FACES: return R.string.face_capture_status_multiple;
            case FACE_TOO_SMALL: return R.string.face_capture_status_too_small;
            case NO_FACE:
            default: return R.string.face_capture_status_searching;
        }
    }

    private void capture() {
        if (imageCapture == null || !liveFrameOk) return;
        btnCapture.setEnabled(false);
        File outFile = new File(getCacheDir(), "face_capture_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(outFile).build();
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Intent result = new Intent();
                        result.putExtra(EXTRA_RESULT_IMAGE_PATH, outFile.getAbsolutePath());
                        setResult(RESULT_OK, result);
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        btnCapture.setEnabled(true);
                        Toast.makeText(FaceCaptureActivity.this, R.string.face_capture_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        liveDetector.close();
    }
}
