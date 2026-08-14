package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.biopay.databases.Datasource;
import com.biopay.utilities.Logging;
import com.biopay.utilities.Rows;

/**
 * The configurable geo hierarchy (states -> counties -> locations ->
 * villages) an anchor sets up so its organisations can register households
 * against real places instead of free-typed codes -- see
 * 006_geo_hierarchy.sql for the scoping rationale (anchor-owned, shared
 * across every organisation under that anchor). Both anchor admins and
 * organisation admins may read and write; organisation admins carry
 * anchor_id in their session too (see Auth#loginUser), so writes they make
 * land in the same shared hierarchy as the anchor's own.
 */
public class Geography extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Geography =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_STATE", msg -> create(msg, "geo_states", "state_code",
                new String[] {}, new String[] {}));
        eventBus.consumer("GET_STATES", msg -> list(msg, "geo_states", "state_code", null, null));

        eventBus.consumer("CREATE_COUNTY", msg -> create(msg, "geo_counties", "county_code",
                new String[] { "state_code" }, new String[] { "stateCode" }));
        eventBus.consumer("GET_COUNTIES", msg -> list(msg, "geo_counties", "county_code", "state_code", "stateCode"));

        eventBus.consumer("CREATE_LOCATION", msg -> create(msg, "geo_locations", "location_code",
                new String[] { "state_code", "county_code" }, new String[] { "stateCode", "countyCode" }));
        eventBus.consumer("GET_LOCATIONS", msg -> list(msg, "geo_locations", "location_code", "county_code", "countyCode"));

        eventBus.consumer("CREATE_VILLAGE", msg -> create(msg, "geo_villages", "village_code",
                new String[] { "state_code", "county_code", "location_code" },
                new String[] { "stateCode", "countyCode", "locationCode" }));
        eventBus.consumer("GET_VILLAGES", msg -> list(msg, "geo_villages", "village_code", "location_code", "locationCode"));

        eventBus.consumer("UPDATE_GEO_NODE", this::update);
        eventBus.consumer("DELETE_GEO_NODE", this::delete);
        startPromise.complete();
    }

    private static void reply(Message<Object> message, JsonObject obj) {
        message.reply(obj.toString().trim());
    }

    private static void replyError(Message<Object> message, String responseMessage) {
        reply(message, new JsonObject().put("responseCode", "999").put("responseMessage", responseMessage));
    }

    private void onDbError(Message<Object> message, Throwable err) {
        Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3);
        replyError(message, "Failed with an error");
    }

    private static boolean canManage(JsonObject payload) {
        String role = payload.getString("actorRole", "");
        return "ANCHOR".equalsIgnoreCase(role) || "ORGANISATION".equalsIgnoreCase(role);
    }

    private static Integer anchorIdOf(JsonObject payload) {
        Object v = payload.getValue("anchorId");
        return v == null ? null : Integer.parseInt(v.toString());
    }

    private static String tableForLevel(String level) {
        if (level == null) return null;
        switch (level.toUpperCase()) {
            case "STATE": return "geo_states";
            case "COUNTY": return "geo_counties";
            case "LOCATION": return "geo_locations";
            case "VILLAGE": return "geo_villages";
            default: return null;
        }
    }

    private static String codeColumnForLevel(String level) {
        switch (level.toUpperCase()) {
            case "STATE": return "state_code";
            case "COUNTY": return "county_code";
            case "LOCATION": return "location_code";
            case "VILLAGE": return "village_code";
            default: return null;
        }
    }

    // ---- CREATE_STATE / CREATE_COUNTY / CREATE_LOCATION / CREATE_VILLAGE ----------
    // parentColumns/parentFields: e.g. ["state_code","county_code"] <- payload's ["stateCode","countyCode"]

    private void create(Message<Object> message, String table, String codeColumn,
            String[] parentColumns, String[] parentFields) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!canManage(payload)) {
            replyError(message, "Not authorised to manage locations");
            return;
        }
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "anchorId is required");
            return;
        }
        String code = payload.getString(camelCode(codeColumn), "").trim();
        String name = payload.getString("name", "").trim();
        if (code.isEmpty() || name.isEmpty()) {
            replyError(message, "code and name are required");
            return;
        }
        for (String field : parentFields) {
            if (payload.getString(field, "").trim().isEmpty()) {
                replyError(message, field + " is required");
                return;
            }
        }

        StringBuilder columns = new StringBuilder("anchor_id, ");
        StringBuilder placeholders = new StringBuilder("@p1, ");
        Tuple params = Tuple.of(anchorId);
        int idx = 2;
        for (int i = 0; i < parentColumns.length; i++) {
            columns.append(parentColumns[i]).append(", ");
            placeholders.append("@p").append(idx++).append(", ");
            params = params.addString(payload.getString(parentFields[i], "").trim());
        }
        columns.append(codeColumn).append(", name, status, created_by, created_at");
        placeholders.append("@p").append(idx++).append(", @p").append(idx++).append(", 1, @p").append(idx).append(", GETDATE()");
        params = params.addString(code).addString(name).addString(String.valueOf(payload.getValue("actorId")));

        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Created successfully"));
                    } else {
                        replyError(message, "Failed to create. Code may already exist");
                    }
                });
    }

    private static String camelCode(String snakeCodeColumn) {
        // state_code -> stateCode, county_code -> countyCode, etc.
        String[] parts = snakeCodeColumn.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    // ---- GET_STATES / GET_COUNTIES / GET_LOCATIONS / GET_VILLAGES -----------------

    private void list(Message<Object> message, String table, String codeColumn, String filterColumn, String filterField) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "anchorId is required");
            return;
        }
        String filterValue = filterField == null ? null : payload.getString(filterField, null);

        String sql = "SELECT * FROM " + table + " WHERE anchor_id=@p1 AND status=1"
                + (filterColumn == null ? "" : " AND (@p2 IS NULL OR " + filterColumn + "=@p2)")
                + " ORDER BY name";
        Tuple params = filterColumn == null ? Tuple.of(anchorId) : Tuple.of(anchorId, filterValue);

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        JsonObject obj = new JsonObject()
                                .put("code", Rows.str(r, codeColumn))
                                .put("name", Rows.str(r, "name"));
                        if (hasColumn(table, "state_code")) obj.put("stateCode", Rows.str(r, "state_code"));
                        if (hasColumn(table, "county_code")) obj.put("countyCode", Rows.str(r, "county_code"));
                        if (hasColumn(table, "location_code")) obj.put("locationCode", Rows.str(r, "location_code"));
                        results.add(obj);
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No results found" : "Results found")
                            .put("results", results));
                });
    }

    private static boolean hasColumn(String table, String column) {
        switch (table) {
            case "geo_counties": return column.equals("state_code");
            case "geo_locations": return column.equals("state_code") || column.equals("county_code");
            case "geo_villages": return column.equals("state_code") || column.equals("county_code") || column.equals("location_code");
            default: return false;
        }
    }

    // ---- UPDATE_GEO_NODE { level, code, name } -------------------------------------

    private void update(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!canManage(payload)) {
            replyError(message, "Not authorised to manage locations");
            return;
        }
        String table = tableForLevel(payload.getString("level", ""));
        String codeColumn = codeColumnForLevel(payload.getString("level", ""));
        Integer anchorId = anchorIdOf(payload);
        String code = payload.getString("code", "").trim();
        String name = payload.getString("name", "").trim();
        if (table == null || anchorId == null || code.isEmpty() || name.isEmpty()) {
            replyError(message, "level, code and name are required");
            return;
        }

        String sql = "UPDATE " + table + " SET name=@p1, updated_at=GETDATE() WHERE anchor_id=@p2 AND " + codeColumn + "=@p3";
        pool.preparedQuery(sql)
                .execute(Tuple.of(name, anchorId, code))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Updated successfully"));
                    } else {
                        replyError(message, "Not found");
                    }
                });
    }

    // ---- DELETE_GEO_NODE { level, code } (soft delete) ------------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!canManage(payload)) {
            replyError(message, "Not authorised to manage locations");
            return;
        }
        String table = tableForLevel(payload.getString("level", ""));
        String codeColumn = codeColumnForLevel(payload.getString("level", ""));
        Integer anchorId = anchorIdOf(payload);
        String code = payload.getString("code", "").trim();
        if (table == null || anchorId == null || code.isEmpty()) {
            replyError(message, "level and code are required");
            return;
        }

        String sql = "UPDATE " + table + " SET status=0, updated_at=GETDATE() WHERE anchor_id=@p1 AND " + codeColumn + "=@p2";
        pool.preparedQuery(sql)
                .execute(Tuple.of(anchorId, code))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Deleted successfully"));
                    } else {
                        replyError(message, "Not found");
                    }
                });
    }
}
