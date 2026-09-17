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
import com.biopay.utilities.CountryCodes;
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
 * Codes are generated here, not typed by the caller. State display codes are
 * canonical across anchors by ISO country + normalized state name, so "Kenya"
 * is always shown with the same public code even when several anchors use it.
 * Legacy internal keys remain untouched and use geo_states.display_code for
 * their user-facing identifier.
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
        eventBus.consumer("TOGGLE_GEO_NODE_STATUS", this::toggleStatus);
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
        return "SYSTEM".equalsIgnoreCase(role) || "ANCHOR".equalsIgnoreCase(role) || "ORGANISATION".equalsIgnoreCase(role);
    }

    private static Integer anchorIdOf(JsonObject payload) {
        // Tenant actors can never widen their geography scope with a supplied
        // targetAnchorId. EntryPoint already replaces anchorId from the JWT; use the
        // separate JWT-derived value again here as defense in depth.
        Object v = payload.getBoolean("systemAdmin", false)
                ? payload.getValue("anchorId")
                : payload.getValue("sessionAnchorId");
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
     * A state's chosen country or, for every other level, its ancestor state's
     * geo_states.country. Legacy states may still have no country until edited.
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
     * sequence regardless of country prefix. State display codes use the same
     * sequence globally so the same state cannot drift between anchors.
     */
    private Future<Integer> nextSequence(String table, String codeColumn, Integer anchorId) {
        String sequenceSource = "geo_states".equals(table) ? "COALESCE(display_code, state_code)" : codeColumn;
        String sql = "SELECT MAX(CASE WHEN PATINDEX('%[0-9]%', " + sequenceSource + ") > 0 "
                + "THEN TRY_CAST(SUBSTRING(" + sequenceSource + ", PATINDEX('%[0-9]%', " + sequenceSource + "), LEN(" + sequenceSource + ")) AS INT) "
                + "ELSE NULL END) AS mx FROM " + table
                + ("geo_states".equals(table) ? "" : " WHERE anchor_id=@p1");
        Tuple params = "geo_states".equals(table) ? Tuple.tuple() : Tuple.of(anchorId);
        return pool.preparedQuery(sql).execute(params)
                .map(rows -> {
                    if (rows.size() == 0) return 1000;
                    Integer max = Rows.intVal(rows.iterator().next(), "mx");
                    return max == null ? 1000 : ((max / 1000) + 1) * 1000;
                });
    }

    private Future<String> canonicalStateDisplayCode(String name, String country) {
        String existingSql = "SELECT TOP 1 display_code FROM geo_states "
                + "WHERE country=@p1 AND display_code IS NOT NULL "
                + "AND LOWER(LTRIM(RTRIM(name)))=LOWER(LTRIM(RTRIM(@p2))) "
                + "ORDER BY CASE WHEN PATINDEX('%[0-9]%', display_code) > 0 "
                + "THEN TRY_CAST(SUBSTRING(display_code, PATINDEX('%[0-9]%', display_code), LEN(display_code)) AS INT) "
                + "ELSE 2147483647 END, display_code";
        return pool.preparedQuery(existingSql).execute(Tuple.of(country, name))
                .compose(rows -> {
                    if (rows.size() > 0) {
                        return Future.succeededFuture(Rows.str(rows.iterator().next(), "display_code"));
                    }
                    return nextSequence("geo_states", "state_code", null).map(seq -> country + seq);
                });
    }

    private Future<String> nextCode(Integer anchorId, String level, String table, String codeColumn,
            String stateCode, String explicitCountry, String name) {
        if ("STATE".equalsIgnoreCase(level)) {
            return canonicalStateDisplayCode(name, explicitCountry);
        }
        return countryPrefixFor(anchorId, level, stateCode, explicitCountry)
                .compose(prefix -> nextSequence(table, codeColumn, anchorId).map(seq -> prefix + seq));
    }

    /**
     * Two active rows in the same table, same anchor and same immediate parent
     * (e.g. same state for a county) must never share a name -- that's how a
     * place like "Kenya" ends up listed twice with two different codes. Compared
     * case- and whitespace-insensitively since typed names vary that way.
     */
    private Future<Boolean> nameExists(String table, Integer anchorId, String[] parentColumns,
            String[] parentValues, String name) {
        StringBuilder sql = new StringBuilder(
                "SELECT TOP 1 1 FROM " + table + " WHERE anchor_id=@p1 AND status=1 "
                        + "AND LOWER(LTRIM(RTRIM(name)))=LOWER(LTRIM(RTRIM(@p2)))");
        Tuple params = Tuple.of(anchorId).addString(name);
        int idx = 3;
        for (int i = 0; i < parentColumns.length; i++) {
            sql.append(" AND ").append(parentColumns[i]).append("=@p").append(idx++);
            params = params.addString(parentValues[i]);
        }
        return pool.preparedQuery(sql.toString()).execute(params).map(rows -> rows.size() > 0);
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
        if ("geo_states".equals(table)) {
            columns.append("display_code, ");
            placeholders.append("@p").append(idx++).append(", ");
            params = params.addString(code);
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
        final String explicitCountry;
        try {
            explicitCountry = "STATE".equalsIgnoreCase(level)
                    ? CountryCodes.requireAlpha2(payload.getString("country"))
                    : payload.getString("country");
        } catch (IllegalArgumentException error) {
            replyError(message, error.getMessage());
            return;
        }

        nameExists(table, anchorId, parentColumns, parentValues, name)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(exists -> {
                    if (exists) {
                        replyError(message, capitalize(level) + " \"" + name + "\" already exists");
                        return;
                    }
                    nextCode(anchorId, level, table, codeColumn, stateCode, explicitCountry, name)
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
                });
    }

    private static String capitalize(String level) {
        String lower = level.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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
        final String explicitCountry;
        try {
            explicitCountry = "STATE".equalsIgnoreCase(level)
                    ? CountryCodes.requireAlpha2(row.getString("country"))
                    : row.getString("country");
        } catch (IllegalArgumentException error) {
            errors.add(new JsonObject().put("row", index + 1).put("message", error.getMessage()));
            processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                    actorId, rows, index + 1, created, errors);
            return;
        }

        nameExists(table, anchorId, parentColumns, parentValues, name)
                .onFailure(err -> {
                    errors.add(new JsonObject().put("row", index + 1).put("message", "Failed to validate " + name));
                    processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                            actorId, rows, index + 1, created, errors);
                })
                .onSuccess(exists -> {
                    if (exists) {
                        errors.add(new JsonObject().put("row", index + 1).put("message", name + " already exists"));
                        processGeoUploadRow(message, level, table, codeColumn, anchorId, stateCode, parentColumns, parentValues,
                                actorId, rows, index + 1, created, errors);
                        return;
                    }
                    nextCode(anchorId, level, table, codeColumn, stateCode, explicitCountry, name)
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
                });
    }

    // ---- GET_STATES / GET_COUNTIES / GET_LOCATIONS / GET_VILLAGES -----------------

    private void list(Message<Object> message, String table, String codeColumn, String filterColumn, String filterField) {
        JsonObject payload = new JsonObject(message.body().toString());
        // Same "show everything, then filter" rule every other browse endpoint follows: a
        // Super Admin who hasn't picked a target anchor sees geography across all of them
        // instead of an error; an Anchor Administrator always has their own anchorId from
        // the JWT, so this never affects that role.
        Integer anchorId = anchorIdOf(payload);
        String filterValue = filterField == null ? null : payload.getString(filterField, null);
        // Every existing caller (create-flow pickers, bulk-upload parent selects, ...) calls this
        // without a status opinion and must keep seeing active rows only. The Locations management
        // page is the only caller that ever needs inactive rows back (to show/restore them), so it
        // opts in explicitly by sending a "status" key at all (its value may be 0, 1, or left blank
        // for "every status") rather than the default changing under every other caller.
        boolean statusFilterRequested = payload.containsKey("status");
        Integer explicitStatus = statusFilterRequested ? payload.getInteger("status") : null;
        int statusParamIdx = filterColumn == null ? 2 : 3;
        String statusClause = statusFilterRequested ? " AND (@p" + statusParamIdx + " IS NULL OR t.status=@p" + statusParamIdx + ")" : " AND t.status=1";

        String stateCatalogueGuard = "geo_states".equals(table)
                ? " AND t.name NOT LIKE 'E2E Test%'"
                    + " AND NOT EXISTS (SELECT 1 FROM geo_counties c WHERE c.anchor_id=t.anchor_id"
                    + " AND c.status=1 AND LOWER(LTRIM(RTRIM(c.name)))=LOWER(LTRIM(RTRIM(t.name))))"
                : "";
        String baseWhere = " WHERE (@p1 IS NULL OR t.anchor_id=@p1)" + statusClause
                + (filterColumn == null ? "" : " AND (@p2 IS NULL OR t." + filterColumn + "=@p2)")
                + stateCatalogueGuard;
        String sql;
        if ("geo_states".equals(table)) {
            sql = "WITH visible_states AS ("
                    + "SELECT t.*, ROW_NUMBER() OVER ("
                    + "PARTITION BY COALESCE(t.country,''), LOWER(LTRIM(RTRIM(t.name))), "
                    + "COALESCE(t.display_code,t.state_code), t.status "
                    + "ORDER BY t.id) AS duplicate_rank "
                    + "FROM geo_states t" + baseWhere
                    + ") SELECT * FROM visible_states WHERE (@p1 IS NOT NULL OR duplicate_rank=1) ORDER BY name";
        } else {
            // The all-anchor browse view is a catalogue, not a tenant data dump. Keep
            // a place name once for each of its actual parents, so County/Location/
            // Village pickers cannot offer the same visible choice multiple times just
            // because another anchor carries the same hierarchy. Anchor-scoped views
            // retain every row for normal administration.
            sql = "WITH visible_nodes AS ("
                    + "SELECT t.*, ROW_NUMBER() OVER (PARTITION BY " + duplicatePartition(table)
                    + " ORDER BY t.id) AS duplicate_rank FROM " + table + " t" + baseWhere
                    + ") SELECT * FROM visible_nodes WHERE (@p1 IS NOT NULL OR duplicate_rank=1) ORDER BY name";
        }
        Tuple params = filterColumn == null ? Tuple.of(anchorId) : Tuple.of(anchorId, filterValue);
        if (statusFilterRequested) params = params.addValue(explicitStatus);

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        JsonObject obj = new JsonObject()
                                .put("code", Rows.str(r, codeColumn))
                                .put("name", Rows.str(r, "name"))
                                .put("anchorId", Rows.intVal(r, "anchor_id"))
                                .put("status", Rows.intVal(r, "status"));
                        if (hasColumn(table, "state_code")) obj.put("stateCode", Rows.str(r, "state_code"));
                        if (hasColumn(table, "county_code")) obj.put("countyCode", Rows.str(r, "county_code"));
                        if (hasColumn(table, "location_code")) obj.put("locationCode", Rows.str(r, "location_code"));
                        if ("geo_states".equals(table)) {
                            obj.put("country", Rows.str(r, "country"));
                            obj.put("displayCode", Rows.str(r, "display_code"));
                        }
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

    /** Hierarchy-aware visible identity for all-anchor catalogue lists. */
    private static String duplicatePartition(String table) {
        String normalizedName = "LOWER(LTRIM(RTRIM(t.name)))";
        switch (table) {
            case "geo_counties":
                return "COALESCE(t.state_code,''), " + normalizedName + ", t.status";
            case "geo_locations":
                return "COALESCE(t.state_code,''), COALESCE(t.county_code,''), "
                        + normalizedName + ", t.status";
            case "geo_villages":
                return "COALESCE(t.state_code,''), COALESCE(t.county_code,''), "
                        + "COALESCE(t.location_code,''), " + normalizedName + ", t.status";
            default:
                return normalizedName + ", t.status";
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

        String sql;
        Tuple params;
        if ("geo_states".equals(table)) {
            final String country;
            try {
                country = CountryCodes.requireAlpha2(payload.getString("country"));
            } catch (IllegalArgumentException error) {
                replyError(message, error.getMessage());
                return;
            }
            canonicalStateDisplayCode(name, country)
                    .onFailure(err -> onDbError(message, err))
                    .onSuccess(displayCode -> pool.preparedQuery(
                                    "UPDATE geo_states SET name=@p1, country=@p2, display_code=@p3, "
                                            + "updated_at=GETDATE() WHERE anchor_id=@p4 AND state_code=@p5")
                            .execute(Tuple.of(name, country, displayCode, anchorId, code))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(rows -> {
                                if (rows.rowCount() > 0) {
                                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Updated successfully"));
                                } else {
                                    replyError(message, "Not found");
                                }
                            }));
            return;
        } else {
            sql = "UPDATE " + table + " SET name=@p1, updated_at=GETDATE() WHERE anchor_id=@p2 AND " + codeColumn + "=@p3";
            params = Tuple.of(name, anchorId, code);
        }
        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Updated successfully"));
                    } else {
                        replyError(message, "Not found");
                    }
                });
    }

    // ---- TOGGLE_GEO_NODE_STATUS { level, code, status } (deactivate / reactivate) --

    private void toggleStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!canManage(payload)) {
            replyError(message, "Not authorised to manage locations");
            return;
        }
        String level = payload.getString("level", "");
        String table = tableForLevel(level);
        String codeColumn = codeColumnForLevel(level);
        Integer anchorId = anchorIdOf(payload);
        String code = payload.getString("code", "").trim();
        Integer status = payload.getInteger("status");
        if (table == null || anchorId == null || code.isEmpty() || status == null) {
            replyError(message, "level, code and status are required");
            return;
        }

        String sql = "UPDATE " + table + " SET status=@p1, updated_at=GETDATE() WHERE anchor_id=@p2 AND " + codeColumn + "=@p3";
        pool.preparedQuery(sql)
                .execute(Tuple.of(status, anchorId, code))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000")
                                .put("responseMessage", status == 1 ? capitalize(level) + " activated" : capitalize(level) + " deactivated"));
                    } else {
                        replyError(message, "Not found");
                    }
                });
    }

    // ---- DELETE_GEO_NODE { level, code } (real delete, blocked while anything still --
    // ---- depends on this node -- deactivate instead if it does) --------------------

    /**
     * Whether anything still points at this node: a child geo row (any status -- a
     * deactivated child would otherwise dangle), a household, or an officer's location
     * assignment. Households/officer_locations store codes as loosely-coupled strings
     * with no FK (see 006_geo_hierarchy.sql), so a hard delete here would silently orphan
     * them if it weren't blocked first.
     */
    private Future<Boolean> hasDependents(String level, Integer anchorId, String code) {
        String sql;
        switch (level.toUpperCase()) {
            case "STATE":
                sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM geo_counties c WHERE c.anchor_id=@p1 AND c.state_code=@p2) "
                        + "OR EXISTS (SELECT 1 FROM households h JOIN organizations o ON o.organization_code=h.organization_code "
                        + "WHERE o.anchor_id=@p1 AND h.state_code=@p2) "
                        + "OR EXISTS (SELECT 1 FROM officer_locations ol JOIN field_officers fo ON fo.officer_code=ol.officer_code "
                        + "WHERE fo.anchor_id=@p1 AND ol.state_code=@p2)";
                break;
            case "COUNTY":
                sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM geo_locations l WHERE l.anchor_id=@p1 AND l.county_code=@p2) "
                        + "OR EXISTS (SELECT 1 FROM households h JOIN organizations o ON o.organization_code=h.organization_code "
                        + "WHERE o.anchor_id=@p1 AND h.county_code=@p2) "
                        + "OR EXISTS (SELECT 1 FROM officer_locations ol JOIN field_officers fo ON fo.officer_code=ol.officer_code "
                        + "WHERE fo.anchor_id=@p1 AND ol.county_code=@p2)";
                break;
            case "LOCATION":
                sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM geo_villages v WHERE v.anchor_id=@p1 AND v.location_code=@p2) "
                        + "OR EXISTS (SELECT 1 FROM households h JOIN organizations o ON o.organization_code=h.organization_code "
                        + "WHERE o.anchor_id=@p1 AND (h.location_code=@p2 OR h.payam_code=@p2)) "
                        + "OR EXISTS (SELECT 1 FROM officer_locations ol JOIN field_officers fo ON fo.officer_code=ol.officer_code "
                        + "WHERE fo.anchor_id=@p1 AND (ol.location_code=@p2 OR ol.payam_code=@p2))";
                break;
            default:
                sql = "SELECT 1 WHERE EXISTS (SELECT 1 FROM households h JOIN organizations o ON o.organization_code=h.organization_code "
                        + "WHERE o.anchor_id=@p1 AND (h.village_code=@p2 OR h.boma_code=@p2)) "
                        + "OR EXISTS (SELECT 1 FROM officer_locations ol JOIN field_officers fo ON fo.officer_code=ol.officer_code "
                        + "WHERE fo.anchor_id=@p1 AND (ol.village_code=@p2 OR ol.boma_code=@p2))";
        }
        return pool.preparedQuery(sql).execute(Tuple.of(anchorId, code)).map(rows -> rows.size() > 0);
    }

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!canManage(payload)) {
            replyError(message, "Not authorised to manage locations");
            return;
        }
        String level = payload.getString("level", "");
        String table = tableForLevel(level);
        String codeColumn = codeColumnForLevel(level);
        Integer anchorId = anchorIdOf(payload);
        String code = payload.getString("code", "").trim();
        if (table == null || anchorId == null || code.isEmpty()) {
            replyError(message, "level and code are required");
            return;
        }

        hasDependents(level, anchorId, code)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(dependents -> {
                    if (dependents) {
                        replyError(message, "Deactivate it instead -- it still has locations, households or officer assignments under it");
                        return;
                    }
                    String sql = "DELETE FROM " + table + " WHERE anchor_id=@p1 AND " + codeColumn + "=@p2";
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
                });
    }
}
