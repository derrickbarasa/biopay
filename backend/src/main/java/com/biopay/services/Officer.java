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
import com.biopay.utilities.Passwords;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;
import com.biopay.utilities.TenantScope;

/**
 * Officers (the existing {@code field_officers} table -- the field agents who
 * log into the Android app). Scoped the same way as {@link Organization}:
 * an anchor admin can manage officers across every organisation, an
 * organisation admin only within their own {@code partnerCode}.
 */
public class Officer extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Officer =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_OFFICER", this::create);
        eventBus.consumer("UPDATE_OFFICER", this::update);
        eventBus.consumer("DELETE_OFFICER", this::delete);
        eventBus.consumer("TOGGLE_OFFICER_STATUS", this::toggleStatus);
        eventBus.consumer("ASSIGN_OFFICER_LOCATION", this::assignLocation);
        eventBus.consumer("GET_OFFICER", this::getOne);
        eventBus.consumer("GET_OFFICERS", this::retrieveAll);
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
        return TenantScope.managesOrganisations(payload);
    }

    /** The organisation an actor is allowed to act within; null (anchor) means "any". */
    private static String scopedPartnerCode(JsonObject payload, String requested) {
        if (isAnchor(payload)) {
            return requested;
        }
        return payload.getString("partnerCode", "");
    }

    /** The one designated cross-anchor operator (admin@biopay.com). */
    private static boolean isSystemAdmin(JsonObject payload) {
        return TenantScope.isSystemOwner(payload);
    }

    // ---- CREATE_OFFICER (anchor or organisation admin) ---------------------------

    private void create(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = scopedPartnerCode(payload, payload.getString("organisationCode", ""));
        String firstName = payload.getString("firstName", "").trim();
        String lastName = payload.getString("lastName", "").trim();
        String email = payload.getString("email", "").trim().toLowerCase();
        Integer anchorId = TenantScope.anchorId(payload);

        if (partnerCode.isEmpty() || email.isEmpty() || firstName.isEmpty()) {
            replyError(message, "organisationCode, firstName and email are required");
            return;
        }

        String tempPassword = Utilities.generateRandomPassword(10);
        String hash = Passwords.hash(tempPassword);
        int supervisorId = (int) (System.currentTimeMillis() % 1000000);

        String sql = "INSERT INTO field_officers (officer_code, username, email, password, firstname, lastname, "
                + "organization_code, role, active, anchor_id, must_change_password, created_by, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10, 1, @p11, GETDATE())";
        pool.preparedQuery("SELECT anchor_id FROM organizations WHERE organization_code=@p1 AND (@p2=1 OR anchor_id=@p3)")
                .execute(Tuple.of(partnerCode, isSystemAdmin(payload), anchorId))
                .compose(orgRows -> orgRows.size() == 0 ? io.vertx.core.Future.failedFuture("Organisation is outside your anchor")
                        : pool.preparedQuery(sql).execute(Tuple.of(supervisorId, email, email, hash, firstName, lastName,
                                partnerCode, 2, "1", Rows.intVal(orgRows.iterator().next(), "anchor_id"), payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() <= 0) {
                        replyError(message, "Failed to create officer. Email may already be registered");
                        return;
                    }
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Your BioPay Field Agent Account")
                            .put("msg", "Dear " + firstName + ",<br />Your temporary password is <strong>" + tempPassword
                                    + "</strong>. Please change it after first login on the BioPay app.")
                            .toString());
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Officer created successfully. Temporary password sent by email"));
                });
    }

    // ---- UPDATE_OFFICER ------------------------------------------------------------

    private void update(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();
        if (email.isEmpty()) {
            replyError(message, "email is required");
            return;
        }

        String sql = "UPDATE field_officers SET firstname=@p1, lastname=@p2, updated_at=GETDATE() WHERE email=@p3"
                + (isAnchor(payload) ? " AND (@p4=1 OR anchor_id=@p5)" : " AND organization_code=@p4");

        Tuple params = isAnchor(payload)
                ? Tuple.of(payload.getString("firstName", "").trim(), payload.getString("lastName", "").trim(), email,
                        isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(payload.getString("firstName", "").trim(), payload.getString("lastName", "").trim(), email, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Officer updated successfully"));
                    } else {
                        replyError(message, "Officer not found or not in your organisation");
                    }
                });
    }

    // ---- DELETE_OFFICER (soft delete: active=0) -------------------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();

        String sql = "UPDATE field_officers SET active=@p1, updated_at=GETDATE() WHERE email=@p2"
                + (isAnchor(payload) ? " AND (@p3=1 OR anchor_id=@p4)" : " AND organization_code=@p3");
        Tuple params = isAnchor(payload)
                ? Tuple.of("0", email, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of("0", email, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Officer deleted successfully"));
                    } else {
                        replyError(message, "Officer not found or not in your organisation");
                    }
                });
    }

    private void toggleStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();
        String active = payload.getInteger("active", 0) == 1 ? "1" : "0";
        if (email.isEmpty()) {
            replyError(message, "email is required");
            return;
        }
        String sql = "UPDATE field_officers SET active=@p1, updated_at=GETDATE() WHERE email=@p2"
                + (isAnchor(payload) ? " AND (@p3=1 OR anchor_id=@p4)" : " AND organization_code=@p3");
        Tuple params = isAnchor(payload) ? Tuple.of(active, email, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(active, email, payload.getString("partnerCode", ""));
        pool.preparedQuery(sql).execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() == 0) replyError(message, "Officer not found or not in your organisation");
                    else reply(message, new JsonObject().put("responseCode", "000")
                            .put("responseMessage", "1".equals(active) ? "Officer activated" : "Officer deactivated"));
                });
    }

    // ---- ASSIGN_OFFICER_LOCATION -----------------------------------------------------

    private void assignLocation(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer supervisorId = payload.getInteger("supervisorId");
        String bomaCode = payload.getString("bomaCode", "").trim();
        if (supervisorId == null || bomaCode.isEmpty()) {
            replyError(message, "supervisorId and bomaCode are required");
            return;
        }

        String sql = "INSERT INTO officer_locations (officer_code, state_code, county_code, payam_code, boma_code, assigned_by, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(supervisorId,
                        payload.getString("stateCode", ""),
                        payload.getString("countyCode", ""),
                        payload.getString("payamCode", ""),
                        bomaCode,
                        String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Location assigned successfully")));
    }

    // ---- GET_OFFICER --------------------------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();

        String sql = "SELECT * FROM field_officers WHERE email=@p1" + (isAnchor(payload) ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2");
        Tuple params = isAnchor(payload) ? Tuple.of(email, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(email, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Officer not found").put("results", new JsonArray()));
                        return;
                    }
                    Row r = rows.iterator().next();
                    fetchLocations(Rows.intVal(r, "id")).onComplete(locAr -> reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Officer found")
                            .put("results", new JsonArray().add(summary(r).put("locations", locAr.succeeded() ? locAr.result() : new JsonArray())))));
                });
    }

    // ---- GET_OFFICERS -----------------------------------------------------------

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String activeFilter = payload.getString("active", null);
        boolean systemAdmin = isSystemAdmin(payload);
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        // field_officers already carries anchor_id directly, so a plain anchor admin is
        // scoped to it with no join needed. @p4 is NULL for a system admin with no target
        // anchor chosen (browse everything), the requested target anchor once chosen, or
        // the caller's own anchor for an anchor admin.
        String sql = isAnchor(payload)
                ? "SELECT * FROM field_officers WHERE (@p1 IS NULL OR organization_code=@p1) AND (@p2 IS NULL OR active=@p2) "
                        + "AND (@p4 IS NULL OR anchor_id=@p4) ORDER BY created_at DESC"
                : "SELECT * FROM field_officers WHERE organization_code=@p1 AND (@p2 IS NULL OR active=@p2) ORDER BY created_at DESC";
        String organisationFilter = payload.getString("organisationCode", null);
        Tuple params = Tuple.of(isAnchor(payload) ? organisationFilter : payload.getString("partnerCode", ""), activeFilter);
        if (isAnchor(payload)) {
            params = params.addBoolean(systemAdmin).addInteger(anchorId);
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
                            .put("responseMessage", results.isEmpty() ? "No officers found" : "Officers found")
                            .put("results", results));
                });
    }

    private io.vertx.core.Future<JsonArray> fetchLocations(Integer supervisorId) {
        if (supervisorId == null) {
            return io.vertx.core.Future.succeededFuture(new JsonArray());
        }
        return pool.preparedQuery("SELECT * FROM officer_locations WHERE officer_code=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(supervisorId))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("stateCode", Rows.str(r, "state_code"))
                                .put("countyCode", Rows.str(r, "county_code"))
                                .put("payamCode", Rows.str(r, "payam_code"))
                                .put("bomaCode", Rows.str(r, "boma_code")));
                    }
                    return arr;
                })
                .recover(err -> io.vertx.core.Future.succeededFuture(new JsonArray()));
    }

    private static JsonObject summary(Row r) {
        return new JsonObject()
                .put("id", Rows.intVal(r, "id"))
                .put("email", Rows.str(r, "email"))
                .put("firstName", Rows.str(r, "firstname"))
                .put("lastName", Rows.str(r, "lastname"))
                .put("organisationCode", Rows.str(r, "organization_code"))
                .put("anchorId", Rows.intVal(r, "anchor_id"))
                .put("active", Rows.str(r, "active"))
                .put("createdAt", Rows.str(r, "created_at"))
                .put("updatedAt", Rows.str(r, "updated_at"));
    }
}
