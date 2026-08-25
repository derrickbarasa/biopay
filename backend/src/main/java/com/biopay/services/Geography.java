package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
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
 *
 * Codes are generated here, not typed by the caller: <countryPrefix><sequence>,
 * e.g. Kenya -> KE2000, Uganda -> UG3000 (019_geo_location_country_codes.sql).
 * A state's country is chosen on creation and stored on geo_states.country;
 * counties/locations/villages resolve their prefix by looking up their
 * ancestor state's country. A state created without a country (the column is
 * nullable) keeps the old plain-numeric-code behaviour for itself and
 * everything under it.
 */
public class Geography extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Geography =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_STATE", msg -> create(msg, "STATE", "geo_states", "state_code",
                new String[] {}, new String[] {}));
        eventBus.consumer("GET_STATES", msg -> list(msg, "geo_states", "state_code", null, null));

        eventBus.consumer("CREATE_COUNTY", msg -> create(msg, "COUNTY", "geo_counties", "county_code",
                new String[] { "state_code" }, new String[] { "stateCode" }));
        eventBus.consumer("GET_COUNTIES", msg -> list(msg, "geo_counties", "county_code", "state_code", "stateCode"));

        eventBus.consumer("CREATE_LOCATION", msg -> create(msg, "LOCATION", "geo_locations", "location_code",
                new String[] { "state_code", "county_code" }, new String[] { "stateCode", "countyCode" }));
        eventBus.consumer("GET_LOCATIONS", msg -> list(msg, "geo_locations", "location_code", "county_code", "countyCode"));

        eventBus.consumer("CREATE_VILLAGE", msg -> create(msg, "VILLAGE", "geo_villages", "village_code",
                new String[] { "state_code", "county_code", "location_code" },
                new String[] { "stateCode", "countyCode", "locationCode" }));
        eventBus.consumer("GET_VILLAGES", msg -> list(msg, "geo_villages", "village_code", "location_code", "locationCode"));

        eventBus.consumer("BULK_UPLOAD_GEO_NODES", this::bulkUpload);

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

    // ---- code generation: <countryPrefix><sequence>, e.g. KE2000 -------------------

    /**
     * A state's own chosen country (may be empty/absent -> no prefix) or, for
     * every other level, its ancestor state's geo_states.country (may be null
     * if that state was never given one -> no prefix).
     */
    private Future<String> countryPrefixFor(Integer anchorId, String level, String stateCode, String explicitCountry) {
        if ("STATE".equalsIgnoreCase(level)) {
            String c = explicitCountry == null ? "" : explicitCountry.trim().toUpperCase();
            return Future.succeededFuture(c);
        }
        return pool.preparedQuery("SELECT country FROM geo_states WHERE anchor_id=@p1 AND state_code=@p2")
                .execute(Tuple.of(anchorId, stateCode))
                .map(rows -> {
                    if (rows.size() == 0) return "";
                    String c = Rows.str(rows.iterator().next(), "country");
                    return c == null ? "" : c;
                });
    }

    /**
     * Next sequential number for this anchor+table, continuing the same running
     * sequence regardless of country prefix (so KE/UG/etc. codes under one
     * anchor don't each restart their own numbering). Starts at 1000. Scans
     * every row (including soft-deleted ones) because UQ_..._anchor_code isn't
     * status-filtered -- a deleted code's number can't be reused.
     */
    private Future<Integer> nextSequence(String table, String codeColumn, Integer anchorId) {
        String sql = "SELECT MAX(CASE WHEN PATINDEX('%[0-9]%', " + codeColumn + ") > 0 "
                + "THEN TRY_CAST(SUBSTRING(" + codeColumn + ", PATINDEX('%[0-9]%', " + codeColumn + "), LEN(" + codeColumn + ")) AS INT) "
                + "ELSE NULL END) AS mx FROM " + table + " WHERE anchor_id=@p1";
        return pool.preparedQuery(sql).execute(Tuple.of(anchorId))
                .map(rows -> {
                    if (rows.size() == 0) return 1000;
                    Integer max = Rows.intVal(rows.iterator().next(), "mx");
                    return (max == null ? 999 : max) + 1;
                });
    }

    private Future<String> nextCode(Integer anchorId, String level, String table, String codeColumn,
            String stateCode, String explicitCountry) {
        return countryPrefixFor(anchorId, level, stateCode, explicitCountry)
                .compose(prefix -> nextSequence(table, codeColumn, anchorId).map(seq -> prefix + seq));
    }

    private static String statePrefix(String name, String requestedPrefix) {
        if (requestedPrefix != null && !requestedPrefix.isBlank()) return requestedPrefix.trim().toUpperCase();
        String letters = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        return letters.substring(0, Math.min(2, letters.length()));
    }

    // ---- shared insert used by both the single create() and the bulk loop ---------

    private Future<RowSet<Row>> insertGeoRow(String table, String codeColumn, Integer anchorId,
            String[] parentColumns, String[] parentValues, String code, String name, String country, Object actorId) {
        StringBuilder columns = new StringBuilder("anchor_id, ");
        StringBuilder placeholders = new StringBuilder("@p1, ");
        Tuple params = Tuple.of(anchorId);
        int idx = 2;
        for (int i = 0; i < parentColumns.length; i++) {
            columns.append(parentColumns[i]).append(", ");
            placeholders.append("@p").append(idx++).append(", ");
            params = params.addString(parentValues[i]);
        }
        if (country != null && !country.trim().isEmpty()) {
            columns.append("country, ");
            placeholders.append("@p").append(idx++).append(", ");
            params = params.addString(country.trim().toUpperCase());
        }
        columns.append(codeColumn).append(", name, status, created_by, created_at");
        placeholders.append("@p").append(idx++).append(", @p").append(idx++).append(", 1, @p").append(idx).append(", GETDATE()");
        params = params.addString(code).addString(name).addString(String.valueOf(actorId));

        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        return pool.preparedQuery(sql).execute(params);
    }

    // ---- CREATE_STATE / CREATE_COUNTY / CREATE_LOCATION / CREATE_VILLAGE ----------
    // parentColumns/parentFields: e.g. ["state_code","county_code"] <- payload's ["stateCode","countyCode"]

    private void create(Message<Object> message, String level, String table, String codeColumn,
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
        String name = payload.getString("name", "").trim();
        if (name.isEmpty()) {
            replyError(message, "name is required");
            return;
        }
        String[] parentValues = new String[parentFields.length];
        for (int i = 0; i < parentFields.length; i++) {
            String v = payload.getString(parentFields[i], "").trim();
            if (v.isEmpty()) {
                replyError(message, parentFields[i] + " is required");
                return;
            }
            parentValues[i] = v;
        }
        String stateCode = parentFields.length > 0 ? parentValues[0] : null;
        final String explicitCountry = "STATE".equalsIgnoreCase(level)
                ? statePrefix(name, payload.getString("country")) : payload.getString("country");

        nextCode(anchorId, level, table, codeColumn, stateCode, explicitCountry)
                .onComplete(codeAr -> {
                    if (codeAr.failed()) {
                        onDbError(message, codeAr.cause());
                        return;
                    }
                    String code = codeAr.result();
                    insertGeoRow(table, codeColumn, anchorId, parentColumns, parentValues, code, name,
                            "STATE".equalsIgnoreCase(level) ? explicitCountry : null, payload.getValue("actorId"))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(rows -> {
                                if (rows.rowCount() > 0) {
                                    reply(message, new JsonObject()
                                            .put("responseCode", "000")
                                            .put("responseMessage", "Created successfully")
                                            .put("code", code));
                                } else {
                                    replyError(message, "Failed to create. Code may already exist");
                                }
                            });
                });
    }

    // ---- BULK_UPLOAD_GEO_NODES { level, stateCode?, countyCode?, locationCode?, rows:[{name, country?}] } ----
    // Mirrors Household#bulkUpload's one-row-at-a-time recursion. Each row's code
    // depends on the previous insert's MAX, so rows are processed strictly in
    // sequence -- they can't be parallelized without risking duplicate codes.

    private void bulkUpload(Message<Object> message) {
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
        String level = payload.getString("level", "").trim().toUpperCase();
        String table = tableForLevel(level);
        String codeColumn = codeColumnForLevel(level);
        if (table == null) {
            replyError(message, "A valid level (STATE, COUNTY, LOCATION, VILLAGE) is required");
            return;
        }
        JsonArray rows = payload.getJsonArray("rows", new JsonArray());
        if (rows.isEmpty()) {
            replyError(message, "At least one row is required");
            return;
        }
        if (rows.size() > 1000) {
            replyError(message, "A single upload is limited to 1000 rows");
            return;
        }

        String stateCode = payload.getString("stateCode", "").trim();
        String countyCode = payload.getString("countyCode", "").trim();
        String locationCode = payload.getString("locationCode", "").trim();
        if (!"STATE".equals(level) && stateCode.isEmpty()) {
            replyError(message, "stateCode is required");
            return;
        }
        if (("LOCATION".equals(level) || "VILLAGE".equals(level)) && countyCode.isEmpty()) {
            replyError(message, "countyCode is required");
            return;
        }
        if ("VILLAGE".equals(level) && locationCode.isEmpty()) {
            replyError(message, "locationCode is required");
            return;
        }

        String[] parentColumns;
        switch (level) {
            case "STATE": parentColumns = new String[] {}; break;
            case "COUNTY": parentColumns = new String[] { "state_code" }; break;
            case "LOCATION": parentColumns = new String[] { "state_code", "county_code" }; break;
            default: parentColumns = new String[] { "state_code", "county_code", "location_code" };
        }
        String[] parentValues;
        switch (level) {
            case "STATE": parentValues = new String[] {}; break;
            case "COUNTY": parentValues = new String[] { stateCode }; break;
            case "LOCATION": parentValues = new String[] { stateCode, countyCode }; break;
            default: parentValues = new String[] { stateCode, countyCode, locationCode };
        }

        processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                payload.getValue("actorId"), rows, 0, new JsonArray(), new JsonArray());
    }

    private void processGeoUploadRow(Message<Object> message, String level, String table, String codeColumn,
            Integer anchorId, String stateCode, String[] parentColumns, String[] parentValues, Object actorId,
            JsonArray rows, int index, JsonArray created, JsonArray errors) {
        if (index >= rows.size()) {
            reply(message, new JsonObject()
                    .put("responseCode", "000")
                    .put("responseMessage", "Bulk upload complete")
                    .put("successCount", created.size())
                    .put("failureCount", errors.size())
                    .put("created", created)
                    .put("errors", errors));
            return;
        }

        JsonObject row = rows.getJsonObject(index);
        String name = row.getString("name", "").trim();
        if (name.isEmpty()) {
            errors.add(new JsonObject().put("row", index + 1).put("message", "name is required"));
            processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                    actorId, rows, index + 1, created, errors);
            return;
        }
        final String explicitCountry = "STATE".equalsIgnoreCase(level)
                ? statePrefix(name, row.getString("country")) : row.getString("country");

        nextCode(anchorId, level, table, codeColumn, stateCode, explicitCountry)
                .onComplete(codeAr -> {
                    if (codeAr.failed()) {
                        errors.add(new JsonObject().put("row", index + 1).put("message", "Failed to generate code"));
                        processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                                actorId, rows, index + 1, created, errors);
                        return;
                    }
                    String code = codeAr.result();
                    insertGeoRow(table, codeColumn, anchorId, parentColumns, parentValues, code, name,
                            "STATE".equalsIgnoreCase(level) ? explicitCountry : null, actorId)
                            .onComplete(insertAr -> {
                                if (insertAr.succeeded() && insertAr.result().rowCount() > 0) {
                                    created.add(new JsonObject().put("code", code).put("name", name));
                                } else {
                                    errors.add(new JsonObject().put("row", index + 1).put("message", "Failed to create " + name));
                                }
                                processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                                        actorId, rows, index + 1, created, errors);
                            });
                });
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
                        if ("geo_states".equals(table)) obj.put("country", Rows.str(r, "country"));
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
