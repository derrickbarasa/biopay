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
import java.util.Set;
import com.biopay.databases.Datasource;
import com.biopay.utilities.EligibleApprovers;
import com.biopay.utilities.Logging;
import com.biopay.utilities.PaymentCycleApprovals;
import com.biopay.utilities.Rows;
import com.biopay.utilities.TenantScope;
import com.biopay.utilities.Utilities;

/**
 * Payroll cycles: generate (maker, anyone with ACCESS_PAYMENT_CYCLES -- an organisation's own
 * user, or an anchor generating on that organisation's behalf) -> approve (checker) -> disburse.
 * The organisation is always the approving and disbursing authority for its own cycles -- the
 * anchor never approves or disburses, even for a cycle it generated itself. {@link #approve},
 * {@link #reject} and {@link #disburse} are gated on both {@link TenantScope#isOrganisationAdministrator}
 * (organisation scope) and the dedicated {@code CHECK_PAYMENT_CYCLES} permission (see
 * PermissionPolicy.java) -- deliberately separate from the general {@code ACCESS_PAYMENT_CYCLES}
 * a maker only needs, so an organisation can grant "generate/view" to some users and
 * "approve/disburse" to a distinct, smaller set. Both generation and approval require a fresh
 * EMAIL OTP (see {@link OtpService}).
 *
 * <p>An organisation may require more than one of its own approvers to sign off on a cycle --
 * {@code required_approvals}, its own policy (set only by itself, see
 * {@link com.biopay.services.Organization#setApprovalPolicy}), is snapshotted onto the cycle at
 * generation time and enforced by {@link com.biopay.utilities.PaymentCycleApprovals}, shared with
 * the email-link approval path in {@link Approval#confirmRequest}. Any organisation user with
 * the approval permission, including the cycle's maker, may contribute an approval.
 */
public class Payroll extends AbstractVerticle {

    private record BeneficiaryAmount(String householdNumber, double amountFcy, double exchangeRate, double amountLcy) {}

    EventBus eventBus;
    MSSQLPool pool;
    OtpService otpService;

    private static final String APPROVALS_RECORDED_SUBQUERY =
            "(SELECT COUNT(*) FROM payment_cycle_approvals pca WHERE pca.payment_cycle_id=payment_cycles.id) AS approvals_recorded";

    /** The most approvals a cycle could ever collect: its organisation's own active users who
     *  hold CHECK_PAYMENT_CYCLES. If this ever falls below required_approvals, the cycle can never clear approval no matter how
     *  long it waits -- see #generate's own warning at creation time. */
    private static final String ELIGIBLE_APPROVERS_SUBQUERY =
            "(SELECT COUNT(DISTINCT u.id) FROM users u "
                    + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                    + "JOIN permissions p ON p.id=rp.permission_id AND p.permission_name='CHECK_PAYMENT_CYCLES' "
                    + "WHERE u.active=1 AND u.status=1 AND u.user_scope='ORGANISATION' "
                    + "AND u.organization_code=payment_cycles.organization_code) AS eligible_approvers";

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
        String referenceCode = payload.getString("cycleCode", "");

