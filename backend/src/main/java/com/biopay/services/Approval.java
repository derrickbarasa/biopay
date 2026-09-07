package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.EmailTemplates;
import com.biopay.utilities.Env;
import com.biopay.utilities.Hashing;
import com.biopay.utilities.Logging;
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
        if ("PAYROLL".equals(type) && anchorId == null) {
            replyError(message, "anchorId is required for payroll approval");
            return;
        }
        if ("HOUSEHOLD".equals(type) && organizationCode.isEmpty()) {
            replyError(message, "organisationCode is required for household approval");
            return;
        }

        findApprovers(type, anchorId, organizationCode, makerId)
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

    private Future<RowSet<Row>> findApprovers(String type, Integer anchorId, String organizationCode, Integer makerId) {
        String permission = "PAYROLL".equals(type) ? "ACCESS_PAYMENT_CYCLES" : "ACCESS_HOUSEHOLDS";
        String scope = "PAYROLL".equals(type)
                ? "u.user_scope='ANCHOR' AND (u.id=@p2 OR u.anchor_id=@p2)"
                : "((u.user_scope='ORGANISATION' AND u.organization_code=@p2) "
                        + "OR (u.user_scope='ANCHOR' AND (u.id=o.anchor_id OR u.anchor_id=o.anchor_id)))";
        String organizationJoin = "PAYROLL".equals(type)
                ? ""
                : "JOIN organizations o ON o.organization_code=@p2 AND o.status=1 ";
        String sql = "SELECT DISTINCT u.id, u.email, u.first_name, u.other_names FROM users u "
                + organizationJoin
                + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                + "JOIN permissions p ON p.id=rp.permission_id AND p.permission_name=@p1 "
                + "WHERE u.active=1 AND u.status=1 AND " + scope
                + ("PAYROLL".equals(type) ? " AND (@p3 IS NULL OR u.id<>@p3)" : "")
                + " AND NULLIF(LTRIM(RTRIM(u.email)),'') IS NOT NULL";
        Tuple params = Tuple.of(permission, "PAYROLL".equals(type) ? anchorId : organizationCode);
        if ("PAYROLL".equals(type)) {
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

            Future<?> write = pool.preparedQuery("INSERT INTO email_approval_requests "
                            + "(request_type, reference_code, approver_id, anchor_id, organization_code, token_hash, request_data, expires_at, created_at) "
                            + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,DATEADD(HOUR,@p8,GETDATE()),GETDATE())")
                    .execute(Tuple.of(type, reference, approverId, anchorId,
                            organizationCode.isEmpty() ? null : organizationCode, tokenHash, requestData.encode(), expiryHours))
                    .compose(v -> eventBus.<JsonObject>request("EMAIL", new JsonObject()
                                    .put("mailTo", email)
                                    .put("subject", "Approval needed: " + reference)
                                    .put("msg", EmailTemplates.approvalRequestEmail(name, requestData.getString("title"),
                                            reference, requestData.getString("summary"), actionUrl, expiryHours))
                                    .put("inlineImages", EmailTemplates.logoInlineImages()))
                            .mapEmpty());
            writes.add(write);
        }
        if (writes.isEmpty()) {
            return Future.succeededFuture(0);
        }
        return Future.all(writes).map(approvers.size());
    }

    private static String displayName(Row approver) {
        String firstName = Rows.str(approver, "first_name");
        String otherNames = Rows.str(approver, "other_names");
        return ((firstName == null ? "" : firstName) + " " + (otherNames == null ? "" : otherNames)).trim();
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
                                + "CASE WHEN ear.request_type='PAYROLL' THEN 'ACCESS_PAYMENT_CYCLES' ELSE 'ACCESS_HOUSEHOLDS' END "
                                + "LEFT JOIN organizations o ON o.organization_code=ear.organization_code AND o.status=1 "
                                + "WHERE u.id=ear.approver_id AND u.active=1 AND u.status=1 AND "
                                + "((ear.request_type='PAYROLL' AND u.user_scope='ANCHOR' "
                                + "AND (u.id=ear.anchor_id OR u.anchor_id=ear.anchor_id)) OR "
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
        Future<RowSet<Row>> targetUpdate;

        if ("PAYROLL".equals(type)) {
            targetUpdate = connection.preparedQuery("UPDATE payment_cycles SET status='APPROVED', checker_id=@p1, "
                            + "checker_at=GETDATE(), otp_verified=1, updated_at=GETDATE() "
                            + "WHERE cycle_code=@p2 AND status='PENDING_APPROVAL' AND (maker_id IS NULL OR maker_id<>@p1)")
                    .execute(Tuple.of(approverId, reference));
        } else {
            targetUpdate = connection.preparedQuery("UPDATE households SET review_status='APPROVED', rejection_reason=NULL, "
                            + "updated_by=@p1, updated_at=GETDATE() WHERE household_number=@p2 "
                            + "AND (review_status IS NULL OR review_status IN ('PENDING','CHECKED'))")
                    .execute(Tuple.of(String.valueOf(approverId), reference));
        }

        return targetUpdate.compose(updated -> {
            if (updated.rowCount() == 0) {
                return Future.failedFuture("This request has already been decided and cannot be approved again");
            }
            Future<RowSet<Row>> children = "PAYROLL".equals(type)
                    ? connection.preparedQuery("UPDATE payments SET approved=1, approved_by=@p1, approved_at=GETDATE() "
                                    + "WHERE payment_cycle_id=(SELECT id FROM payment_cycles WHERE cycle_code=@p2) AND rejected=0")
                            .execute(Tuple.of(approverId, reference))
                    : Future.succeededFuture(updated);
            return children.compose(v -> connection.preparedQuery(
                            "UPDATE email_approval_requests SET used_at=GETDATE(), decision='APPROVED' "
                                    + "WHERE request_type=@p1 AND reference_code=@p2 AND used_at IS NULL")
                    .execute(Tuple.of(type, reference)));
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
        if (LINK_UNAVAILABLE.equals(message) || (message != null && message.startsWith("This request"))) {
            return message;
        }
        Logging.applicationLog(Logging.logPreString() + "Email approval failed: " + message + "\n\n", "", 3);
        return "The approval could not be completed. Please use the dashboard OTP option";
    }
}
