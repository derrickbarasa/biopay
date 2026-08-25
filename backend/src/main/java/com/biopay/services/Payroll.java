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
import java.util.ArrayList;
import java.util.List;
import com.biopay.databases.Datasource;
import com.biopay.utilities.Logging;
import com.biopay.utilities.Rows;
import com.biopay.utilities.TenantScope;
import com.biopay.utilities.Utilities;

/**
 * Payroll cycles: generate (maker) -> approve (checker, anchor-only per the
 * frontend spec's "approve/disburse buttons (anchor only)") -> disburse.
 * Both generation and approval require a fresh EMAIL OTP (see
 * {@link OtpService}); the same person can never be both maker and checker
 * on one cycle, even if both hold the ANCHOR role.
 */
public class Payroll extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;
    OtpService otpService;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Payroll =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();
        otpService = new OtpService(pool, eventBus);

        eventBus.consumer("REQUEST_PAYROLL_OTP", this::requestOtp);
        eventBus.consumer("GENERATE_PAYROLL", this::generate);
        eventBus.consumer("APPROVE_PAYROLL", this::approve);
        eventBus.consumer("REJECT_PAYROLL", this::reject);
        eventBus.consumer("REJECT_PAYROLL_ITEMS", this::rejectItems);
        eventBus.consumer("DISBURSE_PAYROLL", this::disburse);
        eventBus.consumer("DELETE_PAYROLL", this::delete);
        eventBus.consumer("GET_PAYROLL", this::getOne);
        eventBus.consumer("GET_PAYROLLS", this::retrieveAll);
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

    private static int actorId(JsonObject payload) {
        return Integer.parseInt(payload.getValue("actorId").toString());
    }

    // ---- REQUEST_PAYROLL_OTP ------------------------------------------------------

    private void requestOtp(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String action = payload.getString("action", "").trim().toUpperCase(); // GENERATE | APPROVE
        String email = payload.getString("actorEmail", payload.getString("email", "")).trim();
        String referenceCode = payload.getString("cycleCode", "");

        if (!"GENERATE".equals(action) && !"APPROVE".equals(action)) {
            replyError(message, "action must be GENERATE or APPROVE");
            return;
        }
        if (email.isEmpty()) {
            replyError(message, "actorEmail is required to send the verification code");
            return;
        }

        String referenceType = "APPROVE".equals(action) ? "PAYROLL_APPROVE" : "PAYROLL_GENERATE";
        otpService.request(referenceType, referenceCode, actorId(payload), payload.getString("actorRole", ""), email)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(v -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Verification code sent to " + email)));
    }

    // ---- GENERATE_PAYROLL (maker) --------------------------------------------------

    private void generate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        String periodStart = payload.getString("periodStart", "");
        String periodEnd = payload.getString("periodEnd", "");
        Double amountPerHousehold = payload.getDouble("amountPerHousehold");
        String otpCode = payload.getString("otpCode", "");
        String currency = payload.getString("currency", "USD").trim().toUpperCase();
        Double exchangeRateVal = payload.getDouble("exchangeRate", 1.0);
        double exchangeRate = exchangeRateVal == null ? 1.0 : exchangeRateVal;
        List<String> householdNumbers = new ArrayList<>();
        for (Object o : payload.getJsonArray("householdNumbers", new JsonArray())) {
            if (o != null && !o.toString().trim().isEmpty()) {
                householdNumbers.add(o.toString().trim());
            }
        }

        if (partnerCode.isEmpty() || periodStart.isEmpty() || periodEnd.isEmpty() || amountPerHousehold == null || amountPerHousehold <= 0) {
            replyError(message, "organisationCode, periodStart, periodEnd and amountPerHousehold are required");
            return;
        }
        if (householdNumbers.isEmpty()) {
            replyError(message, "At least one householdNumbers entry is required -- select which households this cycle is for");
            return;
        }
        if (exchangeRate <= 0) {
            replyError(message, "exchangeRate must be greater than zero");
            return;
        }
        if (otpCode.isEmpty()) {
            replyError(message, "otpCode is required");
            return;
        }

        otpService.verify("PAYROLL_GENERATE", actorId(payload), otpCode)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(verified -> {
                    if (!Boolean.TRUE.equals(verified)) {
                        replyError(message, "Invalid or expired verification code");
                        return;
                    }
                    countActiveHouseholds(partnerCode, householdNumbers)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(householdCount -> {
                                if (householdCount == 0) {
                                    replyError(message, "None of the selected households are active in this organisation");
                                    return;
                                }
                                double total = householdCount * amountPerHousehold;
                                String cycleCode = Utilities.generateCode("PAYROLL");
                                Object anchorIdVal = payload.getValue("anchorId");

                                // Cycle header + its payment line items must land together --
                                // a mid-way failure here must not leave a cycle with zero (or
                                // partial) line items sitting in PENDING_APPROVAL.
                                pool.withTransaction(client -> {
                                    String sql = "INSERT INTO payment_cycles (cycle_code, organization_code, anchor_id, period_start, period_end, "
                                            + "amount_per_household, household_count, total_amount, currency, exchange_rate, status, maker_id, maker_at, "
                                            + "otp_verified, created_by, created_at) "
                                            + "OUTPUT INSERTED.id "
                                            + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,'PENDING_APPROVAL',@p11,GETDATE(),1,@p12,GETDATE())";
                                    return client.preparedQuery(sql)
                                            .execute(Tuple.of(cycleCode, partnerCode,
                                                    anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString()),
                                                    periodStart, periodEnd, amountPerHousehold, householdCount, total, currency, exchangeRate,
                                                    actorId(payload), String.valueOf(actorId(payload))))
                                            .compose(insertRows -> {
                                                int cycleId = Rows.intVal(insertRows.iterator().next(), "id");
                                                return createLineItems(client, cycleId, cycleCode, partnerCode, anchorIdVal,
                                                        periodStart, periodEnd, amountPerHousehold, currency, exchangeRate,
                                                        actorId(payload), householdNumbers);
                                            });
                                }).onFailure(err -> onDbError(message, err))
                                  .onSuccess(lineCount -> reply(message, new JsonObject()
                                          .put("responseCode", "000")
                                          .put("responseMessage", "Payroll cycle generated and pending approval")
                                          .put("cycleCode", cycleCode)
                                          .put("householdCount", householdCount)
                                          .put("totalAmount", total)
                                          .put("currency", currency)
                                          .put("exchangeRate", exchangeRate)));
                            });
                });
    }

    /** @p1..@pN placeholders for an IN (...) list starting at parameter index {@code startIdx}. */
    private static String inClause(int startIdx, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("@p").append(startIdx + i);
        }
        return sb.toString();
    }

    private Future<Integer> countActiveHouseholds(String partnerCode, List<String> householdNumbers) {
        String sql = "SELECT COUNT(*) AS cnt FROM households WHERE organization_code=@p1 AND status=1 AND household_number IN ("
                + inClause(2, householdNumbers.size()) + ")";
        Tuple params = Tuple.of(partnerCode);
        for (String hn : householdNumbers) {
            params = params.addString(hn);
        }
        return pool.preparedQuery(sql)
                .execute(params)
                .map(rows -> rows.size() == 0 ? 0 : Rows.intVal(rows.iterator().next(), "cnt"));
    }

    private Future<Integer> createLineItems(io.vertx.sqlclient.SqlClient client, int cycleId, String cycleCode,
            String partnerCode, Object anchorIdVal, String periodStart, String periodEnd, double amount,
            String currency, double exchangeRate, int makerId, List<String> householdNumbers) {
        // uuid has a UNIQUE index (UQ_payments_UUID) -- SQL Server allows at most one NULL in a
        // unique index, so every row here needs its own value; NEWID() supplies a fresh one per
        // row within the single INSERT...SELECT (a bound Tuple parameter would repeat the same
        // value for all rows and collide after the first).
        String sql = "INSERT INTO payments (household_number, household_name, gender, boma_code, organization_code, anchor_id, "
                + "payment_cycle_id, cycle, payment_cycle, date_from, date_to, amount, status, approved, uuid, currency, exchange_rate, "
                + "created_by, created_at) "
                + "SELECT household_number, household_name, gender, boma_code, @p1, @p2, "
                + "@p3, @p4, @p4, @p5, @p6, @p7, 0, 0, CONVERT(VARCHAR(50), NEWID()), @p9, @p10, @p8, GETDATE() "
                + "FROM households WHERE organization_code=@p1 AND status=1 AND household_number IN ("
                + inClause(11, householdNumbers.size()) + ")";
        Object anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        Tuple params = Tuple.of(partnerCode, anchorId, cycleId, cycleCode, periodStart, periodEnd, amount, makerId, currency, exchangeRate);
        for (String hn : householdNumbers) {
            params = params.addString(hn);
        }
        return client.preparedQuery(sql)
                .execute(params)
                .map(rows -> rows.rowCount());
    }

    // ---- APPROVE_PAYROLL (checker, anchor only) ------------------------------------

    private void approve(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can approve payroll");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();
        String otpCode = payload.getString("otpCode", "").trim();
        if (cycleCode.isEmpty() || otpCode.isEmpty()) {
            replyError(message, "cycleCode and otpCode are required");
            return;
        }

        pool.preparedQuery("SELECT * FROM payment_cycles WHERE cycle_code=@p1 AND (@p2=1 OR anchor_id=@p3)")
                .execute(Tuple.of(cycleCode, isSystemAdmin(payload), TenantScope.anchorId(payload)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Payroll cycle not found");
                        return;
                    }
                    Row r = rows.iterator().next();
                    if (!"PENDING_APPROVAL".equals(Rows.str(r, "status"))) {
                        replyError(message, "Only a cycle pending approval can be approved");
                        return;
                    }
                    Integer makerId = Rows.intVal(r, "maker_id");
                    if (makerId != null && makerId == actorId(payload)) {
                        replyError(message, "The maker of a payroll cycle cannot also approve it");
                        return;
                    }

                    otpService.verify("PAYROLL_APPROVE", actorId(payload), otpCode)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(verified -> {
                                if (!Boolean.TRUE.equals(verified)) {
                                    replyError(message, "Invalid or expired verification code");
                                    return;
                                }
                                pool.preparedQuery("UPDATE payment_cycles SET status='APPROVED', checker_id=@p1, checker_at=GETDATE(), "
                                                + "updated_at=GETDATE() WHERE cycle_code=@p2")
                                        .execute(Tuple.of(actorId(payload), cycleCode))
                                        .compose(u -> pool.preparedQuery(
                                                        "UPDATE payments SET approved=1, approved_by=@p1, approved_at=GETDATE() "
                                                                + "WHERE payment_cycle_id=@p2 AND rejected=0")
                                                .execute(Tuple.of(actorId(payload), Rows.intVal(r, "id"))))
                                        .onFailure(err -> onDbError(message, err))
                                        .onSuccess(u -> reply(message, new JsonObject()
                                                .put("responseCode", "000")
                                                .put("responseMessage", "Payroll cycle approved")));
                            });
                });
    }

    // ---- REJECT_PAYROLL (checker, anchor only) -------------------------------------

    private void reject(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can reject payroll");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();
        String reason = payload.getString("reason", "").trim();

        pool.preparedQuery("UPDATE payment_cycles SET status='REJECTED', checker_id=@p1, checker_at=GETDATE(), "
                        + "rejection_reason=@p2, updated_at=GETDATE() WHERE cycle_code=@p3 AND status='PENDING_APPROVAL' "
                        + "AND (@p4=1 OR anchor_id=@p5)")
                .execute(Tuple.of(actorId(payload), reason, cycleCode, isSystemAdmin(payload), TenantScope.anchorId(payload)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payroll cycle rejected"));
                    } else {
                        replyError(message, "Cycle not found or not pending approval");
                    }
                });
    }

    // ---- REJECT_PAYROLL_ITEMS (the cycle's own maker, or any anchor-admin checker;
    //      pending-approval cycles only) ----------------------------------------------

    private void rejectItems(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String cycleCode = payload.getString("cycleCode", "").trim();
        String reason = payload.getString("reason", "").trim();
        List<Integer> paymentIds = new ArrayList<>();
        for (Object o : payload.getJsonArray("paymentIds", new JsonArray())) {
            if (o != null) {
                paymentIds.add(Integer.parseInt(o.toString()));
            }
        }
        if (cycleCode.isEmpty() || paymentIds.isEmpty()) {
            replyError(message, "cycleCode and paymentIds are required");
            return;
        }

        pool.preparedQuery("SELECT id, maker_id FROM payment_cycles WHERE cycle_code=@p1 AND status='PENDING_APPROVAL' "
                        + "AND (@p2=1 OR (@p3=1 AND anchor_id=@p4) OR organization_code=@p5)")
                .execute(Tuple.of(cycleCode, isSystemAdmin(payload), TenantScope.isAnchorAdministrator(payload),
                        TenantScope.anchorId(payload), payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Only a cycle pending approval can have line items rejected");
                        return;
                    }
                    Row cycleRow = rows.iterator().next();
                    // Either the checker (any anchor admin) or the cycle's own maker/generator can
                    // reject line items pre-approval -- the maker picks who's in a cycle up front,
                    // then can still trim it down before it goes to approval.
                    Integer makerId = Rows.intVal(cycleRow, "maker_id");
                    boolean isMaker = makerId != null && makerId == actorId(payload);
                    if (!isAnchor(payload) && !isMaker) {
                        replyError(message, "Only the cycle's generator or an anchor administrator can reject payroll line items");
                        return;
                    }
                    int cycleId = Rows.intVal(cycleRow, "id");
                    String sql = "UPDATE payments SET rejected=1, rejected_by=@p1, rejected_at=GETDATE(), rejection_reason=@p2, "
                            + "updated_at=GETDATE() WHERE payment_cycle_id=@p3 AND rejected=0 AND id IN ("
                            + inClause(4, paymentIds.size()) + ")";
                    Tuple params = Tuple.of(actorId(payload), reason, cycleId);
                    for (Integer id : paymentIds) {
                        params = params.addInteger(id);
                    }
                    pool.preparedQuery(sql)
                            .execute(params)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(u -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", u.rowCount() + " line item(s) rejected")
                                    .put("rejectedCount", u.rowCount())));
                });
    }

    // ---- DISBURSE_PAYROLL (anchor only) --------------------------------------------

    private void disburse(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!isAnchor(payload)) {
            replyError(message, "Only an anchor administrator can disburse payroll");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();

        pool.preparedQuery("SELECT id FROM payment_cycles WHERE cycle_code=@p1 AND status='APPROVED' AND (@p2=1 OR anchor_id=@p3)")
                .execute(Tuple.of(cycleCode, isSystemAdmin(payload), TenantScope.anchorId(payload)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Only an approved cycle can be disbursed");
                        return;
                    }
                    int cycleId = Rows.intVal(rows.iterator().next(), "id");
                    pool.preparedQuery("UPDATE payment_cycles SET status='DISBURSED', disbursed_at=GETDATE(), updated_at=GETDATE() WHERE id=@p1")
                            .execute(Tuple.of(cycleId))
                            .compose(u -> pool.preparedQuery("UPDATE payments SET status=1 WHERE payment_cycle_id=@p1 AND rejected=0")
                                    .execute(Tuple.of(cycleId)))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(u -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Payroll cycle disbursed")));
                });
    }

    // ---- DELETE_PAYROLL (draft/pending only) ---------------------------------------

    private void delete(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String cycleCode = payload.getString("cycleCode", "").trim();
        String scopeClause = isAnchor(payload) ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2";

        pool.preparedQuery("SELECT id FROM payment_cycles WHERE cycle_code=@p1 AND status IN ('DRAFT','PENDING_APPROVAL')" + scopeClause)
                .execute(isAnchor(payload) ? Tuple.of(cycleCode, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(cycleCode, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Only a draft or pending-approval cycle in your organisation can be deleted");
                        return;
                    }
                    int cycleId = Rows.intVal(rows.iterator().next(), "id");
                    pool.preparedQuery("DELETE FROM payments WHERE payment_cycle_id=@p1")
                            .execute(Tuple.of(cycleId))
                            .compose(d -> pool.preparedQuery("DELETE FROM payment_cycles WHERE id=@p1").execute(Tuple.of(cycleId)))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(d -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Payroll cycle deleted")));
                });
    }

    // ---- GET_PAYROLL / GET_PAYROLLS -------------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String cycleCode = payload.getString("cycleCode", "").trim();
        String scopeClause = isAnchor(payload) ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2";

        pool.preparedQuery("SELECT * FROM payment_cycles WHERE cycle_code=@p1" + scopeClause)
                .execute(isAnchor(payload) ? Tuple.of(cycleCode, isSystemAdmin(payload), TenantScope.anchorId(payload)) : Tuple.of(cycleCode, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Payroll cycle not found").put("results", new JsonArray()));
                        return;
                    }
                    Row cycleRow = rows.iterator().next();
                    int cycleId = Rows.intVal(cycleRow, "id");
                    // Line items for the "view more" panel and the approval dialog's
                    // per-row reject checkboxes -- both read off this same array.
                    pool.preparedQuery("SELECT * FROM payments WHERE payment_cycle_id=@p1 ORDER BY household_name")
                            .execute(Tuple.of(cycleId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(paymentRows -> {
                                JsonArray payments = new JsonArray();
                                for (Row pr : paymentRows) {
                                    payments.add(paymentSummary(pr));
                                }
                                reply(message, new JsonObject()
                                        .put("responseCode", "000")
                                        .put("responseMessage", "Payroll cycle found")
                                        .put("results", new JsonArray().add(summary(cycleRow)))
                                        .put("payments", payments));
                            });
                });
    }

    private void retrieveAll(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String status = payload.getString("status", null);
        boolean systemAdmin = isSystemAdmin(payload);
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        // payment_cycles already carries anchor_id directly, so a plain anchor admin is
        // scoped to it with no join needed. @p4 is NULL for a system admin with no target
        // anchor chosen (browse everything), the requested target anchor once chosen, or
        // the caller's own anchor for an anchor admin.
        String sql = "SELECT * FROM payment_cycles WHERE (@p1 IS NULL OR organization_code=@p1) AND (@p2 IS NULL OR status=@p2) "
                + "AND (@p4 IS NULL OR anchor_id=@p4) ORDER BY created_at DESC";
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", null) : payload.getString("partnerCode", "");

        pool.preparedQuery(sql)
                .execute(Tuple.of(partnerCode, status).addBoolean(systemAdmin).addInteger(anchorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(summary(r));
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", results.isEmpty() ? "No payroll cycles found" : "Payroll cycles found")
                            .put("results", results));
                });
    }

    private static JsonObject summary(Row r) {
        Double rate = dblSafe(r, "exchange_rate");
        double exchangeRate = rate == null ? 1.0 : rate;
        Double totalAmount = Rows.dbl(r, "total_amount");
        return new JsonObject()
                .put("cycleCode", Rows.str(r, "cycle_code"))
                .put("organisationCode", Rows.str(r, "organization_code"))
                .put("anchorId", Rows.intVal(r, "anchor_id"))
                .put("periodStart", Rows.str(r, "period_start"))
                .put("periodEnd", Rows.str(r, "period_end"))
                .put("amountPerHousehold", Rows.dbl(r, "amount_per_household"))
                .put("householdCount", Rows.intVal(r, "household_count"))
                .put("totalAmount", totalAmount)
                // currency/exchange_rate arrive with migration 021; guard the read so cycles
                // still list/load if that migration hasn't been applied to this environment yet.
                .put("currency", strSafe(r, "currency"))
                .put("exchangeRate", exchangeRate)
                .put("amountOut", totalAmount)
                .put("amountIn", totalAmount == null ? null : totalAmount * exchangeRate)
                .put("status", Rows.str(r, "status"))
                .put("makerId", Rows.intVal(r, "maker_id"))
                .put("makerAt", Rows.str(r, "maker_at"))
                .put("checkerId", Rows.intVal(r, "checker_id"))
                .put("checkerAt", Rows.str(r, "checker_at"))
                .put("rejectionReason", Rows.str(r, "rejection_reason"))
                .put("disbursedAt", Rows.str(r, "disbursed_at"))
                .put("createdAt", Rows.str(r, "created_at"));
    }

    private static JsonObject paymentSummary(Row r) {
        Double rate = dblSafe(r, "exchange_rate");
        double exchangeRate = rate == null ? 1.0 : rate;
        Double amount = Rows.dbl(r, "amount");
        return new JsonObject()
                .put("id", Rows.intVal(r, "id"))
                .put("uuid", Rows.str(r, "uuid"))
                .put("householdNumber", Rows.str(r, "household_number"))
                .put("householdName", Rows.str(r, "household_name"))
                .put("gender", Rows.str(r, "gender"))
                .put("bomaCode", Rows.str(r, "boma_code"))
                .put("amount", amount)
                .put("currency", strSafe(r, "currency"))
                .put("exchangeRate", exchangeRate)
                .put("amountOut", amount)
                .put("amountIn", amount == null ? null : amount * exchangeRate)
                .put("status", Rows.intVal(r, "status"))
                .put("approved", Rows.intVal(r, "approved"))
                // rejected/rejected* arrive with migration 021; same defensive read as above.
                .put("rejected", intSafe(r, "rejected"))
                .put("rejectionReason", strSafe(r, "rejection_reason"))
                .put("createdAt", Rows.str(r, "created_at"));
    }

    /** Null-safe text read of a column that may not exist on this row (pre-migration-021 rows). */
    private static String strSafe(Row r, String column) {
        return r.getColumnIndex(column) < 0 ? null : Rows.str(r, column);
    }

    /** Null-safe integer read of a column that may not exist on this row (pre-migration-021 rows). */
    private static Integer intSafe(Row r, String column) {
        return r.getColumnIndex(column) < 0 ? null : Rows.intVal(r, column);
    }

    /** Null-safe double read of a column that may not exist on this row (pre-migration-021 rows). */
    private static Double dblSafe(Row r, String column) {
        return r.getColumnIndex(column) < 0 ? null : Rows.dbl(r, column);
    }
}
