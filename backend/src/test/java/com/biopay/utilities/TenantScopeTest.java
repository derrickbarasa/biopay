package com.biopay.utilities;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantScopeTest {
    @Test void systemOwnerIsNotAnAnchorAdministrator() {
        JsonObject owner = new JsonObject().put("actorRole", "SYSTEM").put("systemAdmin", true);
        assertTrue(TenantScope.isSystemOwner(owner));
        assertFalse(TenantScope.isAnchorAdministrator(owner));
        assertTrue(TenantScope.managesOrganisations(owner));
    }

    @Test void anchorAdministratorManagesOnlyAnAnchorContext() {
        JsonObject anchor = new JsonObject().put("actorRole", "ANCHOR").put("anchorId", 7);
        assertFalse(TenantScope.isSystemOwner(anchor));
        assertTrue(TenantScope.isAnchorAdministrator(anchor));
        assertTrue(TenantScope.managesOrganisations(anchor));
        assertEquals(7, TenantScope.anchorId(anchor));
    }

    @Test void organisationAdministratorCannotManageAcrossOrganisations() {
        JsonObject organisation = new JsonObject().put("actorRole", "ORGANISATION").put("anchorId", 7);
        assertFalse(TenantScope.managesOrganisations(organisation));
        assertEquals(7, TenantScope.anchorId(organisation));
    }
}
