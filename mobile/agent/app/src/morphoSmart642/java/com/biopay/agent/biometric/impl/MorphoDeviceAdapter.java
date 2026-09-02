package com.biopay.agent.biometric.impl;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.CaptureCallback;
import com.biopay.agent.biometric.VerifyCallback;
import com.idemia.peripherals.PeripheralsPowerInterface;
import com.morpho.android.usb.USBManager;
import com.morpho.morphosmart.sdk.CallbackMask;
import com.morpho.morphosmart.sdk.CallbackMessage;
import com.morpho.morphosmart.sdk.Coder;
import com.morpho.morphosmart.sdk.CompressionAlgorithm;
import com.morpho.morphosmart.sdk.CustomInteger;
import com.morpho.morphosmart.sdk.DetectionMode;
import com.morpho.morphosmart.sdk.EnrollmentType;
import com.morpho.morphosmart.sdk.ErrorCodes;
import com.morpho.morphosmart.sdk.FalseAcceptanceRate;
import com.morpho.morphosmart.sdk.LatentDetection;
import com.morpho.morphosmart.sdk.MatchingStrategy;
import com.morpho.morphosmart.sdk.MorphoDevice;
import com.morpho.morphosmart.sdk.MorphoImage;
import com.morpho.morphosmart.sdk.ResultMatching;
import com.morpho.morphosmart.sdk.Template;
import com.morpho.morphosmart.sdk.TemplateFVPType;
import com.morpho.morphosmart.sdk.TemplateList;
import com.morpho.morphosmart.sdk.TemplateType;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link BiometricDevice} backed by IDEMIA/Morpho SDK 6.42.0.0, ported from
 * dca's per-Activity capture/verify code (which called {@link MorphoDevice}
 * directly, 3 times over, with no shared wrapper). This class is that
 * wrapper -- the capture/verify parameter choices below are exactly what
 * dca used.
 *
 * <p>Also binds IDEMIA's Peripheral Management AIDL service
 * ({@code com.idemia.peripherals.PeripheralsPowerInterface}, see
 * {@link #powerOnFingerprintPeripherals}) before every {@link #isAvailable}
 * and {@link #open} call. On ID Screen / ID Screen 60 tablets the fingerprint
 * sensor's power rail and host USB port default off to save battery, so
 * without this the SDK's own USB enumeration never sees the sensor at all --
 * it isn't a cable/pairing problem, the port is simply unpowered.
 */
public class MorphoDeviceAdapter implements BiometricDevice, Observer {

    private static final String TAG = "MorphoDeviceAdapter";
    private static final int TIMEOUT_SECONDS = 30;
    private static final long PERIPHERALS_BIND_TIMEOUT_MS = 2000;
    private static final int ENUMERATION_ATTEMPTS = 12;
    private static final long ENUMERATION_RETRY_MS = 250;
    private static final int IDEMIA_USB_VENDOR_ID = 8797;
    private static final int IDEMIA_CBM_E3_PRODUCT_ID = 8;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MorphoDevice morphoDevice;
    private ImageView previewView;

    // Set for the duration of one capture/verify call so the shared Observer#update callback
    // (the SDK's only callback mechanism) knows which listener to forward to.
    private CaptureCallback activeCaptureCallback;
    private VerifyCallback activeVerifyCallback;

    @Override
    public String getDeviceId() {
        return "MORPHO_SMART_642";
    }

    @Override
    public String getDisplayName() {
        return "IDEMIA embedded scanner (SDK 6.42)";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        try {
            powerOnFingerprintPeripherals(activity);
            for (int attempt = 1; attempt <= ENUMERATION_ATTEMPTS; attempt++) {
                USBManager.getInstance().initialize(activity, "com.biopay.agent.USB_ACTION", true);
                MorphoDevice probe = new MorphoDevice();
                CustomInteger nbUsbDevice = new CustomInteger();
                int ret = probe.initUsbDevicesNameEnum(nbUsbDevice);
                Log.i(TAG, "Scanner enumeration attempt " + attempt + ": ret=" + ret
                        + ", devices=" + nbUsbDevice.getValueOf());
                if (ret == ErrorCodes.MORPHO_OK && nbUsbDevice.getValueOf() >= 1) {
                    return true;
                }
                SystemClock.sleep(ENUMERATION_RETRY_MS);
            }
            return false;
        } catch (Throwable ex) {
            // USBManager's static initializer loads the vendor .so on first touch of this class --
            // on a device/APK combination missing that native library (e.g. no matching ABI, or no
            // real Morpho hardware ever plugged in) that throws UnsatisfiedLinkError, an Error, not
            // an Exception. This is a best-effort check per the BiometricDevice contract, so it must
            // degrade to "not available" instead of crashing whatever screen called it.
            Log.e(TAG, "Scanner availability check failed", ex);
            return false;
        }
    }

    @Override
    public void open(Activity activity, ImageView previewView) throws BiometricDeviceException {
        this.previewView = previewView;
        powerOnFingerprintPeripherals(activity);
        USBManager.getInstance().initialize(activity, "com.biopay.agent.USB_ACTION", true);
        MorphoDevice device = new MorphoDevice();
        CustomInteger nbUsbDevice = new CustomInteger();
        int ret = ErrorCodes.MORPHOERR_UNAVAILABLE;
        for (int attempt = 1; attempt <= ENUMERATION_ATTEMPTS; attempt++) {
            USBManager.getInstance().initialize(activity, "com.biopay.agent.USB_ACTION", true);
            ret = device.initUsbDevicesNameEnum(nbUsbDevice);
            if (ret == ErrorCodes.MORPHO_OK && nbUsbDevice.getValueOf() >= 1) {
                break;
            }
            SystemClock.sleep(ENUMERATION_RETRY_MS);
        }
        if (ret != ErrorCodes.MORPHO_OK) {
            throw new BiometricDeviceException("Error initialising USB device", ret);
        }
        if (nbUsbDevice.getValueOf() != 1) {
            throw new BiometricDeviceException("Expected exactly one fingerprint scanner, found "
                    + nbUsbDevice.getValueOf(), ret);
        }
        String sensorName = device.getUsbDeviceName(0);
        ret = device.openUsbDevice(sensorName, 0);
        if (ret != ErrorCodes.MORPHO_OK) {
            throw new BiometricDeviceException("Error opening USB device", ret);
        }
        morphoDevice = device;
    }

    /**
     * Binds IDEMIA's Peripheral Management AIDL service and switches on the fingerprint sensor and
     * host USB port power rails, then unbinds. The switches are system-level hardware state (see
     * the guide's own "improves battery life" framing for why they default off), not tied to the
     * binding's lifetime, so a short bind-call-unbind round trip per call is sufficient -- no need
     * to hold the binding open across a whole capture/verify session.
     *
     * <p>Best-effort and silent: on hardware/firmware without this service (an emulator, a non-ID-
     * Screen device, or the {@code morphoSmart615} tablet flavor's own path) this simply no-ops, so
     * USB enumeration proceeds exactly as it did before this method existed.
     */
    private void powerOnFingerprintPeripherals(Activity activity) {
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<PeripheralsPowerInterface> peripheralsRef = new AtomicReference<>();
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                peripheralsRef.set(PeripheralsPowerInterface.Stub.asInterface(service));
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                peripheralsRef.set(null);
            }
        };

        Intent aidlIntent = new Intent("idemia.intent.action.CONN_PERIPHERALS_SERVICE_AIDL");
        aidlIntent.setPackage("com.android.settings");

        boolean bound;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // The legacy overload dispatches onServiceConnected on the main thread. Most
                // callers invoke this method from that thread, so waiting on the latch would
                // prevent the callback from ever running. ID Screen firmware is Android 10+;
                // its executor overload lets the binder callback arrive independently.
                bound = activity.bindService(aidlIntent, Context.BIND_AUTO_CREATE,
                        callbackExecutor, connection);
            } else {
                bound = activity.bindService(aidlIntent, connection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Peripheral Management service unavailable, skipping sensor power-on", ex);
            callbackExecutor.shutdownNow();
            return;
        }
        if (!bound) {
            Log.w(TAG, "Peripheral Management service did not bind");
            callbackExecutor.shutdownNow();
            return;
        }

        try {
            connected.await(PERIPHERALS_BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            PeripheralsPowerInterface peripherals = peripheralsRef.get();
            if (peripherals != null) {
                boolean fingerprintWasOn = peripherals.getFingerPrintSwitch();
                if (fingerprintWasOn && !hasEmbeddedScannerOnUsb(activity)) {
                    // ID Screen firmware can leave the sysfs switch reading "1" after the
                    // embedded module has dropped off USB. Rewriting "1" alone does not reset
                    // that stale hardware state; cycle only when the expected 8797:0008 device
                    // is genuinely absent so an active scanner session is never interrupted.
                    peripherals.setFingerPrintSwitch(false);
                    // The ID Screen controller needs time to remove the internal USB device
                    // before power is applied again. A short pulse can leave the firmware's
                    // UI switch ON while the scanner itself remains absent.
                    SystemClock.sleep(1000);
                }
                boolean fingerprintSet = peripherals.setFingerPrintSwitch(true);
                boolean hostUsbSet = peripherals.setHostUsbPortSwitch(true);
                // Reading the proc-backed switch immediately races the kernel driver, and USB
                // enumeration cannot start until the internal module has completed its boot.
                SystemClock.sleep(1000);
                Log.i(TAG, "Embedded scanner power: fingerprintSet=" + fingerprintSet
                        + ", fingerprintOn=" + peripherals.getFingerPrintSwitch()
                        + ", hostUsbSet=" + hostUsbSet
                        + ", hostUsbOn=" + peripherals.getHostUsbPortSwitch());
            } else {
                Log.w(TAG, "Peripheral Management service callback timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RemoteException ex) {
            Log.w(TAG, "Failed to power on fingerprint sensor/host USB port", ex);
        } finally {
            activity.unbindService(connection);
            callbackExecutor.shutdownNow();
        }
    }

    private boolean hasEmbeddedScannerOnUsb(Activity activity) {
        UsbManager usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return false;
        for (Map.Entry<String, UsbDevice> entry : usbManager.getDeviceList().entrySet()) {
            UsbDevice device = entry.getValue();
            if (device.getVendorId() == IDEMIA_USB_VENDOR_ID
                    && device.getProductId() == IDEMIA_CBM_E3_PRODUCT_ID) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void startCapture(int fingerPosition, CaptureCallback callback) {
        if (morphoDevice == null) {
            callback.onError(ErrorCodes.MORPHOERR_UNAVAILABLE, "Device is not open");
            return;
        }
        activeCaptureCallback = callback;
        activeVerifyCallback = null;

        new Thread(() -> {
            TemplateList templateList = new TemplateList();
            int callbackCmd = CallbackMask.MORPHO_CALLBACK_COMMAND_CMD.getValue()
                    | CallbackMask.MORPHO_CALLBACK_IMAGE_CMD.getValue()
                    | CallbackMask.MORPHO_CALLBACK_CODEQUALITY.getValue()
                    | CallbackMask.MORPHO_CALLBACK_DETECTQUALITY.getValue();
            int detectModeChoice = DetectionMode.MORPHO_ENROLL_DETECT_MODE.getValue()
                    | DetectionMode.MORPHO_FORCE_FINGER_ON_TOP_DETECT_MODE.getValue();

            int ret = morphoDevice.capture(TIMEOUT_SECONDS, 0, 0, fingerPosition,
                    TemplateType.MORPHO_PK_ISO_FMR_2011, TemplateFVPType.MORPHO_NO_PK_FVP, 512,
                    EnrollmentType.ONE_ACQUISITIONS, LatentDetection.LATENT_DETECT_ENABLE,
                    Coder.MORPHO_DEFAULT_CODER, detectModeChoice,
                    CompressionAlgorithm.MORPHO_NO_COMPRESS, 0, templateList, callbackCmd, this);

            if (ret == ErrorCodes.MORPHO_OK && templateList.getNbTemplate() == 1) {
                Template template = templateList.getTemplate(0);
                byte[] data = template.getData();
                postCaptured(data, null);
            } else {
                postCaptureError(ret);
            }
        }).start();
    }

    @Override
    public void startVerify(byte[] candidateTemplate, VerifyCallback callback) {
        if (morphoDevice == null) {
            callback.onError(ErrorCodes.MORPHOERR_UNAVAILABLE, "Device is not open");
            return;
        }
        activeVerifyCallback = callback;
        activeCaptureCallback = null;

        new Thread(() -> {
            TemplateList templateList = new TemplateList();
            Template template = new Template();
            template.setData(candidateTemplate);
            template.setTemplateType(TemplateType.MORPHO_PK_ISO_FMR_2011);
            templateList.putTemplate(template);

            int callbackCmd = CallbackMask.MORPHO_CALLBACK_IMAGE_CMD.getValue()
                    | CallbackMask.MORPHO_CALLBACK_COMMAND_CMD.getValue();
            int detectModeChoice = DetectionMode.MORPHO_ENROLL_DETECT_MODE.getValue()
                    | DetectionMode.MORPHO_FORCE_FINGER_ON_TOP_DETECT_MODE.getValue();
            int matchingStrategy = MatchingStrategy.MORPHO_STANDARD_MATCHING_STRATEGY.getValue();
            ResultMatching resultMatching = new ResultMatching();

            int ret = morphoDevice.verify(TIMEOUT_SECONDS, FalseAcceptanceRate.MORPHO_FAR_5,
                    Coder.MORPHO_MSO_V9_CODER, detectModeChoice, matchingStrategy, templateList,
                    callbackCmd, this, resultMatching);

            if (ret == ErrorCodes.MORPHO_OK) {
                postMatched(resultMatching.getMatchingScore());
            } else if (ret == ErrorCodes.MORPHOERR_INVALID_FINGER || ret == ErrorCodes.MORPHOERR_NO_HIT) {
                postNoMatch();
            } else {
                postVerifyError(ret);
            }
        }).start();
    }

    @Override
    public MatchResult templatesMatch(byte[] templateA, byte[] templateB) {
        if (morphoDevice == null) return MatchResult.ERROR;

        Template candidate = new Template();
        candidate.setData(templateA);
        candidate.setTemplateType(TemplateType.MORPHO_PK_ISO_FMR_2011);
        TemplateList candidateList = new TemplateList();
        candidateList.putTemplate(candidate);

        Template reference = new Template();
        reference.setData(templateB);
        reference.setTemplateType(TemplateType.MORPHO_PK_ISO_FMR_2011);
        TemplateList referenceList = new TemplateList();
        referenceList.putTemplate(reference);

        int ret = morphoDevice.verifyMatch(FalseAcceptanceRate.MORPHO_FAR_5, candidateList, referenceList, new CustomInteger());
        if (ret == ErrorCodes.MORPHO_OK) return MatchResult.MATCHED;
        if (ret == ErrorCodes.MORPHOERR_NO_HIT) return MatchResult.NO_MATCH;
        Log.e(TAG, "templatesMatch: unexpected verifyMatch return code " + ret);
        return MatchResult.ERROR;
    }

    @Override
    public void cancelLiveAcquisition() {
        if (morphoDevice != null) {
            try {
                morphoDevice.cancelLiveAcquisition();
            } catch (Exception ignored) {
                // Best-effort: cancelling an already-finished acquisition is a no-op we don't care about.
            }
        }
    }

    @Override
    public void close() {
        if (morphoDevice != null) {
            try {
                morphoDevice.cancelLiveAcquisition();
                morphoDevice.closeDevice();
            } catch (Exception ignored) {
                // Best-effort: the device may already be closed/unplugged.
            } finally {
                morphoDevice = null;
            }
        }
    }

    // ---- Observer#update: the SDK's single callback channel, fanned out to whichever of
    // capture/verify is currently in flight. ------------------------------------------------

    @Override
    public void update(Observable observable, Object arg) {
        try {
            CallbackMessage message = (CallbackMessage) arg;
            switch (message.getMessageType()) {
                case 1: // command / prompt
                    postProgress(promptFor((Integer) message.getMessage()));
                    break;
                case 2: // low-res preview image
                    postPreviewFrame((byte[]) message.getMessage());
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
            // A malformed callback payload shouldn't crash the capture/verify flow already in
            // progress -- the SDK will still deliver its final capture()/verify() return code.
        }
    }

    private String promptFor(int command) {
        switch (command) {
            case 0: return "Place finger for acquisition";
            case 1: return "Move finger up";
            case 2: return "Move finger down";
            case 3: return "Move finger left";
            case 4: return "Move finger right";
            case 5: return "Press harder";
            case 6: return "Remove finger";
            case 7: return "Remove finger";
            case 8: return "Finger detected";
            default: return "";
        }
    }

    private void postProgress(String message) {
        mainHandler.post(() -> {
            if (activeCaptureCallback != null) activeCaptureCallback.onProgress(message);
            if (activeVerifyCallback != null) activeVerifyCallback.onProgress(message);
        });
    }

    private void postPreviewFrame(byte[] rawImage) {
        if (previewView == null || activeCaptureCallback == null) return;
        try {
            MorphoImage morphoImage = MorphoImage.getMorphoImageFromLive(rawImage);
            if (morphoImage == null) return;
            int rows = morphoImage.getMorphoImageHeader().getNbRow();
            int cols = morphoImage.getMorphoImageHeader().getNbColumn();
            Bitmap bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ALPHA_8);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(morphoImage.getImage()));
            mainHandler.post(() -> {
                previewView.setImageBitmap(bitmap);
                if (activeCaptureCallback != null) activeCaptureCallback.onPreviewFrame(bitmap);
            });
        } catch (Exception ignored) {
            // A dropped preview frame doesn't affect the eventual capture result.
        }
    }

    private void postCaptured(byte[] template, Bitmap finalImage) {
        mainHandler.post(() -> {
            if (activeCaptureCallback != null) activeCaptureCallback.onCaptured(template, finalImage);
        });
    }

    private void postCaptureError(int ret) {
        mainHandler.post(() -> {
            if (activeCaptureCallback != null) activeCaptureCallback.onError(ret, errorMessageFor(ret));
        });
    }

    private void postMatched(int score) {
        mainHandler.post(() -> {
            if (activeVerifyCallback != null) activeVerifyCallback.onMatched(score);
        });
    }

    private void postNoMatch() {
        mainHandler.post(() -> {
            if (activeVerifyCallback != null) activeVerifyCallback.onNoMatch();
        });
    }

    private void postVerifyError(int ret) {
        mainHandler.post(() -> {
            if (activeVerifyCallback != null) activeVerifyCallback.onError(ret, errorMessageFor(ret));
        });
    }

    private String errorMessageFor(int ret) {
        if (ret == ErrorCodes.MORPHOERR_TIMEOUT) return "Timed out";
        if (ret == ErrorCodes.MORPHOERR_CMDE_ABORTED) return "Aborted";
        if (ret == ErrorCodes.MORPHOERR_UNAVAILABLE) return "Device is not available";
        return "Error code " + ret;
    }
}
