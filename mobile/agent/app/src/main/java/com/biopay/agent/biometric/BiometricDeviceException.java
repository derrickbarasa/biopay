package com.biopay.agent.biometric;

/** Thrown by {@link BiometricDevice#open} when the scanner can't be reached at all. */
public class BiometricDeviceException extends Exception {

    private final int errorCode;

    public BiometricDeviceException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
