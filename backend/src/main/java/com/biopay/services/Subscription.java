package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
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

        // Upsert: a new period runs one month from whichever is later -- the current
        // (not-yet-lapsed) expiry, or today for an expired/absent subscription -- so
        // renewing early never loses remaining paid days.
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
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Subscription renewed")));
    }
}
