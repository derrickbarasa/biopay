package com.biopay.agent.biometric;

import android.app.Activity;
import android.widget.ImageView;

/**
 * Contract every fingerprint-scanner integration implements, independent of
 * which vendor SDK (or SDK version) actually talks to the hardware.
 *
 * <p>There are two concrete implementations of {@code com.biopay.agent.biometric.impl.MorphoDeviceAdapter}
 * -- one per Gradle product flavor (see app/build.gradle.kts) -- because the
 * 6.42.0.0 and 6.15.3.0 releases of the Morpho SDK both declare classes
 * under {@code com.morpho.morphosmart.sdk}, so their jars cannot coexist on
 * one classpath. {@link BiometricDeviceFactory#create()} is the single
 * place that knows the concrete class name; every screen in the app talks
 * to that instance only through this interface. Adding a third vendor's
 * scanner means: implement this interface, add its jar under a new flavor's
 * {@code libs/}, add the flavor to build.gradle.kts -- nothing here or in
 * any Activity changes.
 */
public interface BiometricDevice {

    /** Short machine-readable id, e.g. "MORPHO_SMART_642". Stored alongside captured data. */
    String getDeviceId();

    /** Human-readable label for Settings/diagnostics screens. */
    String getDisplayName();

    /**
     * Best-effort check for whether this device's hardware/driver is
     * reachable right now (e.g. a USB device enumerates). Does not
     * guarantee {@link #open} will succeed -- it can still fail if the
     * device is claimed by another process or unplugged mid-check.
     */
    boolean isAvailable(Activity activity);

    /**
     * Opens the USB/serial connection to the scanner. Must be called before
     * {@link #startCapture} or {@link #startVerify}. Optionally renders a
     * live preview of the finger being scanned into {@code previewView} --
     * pass null if the screen has no preview surface.
     */
    void open(Activity activity, ImageView previewView) throws BiometricDeviceException;

    /**
     * Starts an asynchronous single-finger capture (enrollment). Results
     * arrive on {@code callback}, which is always invoked on the main
     * thread regardless of which thread the underlying SDK calls back on.
     */
    void startCapture(int fingerPosition, CaptureCallback callback);

    /**
     * Starts an asynchronous 1:1 verification of a live scan against
     * {@code candidateTemplate} (previously captured and stored). Results
     * arrive on {@code callback} on the main thread.
     */
    void startVerify(byte[] candidateTemplate, VerifyCallback callback);

    /** Aborts whatever capture/verify is currently in flight, if any. */
    void cancelLiveAcquisition();

    /** Releases the USB/serial connection. Safe to call even if never opened. */
    void close();
}
