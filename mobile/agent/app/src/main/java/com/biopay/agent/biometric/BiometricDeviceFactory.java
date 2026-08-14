package com.biopay.agent.biometric;

import com.biopay.agent.biometric.impl.MorphoDeviceAdapter;

/**
 * The one place that knows the concrete {@link BiometricDevice}
 * implementation for this build. {@code MorphoDeviceAdapter} is a class
 * name shared by both product flavors -- each flavor's own source set
 * (src/morphoSmart642/java/... or src/morphoSmart615/java/...) supplies a
 * different implementation of it, wrapping a different Morpho SDK jar.
 * Exactly one is ever compiled into a given build variant, so this factory
 * needs no flavor-detection logic of its own.
 */
public final class BiometricDeviceFactory {

    private static BiometricDevice instance;

    private BiometricDeviceFactory() {
    }

    public static synchronized BiometricDevice create() {
        if (instance == null) {
            instance = new MorphoDeviceAdapter();
        }
        return instance;
    }
}
