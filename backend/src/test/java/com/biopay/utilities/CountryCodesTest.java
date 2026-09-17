package com.biopay.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CountryCodesTest {
    @Test
    void resolvesStandardCountryNamesToAlpha2Codes() {
        assertEquals("GQ", CountryCodes.alpha2ForName("Equatorial Guinea"));
        assertEquals("KE", CountryCodes.alpha2ForName("Kenya"));
        assertEquals("SO", CountryCodes.alpha2ForName("Somalia"));
    }

    @Test
    void validatesAlpha2CodesAndRejectsMixedSchemes() {
        assertEquals("KE", CountryCodes.requireAlpha2("ke"));
        assertThrows(IllegalArgumentException.class, () -> CountryCodes.requireAlpha2("SOM"));
        assertThrows(IllegalArgumentException.class, () -> CountryCodes.requireAlpha2("CE"));
    }
}
