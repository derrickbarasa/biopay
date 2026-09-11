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
import java.util.Set;
import com.biopay.databases.Datasource;
import com.biopay.utilities.EmailTemplates;
import com.biopay.utilities.Env;
import com.biopay.utilities.Logging;
import com.biopay.utilities.Rows;
import com.biopay.utilities.TenantScope;
import com.biopay.utilities.Utilities;

/**
 * Per-anchor subscription lifecycle (010_subscriptions.sql, grace/notification columns added in
 * 016_verification_method_both_and_grace_period.sql). Manual-renewal model: {@code
 * RENEW_SUBSCRIPTION} is an explicit admin action extending the period by 30 days; there is no
 * external billing gateway wired yet.
 *
 * <p>Status is always derived in SQL from {@code expires_at + grace_days} (ACTIVE / GRACE /
 * ARCHIVED, currently a 4-day grace window), so it can never fall out of date. The web dashboard
 * reads it via {@code GET_SUBSCRIPTION} to show a grace-period banner and gate access once
 * ARCHIVED; hard server-side enforcement lives in {@code EntryPoint.dispatchGated}. {@link
 * #sendGraceReminders} is this backend's first scheduled job -- it emails each anchor once per
 * lapse when they enter GRACE, complementing the always-on in-app banner.
 */
public class Subscription extends AbstractVerticle {

    /** First scheduled job in this backend (see {@link #sendGraceReminders}) -- checked every
     *  6 hours rather than continuously, since a subscription reminder has no real-time
     *  requirement and this keeps the query load negligible. */
    private static final long GRACE_REMINDER_INTERVAL_MS = 6 * 60 * 60 * 1000;

    EventBus eventBus;
    MSSQLPool pool;
    OtpService otpService;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Subscription =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();
        otpService = new OtpService(pool, eventBus);

        eventBus.consumer("GET_SUBSCRIPTION", this::getStatus);
        eventBus.consumer("GET_ALL_SUBSCRIPTIONS", this::getAllSubscriptions);
        eventBus.consumer("RENEW_SUBSCRIPTION", this::renew);
        eventBus.consumer("GET_SUBSCRIPTION_INVOICES", this::getInvoices);
        eventBus.consumer("GET_SUBSCRIPTION_INVOICE_RECEIPT", this::getInvoiceReceipt);
        // ---- Billing (subscription pricing) --------------------------------------
        eventBus.consumer("GET_SUBSCRIPTION_PRICE", this::getPrice);
        eventBus.consumer("GET_ALL_SUBSCRIPTION_PRICES", this::getAllPrices);
        eventBus.consumer("SET_SUBSCRIPTION_PRICE", this::setPrice);
        eventBus.consumer("SET_DEFAULT_SUBSCRIPTION_PRICE", this::setDefaultPrice);
        // ---- Payment requests (Card/Mobile Money self-serve, Cash, Push Payment Link) --
        eventBus.consumer("CREATE_SUBSCRIPTION_PAYMENT_REQUEST", this::createPaymentRequest);
        eventBus.consumer("GET_SUBSCRIPTION_PAYMENT_REQUESTS", this::getPaymentRequests);
        eventBus.consumer("REQUEST_SUBSCRIPTION_PUSH_OTP", this::requestPushOtp);
        eventBus.consumer("SEND_SUBSCRIPTION_PUSH_PAYMENT_LINK", this::sendPushPaymentLink);
        eventBus.consumer("CONFIRM_SUBSCRIPTION_PAYMENT", this::confirmPaymentRequest);
        eventBus.consumer("CANCEL_SUBSCRIPTION_PAYMENT", this::cancelPaymentRequest);
        vertx.setPeriodic(GRACE_REMINDER_INTERVAL_MS, id -> sendGraceReminders());
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

