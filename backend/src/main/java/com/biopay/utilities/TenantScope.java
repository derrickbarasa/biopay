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
