package com.biopay.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdReviewStatusTest {

    @Test
    void normalizesLegacyAndMissingStatusesToPending() {
        assertEquals("PENDING", Household.normalizeReviewStatus("CHECKED"));
        assertEquals("PENDING", Household.normalizeReviewStatus(null));
        assertEquals("PENDING", Household.normalizeReviewStatus("unexpected"));
    }

    @Test
    void preservesFinalStatuses() {
        assertEquals("APPROVED", Household.normalizeReviewStatus(" approved "));
        assertEquals("REJECTED", Household.normalizeReviewStatus("rejected"));
    }

    @Test
    void acceptsOnlyApproveOrRejectAsReviewDecisions() {
        assertTrue(Household.isReviewDecision("APPROVED"));
        assertTrue(Household.isReviewDecision(" rejected "));
        assertFalse(Household.isReviewDecision("PENDING"));
        assertFalse(Household.isReviewDecision("CHECKED"));
    }
}
