package com.biopay.agent.households;

import java.util.regex.Pattern;

/** Mirrors the dashboard's own phone-format rule (frontend/src/utils/phone.ts): a phone
 *  number is optional everywhere it appears, but if one is entered it must include its
 *  country code. Spaces and dashes are stripped before checking. */
public final class PhoneValidation {
    private PhoneValidation() {}

    private static final Pattern PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    public static boolean isValid(String value) {
        String stripped = value == null ? "" : value.replaceAll("[\\s-]", "");
        return PATTERN.matcher(stripped).matches();
    }
}
