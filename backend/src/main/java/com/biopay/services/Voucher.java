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
import com.biopay.utilities.OrgModules;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;

/**
 * Voucher issuance and redemption -- the vouchers table (005_locations_and_vouchers.sql)
 * already carries the biometric-match fields (matched_fp, latitude, longitude,
 * redeemed_by_officer_id) a future field-agent redemption flow needs, mirroring
 * how {@link Payment} rows are matched; REDEEM_VOUCHER accepts them now as
 * optional so a manual web redemption today and a biometric mobile redemption
 * later use the same processing code. Gated by the VOUCHERS organisation
 * module (see {@link OrgModules}) on every write.
 */
public class Voucher extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Voucher =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("CREATE_VOUCHER", this::create);
        eventBus.consumer("BULK_ISSUE_VOUCHERS", this::bulkIssue);
        eventBus.consumer("GET_VOUCHERS", this::retrieveAll);
        eventBus.consumer("GET_VOUCHER", this::getOne);
        eventBus.consumer("VOID_VOUCHER", this::voidVoucher);
        eventBus.consumer("REDEEM_VOUCHER", this::redeem);
        eventBus.consumer("VOUCHER_SUMMARY", this::summary);
        eventBus.consumer("SYNC_VOUCHERS", this::syncVouchers);
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

    /** organisationCode requested by the caller, constrained to their own scope unless anchor. */
    private static String scopedPartnerCode(JsonObject payload) {
        if (isAnchor(payload)) {
            return payload.getString("organisationCode", null);
        }
        return payload.getString("partnerCode", "");
    }

    private static Integer anchorIdOf(JsonObject payload) {
        Object v = payload.getValue("anchorId");
        return v == null ? null : Integer.parseInt(v.toString());
    }

    /** The one designated cross-anchor operator (admin@biopay.com). */
    private static boolean isSystemAdmin(JsonObject payload) {
        return payload.getBoolean("systemAdmin", false);
    }

    // ---- CREATE_VOUCHER (issue a single voucher) -----------------------------------

    private void create(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        String householdNumber = payload.getString("householdNumber", "").trim();
        Double amount = payload.getDouble("amount");

        if (partnerCode == null || partnerCode.isEmpty() || householdNumber.isEmpty() || amount == null || amount <= 0) {
            replyError(message, "organisationCode, householdNumber and a positive amount are required");
            return;
        }

        OrgModules.isEnabled(pool, partnerCode, OrgModules.VOUCHERS)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(enabled -> {
                    if (!enabled) {
                        replyError(message, "Voucher redemption is not enabled for this organisation");
                        return;
                    }
                    insertVoucher(message, partnerCode, anchorIdOf(payload), householdNumber, amount,
                            payload.getString("purpose"), payload.getString("expiresAt"), payload.getValue("actorId"));
                });
    }

    private void insertVoucher(Message<Object> message, String partnerCode, Integer anchorId, String householdNumber,
            Double amount, String purpose, String expiresAt, Object actorId) {
        String voucherCode = Utilities.generateCode("VCH");
        String sql = "INSERT INTO vouchers (voucher_code, household_number, partner_code, anchor_id, amount, purpose, "
                + "status, expires_at, created_by, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, 'ISSUED', @p7, @p8, GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(voucherCode, householdNumber, partnerCode, anchorId, amount, purpose,
                        expiresAt, String.valueOf(actorId)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "Voucher issued successfully")
                                .put("voucherCode", voucherCode));
                    } else {
                        replyError(message, "Failed to issue voucher");
                    }
                });
    }

    // ---- BULK_ISSUE_VOUCHERS { organisationCode?, expiresAt?, purpose?, rows:[{householdNumber, amount}] } ----

    private void bulkIssue(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        Integer anchorId = anchorIdOf(payload);
        JsonArray rows = payload.getJsonArray("rows", new JsonArray());
        String purpose = payload.getString("purpose");
        String expiresAt = payload.getString("expiresAt");
        Object actorId = payload.getValue("actorId");

        if (partnerCode == null || partnerCode.isEmpty() || rows.isEmpty()) {
            replyError(message, "organisationCode and at least one row are required");
            return;
        }
        if (rows.size() > 500) {
            replyError(message, "A single bulk issue is limited to 500 vouchers");
            return;
        }

        OrgModules.isEnabled(pool, partnerCode, OrgModules.VOUCHERS)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(enabled -> {
                    if (!enabled) {
                        replyError(message, "Voucher redemption is not enabled for this organisation");
                        return;
                    }
                    processBulkRow(message, rows, 0, partnerCode, anchorId, purpose, expiresAt, actorId,
                            new JsonArray(), new JsonArray());
                });
    }

    private void processBulkRow(Message<Object> message, JsonArray rows, int index, String partnerCode, Integer anchorId,
            String purpose, String expiresAt, Object actorId, JsonArray issued, JsonArray errors) {
        if (index >= rows.size()) {
            reply(message, new JsonObject()
                    .put("responseCode", "000")
                    .put("responseMessage", "Bulk voucher issue complete")
                    .put("successCount", issued.size())
                    .put("failureCount", errors.size())
                    .put("issued", issued)
                    .put("errors", errors));
            return;
        }

        JsonObject row = rows.getJsonObject(index);
        String householdNumber = row.getString("householdNumber", "").trim();
        Double amount = row.getDouble("amount");
        if (householdNumber.isEmpty() || amount == null || amount <= 0) {
            errors.add(new JsonObject().put("row", index + 1).put("message", "householdNumber and a positive amount are required"));
            processBulkRow(message, rows, index + 1, partnerCode, anchorId, purpose, expiresAt, actorId, issued, errors);
            return;
        }

        String voucherCode = Utilities.generateCode("VCH");
        String sql = "INSERT INTO vouchers (voucher_code, household_number, partner_code, anchor_id, amount, purpose, "
                + "status, expires_at, created_by, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, 'ISSUED', @p7, @p8, GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(voucherCode, householdNumber, partnerCode, anchorId, amount, purpose, expiresAt, String.valueOf(actorId)))
                .onComplete(ar -> {
                    if (ar.succeeded() && ar.result().rowCount() > 0) {
                        issued.add(new JsonObject().put("householdNumber", householdNumber).put("voucherCode", voucherCode).put("amount", amount));
                    } else {
                        errors.add(new JsonObject().put("row", index + 1).put("message", "Failed to issue voucher for " + householdNumber));
                    }
                    processBulkRow(message, rows, index + 1, partnerCode, anchorId, purpose, expiresAt, actorId, issued, errors);
                });
    }

    // ---- GET_VOUCHERS (filters: status, householdNumber, page) --------------------

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean systemAdmin = isSystemAdmin(payload);
        String partnerCode = scopedPartnerCode(payload);
        Integer anchorId = anchorIdOf(payload);
        String status = payload.getString("status", null);
        String householdNumber = payload.getString("householdNumber", null);
        int page = Math.max(payload.getInteger("page", 1), 1);
        int pageSize = Math.min(Math.max(payload.getInteger("pageSize", 25), 1), 200);
        int offset = (page - 1) * pageSize;

        String sql = "SELECT v.* FROM vouchers v JOIN partners p ON p.partner_id = v.partner_code "
                + "WHERE (@p1 IS NULL OR v.partner_code=@p1) "
                + "AND (@p2 IS NULL OR v.status=@p2) AND (@p3 IS NULL OR v.household_number=@p3) "
                + "AND (@p6 = 1 OR p.anchor_id=@p7) "
                + "ORDER BY v.created_at DESC OFFSET @p4 ROWS FETCH NEXT @p5 ROWS ONLY";

        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode, status, householdNumber, offset, pageSize)
                        .addBoolean(systemAdmin).addInteger(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(summary(r));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No vouchers found" : "Vouchers found")
                            .put("page", page)
                            .put("pageSize", pageSize)
                            .put("results", results));
                });
    }

    // ---- GET_VOUCHER (by voucherCode) ----------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String voucherCode = payload.getString("voucherCode", "").trim();
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p2";

        pool.preparedQuery("SELECT * FROM vouchers WHERE voucher_code=@p1" + scopeClause)
                .execute(isAnchor(payload) ? Tuple.of(voucherCode) : Tuple.of(voucherCode, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Voucher not found").put("results", new JsonArray()));
                        return;
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Voucher found")
                            .put("results", new JsonArray().add(summary(rows.iterator().next()))));
                });
    }

    // ---- VOID_VOUCHER (only if still ISSUED) ---------------------------------------

    private void voidVoucher(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String voucherCode = payload.getString("voucherCode", "").trim();
        if (voucherCode.isEmpty()) {
            replyError(message, "voucherCode is required");
            return;
        }
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p2";
        String sql = "UPDATE vouchers SET status='VOID' WHERE voucher_code=@p1 AND status='ISSUED'" + scopeClause;
        pool.preparedQuery(sql)
                .execute(isAnchor(payload) ? Tuple.of(voucherCode) : Tuple.of(voucherCode, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Voucher voided"));
                    } else {
                        replyError(message, "Voucher not found, not in your organisation, or already redeemed/void");
                    }
                });
    }

    // ---- REDEEM_VOUCHER (manual web entry today; accepts biometric-match fields for a
    //      future mobile flow -- matchedFingerprint/latitude/longitude are optional) ----

    private void redeem(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String voucherCode = payload.getString("voucherCode", "").trim();
        if (voucherCode.isEmpty()) {
            replyError(message, "voucherCode is required");
            return;
        }
        if ("SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))
                && payload.getString("matchedFingerprint", "").isBlank()) {
            replyError(message, "A successful fingerprint verification is required for mobile redemption");
            return;
        }
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p6";
        Object actorId = payload.getValue("actorId");

        String sql = "UPDATE vouchers SET status='REDEEMED', redeemed_by_officer_id=@p1, redeemed_at=GETDATE(), "
                + "matched_fp=@p2, latitude=@p3, longitude=@p4 "
                + "WHERE voucher_code=@p5 AND status='ISSUED'" + scopeClause
                + " AND (expires_at IS NULL OR expires_at >= GETDATE())";
        Tuple params = Tuple.of(actorId, payload.getString("matchedFingerprint"), payload.getString("latitude"),
                payload.getString("longitude"), voucherCode);
        if (!isAnchor(payload)) {
            params = params.addString(payload.getString("partnerCode", ""));
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Voucher redeemed successfully"));
                    } else {
                        replyError(message, "Voucher not found, not issued, expired, or not in your organisation");
                    }
                });
    }

    // ---- SYNC_VOUCHERS (offline field app bundle) ---------------------------------

    private void syncVouchers(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!"SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            replyError(message, "Only a field officer can sync vouchers");
            return;
        }
        pool.preparedQuery("SELECT * FROM vouchers WHERE partner_code=@p1 AND status='ISSUED' "
                        + "AND (expires_at IS NULL OR expires_at>=GETDATE()) ORDER BY created_at DESC")
                .execute(Tuple.of(payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row row : rows) results.add(summary(row));
                    reply(message, new JsonObject().put("responseCode", "000")
                            .put("responseMessage", "Vouchers synced").put("results", results));
                });
    }

    // ---- VOUCHER_SUMMARY (Vouchers page KPIs) ---------------------------------------

    private void summary(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean systemAdmin = isSystemAdmin(payload);
        String partnerCode = scopedPartnerCode(payload);
        Integer anchorId = anchorIdOf(payload);

        String sql = "SELECT COUNT(*) AS cnt, ISNULL(SUM(v.amount), 0) AS total, "
                + "SUM(CASE WHEN v.status='ISSUED' THEN 1 ELSE 0 END) AS issuedCount, "
                + "ISNULL(SUM(CASE WHEN v.status='ISSUED' THEN v.amount ELSE 0 END), 0) AS issuedAmount, "
                + "SUM(CASE WHEN v.status='REDEEMED' THEN 1 ELSE 0 END) AS redeemedCount, "
                + "ISNULL(SUM(CASE WHEN v.status='REDEEMED' THEN v.amount ELSE 0 END), 0) AS redeemedAmount, "
                + "SUM(CASE WHEN v.status='VOID' THEN 1 ELSE 0 END) AS voidCount "
                + "FROM vouchers v JOIN partners p ON p.partner_id = v.partner_code "
                + "WHERE (@p1 IS NULL OR v.partner_code=@p1) AND (@p2 = 1 OR p.anchor_id=@p3)";

        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode).addBoolean(systemAdmin).addInteger(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    Row r = rows.iterator().next();
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "OK")
                            .put("results", new JsonObject()
                                    .put("totalCount", Rows.intVal(r, "cnt"))
                                    .put("totalAmount", Rows.dbl(r, "total"))
                                    .put("issuedCount", Rows.intVal(r, "issuedCount"))
                                    .put("issuedAmount", Rows.dbl(r, "issuedAmount"))
                                    .put("redeemedCount", Rows.intVal(r, "redeemedCount"))
                                    .put("redeemedAmount", Rows.dbl(r, "redeemedAmount"))
                                    .put("voidCount", Rows.intVal(r, "voidCount"))));
                });
    }

    private static JsonObject summary(Row r) {
        return new JsonObject()
                .put("voucherCode", Rows.str(r, "voucher_code"))
                .put("householdNumber", Rows.str(r, "household_number"))
                .put("organisationCode", Rows.str(r, "partner_code"))
                .put("amount", Rows.dbl(r, "amount"))
                .put("purpose", Rows.str(r, "purpose"))
                .put("status", Rows.str(r, "status"))
                .put("expiresAt", Rows.str(r, "expires_at"))
                .put("redeemedByOfficerId", Rows.intVal(r, "redeemed_by_officer_id"))
                .put("redeemedAt", Rows.str(r, "redeemed_at"))
                .put("matchedFingerprint", Rows.str(r, "matched_fp"))
                .put("latitude", Rows.str(r, "latitude"))
                .put("longitude", Rows.str(r, "longitude"))
                .put("createdAt", Rows.str(r, "created_at"));
    }
}
