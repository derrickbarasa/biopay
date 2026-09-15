package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.Rows;
import com.biopay.utilities.TenantScope;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/** Read-only, server-scoped access to the compliance audit trail. */
public class Audit extends AbstractVerticle {
    private MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) {
        pool = Datasource.pool();
        vertx.eventBus().consumer("GET_AUDIT_LOGS", this::getAuditLogs);
        startPromise.complete();
    }

    private void getAuditLogs(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if ("SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            message.reply(new JsonObject().put("responseCode", "999")
                    .put("responseMessage", "Field officers cannot browse administrator audit logs").toString());
            return;
        }
        boolean systemOwner = TenantScope.isSystemOwner(payload);
        boolean anchorUser = TenantScope.isAnchorAdministrator(payload);
        Integer anchorId = TenantScope.anchorId(payload);
        String organizationCode = payload.getString("partnerCode");
        String actorKind = clean(payload.getString("actorKind"));
        Integer actorId = integer(payload.getValue("auditActorId"));
        String action = clean(payload.getString("action"));
        String channel = clean(payload.getString("auditChannel"));

        // The tenant clauses are mandatory and derive only from JWT-enriched values.
        // Client filters can narrow this result but can never widen it.
        String sql = "SELECT TOP 500 l.id,l.actor_type,l.actor_id,l.anchor_id,l.organization_code,l.action,"
                + "l.entity_type,l.entity_id,l.details,l.channel,l.created_at,"
                + "COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(u.first_name,' ',u.surname))),''),"
                + "NULLIF(LTRIM(RTRIM(CONCAT(f.firstname,' ',f.lastname))),''),'Unknown user') AS actor_name,"
                + "COALESCE(u.email,f.email) AS actor_email,a.anchor_name,o.name AS organization_name,"
                + "ru.role_name,ru.role_scope "
                + "FROM audit_logs l "
                + "LEFT JOIN users u ON u.id=l.actor_id AND l.actor_type IN ('USER','SYSTEM','ANCHOR_USER','ORGANISATION_USER','API') "
                + "LEFT JOIN field_officers f ON f.id=l.actor_id AND l.actor_type='SUPERVISOR' "
                + "LEFT JOIN roles ru ON ru.id=u.role_id "
                + "LEFT JOIN users a ON a.id=l.anchor_id AND a.user_scope='ANCHOR' AND a.id=a.anchor_id "
                + "LEFT JOIN organizations o ON o.organization_code=l.organization_code AND o.anchor_id=l.anchor_id "
                + "WHERE (@p1=1 OR l.anchor_id=@p2) "
                + "AND (@p3=0 OR l.organization_code=@p4) "
                + "AND (@p5 IS NULL OR (@p5='OFFICER' AND l.actor_type='SUPERVISOR') "
                + "OR (@p5='USER' AND l.actor_type IN ('USER','SYSTEM','ANCHOR_USER','ORGANISATION_USER'))) "
                + "AND (@p6 IS NULL OR l.actor_id=@p6) AND (@p7 IS NULL OR l.action=@p7) "
                + "AND (@p8 IS NULL OR l.channel=@p8) ORDER BY l.created_at DESC,l.id DESC";

        boolean organizationOnly = !systemOwner && !anchorUser;
        Tuple params = Tuple.of(systemOwner, anchorId, organizationOnly, organizationCode,
                actorKind, actorId, action, channel);
        pool.preparedQuery(sql).execute(params)
                .onFailure(error -> message.reply(new JsonObject().put("responseCode", "999")
                        .put("responseMessage", "Unable to load audit logs").toString()))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row row : rows) {
                        results.add(new JsonObject()
                                .put("id", Rows.intVal(row, "id"))
                                .put("actorType", Rows.str(row, "actor_type"))
                                .put("actorId", Rows.intVal(row, "actor_id"))
                                .put("actorName", Rows.str(row, "actor_name"))
                                .put("actorEmail", Rows.str(row, "actor_email"))
                                .put("actorRole", actorRole(row))
                                .put("anchorId", Rows.intVal(row, "anchor_id"))
                                .put("anchorName", Rows.str(row, "anchor_name"))
                                .put("organisationCode", Rows.str(row, "organization_code"))
                                .put("organisationName", Rows.str(row, "organization_name"))
                                .put("action", Rows.str(row, "action"))
                                .put("entityType", Rows.str(row, "entity_type"))
                                .put("entityId", Rows.str(row, "entity_id"))
                                .put("details", Rows.str(row, "details"))
                                .put("channel", Rows.str(row, "channel"))
                                .put("createdAt", Rows.str(row, "created_at")));
                    }
                    message.reply(new JsonObject().put("responseCode", "000")
                            .put("responseMessage", "Audit logs found").put("results", results).toString());
                });
    }

    // Same built-in-role-name convention Administration.java uses to tell a
    // ships-with-the-platform admin/anchor/org account apart from a custom
    // role assigned to a secondary user in that same scope.
    private static String actorRole(Row row) {
        String actorType = Rows.str(row, "actor_type");
        if ("SUPERVISOR".equals(actorType)) return "Field Officer";
        if ("API".equals(actorType)) return "API Client";
        String roleName = Rows.str(row, "role_name");
        String roleScope = Rows.str(row, "role_scope");
        if (roleScope == null) return "Unassigned";
        boolean builtIn = "Platform Owner".equals(roleName) || "Anchor Administrator".equals(roleName)
                || "Organisation Administrator".equals(roleName);
        switch (roleScope.toUpperCase()) {
            case "SYSTEM": return builtIn ? "Platform Owner" : "Admin User";
            case "ANCHOR": return builtIn ? "Anchor" : "Anchor User";
            case "ORGANISATION": return builtIn ? "Org" : "Org User";
            default: return "Unassigned";
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
