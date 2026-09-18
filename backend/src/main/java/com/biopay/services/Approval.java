package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.EmailTemplates;
import com.biopay.utilities.Env;
import com.biopay.utilities.Hashing;
import com.biopay.utilities.Logging;
import com.biopay.utilities.PaymentCycleApprovals;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;
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
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Secure email approval links shared by the current approval workflows. */
public class Approval extends AbstractVerticle {

    private static final Set<String> REQUEST_TYPES = Set.of("PAYROLL", "HOUSEHOLD");
    private static final String LINK_UNAVAILABLE = "This approval link is invalid, expired, or has already been used";

    private EventBus eventBus;
    private MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) {
        System.out.println("deploymentId Approval =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();
        eventBus.consumer("CREATE_APPROVAL_REQUEST", this::createRequest);
        eventBus.consumer("GET_EMAIL_APPROVAL", this::getRequest);
        eventBus.consumer("CONFIRM_EMAIL_APPROVAL", this::confirmRequest);
        startPromise.complete();
    }

    private static JsonObject payload(Message<Object> message) {
        return message.body() instanceof JsonObject
                ? (JsonObject) message.body()
                : new JsonObject(message.body().toString());
    }

    private static void reply(Message<Object> message, JsonObject value) {
        if (message.replyAddress() != null) {
            message.reply(value.encode());
        }
    }

    private static void replyError(Message<Object> message, String error) {
        reply(message, new JsonObject().put("responseCode", "999").put("responseMessage", error));
    }

    private static String str(JsonObject value, String key) {
        String text = value.getString(key);
        return text == null ? "" : text.trim();
    }

    private static int ttlHours() {
        try {
            return Math.max(1, Math.min(168, Integer.parseInt(Env.get().get("APPROVAL_LINK_TTL_HOURS", "24"))));
        } catch (NumberFormatException ignored) {
            return 24;
        }
    }

    private static String frontendBaseUrl() {
        String base = Env.get().get("FRONTEND_BASE_URL", "http://localhost:5173").trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private void createRequest(Message<Object> message) {
        JsonObject request = payload(message);
        String type = str(request, "requestType").toUpperCase();
        String reference = str(request, "referenceCode");
        Integer anchorId = request.getInteger("anchorId");
        String organizationCode = str(request, "organisationCode");
        Integer makerId = request.getInteger("makerId");

        if (!REQUEST_TYPES.contains(type) || reference.isEmpty()) {
            replyError(message, "A valid approval request type and reference are required");
            return;
        }
        if (organizationCode.isEmpty()) {
            replyError(message, "organisationCode is required for approval");
            return;
        }

        findApprovers(type, organizationCode, makerId)
                .compose(approvers -> supersedeExisting(type, reference)
                        .compose(v -> createTokens(type, reference, anchorId, organizationCode, request, approvers)))
                .onFailure(error -> {
                    Logging.applicationLog(Logging.logPreString() + "Approval request creation failed: "
                            + error.getMessage() + "\n\n", "", 3);
                    replyError(message, "The approval was created, but its email notification could not be prepared");
                })
                .onSuccess(count -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", count > 0
                                ? "Approval email sent to " + count + " approver(s)"
                                : "No eligible approver email was found")
                        .put("recipientCount", count)));
    }

    /** A payment cycle's approvers are always its own organisation's own users -- the anchor
     *  is never in the decisioning chain for a payment cycle (see Payroll.java's class
     *  javadoc), even when the anchor generated the cycle on the organisation's behalf. A
     *  household's approvers may still be either the organisation itself or its anchor. */
    private Future<RowSet<Row>> findApprovers(String type, String organizationCode, Integer makerId) {
        // A payment cycle's approvers need the dedicated checker permission (see
        // PermissionPolicy.java), not just the general ACCESS_PAYMENT_CYCLES every maker also holds.
        String permission = "PAYROLL".equals(type) ? "CHECK_PAYMENT_CYCLES" : "ACCESS_HOUSEHOLDS";
        String scope = "PAYROLL".equals(type)
                ? "u.user_scope='ORGANISATION' AND u.organization_code=@p2"
                : "((u.user_scope='ORGANISATION' AND u.organization_code=@p2) "
                        + "OR (u.user_scope='ANCHOR' AND (u.id=o.anchor_id OR u.anchor_id=o.anchor_id)))";
        // Household makers remain excluded from their own review. Payment-cycle makers are
        // ordinary eligible approvers when they hold CHECK_PAYMENT_CYCLES.
        boolean excludeMaker = "HOUSEHOLD".equals(type);
        String sql = "SELECT DISTINCT u.id, u.email, u.first_name, u.surname FROM users u "
                + "JOIN organizations o ON o.organization_code=@p2 AND o.status=1 "
                + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                + "JOIN permissions p ON p.id=rp.permission_id AND p.permission_name=@p1 "
                + "WHERE u.active=1 AND u.status=1 AND " + scope
                + (excludeMaker ? " AND (@p3 IS NULL OR u.id<>@p3)" : "")
                + " AND NULLIF(LTRIM(RTRIM(u.email)),'') IS NOT NULL";
        Tuple params = Tuple.of(permission, organizationCode);
        if (excludeMaker) {
            params = params.addInteger(makerId);
        }
        return pool.preparedQuery(sql).execute(params);
    }

    private Future<Void> supersedeExisting(String type, String reference) {
        return pool.preparedQuery("UPDATE email_approval_requests SET used_at=GETDATE(), decision='SUPERSEDED' "
                        + "WHERE request_type=@p1 AND reference_code=@p2 AND used_at IS NULL")
                .execute(Tuple.of(type, reference))
                .mapEmpty();
    }

    private Future<Integer> createTokens(String type, String reference, Integer anchorId, String organizationCode,
            JsonObject request, RowSet<Row> approvers) {
        List<Future<?>> writes = new ArrayList<>();
        int expiryHours = ttlHours();
        JsonObject requestData = new JsonObject()
                .put("title", str(request, "title"))
                .put("summary", str(request, "summary"));

        for (Row approver : approvers) {
            int approverId = Rows.intVal(approver, "id");
            String email = Rows.str(approver, "email");
            String name = displayName(approver);
            String rawToken = Utilities.newUuid().replace("-", "") + Utilities.newUuid().replace("-", "");
            String tokenHash = Hashing.sha256Hex(rawToken);
            String actionUrl = frontendBaseUrl() + "/approval?token=" + rawToken;

            JsonObject emailMessage = new JsonObject()
                    .put("mailTo", email)
                    .put("subject", "Approval needed: " + reference)
                    .put("msg", EmailTemplates.approvalRequestEmail(name, requestData.getString("title"),
                            reference, requestData.getString("summary"), actionUrl, expiryHours))
                    .put("inlineImages", EmailTemplates.logoInlineImages());
            Future<?> write = pool.preparedQuery("INSERT INTO email_approval_requests "
                            + "(request_type, reference_code, approver_id, anchor_id, organization_code, token_hash, request_data, expires_at, created_at) "
                            + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,DATEADD(HOUR,@p8,GETDATE()),GETDATE())")
                    .execute(Tuple.of(type, reference, approverId, anchorId,
                            organizationCode.isEmpty() ? null : organizationCode, tokenHash, requestData.encode(), expiryHours))
                    // The approval request is durable once its token row is stored. Email delivery is
                    // intentionally fire-and-forget so a slow provider cannot hold the original save
                    // request open until the HTTP/event-bus timeout after the business row committed.
                    .onSuccess(v -> eventBus.send("EMAIL", emailMessage));
            writes.add(write);
        }
        if (writes.isEmpty()) {
            return Future.succeededFuture(0);
        }
        return Future.all(writes).map(approvers.size());
    }

    private static String displayName(Row approver) {
        String firstName = Rows.str(approver, "first_name");
        String surname = Rows.str(approver, "surname");
        return ((firstName == null ? "" : firstName) + " " + (surname == null ? "" : surname)).trim();
    }

    private void getRequest(Message<Object> message) {
        String rawToken = str(payload(message), "token");
        if (rawToken.isEmpty()) {
            replyError(message, LINK_UNAVAILABLE);
            return;
        }
        String tokenHash = Hashing.sha256Hex(rawToken);
        pool.preparedQuery("SELECT TOP 1 request_type, reference_code, request_data, expires_at, used_at, decision, "
                        + "CASE WHEN expires_at>GETDATE() AND used_at IS NULL THEN 1 ELSE 0 END AS available "
                        + "FROM email_approval_requests WHERE token_hash=@p1")
                .execute(Tuple.of(tokenHash))
                .compose(rows -> rows.size() == 0
                        ? Future.failedFuture(LINK_UNAVAILABLE)
                        : describe(rows.iterator().next()))
                .onFailure(error -> replyError(message, safeMessage(error)))
                .onSuccess(result -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("results", result)));
    }

    private Future<JsonObject> describe(Row requestRow) {
        String type = Rows.str(requestRow, "request_type");
        String reference = Rows.str(requestRow, "reference_code");
        boolean available = Integer.valueOf(1).equals(Rows.intVal(requestRow, "available"));
        JsonObject data = requestData(requestRow);
        String sql = "PAYROLL".equals(type)
                ? "SELECT status FROM payment_cycles WHERE cycle_code=@p1"
                : "SELECT review_status AS status FROM households WHERE household_number=@p1";
        return pool.preparedQuery(sql).execute(Tuple.of(reference)).map(rows -> {
            String currentStatus = rows.size() == 0 ? "UNAVAILABLE" : Rows.str(rows.iterator().next(), "status");
            boolean pending = "PAYROLL".equals(type)
                    ? "PENDING_APPROVAL".equals(currentStatus)
                    : currentStatus == null || "PENDING".equals(currentStatus) || "CHECKED".equals(currentStatus);
            String state = available && pending ? "PENDING" : normalizedState(currentStatus, requestRow);
            return new JsonObject()
                    .put("requestType", type)
                    .put("referenceCode", reference)
                    .put("title", data.getString("title", "Approval request"))
                    .put("summary", data.getString("summary", ""))
                    .put("expiresAt", Rows.str(requestRow, "expires_at"))
                    .put("state", state)
                    .put("canApprove", available && pending);
        });
    }

    private static String normalizedState(String currentStatus, Row requestRow) {
        if ("APPROVED".equals(currentStatus)) return "APPROVED";
        if ("REJECTED".equals(currentStatus)) return "REJECTED";
        String decision = Rows.str(requestRow, "decision");
        if (decision != null && !decision.isBlank()) return decision;
        return "EXPIRED";
    }

    private void confirmRequest(Message<Object> message) {
        String rawToken = str(payload(message), "token");
        if (rawToken.isEmpty()) {
            replyError(message, LINK_UNAVAILABLE);
            return;
        }
        String tokenHash = Hashing.sha256Hex(rawToken);
        pool.withTransaction(connection -> connection.preparedQuery(
                        "SELECT TOP 1 ear.* FROM email_approval_requests ear WITH (UPDLOCK, ROWLOCK) "
                                + "WHERE ear.token_hash=@p1 AND ear.used_at IS NULL AND ear.expires_at>GETDATE() "
                                + "AND EXISTS (SELECT 1 FROM users u "
                                + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                                + "JOIN permissions p ON p.id=rp.permission_id AND p.permission_name="
                                + "CASE WHEN ear.request_type='PAYROLL' THEN 'CHECK_PAYMENT_CYCLES' ELSE 'ACCESS_HOUSEHOLDS' END "
                                + "LEFT JOIN organizations o ON o.organization_code=ear.organization_code AND o.status=1 "
                                + "WHERE u.id=ear.approver_id AND u.active=1 AND u.status=1 AND "
                                + "((ear.request_type='PAYROLL' AND u.user_scope='ORGANISATION' "
                                + "AND u.organization_code=ear.organization_code) OR "
                                + "(ear.request_type='HOUSEHOLD' AND ((u.user_scope='ORGANISATION' "
                                + "AND u.organization_code=ear.organization_code) OR (u.user_scope='ANCHOR' "
                                + "AND (u.id=o.anchor_id OR u.anchor_id=o.anchor_id))))))")
                .execute(Tuple.of(tokenHash))
                .compose(rows -> rows.size() == 0
                        ? Future.failedFuture(LINK_UNAVAILABLE)
                        : approveTarget(connection, rows.iterator().next())))
                .onFailure(error -> replyError(message, safeMessage(error)))
                .onSuccess(result -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Approval completed")
                        .put("results", result)));
    }

    private Future<JsonObject> approveTarget(SqlClient connection, Row requestRow) {
        String type = Rows.str(requestRow, "request_type");
        String reference = Rows.str(requestRow, "reference_code");
        int approverId = Rows.intVal(requestRow, "approver_id");

        if ("PAYROLL".equals(type)) {
            // Only this approver's own token is consumed -- a payment cycle can need several
            // distinct approvers now, so the other outstanding approvers' links must stay live
            // (and keep showing PENDING) until the cycle actually clears its threshold.
            int requestId = Rows.intVal(requestRow, "id");
            return approvePaymentCycle(connection, reference, approverId)
                    .compose(outcome -> markSingleRequestUsed(connection, requestId)
                            .map(v -> new JsonObject()
                                    .put("requestType", type)
                                    .put("referenceCode", reference)
                                    .put("title", requestData(requestRow).getString("title", "Approval request"))
                                    .put("summary", requestData(requestRow).getString("summary", ""))
                                    .put("state", outcome.approved ? "APPROVED" : "PENDING")
                                    .put("approvalsRecorded", outcome.approvalCount)
                                    .put("approvalsRequired", outcome.requiredApprovals)
                                    .put("canApprove", false)));
        }

        return connection.preparedQuery("UPDATE households SET review_status='APPROVED', status=1, rejection_reason=NULL, "
                        + "updated_by=@p1, updated_at=GETDATE() WHERE household_number=@p2 "
                        + "AND (review_status IS NULL OR review_status IN ('PENDING','CHECKED'))")
                .execute(Tuple.of(String.valueOf(approverId), reference))
                .compose(updated -> {
                    if (updated.rowCount() == 0) {
                        return Future.failedFuture("This request has already been decided and cannot be approved again");
                    }
                    return markRequestUsed(connection, type, reference);
                }).map(v -> {
                    JsonObject data = requestData(requestRow);
                    return new JsonObject()
                            .put("requestType", type)
                            .put("referenceCode", reference)
                            .put("title", data.getString("title", "Approval request"))
                            .put("summary", data.getString("summary", ""))
                            .put("state", "APPROVED")
                            .put("canApprove", false);
                });
    }

    /** The cycle row is locked (see confirmRequest's own UPDLOCK on email_approval_requests --
     *  distinct approvers hold distinct token rows, so that alone doesn't serialize concurrent
     *  approvers of the *same* cycle) so two approvers submitting at once can't both cross the
     *  required-approvals threshold. */
    private Future<PaymentCycleApprovals.Outcome> approvePaymentCycle(SqlClient connection, String cycleCode, int approverId) {
        return connection.preparedQuery(
                        "SELECT id, required_approvals FROM payment_cycles WITH (UPDLOCK, ROWLOCK) WHERE cycle_code=@p1 "
                                + "AND status='PENDING_APPROVAL'")
                .execute(Tuple.of(cycleCode, approverId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture("This request has already been decided and cannot be approved again");
                    }
                    Row cycleRow = rows.iterator().next();
                    int cycleId = Rows.intVal(cycleRow, "id");
                    Integer requiredVal = Rows.intVal(cycleRow, "required_approvals");
                    int required = requiredVal == null ? 1 : requiredVal;
                    return PaymentCycleApprovals.recordApproval(connection, cycleId, required, approverId);
                });
    }

    private Future<Void> markRequestUsed(SqlClient connection, String type, String reference) {
        return connection.preparedQuery(
                        "UPDATE email_approval_requests SET used_at=GETDATE(), decision='APPROVED' "
                                + "WHERE request_type=@p1 AND reference_code=@p2 AND used_at IS NULL")
                .execute(Tuple.of(type, reference))
                .mapEmpty();
    }

    private Future<Void> markSingleRequestUsed(SqlClient connection, int requestId) {
        return connection.preparedQuery(
                        "UPDATE email_approval_requests SET used_at=GETDATE(), decision='APPROVED' "
                                + "WHERE id=@p1 AND used_at IS NULL")
                .execute(Tuple.of(requestId))
                .mapEmpty();
    }

    private static JsonObject requestData(Row row) {
        String encoded = Rows.str(row, "request_data");
        if (encoded == null || encoded.isBlank()) return new JsonObject();
        try {
            return new JsonObject(encoded);
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (LINK_UNAVAILABLE.equals(message) || (message != null && message.startsWith("This request"))
                || "You have already approved this payment cycle".equals(message)) {
            return message;
        }
        Logging.applicationLog(Logging.logPreString() + "Email approval failed: " + message + "\n\n", "", 3);
        return "The approval could not be completed. Please use the dashboard OTP option";
    }
}
