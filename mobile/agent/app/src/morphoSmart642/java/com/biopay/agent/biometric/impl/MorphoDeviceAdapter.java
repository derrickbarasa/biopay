package com.biopay.agent.biometric.impl;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.biopay.agent.biometric.BiometricDevice;
import com.biopay.agent.biometric.BiometricDeviceException;
import com.biopay.agent.biometric.CaptureCallback;
import com.biopay.agent.biometric.VerifyCallback;
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
import java.util.Observable;
import java.util.Observer;

/**
 * {@link BiometricDevice} backed by IDEMIA/Morpho SDK 6.42.0.0, ported from
 * dca's per-Activity capture/verify code (which called {@link MorphoDevice}
 * directly, 3 times over, with no shared wrapper). This class is that
 * wrapper -- the capture/verify parameter choices below are exactly what
 * dca used.
 *
 * <p>Not yet ported: dca's optional AIDL binding to
 * {@code com.idemia.peripherals.PeripheralsPowerInterface} for
 * MorphoTablet-specific sensor power/USB-role control. That hardware-
 * specific extra sits on top of this class (it gates whether the sensor is
 * powered before {@link #open} is even attempted) and will be added as an
 * opt-in helper once the core capture/verify path is verified end-to-end.
 */
public class MorphoDeviceAdapter implements BiometricDevice, Observer {

    private static final int TIMEOUT_SECONDS = 30;

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
        return "MorphoSmart 6.42 (USB)";
    }

    @Override
    public boolean isAvailable(Activity activity) {
        try {
            USBManager.getInstance().initialize(activity, "com.morpho.morphosample.USB_ACTION", true);
            MorphoDevice probe = new MorphoDevice();
            CustomInteger nbUsbDevice = new CustomInteger();
            int ret = probe.initUsbDevicesNameEnum(nbUsbDevice);
            return ret == ErrorCodes.MORPHO_OK && nbUsbDevice.getValueOf() >= 1;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void open(Activity activity, ImageView previewView) throws BiometricDeviceException {
        this.previewView = previewView;
        USBManager.getInstance().initialize(activity, "com.morpho.morphosample.USB_ACTION", true);
        MorphoDevice device = new MorphoDevice();
        CustomInteger nbUsbDevice = new CustomInteger();
        int ret = device.initUsbDevicesNameEnum(nbUsbDevice);
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
