package com.biopay.services;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardScopeTest {
    @Test void onlyExplicitSystemContextMayBeUnscoped() {
        assertTrue(Dashboard.hasScope(new JsonObject().put("actorRole", "SYSTEM")));
        assertTrue(Dashboard.hasScope(new JsonObject().put("systemAdmin", true)));
        assertFalse(Dashboard.hasScope(new JsonObject()));
    }
    @Test void missingOrMalformedAnchorNeverBecomesCrossAnchorAccess() {
        for (Object id : new Object[]{"bad", 0, -1}) {
            assertFalse(Dashboard.hasScope(new JsonObject().put("actorRole", "ANCHOR").put("anchorId", id)));
        }
        assertFalse(Dashboard.hasScope(new JsonObject().put("actorRole", "ANCHOR")));
        assertTrue(Dashboard.hasScope(new JsonObject().put("actorRole", "ANCHOR").put("anchorId", 7)));
    }
    @Test void organisationMustHaveAnOrganisationCode() {
        assertFalse(Dashboard.hasScope(new JsonObject().put("actorRole", "ORGANISATION").putNull("partnerCode")));
        assertFalse(Dashboard.hasScope(new JsonObject().put("actorRole", "ORGANISATION").put("partnerCode", " ")));
        assertTrue(Dashboard.hasScope(new JsonObject().put("actorRole", "ORGANISATION").put("partnerCode", "ORG-7")));
    }
}
