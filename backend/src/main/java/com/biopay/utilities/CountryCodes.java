package com.biopay.utilities;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** ISO-3166-1 alpha-2 validation and name lookup for geography codes. */
public final class CountryCodes {
    private static final Set<String> ISO_ALPHA_2 = Collections.unmodifiableSet(
            Arrays.stream(Locale.getISOCountries()).collect(Collectors.toSet()));
    private static final Map<String, String> CODE_BY_ENGLISH_NAME = buildNameMap();

    private CountryCodes() {}

    private static Map<String, String> buildNameMap() {
        Map<String, String> result = new HashMap<>();
        for (String code : ISO_ALPHA_2) {
            Locale country = new Locale.Builder().setRegion(code).build();
            result.put(country.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        return Collections.unmodifiableMap(result);
    }

    public static String requireAlpha2(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_ALPHA_2.contains(normalized)) {
            throw new IllegalArgumentException("country must be a valid ISO alpha-2 code");
        }
        return normalized;
    }

    public static String alpha2ForName(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (ISO_ALPHA_2.contains(normalized)) return normalized;
        return CODE_BY_ENGLISH_NAME.get(value.trim().toLowerCase(Locale.ROOT));
    }

    public static String alpha2OrNamePrefix(String value) {
        String iso = alpha2ForName(value);
        if (iso != null) return iso;
        String letters = value == null ? "" : value.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return letters.length() >= 2 ? letters.substring(0, 2) : "ST";
    }
}