    /** Whether the anchor account itself (its own row in `users`, user_scope='ANCHOR') is
     *  active -- distinct from subscription status. A deactivated anchor blocks its own
     *  login already (see Auth#loginUser), but organisation/field-officer accounts under it
     *  stay individually active, so they need this to know to show "Contact your anchor"
     *  instead of getting a confusing stream of failed requests. Fails open (true) on error
     *  or a missing row, same fail-open convention as {@link #statusFor}. */
    public static Future<Boolean> anchorActiveFor(MSSQLPool pool, Integer anchorId) {
        if (anchorId == null) return Future.succeededFuture(true);
        return pool.preparedQuery("SELECT status FROM users WHERE id=@p1 AND user_scope='ANCHOR'")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.size() == 0 || Rows.intVal(rows.iterator().next(), "status") == 1)
                .recover(err -> Future.succeededFuture(true));
    }

    private void getStatus(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            // No anchor context -> nothing to gate on; report an implicit active state.
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK")
                    .put("results", new JsonObject().put("status", "NONE").put("anchorActive", true)));
            return;
        }

        String sql = "SELECT plan_code, expires_at, grace_days, "
                + "CASE WHEN CAST(GETDATE() AS DATE) <= expires_at THEN 'ACTIVE' "
                + "     WHEN CAST(GETDATE() AS DATE) <= DATEADD(DAY, grace_days, expires_at) THEN 'GRACE' "
                + "     ELSE 'ARCHIVED' END AS status, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), expires_at) AS days_to_expiry, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), DATEADD(DAY, grace_days, expires_at)) AS days_to_archive "
                + "FROM subscriptions WHERE anchor_id=@p1";

        Future<RowSet<Row>> subscriptionRows = pool.preparedQuery(sql).execute(Tuple.of(anchorId));
        Future<Boolean> anchorActiveFuture = anchorActiveFor(pool, anchorId);

        Future.all(subscriptionRows, anchorActiveFuture)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    RowSet<Row> rows = cf.resultAt(0);
                    boolean active = cf.resultAt(1);
                    if (rows.size() == 0) {
                        // No subscription row provisioned -> treat as active (fail-open) so an
                        // un-provisioned anchor is never locked out by this feature.
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK")
                                .put("results", new JsonObject().put("status", "NONE").put("anchorActive", active)));
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
                                    .put("daysToArchive", Rows.intVal(r, "days_to_archive"))
                                    .put("anchorActive", active)));
                });
    }

    // ---- GET_ALL_SUBSCRIPTIONS (super admin only -- every anchor, cross-tenant) -----

    private void getAllSubscriptions(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!payload.getBoolean("systemAdmin", false)) {
            replyError(message, "Only the platform owner can view every anchor's subscription");
            return;
        }
        String sql = "SELECT a.id AS anchor_id, a.anchor_code, a.anchor_name, s.plan_code, s.expires_at, s.grace_days, "
                + "CASE WHEN s.expires_at IS NULL THEN 'NONE' "
                + "     WHEN CAST(GETDATE() AS DATE) <= s.expires_at THEN 'ACTIVE' "
                + "     WHEN CAST(GETDATE() AS DATE) <= DATEADD(DAY, s.grace_days, s.expires_at) THEN 'GRACE' "
                + "     ELSE 'ARCHIVED' END AS status, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), s.expires_at) AS days_to_expiry "
                + "FROM users a LEFT JOIN subscriptions s ON s.anchor_id = a.id WHERE a.user_scope='ANCHOR' ORDER BY a.anchor_name";
        pool.query(sql).execute()
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(new JsonObject()
                                .put("anchorId", Rows.intVal(r, "anchor_id"))
                                .put("anchorCode", Rows.str(r, "anchor_code"))
                                .put("anchorName", Rows.str(r, "anchor_name"))
                                .put("status", Rows.str(r, "status"))
                                .put("planCode", Rows.str(r, "plan_code"))
                                .put("expiresAt", Rows.str(r, "expires_at"))
                                .put("graceDays", Rows.intVal(r, "grace_days"))
                                .put("daysToExpiry", Rows.intVal(r, "days_to_expiry")));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", results));
                });
    }

    // ---- RENEW_SUBSCRIPTION (manual admin action; upsert, extends by one month) ------

    private void renew(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.managesOrganisations(payload)) {
            replyError(message, "Only the platform owner or an anchor administrator can renew the subscription");
            return;
        }
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "No anchor on this session");
            return;
        }
        String planCode = payload.getString("planCode", null);
        Object actorId = payload.getValue("actorId");

        // The amount charged is always the price set for this anchor in Billing -- never a
        // client-supplied figure -- so a renewal can't record (or be tricked into recording)
        // any other amount than what the platform owner actually configured.
        currentPrice(anchorId).onFailure(err -> onDbError(message, err)).onSuccess(price -> {
            double amount = price.getDouble("amount", 0d);
            String currency = price.getString("currency", "USD");
            if (amount <= 0) {
                replyError(message, "No subscription price has been configured yet. Contact BioPay to set one up.");
                return;
            }
            renewSubscriptionRow(anchorId, planCode, String.valueOf(actorId))
                    .onFailure(err -> onDbError(message, err))
                    .onSuccess(sub -> {
                        if (sub != null) {
                            recordInvoice(anchorId, Rows.str(sub, "plan_code"), amount, currency,
                                    Rows.str(sub, "expires_at"), String.valueOf(actorId));
                        }
                        reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "Subscription renewed"));
                    });
        });
    }

    /** Upsert shared by {@link #renew} and {@link #confirmPaymentRequest}: a new period runs
     *  30 days from whichever is later -- the current (not-yet-lapsed) expiry, or today for
     *  an expired/absent subscription -- so renewing early never loses remaining paid days.
     *  Returns the row read back afterward (rather than recomputed in Java) so a caller's
     *  invoice always matches exactly what was just written, even under concurrent renewals.
     *  Clearing grace_notified_at lets the reminder job email again next time this anchor
     *  lapses into GRACE, rather than staying permanently "already notified". */
    private Future<Row> renewSubscriptionRow(Integer anchorId, String planCode, String actorLabel) {
        String sql = "IF EXISTS (SELECT 1 FROM subscriptions WHERE anchor_id=@p1) "
                + "UPDATE subscriptions SET expires_at = DATEADD(DAY, 30, "
                + "  CASE WHEN expires_at > CAST(GETDATE() AS DATE) THEN expires_at ELSE CAST(GETDATE() AS DATE) END), "
                + "  plan_code = COALESCE(@p2, plan_code), renewed_by=@p3, renewed_at=GETDATE(), updated_at=GETDATE(), "
                + "  grace_notified_at = NULL "
                + "  WHERE anchor_id=@p1; "
                + "ELSE INSERT INTO subscriptions (anchor_id, plan_code, expires_at, grace_days, renewed_by, renewed_at, created_at) "
                + "  VALUES (@p1, @p2, DATEADD(DAY, 30, CAST(GETDATE() AS DATE)), 4, @p3, GETDATE(), GETDATE());";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(anchorId, planCode, actorLabel))
                .compose(ignored -> pool.preparedQuery("SELECT plan_code, expires_at FROM subscriptions WHERE anchor_id=@p1")
                        .execute(Tuple.of(anchorId)))
                .map(rows -> rows.size() == 0 ? null : rows.iterator().next());
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

    private static String frontendBaseUrl() {
        return Env.get().get("FRONTEND_BASE_URL", "http://localhost:5173");
    }

    // ---- Billing: subscription pricing ----------------------------------------------

    /** Looks up the price an anchor owes for its next renewal: its own override in
     *  subscription_prices if one has been set, otherwise the platform-wide default from
     *  billing_settings. Used by the price display on both Make Payment screens. */
    private Future<JsonObject> currentPrice(Integer anchorId) {
        return pool.preparedQuery("SELECT amount, currency FROM subscription_prices WHERE anchor_id=@p1")
                .execute(Tuple.of(anchorId))
                .compose(rows -> {
                    if (rows.size() > 0) {
                        Row r = rows.iterator().next();
                        return Future.succeededFuture(new JsonObject()
                                .put("amount", Rows.dbl(r, "amount")).put("currency", Rows.str(r, "currency")).put("isDefault", false));
                    }
                    return pool.query("SELECT default_amount, default_currency FROM billing_settings WHERE id=1").execute()
                            .map(defRows -> {
                                if (defRows.size() == 0) {
                                    return new JsonObject().put("amount", 0d).put("currency", "USD").put("isDefault", true);
                                }
                                Row d = defRows.iterator().next();
                                return new JsonObject().put("amount", Rows.dbl(d, "default_amount"))
                                        .put("currency", Rows.str(d, "default_currency")).put("isDefault", true);
                            });
                });
    }

    private void getPrice(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "No anchor on this session");
            return;
        }
        currentPrice(anchorId)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(price -> reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", price)));
    }

    private void getAllPrices(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can view subscription pricing");
            return;
        }
        Future<Row> defaultRow = pool.query("SELECT default_amount, default_currency FROM billing_settings WHERE id=1").execute()
                .map(rows -> rows.size() == 0 ? null : rows.iterator().next());
        Future<JsonArray> anchorRows = pool.query(
                "SELECT a.id AS anchor_id, a.anchor_name, p.amount, p.currency FROM users a "
                        + "LEFT JOIN subscription_prices p ON p.anchor_id = a.id "
                        + "WHERE a.user_scope='ANCHOR' ORDER BY a.anchor_name").execute()
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        boolean hasOverride = r.getValue("amount") != null;
                        arr.add(new JsonObject()
                                .put("anchorId", Rows.intVal(r, "anchor_id"))
                                .put("anchorName", Rows.str(r, "anchor_name"))
                                .put("amount", hasOverride ? Rows.dbl(r, "amount") : null)
                                .put("currency", hasOverride ? Rows.str(r, "currency") : null)
                                .put("hasOverride", hasOverride));
                    }
                    return arr;
                });
        Future.all(defaultRow, anchorRows)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    Row d = cf.resultAt(0);
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK")
                            .put("results", new JsonObject()
                                    .put("defaultAmount", d == null ? 0d : Rows.dbl(d, "default_amount"))
                                    .put("defaultCurrency", d == null ? "USD" : Rows.str(d, "default_currency"))
                                    .put("anchors", (JsonArray) cf.resultAt(1))));
                });
    }

    private void setPrice(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can set subscription pricing");
            return;
        }
        Object targetAnchorIdVal = payload.getValue("targetAnchorId");
        Double amount = payload.getDouble("amount");
        String currency = payload.getString("currency", "USD").trim().toUpperCase();
        if (targetAnchorIdVal == null || amount == null || amount < 0) {
            replyError(message, "targetAnchorId and a non-negative amount are required");
            return;
        }
        Integer targetAnchorId;
        try {
            targetAnchorId = Integer.parseInt(targetAnchorIdVal.toString());
        } catch (NumberFormatException ex) {
            replyError(message, "targetAnchorId must be a number");
            return;
        }
        String actorEmail = payload.getString("actorEmail", "");
        String sql = "IF EXISTS (SELECT 1 FROM subscription_prices WHERE anchor_id=@p1) "
                + "UPDATE subscription_prices SET amount=@p2, currency=@p3, updated_by=@p4, updated_at=SYSUTCDATETIME() WHERE anchor_id=@p1; "
                + "ELSE INSERT INTO subscription_prices (anchor_id, amount, currency, updated_by, updated_at) VALUES (@p1, @p2, @p3, @p4, SYSUTCDATETIME());";
        pool.preparedQuery(sql).execute(Tuple.of(targetAnchorId, amount, currency, actorEmail))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Subscription price saved")));
    }

    private void setDefaultPrice(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can set the default subscription price");
            return;
        }
        Double amount = payload.getDouble("amount");
        String currency = payload.getString("currency", "USD").trim().toUpperCase();
        if (amount == null || amount < 0) {
            replyError(message, "A non-negative amount is required");
            return;
        }
        String actorEmail = payload.getString("actorEmail", "");
        pool.preparedQuery("UPDATE billing_settings SET default_amount=@p1, default_currency=@p2, updated_by=@p3, updated_at=SYSUTCDATETIME() WHERE id=1")
                .execute(Tuple.of(amount, currency, actorEmail))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Default subscription price saved")));
    }

    // ---- Payment requests: anchor self-serve Card/Mobile Money -----------------------

    /** No payment gateway is wired up yet (see class doc) -- this records a PENDING intent
     *  for the platform owner to reconcile and confirm manually once the money is actually
     *  seen off-platform, rather than pretending a charge succeeded. Deliberately never
     *  accepts or stores a card number/CVV; Card requests carry no card fields at all. */
    private void createPaymentRequest(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.managesOrganisations(payload)) {
            replyError(message, "Only the anchor administrator can pay for the subscription");
            return;
        }
        Integer anchorId = anchorIdOf(payload);
        if (anchorId == null) {
            replyError(message, "No anchor on this session");
            return;
        }
        String method = payload.getString("method", "").trim().toUpperCase();
        if (!"CARD".equals(method) && !"MOBILE_MONEY".equals(method)) {
            replyError(message, "method must be CARD or MOBILE_MONEY");
            return;
        }
        String mobileProvider = null;
        String phoneNumber = null;
        if ("MOBILE_MONEY".equals(method)) {
            mobileProvider = payload.getString("mobileProvider", "").trim().toUpperCase();
            phoneNumber = payload.getString("phoneNumber", "").trim();
            if (!Set.of("MPESA", "AIRTEL", "MTN").contains(mobileProvider)) {
                replyError(message, "mobileProvider must be MPESA, AIRTEL or MTN");
                return;
            }
            if (!phoneNumber.matches("\\+\\d{9,15}")) {
                replyError(message, "A valid phone number (with country code) is required");
                return;
            }
        }
        String actorEmail = payload.getString("actorEmail", "");
        String finalMobileProvider = mobileProvider;
        String finalPhoneNumber = phoneNumber;

        currentPrice(anchorId).onFailure(err -> onDbError(message, err)).onSuccess(price -> {
            double amount = price.getDouble("amount", 0d);
            String currency = price.getString("currency", "USD");
            if (amount <= 0) {
                replyError(message, "No subscription price has been configured yet. Contact BioPay to set one up.");
                return;
            }
            String reference = "SUBPAY-" + anchorId + "-" + System.currentTimeMillis();
            pool.preparedQuery("INSERT INTO subscription_payment_requests "
                            + "(anchor_id, reference, method, mobile_provider, phone_number, amount, currency, status, initiated_by, initiated_role, created_at) "
                            + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,'PENDING',@p8,'ANCHOR',SYSUTCDATETIME())")
                    .execute(Tuple.of(anchorId, reference, method, finalMobileProvider, finalPhoneNumber, amount, currency, actorEmail))
                    .onFailure(err -> onDbError(message, err))
                    .onSuccess(rows -> reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "MOBILE_MONEY".equals(method)
                                    ? "Check your phone to approve the payment prompt. We'll confirm your subscription once it's received."
                                    : "Card payment recorded. We'll confirm your subscription once it's received.")
                            .put("results", new JsonObject().put("reference", reference).put("amount", amount).put("currency", currency))));
        });
    }

    private void getPaymentRequests(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.managesOrganisations(payload)) {
            replyError(message, "Only the anchor administrator can view subscription payment requests");
            return;
        }
        boolean systemOwner = TenantScope.isSystemOwner(payload);
        Integer anchorId = anchorIdOf(payload);
        String sql;
        Tuple params;
        if (systemOwner && anchorId == null) {
            sql = "SELECT r.*, a.anchor_name FROM subscription_payment_requests r "
                    + "JOIN users a ON a.id = r.anchor_id AND a.user_scope='ANCHOR' ORDER BY r.created_at DESC";
            params = Tuple.tuple();
        } else if (anchorId != null) {
            sql = "SELECT r.*, a.anchor_name FROM subscription_payment_requests r "
                    + "JOIN users a ON a.id = r.anchor_id AND a.user_scope='ANCHOR' WHERE r.anchor_id=@p1 ORDER BY r.created_at DESC";
            params = Tuple.of(anchorId);
        } else {
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", new JsonArray()));
            return;
        }
        pool.preparedQuery(sql).execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) results.add(paymentRequestSummary(r));
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", results));
                });
    }

    private static JsonObject paymentRequestSummary(Row r) {
        return new JsonObject()
                .put("id", Rows.intVal(r, "id"))
                .put("anchorId", Rows.intVal(r, "anchor_id"))
                .put("anchorName", Rows.str(r, "anchor_name"))
                .put("reference", Rows.str(r, "reference"))
                .put("method", Rows.str(r, "method"))
                .put("mobileProvider", Rows.str(r, "mobile_provider"))
                .put("phoneNumber", Rows.str(r, "phone_number"))
                .put("amount", Rows.dbl(r, "amount"))
                .put("currency", Rows.str(r, "currency"))
                .put("status", Rows.str(r, "status"))
                .put("comment", Rows.str(r, "comment"))
                .put("initiatedBy", Rows.str(r, "initiated_by"))
                .put("initiatedRole", Rows.str(r, "initiated_role"))
                .put("createdAt", Rows.str(r, "created_at"));
    }

    // ---- Push Payment Link (platform owner -> anchor, by email) ----------------------

    private void requestPushOtp(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can send a payment link");
            return;
        }
        String email = payload.getString("actorEmail", "").trim();
        if (email.isEmpty()) {
            replyError(message, "actorEmail is required to send the verification code");
            return;
        }
        otpService.request("SUBSCRIPTION_PUSH_LINK", "", actorId(payload), "SYSTEM", email)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(v -> reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Verification code sent to " + email)));
    }

    private static int actorId(JsonObject payload) {
        Object v = payload.getValue("actorId");
        try {
            return v == null ? 0 : Integer.parseInt(v.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void sendPushPaymentLink(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can send a payment link");
            return;
        }
        Object targetAnchorIdVal = payload.getValue("targetAnchorId");
        String comment = payload.getString("comment", "").trim();
        String otpCode = payload.getString("otpCode", "").trim();
        if (targetAnchorIdVal == null) {
            replyError(message, "targetAnchorId is required");
            return;
        }
        if (comment.isEmpty()) {
            replyError(message, "A comment describing this payment request is required");
            return;
        }
        if (otpCode.isEmpty()) {
            replyError(message, "otpCode is required");
            return;
        }
        Integer targetAnchorId;
        try {
            targetAnchorId = Integer.parseInt(targetAnchorIdVal.toString());
        } catch (NumberFormatException ex) {
            replyError(message, "targetAnchorId must be a number");
            return;
        }
        final Integer anchorId = targetAnchorId;

        // As with renew(), the amount is always the anchor's configured Billing price --
        // never taken from the client -- so the link can't be sent for any other figure.
        currentPrice(anchorId).onFailure(err -> onDbError(message, err)).onSuccess(price -> {
            double amount = price.getDouble("amount", 0d);
            String currency = price.getString("currency", "USD");
            if (amount <= 0) {
                replyError(message, "No subscription price has been configured yet. Contact BioPay to set one up.");
                return;
            }
            otpService.verify("SUBSCRIPTION_PUSH_LINK", actorId(payload), otpCode)
                    .onFailure(err -> onDbError(message, err))
                    .onSuccess(verified -> {
                        if (!Boolean.TRUE.equals(verified)) {
                            replyError(message, "Invalid or expired verification code");
                            return;
                        }
                        String reference = "SUBPUSH-" + anchorId + "-" + System.currentTimeMillis();
                        String actorEmail = payload.getString("actorEmail", "");
                        pool.preparedQuery("INSERT INTO subscription_payment_requests "
                                        + "(anchor_id, reference, method, amount, currency, status, comment, initiated_by, initiated_role, created_at) "
                                        + "VALUES (@p1,@p2,'PUSH_LINK',@p3,@p4,'PENDING',@p5,@p6,'SYSTEM',SYSUTCDATETIME())")
                                .execute(Tuple.of(anchorId, reference, amount, currency, comment, actorEmail))
                                .onFailure(err -> onDbError(message, err))
                                .onSuccess(rows -> emailPushPaymentLink(message, anchorId, amount, currency, comment));
                    });
        });
    }

    private void emailPushPaymentLink(Message<Object> message, int anchorId, double amount, String currency, String comment) {
        Future<String> anchorNameFuture = pool.preparedQuery("SELECT anchor_name FROM users WHERE id=@p1 AND user_scope='ANCHOR'")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.size() == 0 ? "" : Rows.str(rows.iterator().next(), "anchor_name"));
        Future<JsonArray> recipientEmailsFuture = pool.preparedQuery(
                        "SELECT email FROM users WHERE anchor_id=@p1 AND user_scope='ANCHOR' AND active=1")
                .execute(Tuple.of(anchorId))
                .map(rows -> {
                    JsonArray emails = new JsonArray();
                    for (Row r : rows) {
                        String email = Rows.str(r, "email");
                        if (email != null && !email.isEmpty()) emails.add(email);
                    }
                    return emails;
                });

        Future.all(anchorNameFuture, recipientEmailsFuture)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    String anchorName = cf.resultAt(0);
                    JsonArray emails = cf.resultAt(1);
                    String amountLabel = currency + " " + String.format(java.util.Locale.ROOT, "%,.2f", amount);
                    String payUrl = frontendBaseUrl() + "/app/subscription/pay";
                    for (Object emailObj : emails) {
                        eventBus.send("EMAIL", new JsonObject()
                                .put("mailTo", (String) emailObj)
                                .put("subject", "BioPay subscription payment requested")
                                .put("msg", EmailTemplates.subscriptionPushPaymentEmail(anchorName, amountLabel, comment, payUrl))
                                .put("inlineImages", EmailTemplates.logoInlineImages()));
                    }
                    reply(message, new JsonObject().put("responseCode", "000")
                            .put("responseMessage", emails.size() > 0
                                    ? "Payment link sent to " + emails.size() + " recipient(s)"
                                    : "No active anchor recipients found to email"));
                });
    }

    // ---- Confirm / cancel a pending payment request -----------------------------------

    private void confirmPaymentRequest(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can confirm a subscription payment");
            return;
        }
        Object requestIdVal = payload.getValue("requestId");
        if (requestIdVal == null) {
            replyError(message, "requestId is required");
            return;
        }
        String actorEmail = payload.getString("actorEmail", "");
        pool.preparedQuery("SELECT * FROM subscription_payment_requests WHERE id=@p1 AND status='PENDING'")
                .execute(Tuple.of(Integer.parseInt(requestIdVal.toString())))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Pending payment request not found");
                        return;
                    }
                    Row request = rows.iterator().next();
                    int anchorId = Rows.intVal(request, "anchor_id");
                    double amount = Rows.dbl(request, "amount");
                    String currency = Rows.str(request, "currency");
                    pool.preparedQuery("UPDATE subscription_payment_requests SET status='CONFIRMED', confirmed_by=@p1, confirmed_at=SYSUTCDATETIME(), updated_at=SYSUTCDATETIME() WHERE id=@p2")
                            .execute(Tuple.of(actorEmail, Rows.intVal(request, "id")))
                            .compose(ignored -> renewSubscriptionRow(anchorId, null, actorEmail))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(sub -> {
                                if (sub != null) {
                                    recordInvoice(anchorId, Rows.str(sub, "plan_code"), amount, currency, Rows.str(sub, "expires_at"), actorEmail);
                                }
                                reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payment confirmed and subscription renewed"));
                            });
                });
    }

    private void cancelPaymentRequest(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isSystemOwner(payload)) {
            replyError(message, "Only the platform owner can cancel a subscription payment");
            return;
        }
        Object requestIdVal = payload.getValue("requestId");
        if (requestIdVal == null) {
            replyError(message, "requestId is required");
            return;
        }
        pool.preparedQuery("UPDATE subscription_payment_requests SET status='CANCELLED', updated_at=SYSUTCDATETIME() WHERE id=@p1 AND status='PENDING'")
                .execute(Tuple.of(Integer.parseInt(requestIdVal.toString())))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payment request cancelled")));
    }

    // ---- Grace-period renewal reminder (scheduled, not an event-bus action) ---------

    /** Finds anchors that have lapsed past {@code expires_at} but are still within their
     *  grace window and haven't been emailed about it yet ({@code grace_notified_at IS NULL}),
     *  and notifies each one once. The web dashboard's always-shown grace banner (see
     *  DefaultLayout.vue) already covers the in-app half of "notify by email, in-app, or
     *  both"; this covers the email half by reusing the existing EMAIL event-bus channel
     *  (same fire-and-forget pattern as Auth/Officer emails). {@link #renew} clears
     *  grace_notified_at so a later lapse is emailed again, not just the first ever one. */
    private void sendGraceReminders() {
        String sql = "SELECT s.anchor_id, a.anchor_name, "
                + "DATEDIFF(DAY, CAST(GETDATE() AS DATE), DATEADD(DAY, s.grace_days, s.expires_at)) AS days_to_archive "
                + "FROM subscriptions s JOIN users a ON a.id = s.anchor_id AND a.user_scope='ANCHOR' "
                + "WHERE CAST(GETDATE() AS DATE) > s.expires_at "
                + "  AND CAST(GETDATE() AS DATE) <= DATEADD(DAY, s.grace_days, s.expires_at) "
                + "  AND s.grace_notified_at IS NULL";
        pool.query(sql).execute()
                .onFailure(err -> Logging.applicationLog(
                        Logging.logPreString() + "sendGraceReminders lookup failed. " + err.getMessage() + "\n\n", "", 3))
                .onSuccess(rows -> {
                    for (Row r : rows) {
                        notifyAnchorInGrace(Rows.intVal(r, "anchor_id"), Rows.str(r, "anchor_name"), Rows.intVal(r, "days_to_archive"));
                    }
                });
    }

    private void notifyAnchorInGrace(int anchorId, String anchorName, int daysToArchive) {
        pool.preparedQuery("SELECT email FROM users WHERE anchor_id=@p1 AND user_scope='ANCHOR' AND active=1")
                .execute(Tuple.of(anchorId))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString()
                        + "notifyAnchorInGrace lookup failed for anchor " + anchorId + ". " + err.getMessage() + "\n\n", "", 3))
                .onSuccess(rows -> {
                    for (Row r : rows) {
                        String email = Rows.str(r, "email");
                        if (email == null || email.isEmpty()) continue;
                        eventBus.send("EMAIL", new JsonObject()
                                .put("mailTo", email)
                                .put("subject", "BioPay subscription renewal needed")
                                .put("msg", "Your BioPay subscription for "
                                        + (anchorName == null || anchorName.isEmpty() ? "your organisation" : anchorName)
                                        + " has expired. You have " + daysToArchive + " day(s) left in the grace period "
                                        + "before access is locked. Please renew from the Subscription page to avoid interruption."));
                    }
                    markGraceNotified(anchorId);
                });
    }

    private void markGraceNotified(int anchorId) {
        pool.preparedQuery("UPDATE subscriptions SET grace_notified_at = GETDATE() WHERE anchor_id=@p1")
                .execute(Tuple.of(anchorId))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString()
                        + "markGraceNotified failed for anchor " + anchorId + ". " + err.getMessage() + "\n\n", "", 3));
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
        pool.preparedQuery("SELECT i.*, a.anchor_name FROM subscription_invoices i "
                        + "JOIN users a ON a.id = i.anchor_id AND a.user_scope='ANCHOR' "
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
