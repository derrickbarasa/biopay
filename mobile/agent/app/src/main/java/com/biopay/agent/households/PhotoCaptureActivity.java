package com.biopay.agent.households;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.OutcomeFeedback;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Plain rear-camera "take a photo of this person" screen -- deliberately dumb compared to {@link
 * com.biopay.agent.face.FaceCaptureActivity}: no live face detection, no embedding, no identity
 * matching. The photo is never used to verify anyone; it exists only so an officer or supervisor
 * can visually confirm who a registered household head/alternate is. Returns the captured JPEG's
 * cache path; the caller ({@link PersonCaptureActivity}) is responsible for moving it into
 * persistent storage and recording it via {@code ImageDao}.
 */
public class PhotoCaptureActivity extends BaseActivity {

    public static final String EXTRA_RESULT_IMAGE_PATH = "photo_capture_image_path";

    private PreviewView previewView;
    private MaterialButton btnCapture;
    private ImageCapture imageCapture;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    OutcomeFeedback.error(this, R.string.photo_capture_permission_denied);
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_capture);
        setupBackToolbar(R.id.toolbar);

        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(v -> capture());

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
                OutcomeFeedback.error(this, R.string.face_capture_camera_unavailable);
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        provider.unbindAll();
        provider.bindToLifecycle(this, selector, preview, imageCapture);
        btnCapture.setEnabled(true);
    }

    private void capture() {
        if (imageCapture == null) return;
        btnCapture.setEnabled(false);
        File outFile = new File(getCacheDir(), "person_photo_" + System.currentTimeMillis() + ".jpg");
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
                        OutcomeFeedback.error(PhotoCaptureActivity.this, R.string.face_capture_failed);
                    }
                });
    }
}
