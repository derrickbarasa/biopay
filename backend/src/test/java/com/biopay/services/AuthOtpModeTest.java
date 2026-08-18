package com.biopay.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthOtpModeTest {

    @Test
    void onlyExplicitFalseDisablesOtp() {
        assertFalse(Auth.isOtpRequired("false"));
        assertFalse(Auth.isOtpRequired(" FALSE "));
    }

    @Test
    void otpIsFailSafeForMissingTrueOrUnrecognisedValues() {
        assertTrue(Auth.isOtpRequired(null));
        assertTrue(Auth.isOtpRequired("true"));
        assertTrue(Auth.isOtpRequired("enabled"));
        assertTrue(Auth.isOtpRequired(""));
    }
}
