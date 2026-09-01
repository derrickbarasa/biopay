package com.biopay.utilities;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HouseholdClassificationTest {
    @Test void normalizesAndDeduplicatesMultipleVulnerabilities() {
        JsonObject payload = new JsonObject().put("vulnerabilityStatuses",
                new JsonArray().add("disability").add("ELDERLY_HEADED").add("disability"));

        assertEquals("DISABILITY,ELDERLY_HEADED", HouseholdClassification.vulnerabilityCsv(payload));
    }

    @Test void acceptsPipeSeparatedCsvImportValues() {
        assertEquals("CHRONIC_ILLNESS,SINGLE_CAREGIVER",
                HouseholdClassification.vulnerabilityCsv("CHRONIC_ILLNESS|SINGLE_CAREGIVER"));
    }

    @Test void rejectsUncontrolledValues() {
        assertThrows(IllegalArgumentException.class,
                () -> HouseholdClassification.vulnerabilityCsv("UNKNOWN_CATEGORY"));
        assertThrows(IllegalArgumentException.class,
                () -> HouseholdClassification.legalStatus(new JsonObject().put("legalStatus", "UNKNOWN")));
    }

    @Test void supportsExplicitNotRecordedFilters() {
        assertEquals("NOT_RECORDED", HouseholdClassification.filterCode("not recorded", true));
        assertEquals("NOT_RECORDED", HouseholdClassification.filterCode("NOT_RECORDED", false));
    }
}
