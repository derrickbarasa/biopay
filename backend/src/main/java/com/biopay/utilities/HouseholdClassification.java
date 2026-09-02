package com.biopay.utilities;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Controlled household classifications shared by web, imports, and mobile sync. */
public final class HouseholdClassification {
    private static final Set<String> VULNERABILITIES = Set.of(
            "DISABILITY", "ELDERLY_HEADED", "CHILD_HEADED", "CHRONIC_ILLNESS",
            "PREGNANT_OR_LACTATING", "SINGLE_CAREGIVER");
    private static final Set<String> LEGAL_STATUSES = Set.of(
            "CITIZEN", "REFUGEE", "IDP", "ASYLUM_SEEKER", "RETURNEE", "STATELESS", "OTHER");

    private HouseholdClassification() {}

    public static String vulnerabilityCsv(JsonObject payload) {
        Object raw = payload.getValue("vulnerabilityStatuses");
        if (raw == null) raw = payload.getValue("vulnerabilityStatus");
        return vulnerabilityCsv(raw);
    }

    public static String vulnerabilityCsv(Object raw) {
        if (raw == null) return null;
        List<?> values;
        if (raw instanceof JsonArray array) {
            values = array.getList();
        } else if (raw instanceof List<?> list) {
            values = list;
        } else {
            String text = raw.toString().trim();
            if (text.isEmpty()) return null;
            // Some Android JSON implementations serialize a Java List as its display form
            // (for example "[DISABILITY, ELDERLY_HEADED]") rather than as a JSON array.
            // Accept that representation so valid offline records cannot become stuck.
            if (text.startsWith("[") && text.endsWith("]")) {
                String contents = text.substring(1, text.length() - 1).trim();
                values = contents.isEmpty() ? List.of() : List.of(contents.split("[,|]"));
            } else {
                values = List.of(text.split("[,|]"));
            }
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null || value.toString().isBlank()) continue;
            String code = normalize(value.toString());
            if (!VULNERABILITIES.contains(code)) {
                throw new IllegalArgumentException("Unknown vulnerability category: " + value);
            }
            normalized.add(code);
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    public static String legalStatus(JsonObject payload) {
        Object raw = payload.getValue("legalStatus");
        if (raw == null || raw.toString().isBlank()) return null;
        String code = normalize(raw.toString());
        if (!LEGAL_STATUSES.contains(code)) {
            throw new IllegalArgumentException("Unknown legal status: " + raw);
        }
        return code;
    }

    public static JsonArray vulnerabilityArray(String csv) {
        JsonArray result = new JsonArray();
        if (csv == null || csv.isBlank()) return result;
        for (String item : csv.split(",")) {
            String code = normalize(item);
            if (!code.isEmpty()) result.add(code);
        }
        return result;
    }

    public static String filterCode(Object raw, boolean vulnerability) {
        if (raw == null || raw.toString().isBlank()) return null;
        String code = normalize(raw.toString());
        if ("NOT_RECORDED".equals(code)) return code;
        Set<String> allowed = vulnerability ? VULNERABILITIES : LEGAL_STATUSES;
        if (!allowed.contains(code)) {
            throw new IllegalArgumentException(vulnerability
                    ? "Unknown vulnerability category: " + raw
                    : "Unknown legal status: " + raw);
        }
        return code;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
