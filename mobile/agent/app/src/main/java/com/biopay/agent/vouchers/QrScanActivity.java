package com.biopay.agent.vouchers;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.OutcomeFeedback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live back-camera QR scanner for the printed voucher's `BIOPAY|VOUCHER|<code>` payload
 * (see VouchersPage.vue's print action, the only place that format is generated). Purely a
 * faster entry point into the existing redemption flow -- on a match this finishes with the
 * bare voucher code and {@link VoucherRedemptionActivity} does the actual lookup/verification,
 * exactly as if the code had been picked from the synced list.
 */
public class QrScanActivity extends BaseActivity {

    public static final String EXTRA_RESULT_VOUCHER_CODE = "qr_scan_result_voucher_code";
    private static final String VOUCHER_QR_PREFIX = "BIOPAY|VOUCHER|";

    private PreviewView previewView;
    private TextView tvScanStatus;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private volatile boolean resolved = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    OutcomeFeedback.error(this, R.string.qr_scan_permission_denied);
                    finish();
                }
            });

    public static Intent intent(Context context) {
        return new Intent(context, QrScanActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);
        setupBackToolbar(R.id.toolbar);

        previewView = findViewById(R.id.previewView);
        tvScanStatus = findViewById(R.id.tvScanStatus);

        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

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
                OutcomeFeedback.error(this, R.string.qr_scan_camera_unavailable);
                finish();
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

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        provider.unbindAll();
        provider.bindToLifecycle(this, selector, preview, analysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (resolved || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(this::onBarcodes)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodes(List<Barcode> barcodes) {
        if (resolved || barcodes.isEmpty()) return;
        for (Barcode barcode : barcodes) {
            String raw = barcode.getRawValue();
            if (raw == null) continue;
            if (raw.startsWith(VOUCHER_QR_PREFIX)) {
                String code = raw.substring(VOUCHER_QR_PREFIX.length()).trim();
                if (!code.isEmpty()) {
                    resolved = true;
                    runOnUiThread(() -> {
                        tvScanStatus.setText(R.string.qr_scan_status_found);
                        Intent result = new Intent();
                        result.putExtra(EXTRA_RESULT_VOUCHER_CODE, code);
                        setResult(RESULT_OK, result);
                        finish();
                    });
                    return;
                }
            }
            runOnUiThread(() -> tvScanStatus.setText(R.string.qr_scan_not_biopay_voucher));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }
}
