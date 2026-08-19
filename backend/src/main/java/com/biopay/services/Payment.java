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
 * Payment line items -- generated in bulk by {@link Payroll#generate}, then
 * managed/inspected individually here (status updates, filtered listing,
 * dashboard-facing summary, deletion of a not-yet-disbursed row).
 */
public class Payment extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Payment =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("GET_PAYMENTS", this::retrieveAll);
        eventBus.consumer("GET_PAYMENT", this::getOne);
        eventBus.consumer("UPDATE_PAYMENT_STATUS", this::updateStatus);
        eventBus.consumer("DELETE_PAYMENT", this::delete);
        eventBus.consumer("PAYMENT_SUMMARY", this::summary);
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

    /** The one designated cross-anchor operator (admin@biopay.com). */
    private static boolean isSystemAdmin(JsonObject payload) {
        return payload.getBoolean("systemAdmin", false);
    }

    // ---- GET_PAYMENTS (filters: organisationCode, status, dateFrom/dateTo, page) --

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean systemAdmin = isSystemAdmin(payload);
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", null) : payload.getString("partnerCode", "");
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        Integer status = payload.getInteger("status");
        String dateFrom = payload.getString("dateFrom", null);
        String dateTo = payload.getString("dateTo", null);
        int page = Math.max(payload.getInteger("page", 1), 1);
        int pageSize = Math.min(Math.max(payload.getInteger("pageSize", 25), 1), 200);
        int offset = (page - 1) * pageSize;

        // Joined against partners.anchor_id (@p7/@p8) so a plain anchor admin can never see
        // another anchor's payments even with organisationCode left blank or forged; the
        // system admin (@p7=1) bypasses that join entirely.
        String sql = "SELECT pay.* FROM payments pay JOIN partners p ON p.partner_id = pay.partner_code "
                + "WHERE (@p1 IS NULL OR pay.partner_code=@p1) AND (@p2 IS NULL OR pay.status=@p2) "
                + "AND (@p3 IS NULL OR pay.date_from >= @p3) AND (@p4 IS NULL OR pay.date_from <= @p4) "
                + "AND (@p7 = 1 OR p.anchor_id=@p8) "
                + "ORDER BY pay.created_at DESC OFFSET @p5 ROWS FETCH NEXT @p6 ROWS ONLY";

        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode, status, dateFrom, dateTo, offset, pageSize)
                        .addBoolean(systemAdmin).addInteger(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(summaryRow(r));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No payments found" : "Payments found")
                            .put("page", page)
                            .put("pageSize", pageSize)
                            .put("results", results));
                });
    }

    // ---- GET_PAYMENT ---------------------------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer id = payload.getInteger("id");
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p2";

        pool.preparedQuery("SELECT * FROM payments WHERE id=@p1" + scopeClause)
                .execute(isAnchor(payload) ? Tuple.of(id) : Tuple.of(id, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payment not found").put("results", new JsonArray()));
                        return;
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Payment found")
                            .put("results", new JsonArray().add(summaryRow(rows.iterator().next()))));
                });
    }

    // ---- UPDATE_PAYMENT_STATUS (e.g. mark verified/paid after biometric match) ----

    private void updateStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer id = payload.getInteger("id");
        Integer status = payload.getInteger("status");
        if (id == null || status == null) {
            replyError(message, "id and status are required");
            return;
        }
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p4";
        Object actorId = payload.getValue("actorId");
        String sql = "UPDATE payments SET status=@p1, verified_by=@p2, verified_at=GETDATE(), updated_at=GETDATE() WHERE id=@p3" + scopeClause;
        Tuple params = isAnchor(payload)
                ? Tuple.of(status, actorId, id)
                : Tuple.of(status, actorId, id, payload.getString("partnerCode", ""));

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payment status updated"));
                    } else {
                        replyError(message, "Payment not found or not in your organisation");
                    }
                });
    }

    // ---- DELETE_PAYMENT (only if not yet disbursed) --------------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer id = payload.getInteger("id");
        String scopeClause = isAnchor(payload) ? "" : " AND partner_code=@p2";

        pool.preparedQuery("DELETE FROM payments WHERE id=@p1 AND status=0" + scopeClause)
                .execute(isAnchor(payload) ? Tuple.of(id) : Tuple.of(id, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payment deleted"));
                    } else {
                        replyError(message, "Only a pending payment in your organisation can be deleted");
                    }
                });
    }

    // ---- PAYMENT_SUMMARY (dashboard/payments-page KPIs) -----------------------------

    private void summary(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean systemAdmin = isSystemAdmin(payload);
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", null) : payload.getString("partnerCode", "");
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        String sql = "SELECT COUNT(*) AS cnt, ISNULL(SUM(pay.amount), 0) AS total, "
                + "SUM(CASE WHEN pay.status=0 THEN 1 ELSE 0 END) AS pendingCount, "
                + "SUM(CASE WHEN pay.status=1 THEN 1 ELSE 0 END) AS paidCount "
                + "FROM payments pay JOIN partners p ON p.partner_id = pay.partner_code "
                + "WHERE (@p1 IS NULL OR pay.partner_code=@p1) AND (@p2 = 1 OR p.anchor_id=@p3)";

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
                                    .put("pendingCount", Rows.intVal(r, "pendingCount"))
                                    .put("paidCount", Rows.intVal(r, "paidCount"))));
                });
    }

    private static JsonObject summaryRow(Row r) {
        return new JsonObject()
                .put("id", Rows.intVal(r, "id"))
                .put("householdNumber", Rows.str(r, "household_number"))
                .put("householdName", Rows.str(r, "household_name"))
                .put("gender", Rows.str(r, "gender"))
                .put("organisationCode", Rows.str(r, "partner_code"))
                .put("bomaCode", Rows.str(r, "boma_code"))
                .put("amount", Rows.dbl(r, "amount"))
                .put("status", Rows.intVal(r, "status"))
                .put("approved", Rows.intVal(r, "approved"))
                .put("cycle", Rows.str(r, "cycle"))
                .put("dateFrom", Rows.str(r, "date_from"))
                .put("dateTo", Rows.str(r, "date_to"))
                .put("matchedFingerprint", Rows.str(r, "matched_fp"))
                .put("latitude", Rows.str(r, "latitude"))
                .put("longitude", Rows.str(r, "longitude"))
                .put("createdAt", Rows.str(r, "created_at"))
                .put("verifiedAt", Rows.str(r, "verified_at"))
                .put("approvedAt", Rows.str(r, "approved_at"));
    }
}
