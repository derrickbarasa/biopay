package com.biopay.agent.households;

import java.util.Locale;

/** Infers gender only where the selected family relationship is unambiguous. */
public final class RelationshipGender {
    private RelationshipGender() {}

    public static String infer(String relationship) {
        String value = relationship == null ? "" : relationship.trim().toLowerCase(Locale.US);
        switch (value) {
            case "husband":
            case "son":
            case "brother":
            case "father":
            case "grandfather":
            case "grandson":
            case "uncle":
            case "nephew":
                return "Male";
            case "wife":
            case "daughter":
            case "sister":
            case "mother":
            case "grandmother":
            case "granddaughter":
            case "aunt":
            case "niece":
                return "Female";
            default:
                return null;
        }
    }
}