        if (!"GENERATE".equals(action) && !"APPROVE".equals(action)) {
            replyError(message, "action must be GENERATE or APPROVE");
            return;
        }
        String referenceType = "APPROVE".equals(action) ? "PAYROLL_APPROVE" : "PAYROLL_GENERATE";
        // Do not trust an email supplied by the browser: every OTP must go to the signed-in
        // user's own account email. This ensures each approver receives their own code.
        pool.preparedQuery("SELECT email FROM users WHERE id=@p1 AND active=1 AND status=1")
                .execute(Tuple.of(actorId(payload)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Your active account email could not be found");
                        return;
                    }
                    String email = Rows.str(rows.iterator().next(), "email");
                    if (email == null || email.trim().isEmpty()) {
                        replyError(message, "Your account needs an email address before a verification code can be sent");
                        return;
                    }
                    otpService.request(referenceType, referenceCode, actorId(payload), payload.getString("actorRole", ""), email.trim())
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Verification code sent to your account email")));
                });
    }

    // ---- GENERATE_PAYROLL (maker) --------------------------------------------------

    private void generate(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? payload.getString("organisationCode", "") : payload.getString("partnerCode", "");
        String periodStart = payload.getString("periodStart", "");
        String periodEnd = payload.getString("periodEnd", "");
        String otpCode = payload.getString("otpCode", "").trim();
        String currency = payload.getString("currency", "USD").trim().toUpperCase();
        List<BeneficiaryAmount> beneficiaries = new ArrayList<>();
        JsonArray beneficiaryRows = payload.getJsonArray("beneficiaries", new JsonArray());
        for (Object value : beneficiaryRows) {
            if (!(value instanceof JsonObject row)) continue;
            String householdNumber = row.getString("householdNumber", "").trim();
            Double amountFcy = row.getDouble("amountFcy");
            Double exchangeRate = row.getDouble("exchangeRate");
            Double amountLcy = row.getDouble("amountLcy");
            if (householdNumber.isEmpty() || amountFcy == null || amountFcy <= 0
                    || exchangeRate == null || exchangeRate <= 0 || amountLcy == null || amountLcy <= 0
                    || Math.abs(amountLcy - amountFcy * exchangeRate) > 0.011) {
                replyError(message, "Every beneficiary needs positive FCY, exchange-rate and LCY values; LCY must equal FCY multiplied by the rate");
                return;
            }
            if (beneficiaries.stream().anyMatch(existing -> existing.householdNumber().equals(householdNumber))) {
                replyError(message, "Each household may appear only once in a payment cycle");
                return;
            }
            beneficiaries.add(new BeneficiaryAmount(householdNumber, amountFcy, exchangeRate, amountLcy));
        }

        // Backward compatibility for existing API integrations: the dashboard now sends
        // beneficiary rows, while the documented legacy payload may still send one amount/rate.
        if (beneficiaries.isEmpty()) {
            Double amountPerHousehold = payload.getDouble("amountPerHousehold");
            Double exchangeRateVal = payload.getDouble("exchangeRate", 1.0);
            double exchangeRate = exchangeRateVal == null ? 1.0 : exchangeRateVal;
            if (amountPerHousehold == null || amountPerHousehold <= 0 || exchangeRate <= 0) {
                replyError(message, "At least one beneficiary amount is required");
                return;
            }
            for (Object value : payload.getJsonArray("householdNumbers", new JsonArray())) {
                if (value == null || value.toString().trim().isEmpty()) continue;
                String householdNumber = value.toString().trim();
                beneficiaries.add(new BeneficiaryAmount(householdNumber, amountPerHousehold, exchangeRate,
                        Math.round(amountPerHousehold * exchangeRate * 100.0) / 100.0));
            }
        }
        List<String> householdNumbers = new ArrayList<>();
        for (BeneficiaryAmount beneficiary : beneficiaries) householdNumbers.add(beneficiary.householdNumber());

        if (partnerCode.isEmpty() || periodStart.isEmpty() || periodEnd.isEmpty()) {
            replyError(message, "organisationCode, periodStart and periodEnd are required");
            return;
        }
        if (householdNumbers.isEmpty()) {
            replyError(message, "At least one householdNumbers entry is required -- select which households this cycle is for");
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
                    resolveOrgPolicy(payload, partnerCode)
                            .onFailure(err -> replyError(message, err.getMessage()))
                            .onSuccess(orgPolicy -> countActiveHouseholds(partnerCode, householdNumbers)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(householdCount -> {
                                if (householdCount != beneficiaries.size()) {
                                    replyError(message, "One or more selected households are not active in this organisation");
                                    return;
                                }
                                double total = beneficiaries.stream().mapToDouble(BeneficiaryAmount::amountFcy).sum();
                                double totalLcy = beneficiaries.stream().mapToDouble(BeneficiaryAmount::amountLcy).sum();
                                double firstAmount = beneficiaries.get(0).amountFcy();
                                double firstRate = beneficiaries.get(0).exchangeRate();
                                Double amountPerHousehold = beneficiaries.stream().allMatch(row -> Math.abs(row.amountFcy() - firstAmount) < 0.001)
                                        ? firstAmount : null;
                                Double cycleExchangeRate = beneficiaries.stream().allMatch(row -> Math.abs(row.exchangeRate() - firstRate) < 0.000001)
                                        ? firstRate : null;
                                String cycleCode = Utilities.generateCode("PAYROLL");

                                // Every cycle starts PENDING_APPROVAL and is decided by the organisation's
                                // own approvers -- see the class javadoc. This is true even when an Anchor
                                // Administrator generates it on an organisation's behalf: the anchor is
                                // never the approving authority for a payment cycle. requiredApprovals is
                                // the organisation's own policy (Organization#setApprovalPolicy), snapshotted
                                // here so a later policy change never retroactively changes how many
                                // approvals an already-pending cycle needs.
                                int requiredApprovals = orgPolicy.requiredApprovals();

                                // Cycle header + its payment line items must land together --
                                // a mid-way failure here must not leave a cycle with zero (or
                                // partial) line items sitting in PENDING_APPROVAL.
                                pool.withTransaction(client -> {
                                    String sql = "INSERT INTO payment_cycles (cycle_code, organization_code, anchor_id, period_start, period_end, "
                                            + "amount_per_household, household_count, total_amount, total_amount_lcy, currency, exchange_rate, status, "
                                            + "required_approvals, maker_id, maker_at, otp_verified, created_by, created_at) "
                                            + "OUTPUT INSERTED.id "
                                            + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,'PENDING_APPROVAL',"
                                            + "@p12,@p13,GETDATE(),1,@p14,GETDATE())";
                                    return client.preparedQuery(sql)
                                            .execute(Tuple.of(cycleCode, partnerCode, orgPolicy.anchorId(),
                                                    periodStart, periodEnd, amountPerHousehold, householdCount, total, totalLcy, currency, cycleExchangeRate,
                                                    requiredApprovals, actorId(payload), String.valueOf(actorId(payload))))
                                            .compose(insertRows -> {
                                                int cycleId = Rows.intVal(insertRows.iterator().next(), "id");
                                                return createLineItems(client, cycleId, cycleCode, partnerCode, orgPolicy.anchorId(),
                                                        periodStart, periodEnd, currency, actorId(payload), beneficiaries);
                                            });
                                }).onFailure(err -> onDbError(message, err))
                                  .onSuccess(lineCount -> {
                                      JsonObject response = new JsonObject()
                                              .put("responseCode", "000")
                                              .put("cycleCode", cycleCode)
                                              .put("householdCount", householdCount)
                                              .put("totalAmount", total)
                                              .put("totalAmountLcy", totalLcy)
                                              .put("currency", currency)
                                              .put("exchangeRate", cycleExchangeRate)
                                              .put("variableAmounts", amountPerHousehold == null)
                                              .put("requiredApprovals", requiredApprovals);
                                      JsonObject approvalRequest = new JsonObject()
                                              .put("requestType", "PAYROLL")
                                              .put("referenceCode", cycleCode)
                                              .put("anchorId", orgPolicy.anchorId())
                                              .put("organisationCode", partnerCode)
                                              .put("makerId", actorId(payload))
                                              .put("title", "Payment cycle approval")
                                              .put("summary", householdCount + " households · " + currency + " "
                                                      + String.format(java.util.Locale.ROOT, "%,.2f", total) + " total");
                                      eventBus.<Object>request("CREATE_APPROVAL_REQUEST", approvalRequest).onComplete(notification -> {
                                          int recipientCount = approvalRecipientCount(notification.succeeded() ? notification.result().body() : null);
                                          response.put("approvalRecipientCount", recipientCount)
                                                  .put("approvalEmailSent", recipientCount > 0);
                                          // Warn -- never block -- when the organisation does not currently
                                          // have enough eligible people to reach this cycle's threshold.
                                          EligibleApprovers.count(pool, partnerCode, null)
                                                  .onComplete(eligibleResult -> {
                                                      int eligible = eligibleResult.succeeded() ? eligibleResult.result() : requiredApprovals;
                                                      boolean insufficientApprovers = eligible < requiredApprovals;
                                                      response.put("eligibleApprovers", eligible)
                                                              .put("insufficientApprovers", insufficientApprovers);
                                                      if (insufficientApprovers) {
                                                          response.put("responseMessage", "Payroll cycle generated, but only " + eligible
                                                                  + " of your organisation's other users can approve it -- this cycle needs "
                                                                  + requiredApprovals + " and may get stuck until more people are granted "
                                                                  + "payment-cycle access.");
                                                      } else {
                                                          response.put("responseMessage", recipientCount > 0
                                                                  ? "Payroll cycle generated; the approver has been emailed"
                                                                  : "Payroll cycle generated and pending approval");
                                                      }
                                                      reply(message, response);
                                                  });
                                      });
                                  });
                            }));
                });
    }

    private static int approvalRecipientCount(Object replyBody) {
        if (replyBody == null) return 0;
        try {
            JsonObject response = replyBody instanceof JsonObject
                    ? (JsonObject) replyBody
                    : new JsonObject(replyBody.toString());
            return "000".equals(response.getString("responseCode"))
                    ? response.getInteger("recipientCount", 0)
                    : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Resolves organisationCode's real anchor and its own payment-cycle approval policy
     *  server-side rather than trusting whatever the client sent -- fails if the organisation
     *  doesn't exist or (for an Anchor Administrator) isn't inside their own anchor. The Super
     *  Admin may target any anchor. */
    private static final String ORG_OUTSIDE_ANCHOR = "Organisation is outside your anchor";

    private record OrgPolicy(Integer anchorId, int requiredApprovals) {}

    private Future<OrgPolicy> resolveOrgPolicy(JsonObject payload, String organisationCode) {
        return pool.preparedQuery("SELECT anchor_id, required_approvals FROM organizations "
                        + "WHERE organization_code=@p1 AND (@p2=1 OR anchor_id=@p3)")
                .execute(Tuple.of(organisationCode, isSystemAdmin(payload), TenantScope.anchorId(payload)))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.<OrgPolicy>failedFuture(ORG_OUTSIDE_ANCHOR);
                    }
                    Row org = rows.iterator().next();
                    Integer required = Rows.intVal(org, "required_approvals");
                    return Future.succeededFuture(new OrgPolicy(Rows.intVal(org, "anchor_id"),
                            required == null ? 1 : required));
                })
                // A DB-level failure here (timeout, connection drop, ...) must not reach the
                // client as raw driver text the way the deliberate ORG_OUTSIDE_ANCHOR message does.
                .recover(err -> ORG_OUTSIDE_ANCHOR.equals(err.getMessage())
                        ? Future.failedFuture(err)
                        : Future.failedFuture(logDbFailureAndMask(err)));
    }

    private String logDbFailureAndMask(Throwable err) {
        Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3);
        return "Failed with an error";
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
            String partnerCode, Object anchorIdVal, String periodStart, String periodEnd,
            String currency, int makerId, List<BeneficiaryAmount> beneficiaries) {
        // uuid has a UNIQUE index (UQ_payments_UUID) -- SQL Server allows at most one NULL in a
        // unique index, so every row here needs its own value; NEWID() supplies a fresh one per
        // row within the single INSERT...SELECT (a bound Tuple parameter would repeat the same
        // value for all rows and collide after the first).
        String sql = "INSERT INTO payments (household_number, household_name, gender, boma_code, organization_code, anchor_id, "
                + "payment_cycle_id, cycle, date_from, date_to, amount, amount_lcy, status, approved, uuid, currency, exchange_rate, "
                + "created_by, created_at) "
                + "SELECT household_number, household_name, gender, boma_code, @p1, @p2, "
                + "@p3, @p4, @p5, @p6, @p7, @p10, 0, 0, CONVERT(VARCHAR(50), NEWID()), @p8, @p9, @p11, GETDATE() "
                + "FROM households WHERE organization_code=@p1 AND status=1 AND household_number=@p12";
        Object anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        List<Tuple> batch = new ArrayList<>();
        for (BeneficiaryAmount beneficiary : beneficiaries) {
            batch.add(Tuple.of(partnerCode, anchorId, cycleId, cycleCode, periodStart, periodEnd,
                    beneficiary.amountFcy(), currency, beneficiary.exchangeRate(), beneficiary.amountLcy(),
                    makerId, beneficiary.householdNumber()));
        }
        return client.preparedQuery(sql)
                .executeBatch(batch)
                .map(rows -> beneficiaries.size());
    }

    // ---- APPROVE_PAYROLL (checker -- the organisation's own approvers only) --------

    /** Business errors from the approval path that are safe to show the caller verbatim,
     *  as opposed to a raw DB failure that must be logged and masked (see #onApprovalFailure). */
    private static final Set<String> KNOWN_APPROVAL_ERRORS = Set.of(
            "Only a cycle pending approval can be approved",
            "You have already approved this payment cycle");

    private void onApprovalFailure(Message<Object> message, Throwable err) {
        if (KNOWN_APPROVAL_ERRORS.contains(err.getMessage())) {
            replyError(message, err.getMessage());
        } else {
            onDbError(message, err);
        }
    }

    private void approve(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isOrganisationAdministrator(payload)) {
            replyError(message, "Only the organisation can approve its own payment cycles");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();
        String otpCode = payload.getString("otpCode", "").trim();
        if (cycleCode.isEmpty() || otpCode.isEmpty()) {
            replyError(message, "cycleCode and otpCode are required");
            return;
        }

        pool.preparedQuery("SELECT id, status, required_approvals FROM payment_cycles "
                        + "WHERE cycle_code=@p1 AND organization_code=@p2")
                .execute(Tuple.of(cycleCode, payload.getString("partnerCode", "")))
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
                    int cycleId = Rows.intVal(r, "id");
                    Integer requiredVal = Rows.intVal(r, "required_approvals");
                    int required = requiredVal == null ? 1 : requiredVal;

                    otpService.verify("PAYROLL_APPROVE", cycleCode, actorId(payload), otpCode)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(verified -> {
                                if (!Boolean.TRUE.equals(verified)) {
                                    replyError(message, "Invalid or expired verification code");
                                    return;
                                }
                                pool.withTransaction(connection -> connection.preparedQuery(
                                                "SELECT 1 FROM payment_cycles WITH (UPDLOCK, ROWLOCK) WHERE id=@p1 AND status='PENDING_APPROVAL'")
                                        .execute(Tuple.of(cycleId))
                                        .compose(lockRows -> lockRows.size() == 0
                                                ? Future.<PaymentCycleApprovals.Outcome>failedFuture("Only a cycle pending approval can be approved")
                                                : PaymentCycleApprovals.recordApproval(connection, cycleId, required, actorId(payload))))
                                        .onFailure(err -> onApprovalFailure(message, err))
                                        .onSuccess(outcome -> reply(message, new JsonObject()
                                                .put("responseCode", "000")
                                                .put("responseMessage", outcome.approved
                                                        ? "Payroll cycle approved"
                                                        : outcome.approvalCount + " of " + outcome.requiredApprovals + " approvals recorded")
                                                .put("approvalsRecorded", outcome.approvalCount)
                                                .put("approvalsRequired", outcome.requiredApprovals)
                                                .put("fullyApproved", outcome.approved)));
                            });
                });
    }

    // ---- REJECT_PAYROLL (checker -- the organisation's own approvers only) ---------

    private void reject(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isOrganisationAdministrator(payload)) {
            replyError(message, "Only the organisation can reject its own payment cycles");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();
        String reason = payload.getString("reason", "").trim();

        Tuple params = Tuple.of(actorId(payload), reason, cycleCode, payload.getString("partnerCode", ""));
        pool.withTransaction(connection -> connection.preparedQuery(
                        "UPDATE payment_cycles SET status='REJECTED', checker_id=@p1, checker_at=GETDATE(), "
                                + "rejection_reason=@p2, updated_at=GETDATE() WHERE cycle_code=@p3 "
                                + "AND status='PENDING_APPROVAL' AND organization_code=@p4")
                .execute(params)
                .compose(rows -> {
                    if (rows.rowCount() == 0) {
                        return Future.failedFuture("Cycle not found or not pending approval");
                    }
                    // A rejected cycle never became a real payable instruction. Keep its line
                    // items for the cycle's audit view, but mark them rejected in the same
                    // transaction so payment lists, summaries and devices cannot offer them.
                    return connection.preparedQuery("UPDATE payments SET rejected=1, rejected_by=@p1, "
                                    + "rejected_at=GETDATE(), rejection_reason=@p2, updated_at=GETDATE() "
                                    + "WHERE payment_cycle_id=(SELECT id FROM payment_cycles WHERE cycle_code=@p3) "
                                    + "AND rejected=0")
                            .execute(Tuple.of(actorId(payload), reason, cycleCode));
                }))
                .onFailure(err -> {
                    if ("Cycle not found or not pending approval".equals(err.getMessage())) {
                        replyError(message, err.getMessage());
                    } else {
                        onDbError(message, err);
                    }
                })
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Payroll cycle rejected")));
    }

    // ---- REJECT_PAYROLL_ITEMS (the cycle's own maker, or any of the organisation's own
    //      checkers; pending-approval cycles only) --------------------------------------

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

        pool.preparedQuery("SELECT id, maker_id, organization_code FROM payment_cycles WHERE cycle_code=@p1 AND status='PENDING_APPROVAL' "
                        + "AND (@p2=1 OR organization_code=@p3 OR maker_id=@p4)")
                .execute(Tuple.of(cycleCode, isSystemAdmin(payload), payload.getString("partnerCode", ""), actorId(payload)))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Only a cycle pending approval can have line items rejected");
                        return;
                    }
                    Row cycleRow = rows.iterator().next();
                    // Either a checker from the cycle's own organisation, or the cycle's own
                    // maker/generator (who may belong to the anchor that generated it on the
                    // organisation's behalf), can reject line items pre-approval -- the maker
                    // picks who's in a cycle up front, then can still trim it down before it
                    // goes to approval.
                    Integer makerId = Rows.intVal(cycleRow, "maker_id");
                    boolean isMaker = makerId != null && makerId == actorId(payload);
                    boolean isOrgChecker = TenantScope.isOrganisationAdministrator(payload)
                            && Rows.str(cycleRow, "organization_code").equals(payload.getString("partnerCode", ""));
                    if (!isSystemAdmin(payload) && !isOrgChecker && !isMaker) {
                        replyError(message, "Only the cycle's generator or the organisation can reject payroll line items");
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

    // ---- DISBURSE_PAYROLL (the organisation's own administrator only) --------------

    private void disburse(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!TenantScope.isOrganisationAdministrator(payload)) {
            replyError(message, "Only the organisation can disburse its own payment cycles");
            return;
        }
        String cycleCode = payload.getString("cycleCode", "").trim();

        pool.preparedQuery("SELECT id FROM payment_cycles WHERE cycle_code=@p1 AND status='APPROVED' AND organization_code=@p2")
                .execute(Tuple.of(cycleCode, payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Only an approved cycle can be disbursed");
                        return;
                    }
                    int cycleId = Rows.intVal(rows.iterator().next(), "id");
                    // Disbursement releases approved entitlements to the field. It does not
                    // prove that a beneficiary received money: each payment remains pending
                    // until RECORD_FIELD_PAYMENT records a successful biometric verification,
                    // or PAY_PAYMENT_ONLINE recovers it after a failed field attempt.
                    pool.preparedQuery("UPDATE payment_cycles SET status='DISBURSED', disbursed_at=GETDATE(), updated_at=GETDATE() WHERE id=@p1")
                            .execute(Tuple.of(cycleId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(u -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Payment funds released for beneficiary verification")));
                });
    }


    // ---- GET_PAYROLL / GET_PAYROLLS -------------------------------------------------

    private void getOne(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String cycleCode = payload.getString("cycleCode", "").trim();
        String scopeClause = isAnchor(payload) ? " AND (@p2=1 OR anchor_id=@p3)" : " AND organization_code=@p2";

        pool.preparedQuery("SELECT *, " + APPROVALS_RECORDED_SUBQUERY + ", " + ELIGIBLE_APPROVERS_SUBQUERY
                        + " FROM payment_cycles WHERE cycle_code=@p1" + scopeClause)
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
        String sql = "SELECT *, " + APPROVALS_RECORDED_SUBQUERY + ", " + ELIGIBLE_APPROVERS_SUBQUERY
                + " FROM payment_cycles WHERE (@p1 IS NULL OR organization_code=@p1) AND (@p2 IS NULL OR status=@p2) "
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
        Double totalAmount = Rows.dbl(r, "total_amount");
        Double storedTotalLcy = dblSafe(r, "total_amount_lcy");
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
                .put("exchangeRate", rate)
                .put("variableAmounts", Rows.dbl(r, "amount_per_household") == null)
                .put("amountOut", totalAmount)
                .put("amountIn", storedTotalLcy != null ? storedTotalLcy : totalAmount == null || rate == null ? null : totalAmount * rate)
                .put("status", Rows.str(r, "status"))
                .put("makerId", Rows.intVal(r, "maker_id"))
                .put("makerAt", Rows.str(r, "maker_at"))
                .put("checkerId", Rows.intVal(r, "checker_id"))
                .put("checkerAt", Rows.str(r, "checker_at"))
                .put("rejectionReason", Rows.str(r, "rejection_reason"))
                .put("disbursedAt", Rows.str(r, "disbursed_at"))
                .put("createdAt", Rows.str(r, "created_at"))
                // required_approvals/approvals_recorded arrive with migration 060; guard the
                // read the same way as the migration-021 columns above.
                .put("requiredApprovals", intSafe(r, "required_approvals") == null ? 1 : intSafe(r, "required_approvals"))
                .put("approvalsRecorded", intSafe(r, "approvals_recorded") == null ? 0 : intSafe(r, "approvals_recorded"))
                .put("eligibleApprovers", intSafe(r, "eligible_approvers"));
    }

    private static JsonObject paymentSummary(Row r) {
        Double rate = dblSafe(r, "exchange_rate");
        double exchangeRate = rate == null ? 1.0 : rate;
        Double amount = Rows.dbl(r, "amount");
        Double storedAmountLcy = dblSafe(r, "amount_lcy");
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
                .put("amountIn", storedAmountLcy != null ? storedAmountLcy : amount == null ? null : amount * exchangeRate)
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
