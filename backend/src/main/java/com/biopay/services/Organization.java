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
import io.vertx.sqlclient.Tuple;
import com.biopay.databases.Datasource;
import com.biopay.utilities.Logging;
import com.biopay.utilities.OrgModules;
import com.biopay.utilities.Rows;

/**
 * Organisations (the existing {@code partners} table -- see the 001
 * migration note on naming). One processingCode per action, dispatched from
 * the JWT-protected {@code /biopay/api/v1/req} route; every handler here
 * re-checks {@code actorRole} because the route only proves who the caller
 * is, not what they're allowed to do.
 */
public class Organization extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Organization =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_ORGANIZATION", this::create);
        eventBus.consumer("UPDATE_ORGANIZATION", this::update);
        eventBus.consumer("DELETE_ORGANIZATION", this::delete);
        eventBus.consumer("TOGGLE_ORGANIZATION_STATUS", this::toggleStatus);
        eventBus.consumer("GET_ORGANIZATION", this::getOne);
        eventBus.consumer("GET_ORGANIZATIONS", this::retrieveAll);
        eventBus.consumer("GET_ORGANIZATION_MODULES", this::getModules);
        eventBus.consumer("UPDATE_ORGANIZATION_MODULES", this::updateModules);
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

    private static boolean isAnchor(JsonObject payload) {
        return "ANCHOR".equalsIgnoreCase(payload.getString("actorRole", ""));
    }

    /** The one designated cross-anchor operator (admin@biopay.com) -- sees every anchor's
     *  organisations instead of being scoped to just their own. Orthogonal to actorRole:
     *  a system admin is still an ANCHOR-scope user for every permission check below. */
    private static boolean isSystemAdmin(JsonObject payload) {
        return payload.getBoolean("systemAdmin", false);
    }

    /** Vert.x's {@code JsonObject.getString(key, def)} only falls back to {@code def} when the
     *  key is entirely absent -- an explicit JSON null still comes back null. */
    private static String strOrEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---- CREATE_ORGANIZATION (anchor only) -------------------------------------

    private void create(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can create organisations");
            return;
        }

        String partnerId = payload.getString("organisationCode", "").trim();
        String name = payload.getString("name", "").trim();
        String authorisedName = payload.getString("authorisedName", "").trim();
        String authorisedEmail = payload.getString("authorisedEmail", "").trim();
        String authorisedContact = payload.getString("authorisedContact", "").trim();
        String address = payload.getString("address", "").trim();
        // payload.getString(key, def) only substitutes def when the key is entirely absent --
        // an explicit JSON null (e.g. a cleared Vuetify select, or the Android client's encoding
        // of an unset optional field) still comes back null, so these go through strOrEmpty first.
        String country = strOrEmpty(payload.getString("country")).trim();
        String verificationMethod = strOrEmpty(payload.getString("verificationMethod")).trim().toUpperCase();
        if (verificationMethod.isEmpty()) {
            verificationMethod = "BIOMETRIC";
        }
        Object anchorIdVal = payload.getValue("anchorId");
        JsonArray modules = payload.getJsonArray("modules", new JsonArray());

        if (partnerId.isEmpty() || name.isEmpty() || anchorIdVal == null) {
            replyError(message, "organisationCode and name are required");
            return;
        }
        if (!"BIOMETRIC".equals(verificationMethod) && !"FACIAL".equals(verificationMethod)) {
            replyError(message, "verificationMethod must be BIOMETRIC or FACIAL");
            return;
        }
        if (modules.isEmpty()) {
            replyError(message, "Select at least one module for this organisation");
            return;
        }
        for (Object m : modules) {
            if (!OrgModules.isValid(String.valueOf(m))) {
                replyError(message, "Unknown module: " + m);
                return;
            }
        }

        String sql = "INSERT INTO partners (partner_id, name, types, authorised_name, authorised_email, "
                + "authorised_contact, address, country, verification_method, anchor_id, status, created_by, created_at, updated_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10, @p11, @p12, GETDATE(), GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerId, name, "1", authorisedName, authorisedEmail, authorisedContact,
                        address, country.isEmpty() ? null : country, verificationMethod,
                        Integer.parseInt(anchorIdVal.toString()), 1, payload.getValue("actorId")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() == 0) {
                        replyError(message, "Failed to create organisation. Code may already exist");
                        return;
                    }
                    saveModules(partnerId, modules)
                            .onComplete(ar -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Organisation created successfully")));
                });
    }

    /** Replaces the full module set for an organisation (used on create and on UPDATE_ORGANIZATION_MODULES). */
    private Future<Void> saveModules(String partnerId, JsonArray modules) {
        return pool.preparedQuery("DELETE FROM organisation_modules WHERE partner_code=@p1")
                .execute(Tuple.of(partnerId))
                .compose(v -> insertModulesFrom(partnerId, modules, 0))
                .recover(err -> {
                    Logging.applicationLog(Logging.logPreString() + "saveModules failed. " + err.getMessage() + "\n\n", "", 3);
                    return Future.succeededFuture();
                });
    }

    private Future<Void> insertModulesFrom(String partnerId, JsonArray modules, int index) {
        if (index >= modules.size()) {
            return Future.succeededFuture();
        }
        return pool.preparedQuery(
                        "INSERT INTO organisation_modules (partner_code, module_code, enabled, created_at) "
                                + "VALUES (@p1, @p2, 1, GETDATE())")
                .execute(Tuple.of(partnerId, String.valueOf(modules.getValue(index)).toUpperCase()))
                .compose(v -> insertModulesFrom(partnerId, modules, index + 1));
    }

    // ---- GET_ORGANIZATION_MODULES -------------------------------------------------

    private void getModules(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerId = payload.getString("organisationCode", "").trim();
        if (!isAnchor(payload) && !partnerId.equals(payload.getString("partnerCode", ""))) {
            replyError(message, "Not authorised to view this organisation's modules");
            return;
        }
        OrgModules.enabledForAsArray(pool, partnerId)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(modules -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "OK")
                        .put("results", modules)));
    }

    // ---- UPDATE_ORGANIZATION_MODULES (anchor only) ---------------------------------

    private void updateModules(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can change organisation modules");
            return;
        }
        String partnerId = payload.getString("organisationCode", "").trim();
        JsonArray modules = payload.getJsonArray("modules", new JsonArray());
        if (partnerId.isEmpty() || modules.isEmpty()) {
            replyError(message, "organisationCode and at least one module are required");
            return;
        }
        for (Object m : modules) {
            if (!OrgModules.isValid(String.valueOf(m))) {
                replyError(message, "Unknown module: " + m);
                return;
            }
        }
        saveModules(partnerId, modules)
                .onComplete(ar -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Organisation modules updated")));
    }

    // ---- UPDATE_ORGANIZATION (anchor only) --------------------------------------

    private void update(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can update organisations");
            return;
        }

        String partnerId = payload.getString("organisationCode", "").trim();
        if (partnerId.isEmpty()) {
            replyError(message, "organisationCode is required");
            return;
        }

        String verificationMethod = strOrEmpty(payload.getString("verificationMethod")).trim().toUpperCase();
        if (!verificationMethod.isEmpty() && !"BIOMETRIC".equals(verificationMethod) && !"FACIAL".equals(verificationMethod)) {
            replyError(message, "verificationMethod must be BIOMETRIC or FACIAL");
            return;
        }

        String sql = "UPDATE partners SET name=@p1, authorised_name=@p2, authorised_email=@p3, "
                + "authorised_contact=@p4, address=@p5, country=@p6, "
                + "verification_method=COALESCE(NULLIF(@p7,''), verification_method), updated_at=GETDATE() WHERE partner_id=@p8";
        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        payload.getString("name", "").trim(),
                        payload.getString("authorisedName", "").trim(),
                        payload.getString("authorisedEmail", "").trim(),
                        payload.getString("authorisedContact", "").trim(),
                        payload.getString("address", "").trim(),
                        strOrEmpty(payload.getString("country")).trim(),
                        verificationMethod,
                        partnerId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Organisation updated successfully"));
                    } else {
                        replyError(message, "Organisation not found");
                    }
                });
    }

    // ---- DELETE_ORGANIZATION (anchor only, soft delete) -------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can delete organisations");
            return;
        }
        String partnerId = payload.getString("organisationCode", "").trim();

        pool.preparedQuery("UPDATE partners SET status=0, updated_at=GETDATE() WHERE partner_id=@p1")
                .execute(Tuple.of(partnerId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Organisation deleted successfully"));
                    } else {
                        replyError(message, "Organisation not found");
                    }
                });
    }

    // ---- TOGGLE_ORGANIZATION_STATUS (anchor only, activate/deactivate) ----------

    private void toggleStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can change organisation status");
            return;
        }
        String partnerId = payload.getString("organisationCode", "").trim();
        Integer status = payload.getInteger("status");
        if (partnerId.isEmpty() || status == null) {
            replyError(message, "organisationCode and status are required");
            return;
        }

        pool.preparedQuery("UPDATE partners SET status=@p1, updated_at=GETDATE() WHERE partner_id=@p2")
                .execute(Tuple.of(status, partnerId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Organisation status updated"));
                    } else {
                        replyError(message, "Organisation not found");
                    }
                });
    }

    // ---- GET_ORGANIZATION --------------------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerId = payload.getString("organisationCode", "").trim();

        // An organisation user may only ever look up their own organisation. A plain anchor
        // admin is implicitly scoped to their own anchor's orgs by GET_ORGANIZATIONS already
        // filtering the list they picked from; the system admin can look up any of them.
        if (!isAnchor(payload) && !isSystemAdmin(payload) && !partnerId.equals(payload.getString("partnerCode", ""))) {
            replyError(message, "Not authorised to view this organisation");
            return;
        }

        pool.preparedQuery("SELECT * FROM partners WHERE partner_id=@p1")
                .execute(Tuple.of(partnerId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Organisation not found").put("results", new JsonArray()));
                        return;
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Organisation found")
                            .put("results", new JsonArray().add(summary(rows.iterator().next()))));
                });
    }

    // ---- GET_ORGANIZATIONS -------------------------------------------------------

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());

        String sql;
        Tuple params;
        if (isSystemAdmin(payload)) {
            Integer status = payload.getInteger("status");
            sql = "SELECT * FROM partners WHERE (@p1 IS NULL OR status=@p1) ORDER BY created_at DESC";
            params = Tuple.of(status);
        } else if (isAnchor(payload)) {
            Object anchorId = payload.getValue("anchorId");
            Integer status = payload.getInteger("status");
            sql = "SELECT * FROM partners WHERE (@p1 IS NULL OR anchor_id=@p1) AND (@p2 IS NULL OR status=@p2) ORDER BY created_at DESC";
            params = Tuple.of(anchorId, status);
        } else {
            String partnerCode = payload.getString("partnerCode", "");
            sql = "SELECT * FROM partners WHERE partner_id=@p1";
            params = Tuple.of(partnerCode);
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(summary(r));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No organisations found" : "Organisations found")
                            .put("results", results));
                });
    }

    private static JsonObject summary(Row r) {
        return new JsonObject()
                .put("organisationCode", Rows.str(r, "partner_id"))
                .put("name", Rows.str(r, "name"))
                .put("authorisedName", Rows.str(r, "authorised_name"))
                .put("authorisedEmail", Rows.str(r, "authorised_email"))
                .put("authorisedContact", Rows.str(r, "authorised_contact"))
                .put("address", Rows.str(r, "address"))
                .put("country", Rows.str(r, "country"))
                .put("verificationMethod", Rows.str(r, "verification_method"))
                .put("anchorId", Rows.intVal(r, "anchor_id"))
                .put("status", Rows.intVal(r, "status"))
                .put("createdAt", Rows.str(r, "created_at"))
                .put("updatedAt", Rows.str(r, "updated_at"));
    }
}
