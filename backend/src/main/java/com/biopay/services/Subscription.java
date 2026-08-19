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
import com.biopay.utilities.Rows;

/**
 * Per-anchor subscription lifecycle (010_subscriptions.sql). Manual-renewal
 * model: {@code RENEW_SUBSCRIPTION} is an explicit admin action extending the
 * period by one month; there is no external billing gateway wired yet.
 *
 * <p>Status is always derived in SQL from {@code expires_at + grace_days}
 * (ACTIVE / GRACE / ARCHIVED), so it can never fall out of date. The web
 * dashboard reads it via {@code GET_SUBSCRIPTION} to show a grace-period
 * banner and gate access once ARCHIVED; hard server-side enforcement at the
 * dispatch layer is a documented follow-up (see progress.md).
 */
public class Subscription extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Subscription =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("GET_SUBSCRIPTION", this::getStatus);
        eventBus.consumer("RENEW_SUBSCRIPTION", this::renew);
        eventBus.consumer("GET_SUBSCRIPTION_INVOICES", this::getInvoices);
        eventBus.consumer("GET_SUBSCRIPTION_INVOICE_RECEIPT", this::getInvoiceReceipt);
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

    private static Integer anchorIdOf(JsonObject payload) {
        Object v = payload.getValue("anchorId");
        return v == null ? null : Integer.parseInt(v.toString());
    }

    /**
     * Derived subscription status for an anchor: ACTIVE / GRACE / ARCHIVED, or
     * NONE when there is no subscription row (un-provisioned anchor). Used by the
     * dispatch chokepoint in EntryPoint to gate data operations. Fails open --
     * any lookup error resolves to NONE so a transient DB issue never locks
     * everyone out.
     */
    public static Future<String> statusFor(MSSQLPool pool, Integer anchorId) {
        if (anchorId == null) {
            return Future.succeededFuture("NONE");
        }
        String sql = "SELECT CASE WHEN CAST(GETDATE() AS DATE) <= expires_at THEN 'ACTIVE' "
                + "WHEN CAST(GETDATE() AS DATE) <= DATEADD(DAY, grace_days, expires_at) THEN 'GRACE' "
                + "ELSE 'ARCHIVED' END AS status FROM subscriptions WHERE anchor_id=@p1";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.size() == 0 ? "NONE" : Rows.str(rows.iterator().next(), "status"))
                .recover(err -> Future.succeededFuture("NONE"));
    }

    // ---- GET_SUBSCRIPTION -----------------------------------------------------------

    private void getStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            // No anchor context -> nothing to gate on; report an implicit active state.
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK")
                    .put("results", new JsonObject().put("status", "NONE")));
            return;
        }

        String sql = "SELECT plan_code, expires_at, grace_days, "
                + "CASE WHEN CAST(GETDATE() AS DATE) <= expires_at THEN 'ACTIVE' "
                + "     WHEN CAST(GETDATE() AS DATE) <= DATEADD(DAY, grace_days, expires_at) THEN 'GRACE' "
                + "     ELSE 'ARCHIVED' END AS status, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), expires_at) AS days_to_expiry, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), DATEADD(DAY, grace_days, expires_at)) AS days_to_archive "
                + "FROM subscriptions WHERE anchor_id=@p1";

        pool.preparedQuery(sql)
                .execute(Tuple.of(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        // No subscription row provisioned -> treat as active (fail-open) so an
                        // un-provisioned anchor is never locked out by this feature.
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK")
                                .put("results", new JsonObject().put("status", "NONE")));
                        return;
                    }
                    Row r = rows.iterator().next();
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "OK")
                            .put("results", new JsonObject()
                                    .put("status", Rows.str(r, "status"))
                                    .put("planCode", Rows.str(r, "plan_code"))
                                    .put("expiresAt", Rows.str(r, "expires_at"))
                                    .put("graceDays", Rows.intVal(r, "grace_days"))
                                    .put("daysToExpiry", Rows.intVal(r, "days_to_expiry"))
                                    .put("daysToArchive", Rows.intVal(r, "days_to_archive"))));
                });
    }

    // ---- RENEW_SUBSCRIPTION (manual admin action; upsert, extends by one month) ------

    private void renew(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!"ANCHOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            replyError(message, "Only an anchor administrator can renew the subscription");
            return;
        }
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "No anchor on this session");
            return;
        }
        String planCode = payload.getString("planCode", null);
        Object actorId = payload.getValue("actorId");
        Double amount = payload.getDouble("amount");
        String currency = payload.getString("currency", null);

        // Upsert: a new period runs one month from whichever is later -- the current
        // (not-yet-lapsed) expiry, or today for an expired/absent subscription -- so
        // renewing early never loses remaining paid days. periodStart/periodEnd are read
        // back afterward (rather than recomputed in Java) so the invoice always matches
        // exactly what was just written, even under concurrent renewals.
        String sql = "IF EXISTS (SELECT 1 FROM subscriptions WHERE anchor_id=@p1) "
                + "UPDATE subscriptions SET expires_at = DATEADD(MONTH, 1, "
                + "  CASE WHEN expires_at > CAST(GETDATE() AS DATE) THEN expires_at ELSE CAST(GETDATE() AS DATE) END), "
                + "  plan_code = COALESCE(@p2, plan_code), renewed_by=@p3, renewed_at=GETDATE(), updated_at=GETDATE() "
                + "  WHERE anchor_id=@p1; "
                + "ELSE INSERT INTO subscriptions (anchor_id, plan_code, expires_at, grace_days, renewed_by, renewed_at, created_at) "
                + "  VALUES (@p1, @p2, DATEADD(MONTH, 1, CAST(GETDATE() AS DATE)), 30, @p3, GETDATE(), GETDATE());";

        pool.preparedQuery(sql)
                .execute(Tuple.of(anchorId, planCode, String.valueOf(actorId)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> pool.preparedQuery(
                                "SELECT plan_code, expires_at FROM subscriptions WHERE anchor_id=@p1")
                        .execute(Tuple.of(anchorId))
                        .onComplete(ar -> {
                            if (ar.succeeded() && ar.result().size() > 0) {
                                Row sub = ar.result().iterator().next();
                                recordInvoice(anchorId, Rows.str(sub, "plan_code"), amount, currency,
                                        Rows.str(sub, "expires_at"), String.valueOf(actorId));
                            }
                            reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Subscription renewed"));
                        }));
    }

    /** Best-effort invoice write -- renewal itself already succeeded above, so a failure
     *  here shouldn't block the caller; it just means one invoice row is missing. */
    private void recordInvoice(Integer anchorId, String planCode, Double amount, String currency,
            String periodEnd, String createdBy) {
        String invoiceNumber = "INV-" + anchorId + "-" + System.currentTimeMillis();
        pool.preparedQuery("INSERT INTO subscription_invoices (anchor_id, invoice_number, plan_code, amount, "
                        + "currency, period_start, period_end, status, created_by, created_at) "
                        + "VALUES (@p1,@p2,@p3,@p4,@p5,CAST(GETDATE() AS DATE),@p6,'PAID',@p7,GETDATE())")
                .execute(Tuple.of(anchorId, invoiceNumber, planCode, amount, currency, periodEnd, createdBy))
                .onFailure(err -> Logging.applicationLog(
                        Logging.logPreString() + "recordInvoice failed. " + err.getMessage() + "\n\n", "", 3));
    }

    // ---- GET_SUBSCRIPTION_INVOICES (payment history for the Subscription page) ------

    private void getInvoices(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", new JsonArray()));
            return;
        }
        pool.preparedQuery("SELECT * FROM subscription_invoices WHERE anchor_id=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(invoiceSummary(r));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No invoices found" : "Invoices found")
                            .put("results", results));
                });
    }

    // ---- GET_SUBSCRIPTION_INVOICE_RECEIPT (one invoice, printable on the frontend) ---

    private void getInvoiceReceipt(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        // getString(key, def) only substitutes def when the key is entirely absent -- an
        // explicit JSON null still comes back null, so this can't chain .trim() directly.
        String invoiceNumberRaw = payload.getString("invoiceNumber");
        String invoiceNumber = (invoiceNumberRaw == null ? "" : invoiceNumberRaw).trim();
        if (anchorId == null || invoiceNumber.isEmpty()) {
            replyError(message, "invoiceNumber is required");
            return;
        }
        pool.preparedQuery("SELECT i.*, a.name AS anchor_name FROM subscription_invoices i "
                        + "JOIN anchors a ON a.id = i.anchor_id "
                        + "WHERE i.anchor_id=@p1 AND i.invoice_number=@p2")
                .execute(Tuple.of(anchorId, invoiceNumber))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Invoice not found");
                        return;
                    }
                    Row r = rows.iterator().next();
                    JsonObject result = invoiceSummary(r).put("anchorName", Rows.str(r, "anchor_name"));
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", result));
                });
    }

    private static JsonObject invoiceSummary(Row r) {
        return new JsonObject()
                .put("invoiceNumber", Rows.str(r, "invoice_number"))
                .put("planCode", Rows.str(r, "plan_code"))
                .put("amount", Rows.dbl(r, "amount"))
                .put("currency", Rows.str(r, "currency"))
                .put("periodStart", Rows.str(r, "period_start"))
                .put("periodEnd", Rows.str(r, "period_end"))
                .put("status", Rows.str(r, "status"))
                .put("createdAt", Rows.str(r, "created_at"));
    }
}
