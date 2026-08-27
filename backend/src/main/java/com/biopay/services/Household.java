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
import com.biopay.utilities.FileStore;
import com.biopay.utilities.Logging;
import com.biopay.utilities.OrgModules;
import com.biopay.utilities.QrSupport;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;
import com.biopay.utilities.TenantScope;

/**
 * Households and their alternates -- the beneficiary registry. Every read
 * and write is scoped to {@code partnerCode} unless the caller is an anchor
 * admin (see {@link #scopedPartnerCode}), matching {@link Organization} and
 * {@link Officer}.
 */
public class Household extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Household =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_HOUSEHOLD", this::create);
        eventBus.consumer("UPDATE_HOUSEHOLD", this::update);
        eventBus.consumer("DELETE_HOUSEHOLD", this::delete);
        eventBus.consumer("GET_HOUSEHOLD", this::getOne);
        eventBus.consumer("GET_HOUSEHOLDS", this::retrieveAll);
        eventBus.consumer("GET_HOUSEHOLD_HISTORY", this::history);
        eventBus.consumer("GET_HOUSEHOLD_VOUCHER", this::voucher);
        eventBus.consumer("CHECK_HOUSEHOLD_DUPLICATE", this::checkDuplicate);
        eventBus.consumer("BULK_UPLOAD_HOUSEHOLDS", this::bulkUpload);
        eventBus.consumer("SET_HOUSEHOLD_REVIEW_STATUS", this::setReviewStatus);

        eventBus.consumer("CREATE_ALTERNATE", this::createAlternate);
        eventBus.consumer("UPDATE_ALTERNATE", this::updateAlternate);
        eventBus.consumer("DELETE_ALTERNATE", this::deleteAlternate);
        eventBus.consumer("GET_ALTERNATES", this::retrieveAlternates);
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

    /** The one designated cross-anchor operator (admin@biopay.com). */
    private static boolean isSystemAdmin(JsonObject payload) {
        return TenantScope.isSystemOwner(payload);
    }

    /** organisationCode requested by the caller, constrained to their own scope unless anchor. */
    private static String scopedPartnerCode(JsonObject payload) {
        if (isAnchor(payload)) {
            return payload.getString("organisationCode", null);
        }
        return payload.getString("partnerCode", "");
    }

    /** Resolves organisationCode's real anchor server-side rather than trusting whatever
     *  anchorId the client sent -- fails if the organisation doesn't exist or (for an Anchor
     *  Administrator) isn't inside their own anchor. The Super Admin may target any anchor. */
    private Future<Integer> resolveOrgAnchorId(JsonObject payload, String organisationCode) {
        return pool.preparedQuery("SELECT anchor_id FROM organizations WHERE organization_code=@p1 AND (@p2=1 OR anchor_id=@p3)")
                .execute(Tuple.of(organisationCode, isSystemAdmin(payload), TenantScope.anchorId(payload)))
                .compose(rows -> rows.size() == 0
                        ? Future.failedFuture("Organisation is outside your anchor")
                        : Future.succeededFuture(Rows.intVal(rows.iterator().next(), "anchor_id")));
    }

    // ---- CREATE_HOUSEHOLD --------------------------------------------------------

    private void create(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        if (partnerCode == null || partnerCode.isEmpty()) {
            replyError(message, "organisationCode is required");
            return;
        }
        String requestedHouseholdNumber = payload.getString("householdNumber", "").trim();
        final String householdNumber = requestedHouseholdNumber.isEmpty() ? Utilities.generateCode("HH") : requestedHouseholdNumber;

        String sql = "INSERT INTO households (officer_code, organization_code, household_number, beneficiary_type, "
                + "household_name, age, marital_status, spouse_name, id_number, phone_number, gender, "
                + "household_size, female_dependants, male_dependants, state_code, county_code, payam_code, boma_code, "
                + "latitude, longitude, status, created_by, created_at, updated_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,@p13,@p14,@p15,@p16,@p17,@p18,@p19,@p20,@p21,@p22,GETDATE(),GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")), partnerCode, householdNumber,
                        payload.getString("beneficiaryType", "1"),
                        payload.getString("householdName", "").trim(),
                        payload.getInteger("age"),
                        payload.getString("maritalStatus"),
                        payload.getString("spouseName"),
                        payload.getString("idNumber"),
                        payload.getString("phoneNumber"),
                        payload.getString("gender"),
                        payload.getInteger("householdSize"),
                        payload.getInteger("femaleDependants"),
                        payload.getInteger("maleDependants"),
                        payload.getString("stateCode"),
                        payload.getString("countyCode"),
                        payload.getString("payamCode"),
                        payload.getString("bomaCode"),
                        payload.getString("latitude"),
                        payload.getString("longitude"),
                        1,
                        String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "Household registered successfully")
                                .put("householdNumber", householdNumber));
                    } else {
                        replyError(message, "Failed to register household");
                    }
                });
    }

    // ---- UPDATE_HOUSEHOLD --------------------------------------------------------

    private void update(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();
        if (householdNumber.isEmpty()) {
            replyError(message, "householdNumber is required");
            return;
        }

        String scopeClause = isAnchor(payload)
                ? " AND (@p10=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p11))"
                : " AND organization_code=@p10";
        String sql = "UPDATE households SET household_name=@p1, age=@p2, marital_status=@p3, phone_number=@p4, "
                + "gender=@p5, household_size=@p6, boma_code=@p7, updated_by=@p8, updated_at=GETDATE() WHERE household_number=@p9"
                + scopeClause;

        Tuple params = Tuple.of(
                payload.getString("householdName"), payload.getInteger("age"), payload.getString("maritalStatus"),
                payload.getString("phoneNumber"), payload.getString("gender"), payload.getInteger("householdSize"),
                payload.getString("bomaCode"), String.valueOf(payload.getValue("actorId")), householdNumber);
        if (isAnchor(payload)) {
            params = params.addBoolean(isSystemAdmin(payload)).addInteger(TenantScope.anchorId(payload));
        } else {
            params = params.addString(payload.getString("partnerCode", ""));
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Household updated successfully"));
                    } else {
                        replyError(message, "Household not found or not in your organisation");
                    }
                });
    }

    // ---- DELETE_HOUSEHOLD (soft delete) ------------------------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();

        String sql = "UPDATE households SET status=0, updated_by=@p1, updated_at=GETDATE() WHERE household_number=@p2"
                + (isAnchor(payload) ? " AND (@p3=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p4))" : " AND organization_code=@p3");
        Tuple params = isAnchor(payload)
                ? Tuple.of(String.valueOf(payload.getValue("actorId")), householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(String.valueOf(payload.getValue("actorId")), householdNumber, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Household deleted successfully"));
                    } else {
                        replyError(message, "Household not found or not in your organisation");
                    }
                });
    }

    // ---- SET_HOUSEHOLD_REVIEW_STATUS (PENDING -> CHECKED -> APPROVED/REJECTED) -----
    // A rejection must carry a reason so the household detail page always has
    // something to show the officer who registered it.

    private static final java.util.Set<String> REVIEW_STATUSES =
            java.util.Set.of("PENDING", "CHECKED", "APPROVED", "REJECTED");

    private void setReviewStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        // payload.getString(key, "") only substitutes the default when the key is entirely
        // absent -- a JSON body with the key explicitly present and null (which is exactly
        // how the Android client encodes an unset optional field) makes it return null, not
        // "", so .trim() must go through this null-safe helper instead of chaining directly.
        String householdNumber = strOrEmpty(payload.getString("householdNumber")).trim();
        String reviewStatus = strOrEmpty(payload.getString("reviewStatus")).trim().toUpperCase();
        String rejectionReason = strOrEmpty(payload.getString("rejectionReason")).trim();

        if (householdNumber.isEmpty() || !REVIEW_STATUSES.contains(reviewStatus)) {
            replyError(message, "householdNumber and a valid reviewStatus (PENDING, CHECKED, APPROVED, REJECTED) are required");
            return;
        }
        if ("REJECTED".equals(reviewStatus) && rejectionReason.isEmpty()) {
            replyError(message, "A reason is required when rejecting a household");
            return;
        }

        String sql = "UPDATE households SET review_status=@p1, "
                + "rejection_reason=@p2, updated_by=@p3, updated_at=GETDATE() WHERE household_number=@p4"
                + (isAnchor(payload) ? " AND (@p5=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p6))" : " AND organization_code=@p5");
        Tuple params = Tuple.of(reviewStatus, "REJECTED".equals(reviewStatus) ? rejectionReason : null,
                String.valueOf(payload.getValue("actorId")), householdNumber);
        if (isAnchor(payload)) {
            params = params.addBoolean(isSystemAdmin(payload)).addInteger(TenantScope.anchorId(payload));
        } else {
            params = params.addString(payload.getString("partnerCode", ""));
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Household review status updated"));
                    } else {
                        replyError(message, "Household not found or not in your organisation");
                    }
                });
    }

    // ---- GET_HOUSEHOLD (with alternate/fingerprint/image status) ----------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();

        String sql = "SELECT * FROM households WHERE household_number=@p1" + (isAnchor(payload)
                ? " AND (@p2=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p3))"
                : " AND organization_code=@p2");
        Tuple params = isAnchor(payload)
                ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(householdNumber, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Household not found").put("results", new JsonArray()));
                        return;
                    }
                    Row r = rows.iterator().next();
                    Future<Integer> alternatesCount = countWhere("alternates", "household_number", householdNumber);
                    Future<Integer> fingerprintsCount = countWhere("fingerprints", "beneficiary_id", householdNumber);
                    Future<JsonArray> imageList = imageUrls(householdNumber);

                    Future.all(alternatesCount, fingerprintsCount, imageList).onComplete(ar -> {
                        JsonObject household = summary(r);
                        if (ar.succeeded()) {
                            JsonArray images = ar.result().resultAt(2);
                            household.put("alternatesCount", ar.result().resultAt(0))
                                    .put("fingerprintStatus", ((Integer) ar.result().resultAt(1)) > 0 ? "ENROLLED" : "PENDING")
                                    .put("imageStatus", images.isEmpty() ? "PENDING" : "UPLOADED")
                                    .put("images", images);
                        }
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "Household found")
                                .put("results", new JsonArray().add(household)));
                    });
                });
    }

    private Future<Integer> countWhere(String table, String column, String value) {
        return pool.preparedQuery("SELECT COUNT(*) AS cnt FROM " + table + " WHERE " + column + "=@p1")
                .execute(Tuple.of(value))
                .map(rows -> rows.size() == 0 ? 0 : Rows.intVal(rows.iterator().next(), "cnt"))
                .recover(err -> Future.succeededFuture(0));
    }

    /** Authenticated file URLs for a household's captured photos (served via /files/:filename). */
    private Future<JsonArray> imageUrls(String householdNumber) {
        return pool.preparedQuery("SELECT photo_url FROM images WHERE beneficiary_id=@p1 AND status=1 ORDER BY created_at DESC")
                .execute(Tuple.of(householdNumber))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        String f = Rows.str(r, "photo_url");
                        if (f != null && !f.isEmpty()) {
                            arr.add("/biopay/api/v1/files/" + f);
                        }
                    }
                    return arr;
                })
                .recover(err -> Future.succeededFuture(new JsonArray()));
    }

    // ---- GET_HOUSEHOLD_HISTORY (payment records + audit trail + vouchers for one household) ----
    // Backs the household detail page's "audit history / when it was paid / voucher history"
    // sections. Vouchers are folded into this existing handler (rather than a new
    // GET_HOUSEHOLD_VOUCHERS code) since vouchers.household_number already exists
    // (005_locations_and_vouchers.sql) and this is the same one-household-number-in,
    // three-lists-out shape as the payments/audit pair already here.

    private void history(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();
        if (householdNumber.isEmpty()) {
            replyError(message, "householdNumber is required");
            return;
        }
        boolean anchor = isAnchor(payload);
        String partnerCode = payload.getString("partnerCode", "");

        String paymentSql = "SELECT id, amount, status, cycle, approved, created_at FROM payments "
                + "WHERE household_number=@p1" + (anchor ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2") + " ORDER BY created_at DESC";
        Tuple paymentParams = anchor ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(householdNumber, partnerCode);

        Future<JsonArray> paymentsFut = pool.preparedQuery(paymentSql)
                .execute(paymentParams)
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("id", Rows.intVal(r, "id"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.intVal(r, "status"))
                                .put("cycle", Rows.str(r, "cycle"))
                                .put("approved", Rows.intVal(r, "approved"))
                                .put("createdAt", Rows.str(r, "created_at")));
                    }
                    return arr;
                })
                .recover(err -> Future.succeededFuture(new JsonArray()));

        String auditSql = "SELECT action, entity_type, details, created_at FROM audit_logs "
                + "WHERE entity_id=@p1" + (anchor ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2") + " ORDER BY created_at DESC";
        Tuple auditParams = anchor ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(householdNumber, partnerCode);

        Future<JsonArray> auditFut = pool.preparedQuery(auditSql)
                .execute(auditParams)
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("action", Rows.str(r, "action"))
                                .put("entityType", Rows.str(r, "entity_type"))
                                .put("details", Rows.str(r, "details"))
                                .put("createdAt", Rows.str(r, "created_at")));
                    }
                    return arr;
                })
                .recover(err -> Future.succeededFuture(new JsonArray()));

        String voucherSql = "SELECT voucher_code, amount, status, purpose, expires_at, redeemed_at, created_at FROM vouchers "
                + "WHERE household_number=@p1" + (anchor ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2") + " ORDER BY created_at DESC";
        Tuple voucherParams = anchor ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(householdNumber, partnerCode);

        Future<JsonArray> vouchersFut = pool.preparedQuery(voucherSql)
                .execute(voucherParams)
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("voucherCode", Rows.str(r, "voucher_code"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.str(r, "status"))
                                .put("purpose", Rows.str(r, "purpose"))
                                .put("expiresAt", Rows.str(r, "expires_at"))
                                .put("redeemedAt", Rows.str(r, "redeemed_at"))
                                .put("createdAt", Rows.str(r, "created_at")));
                    }
                    return arr;
                })
                .recover(err -> Future.succeededFuture(new JsonArray()));

        Future.all(paymentsFut, auditFut, vouchersFut)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "OK")
                        .put("results", new JsonObject()
                                .put("payments", (JsonArray) cf.resultAt(0))
                                .put("events", (JsonArray) cf.resultAt(1))
                                .put("vouchers", (JsonArray) cf.resultAt(2)))));
    }

    // ---- GET_HOUSEHOLD_VOUCHER (self-contained printable payment voucher) -----------
    // Returns the household's full name, photo and a QR code (encoding the household
    // number) all inlined as data URIs, so the web dashboard can render and print a
    // voucher slip without any further authenticated asset fetches.

    private void voucher(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();
        if (householdNumber.isEmpty()) {
            replyError(message, "householdNumber is required");
            return;
        }
        boolean anchor = isAnchor(payload);
        String partnerCode = payload.getString("partnerCode", "");
        String sql = "SELECT household_name, organization_code FROM households WHERE household_number=@p1"
                + (anchor ? " AND (@p2=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p3))" : " AND organization_code=@p2");
        Tuple params = anchor ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(householdNumber, partnerCode);

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "999")
                                .put("responseMessage", "Household not found or not in your organisation"));
                        return;
                    }
                    Row r = rows.iterator().next();
                    String name = Rows.str(r, "household_name");
                    String org = Rows.str(r, "organization_code");

                    pool.preparedQuery("SELECT TOP 1 photo_url FROM images WHERE beneficiary_id=@p1 AND status=1 ORDER BY created_at DESC")
                            .execute(Tuple.of(householdNumber))
                            .onComplete(ar -> {
                                String photo = null;
                                if (ar.succeeded() && ar.result().size() > 0) {
                                    photo = inlineImage(Rows.str(ar.result().iterator().next(), "photo_url"));
                                }
                                String qr;
                                try {
                                    qr = QrSupport.dataUri(householdNumber);
                                } catch (Exception ex) {
                                    qr = null;
                                }
                                reply(message, new JsonObject()
                                        .put("responseCode", "000")
                                        .put("responseMessage", "OK")
                                        .put("results", new JsonObject()
                                                .put("householdNumber", householdNumber)
                                                .put("householdName", name)
                                                .put("organisationCode", org)
                                                .put("photo", photo)
                                                .put("qr", qr)));
                            });
                });
    }

    // ---- CHECK_HOUSEHOLD_DUPLICATE ---------------------------------------------------
    // Screens a would-be registration against existing households in the same
    // organisation on the product's stated match key -- same ID/document number
    // (strongest), same phone number, or same name in the same village -- and
    // returns candidate matches with the reason(s) each one matched. Advisory: the
    // caller decides whether to proceed (mirrors how the mobile app records a
    // duplicate flag rather than hard-blocking).

    private void checkDuplicate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        if (partnerCode == null || partnerCode.isEmpty()) {
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", new JsonArray()));
            return;
        }
        String name = emptyToNull(payload.getString("householdName", ""));
        String phone = emptyToNull(payload.getString("phoneNumber", ""));
        String idNumber = emptyToNull(payload.getString("idNumber", ""));
        // The chosen village narrows a name match (same name in a different village is far
        // weaker evidence). bomaCode is the village code, matching CREATE_HOUSEHOLD.
        String bomaCode = emptyToNull(payload.getString("bomaCode", ""));

        if (name == null && phone == null && idNumber == null) {
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", new JsonArray()));
            return;
        }

        String sql = "SELECT TOP 20 household_number, household_name, phone_number, id_number, boma_code FROM households "
                + "WHERE status=1 AND organization_code=@p1 AND ("
                + "(@p2 IS NOT NULL AND id_number=@p2) "
                + "OR (@p3 IS NOT NULL AND phone_number=@p3) "
                + "OR (@p4 IS NOT NULL AND household_name=@p4 AND (@p5 IS NULL OR boma_code=@p5))"
                + ")";

        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode, idNumber, phone, name, bomaCode))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        JsonArray reasons = new JsonArray();
                        if (idNumber != null && idNumber.equalsIgnoreCase(Rows.str(r, "id_number"))) {
                            reasons.add("Same ID/document number");
                        }
                        if (phone != null && phone.equalsIgnoreCase(Rows.str(r, "phone_number"))) {
                            reasons.add("Same phone number");
                        }
                        if (name != null && name.equalsIgnoreCase(Rows.str(r, "household_name"))) {
                            reasons.add(bomaCode != null && bomaCode.equalsIgnoreCase(Rows.str(r, "boma_code"))
                                    ? "Same name in the same village" : "Same name");
                        }
                        results.add(new JsonObject()
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("householdName", Rows.str(r, "household_name"))
                                .put("phoneNumber", Rows.str(r, "phone_number"))
                                .put("idNumber", Rows.str(r, "id_number"))
                                .put("bomaCode", Rows.str(r, "boma_code"))
                                .put("reasons", reasons));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No duplicates found" : "Possible duplicates found")
                            .put("results", results));
                });
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    /** Vert.x's {@code JsonObject.getString(key, def)} only falls back to {@code def} when the
     *  key is entirely absent -- an explicit JSON null (which is exactly how the Android client
     *  encodes an unset optional field) still comes back null. */
    private static String strOrEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Reads a stored image and returns it inlined as a data URI, or null if unavailable. */
    private static String inlineImage(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = FileStore.read(filename);
            String contentType = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    // ---- GET_HOUSEHOLDS (one filter per parameter: village/location/county/state,
    //      gender, status, plus a household name/number/ID lookup -- all server-side) ----

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean systemAdmin = isSystemAdmin(payload);
        // A plain anchor admin is only ever handed organisationCode values that belong to
        // their own anchor by GET_ORGANIZATIONS, but nothing stopped them (or a forged
        // request) from passing another anchor's code here -- the anchor_id join below
        // closes that instead of trusting the organization_code filter on its own.
        String partnerCode = scopedPartnerCode(payload);
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        String searchRaw = payload.getString("search", "").trim();
        String search = searchRaw.isEmpty() ? null : "%" + searchRaw + "%";
        String stateCode = payload.getString("stateCode", null);
        String countyCode = payload.getString("countyCode", null);
        String locationCode = payload.getString("locationCode", null);
        String villageCode = payload.getString("villageCode", null);
        String gender = payload.getString("gender", null);
        Integer status = payload.getInteger("status");
        String vulnerabilityStatus = payload.getString("vulnerabilityStatus", null);
        String legalStatus = payload.getString("legalStatus", null);
        String reviewStatus = payload.getString("reviewStatus", null);
        // Registration date range (the "time" filter). created_at is DATETIME; string
        // bounds are implicitly converted, same as Payment#retrieveAll's date_from filter.
        String dateFrom = payload.getString("dateFrom", null);
        String dateTo = payload.getString("dateTo", null);
        int page = Math.max(payload.getInteger("page", 1), 1);
        int pageSize = Math.min(Math.max(payload.getInteger("pageSize", 25), 1), 200);
        int offset = (page - 1) * pageSize;

        // Per-row "generated data" counts: how many vouchers this household has been
        // issued, and how many distinct payment cycles it appears in. Correlated
        // subqueries so a household with none still returns a 0 row (no join drops it).
        // anchorId (@p17) is NULL for a system admin who hasn't chosen a target anchor
        // (browse everything), the requested target anchor once EntryPoint has resolved
        // one for them, or the caller's own anchor for an anchor admin -- either way,
        // (@p17 IS NULL OR p.anchor_id=@p17) is the one rule that covers all three roles;
        // org/supervisor sessions stay pinned to their own organization_code exactly as before.
        String sql = "SELECT h.*, "
                + "(SELECT COUNT(*) FROM vouchers v WHERE v.household_number = h.household_number) AS voucher_count, "
                + "(SELECT COUNT(DISTINCT pay.cycle) FROM payments pay WHERE pay.household_number = h.household_number) AS payment_cycle_count "
                + "FROM households h JOIN organizations p ON p.organization_code = h.organization_code "
                + "WHERE (@p1 IS NULL OR h.organization_code=@p1) "
                + "AND (@p2 IS NULL OR h.household_name LIKE @p2 OR h.household_number LIKE @p2 OR h.id_number LIKE @p2) "
                + "AND (@p3 IS NULL OR h.state_code=@p3) AND (@p4 IS NULL OR h.county_code=@p4) "
                + "AND (@p5 IS NULL OR h.payam_code=@p5) AND (@p6 IS NULL OR h.boma_code=@p6) "
                + "AND (@p7 IS NULL OR h.gender=@p7) AND (@p8 IS NULL OR h.status=@p8) "
                + "AND (@p9 IS NULL OR h.vulnerability_status=@p9) AND (@p10 IS NULL OR h.legal_status=@p10) "
                + "AND (@p11 IS NULL OR h.created_at >= @p11) AND (@p12 IS NULL OR h.created_at <= @p12) "
                + "AND (@p15 IS NULL OR h.review_status=@p15) "
                + "AND (@p17 IS NULL OR p.anchor_id=@p17) "
                + "ORDER BY h.created_at DESC OFFSET @p13 ROWS FETCH NEXT @p14 ROWS ONLY";

        Tuple params = Tuple.of(partnerCode, search, stateCode, countyCode, locationCode, villageCode,
                gender, status, vulnerabilityStatus, legalStatus, dateFrom, dateTo, offset, pageSize)
                .addString(reviewStatus).addBoolean(systemAdmin).addInteger(anchorId);

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
                            .put("responseMessage", results.isEmpty() ? "No households found" : "Households found")
                            .put("page", page)
                            .put("pageSize", pageSize)
                            .put("results", results));
                });
    }

    private static JsonObject summary(Row r) {
        return new JsonObject()
                .put("householdNumber", Rows.str(r, "household_number"))
                .put("householdName", Rows.str(r, "household_name"))
                .put("organisationCode", Rows.str(r, "organization_code"))
                .put("beneficiaryType", Rows.str(r, "beneficiary_type"))
                .put("age", Rows.intVal(r, "age"))
                .put("gender", Rows.str(r, "gender"))
                .put("maritalStatus", Rows.str(r, "marital_status"))
                .put("spouseName", Rows.str(r, "spouse_name"))
                .put("idNumber", Rows.str(r, "id_number"))
                .put("phoneNumber", Rows.str(r, "phone_number"))
                .put("householdSize", Rows.intVal(r, "household_size"))
                .put("femaleDependants", Rows.intVal(r, "female_dependants"))
                .put("maleDependants", Rows.intVal(r, "male_dependants"))
                // vulnerability_status / legal_status arrive with migration 009; guard the
                // read so listing still works if the migration hasn't been applied yet.
                .put("vulnerabilityStatus", strSafe(r, "vulnerability_status"))
                .put("legalStatus", strSafe(r, "legal_status"))
                .put("reviewStatus", strSafe(r, "review_status"))
                .put("rejectionReason", strSafe(r, "rejection_reason"))
                .put("stateCode", Rows.str(r, "state_code"))
                .put("countyCode", Rows.str(r, "county_code"))
                .put("payamCode", Rows.str(r, "payam_code"))
                .put("bomaCode", Rows.str(r, "boma_code"))
                .put("latitude", Rows.str(r, "latitude"))
                .put("longitude", Rows.str(r, "longitude"))
                .put("status", Rows.intVal(r, "status"))
                // Generated-data counts -- only present on the GET_HOUSEHOLDS listing query,
                // absent on GET_HOUSEHOLD's SELECT *, so read them defensively.
                .put("voucherCount", intSafe(r, "voucher_count"))
                .put("paymentCycleCount", intSafe(r, "payment_cycle_count"))
                .put("createdAt", Rows.str(r, "created_at"))
                .put("updatedAt", Rows.str(r, "updated_at"));
    }

    /** Null-safe read of a column that may not exist on this row (e.g. pre-migration-009 columns). */
    private static String strSafe(Row r, String column) {
        return r.getColumnIndex(column) < 0 ? null : Rows.str(r, column);
    }

    /** Null-safe integer read of a column that may not exist on this row (e.g. listing-only aggregates). */
    private static Integer intSafe(Row r, String column) {
        return r.getColumnIndex(column) < 0 ? null : Rows.intVal(r, column);
    }

    // ---- BULK_UPLOAD_HOUSEHOLDS { villageCode, rows:[{householdName, age, gender, ...}] } ----
    // One CSV template upload = one village's batch (see the Households page "Bulk Upload"
    // flow); the village resolves to its full state/county/location chain here so callers
    // never have to know or trust anything beyond the village they picked.

    private void bulkUpload(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        String villageCode = payload.getString("villageCode", "").trim();
        JsonArray rows = payload.getJsonArray("rows", new JsonArray());

        if (partnerCode == null || partnerCode.isEmpty() || villageCode.isEmpty() || rows.isEmpty()) {
            replyError(message, "organisationCode, villageCode and at least one row are required");
            return;
        }
        if (rows.size() > 1000) {
            replyError(message, "A single upload is limited to 1000 households");
            return;
        }

        OrgModules.isEnabled(pool, partnerCode, OrgModules.HOUSEHOLDS)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(enabled -> {
                    if (!enabled) {
                        replyError(message, "Household registration is not enabled for this organisation");
                        return;
                    }
                    resolveOrgAnchorId(payload, partnerCode)
                            .onFailure(err -> replyError(message, err.getMessage()))
                            .onSuccess(resolvedAnchorId -> pool.preparedQuery("SELECT * FROM geo_villages WHERE anchor_id=@p1 AND village_code=@p2 AND status=1")
                            .execute(Tuple.of(resolvedAnchorId, villageCode))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(villageRows -> {
                                if (villageRows.size() == 0) {
                                    replyError(message, "Village not found in your organisation's location list");
                                    return;
                                }
                                Row village = villageRows.iterator().next();
                                processUploadRow(message, rows, 0, partnerCode, village, payload.getValue("actorId"),
                                        payload.getString("fileName"), new JsonArray(), new JsonArray());
                            }));
                });
    }

    private void processUploadRow(Message<Object> message, JsonArray rows, int index, String partnerCode, Row village,
            Object actorId, String fileName, JsonArray created, JsonArray errors) {
        if (index >= rows.size()) {
            finishUpload(message, partnerCode, village, fileName, actorId, rows.size(), created, errors);
            return;
        }

        JsonObject row = rows.getJsonObject(index);
        String householdName = row.getString("householdName", "").trim();
        if (householdName.isEmpty()) {
            errors.add(new JsonObject().put("row", index + 1).put("message", "householdName is required"));
            processUploadRow(message, rows, index + 1, partnerCode, village, actorId, fileName, created, errors);
            return;
        }
        String householdNumber = Utilities.generateCode("HH");

        String sql = "INSERT INTO households (officer_code, organization_code, household_number, beneficiary_type, "
                + "household_name, age, marital_status, spouse_name, id_number, phone_number, gender, "
                + "household_size, female_dependants, male_dependants, state_code, county_code, payam_code, boma_code, "
                + "status, created_by, created_at, updated_at) "
                + "VALUES (@p1,@p2,@p3,'1',@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,@p13,@p14,@p15,@p16,@p17,1,@p18,GETDATE(),GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(actorId), partnerCode, householdNumber, householdName,
                        row.getInteger("age"), row.getString("maritalStatus"), row.getString("spouseName"),
                        row.getString("idNumber"), row.getString("phoneNumber"), row.getString("gender"),
                        row.getInteger("householdSize"), row.getInteger("femaleDependants"), row.getInteger("maleDependants"),
                        Rows.str(village, "state_code"), Rows.str(village, "county_code"),
                        Rows.str(village, "location_code"), Rows.str(village, "village_code"),
                        String.valueOf(actorId)))
                .onComplete(ar -> {
                    if (ar.succeeded() && ar.result().rowCount() > 0) {
                        created.add(new JsonObject().put("householdNumber", householdNumber).put("householdName", householdName));
                    } else {
                        errors.add(new JsonObject().put("row", index + 1).put("message", "Failed to register " + householdName));
                    }
                    processUploadRow(message, rows, index + 1, partnerCode, village, actorId, fileName, created, errors);
                });
    }

    private void finishUpload(Message<Object> message, String partnerCode, Row village, String fileName,
            Object actorId, int totalRows, JsonArray created, JsonArray errors) {
        String sql = "INSERT INTO household_upload_batches (organization_code, village_code, file_name, total_rows, "
                + "success_count, failure_count, errors, created_by, created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode, Rows.str(village, "village_code"), fileName, totalRows,
                        created.size(), errors.size(), errors.encode(), String.valueOf(actorId)))
                .onComplete(ar -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Bulk upload complete")
                        .put("successCount", created.size())
                        .put("failureCount", errors.size())
                        .put("created", created)
                        .put("errors", errors)));
    }

    // ---- CREATE_ALTERNATE --------------------------------------------------------

    private void createAlternate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        if (householdNumber.isEmpty()) {
            replyError(message, "householdNumber is required");
            return;
        }
        String alternateNumber = Utilities.generateCode("ALT");

        String sql = "INSERT INTO alternates (officer_code, household_number, organization_code, alternate_number, "
                + "alternate_name, relationship, age, id_number, phone_number, gender, status, created_by, created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")), householdNumber, partnerCode, alternateNumber,
                        payload.getString("alternateName", "").trim(),
                        payload.getString("relationship"),
                        payload.getInteger("age"),
                        payload.getString("idNumber"),
                        payload.getString("phoneNumber"),
                        payload.getString("gender"),
                        1,
                        String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "Alternate added successfully")
                                .put("alternateNumber", alternateNumber));
                    } else {
                        replyError(message, "Failed to add alternate");
                    }
                });
    }

    // ---- UPDATE_ALTERNATE --------------------------------------------------------

    private void updateAlternate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String alternateNumber = payload.getString("alternateNumber", "").trim();
        String scopeClause = isAnchor(payload)
                ? " AND (@p6=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p7))"
                : " AND organization_code=@p6";

        String sql = "UPDATE alternates SET alternate_name=@p1, relationship=@p2, phone_number=@p3, gender=@p4 "
                + "WHERE alternate_number=@p5" + scopeClause;
        Tuple params = Tuple.of(
                payload.getString("alternateName"), payload.getString("relationship"),
                payload.getString("phoneNumber"), payload.getString("gender"), alternateNumber);
        if (isAnchor(payload)) {
            params = params.addBoolean(isSystemAdmin(payload)).addInteger(TenantScope.anchorId(payload));
        } else {
            params = params.addString(payload.getString("partnerCode", ""));
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Alternate updated successfully"));
                    } else {
                        replyError(message, "Alternate not found or not in your organisation");
                    }
                });
    }

    // ---- DELETE_ALTERNATE --------------------------------------------------------

    private void deleteAlternate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String alternateNumber = payload.getString("alternateNumber", "").trim();

        String sql = "UPDATE alternates SET status=0 WHERE alternate_number=@p1" + (isAnchor(payload)
                ? " AND (@p2=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p3))"
                : " AND organization_code=@p2");
        Tuple params = isAnchor(payload)
                ? Tuple.of(alternateNumber, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(alternateNumber, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Alternate deleted successfully"));
                    } else {
                        replyError(message, "Alternate not found or not in your organisation");
                    }
                });
    }

    // ---- GET_ALTERNATES (by household) -------------------------------------------
    // Each alternate carries its own `images` gallery -- the images table's beneficiary_id
    // is already a generic beneficiary key (household_number OR alternate_number, per the
    // mobile beneficiaryType convention: 1 = household head, 2 = alternate), so no schema
    // change is needed to look images up by alternate_number the same way GET_HOUSEHOLD
    // already does by household_number (see #imageUrls).

    private void retrieveAlternates(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String householdNumber = payload.getString("householdNumber", "").trim();

        String sql = "SELECT * FROM alternates WHERE household_number=@p1" + (isAnchor(payload)
                ? " AND (@p2=1 OR organization_code IN (SELECT organization_code FROM organizations WHERE anchor_id=@p3))"
                : " AND organization_code=@p2") + " ORDER BY alternate_rank";
        Tuple params = isAnchor(payload)
                ? Tuple.of(householdNumber, isSystemAdmin(payload), TenantScope.anchorId(payload))
                : Tuple.of(householdNumber, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    java.util.List<Row> rowList = new java.util.ArrayList<>();
                    for (Row r : rows) {
                        rowList.add(r);
                    }
                    java.util.List<Future<?>> imageFutures = new java.util.ArrayList<>();
                    for (Row r : rowList) {
                        imageFutures.add(imageUrls(Rows.str(r, "alternate_number")));
                    }
                    Future.all(imageFutures).onComplete(ar -> {
                        JsonArray results = new JsonArray();
                        for (int i = 0; i < rowList.size(); i++) {
                            Row r = rowList.get(i);
                            JsonArray images = ar.succeeded() ? (JsonArray) ar.result().resultAt(i) : new JsonArray();
                            results.add(new JsonObject()
                                    .put("alternateNumber", Rows.str(r, "alternate_number"))
                                    .put("alternateName", Rows.str(r, "alternate_name"))
                                    .put("relationship", Rows.str(r, "relationship"))
                                    .put("age", Rows.intVal(r, "age"))
                                    .put("phoneNumber", Rows.str(r, "phone_number"))
                                    .put("gender", Rows.str(r, "gender"))
                                    .put("status", Rows.intVal(r, "status"))
                                    .put("images", images));
                        }
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", results.isEmpty() ? "No alternates found" : "Alternates found")
                                .put("results", results));
                    });
                });
    }
}
