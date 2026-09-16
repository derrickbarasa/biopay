package com.biopay.utilities;

import io.vertx.core.json.JsonObject;

/** Shared interpretation of the Super Admin -> Anchor -> Organisation hierarchy. */
public final class TenantScope {
    private TenantScope() {}

    public static boolean isSystemOwner(JsonObject payload) {
        return payload.getBoolean("systemAdmin", false)
                || "SYSTEM".equalsIgnoreCase(payload.getString("actorRole", ""));
    }

    public static boolean isAnchorAdministrator(JsonObject payload) {
        return !isSystemOwner(payload)
                && "ANCHOR".equalsIgnoreCase(payload.getString("actorRole", ""));
    }

    public static boolean managesOrganisations(JsonObject payload) {
        return isSystemOwner(payload) || isAnchorAdministrator(payload);
    }

    public static boolean isOrganisationAdministrator(JsonObject payload) {
        return !isSystemOwner(payload) && !isAnchorAdministrator(payload)
                && "ORGANISATION".equalsIgnoreCase(payload.getString("actorRole", ""));
    }

    /** An Anchor Administrator manages roles for their whole anchor; an Organisation
     *  Administrator may now do the same for just their own organisation (see
     *  Administration#getRoles/saveRole/deleteRole). */
    public static boolean managesRoles(JsonObject payload) {
        return managesOrganisations(payload) || isOrganisationAdministrator(payload);
    }

    public static Integer anchorId(JsonObject payload) {
        Object value = payload.getValue("anchorId");
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
