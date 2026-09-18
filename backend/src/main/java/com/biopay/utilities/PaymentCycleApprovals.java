package com.biopay.utilities;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

/**
 * Records one organisation approver's decision on a payment cycle and, once enough distinct
 * approvers have signed off (the cycle's {@code required_approvals}, snapshotted from the
 * organisation's own policy at generation time -- see {@code Organization#setApprovalPolicy}),
 * flips the cycle to APPROVED and marks its payment line items approved.
 *
 * <p>Shared by {@link com.biopay.services.Payroll#approve} (dashboard OTP) and
 * {@link com.biopay.services.Approval#confirmRequest} (email link) so both paths count toward
 * the same threshold. Callers must already hold a lock on the payment_cycles row for the
 * length of their transaction (e.g. {@code SELECT ... WITH (UPDLOCK, ROWLOCK)}) so concurrent
 * approvers can't race past the threshold check.
 */
public final class PaymentCycleApprovals {
    private PaymentCycleApprovals() {}

    public static final class Outcome {
        public final boolean approved;
        public final int approvalCount;
        public final int requiredApprovals;

        Outcome(boolean approved, int approvalCount, int requiredApprovals) {
            this.approved = approved;
            this.approvalCount = approvalCount;
            this.requiredApprovals = requiredApprovals;
        }
    }

    public static Future<Outcome> recordApproval(SqlClient connection, int cycleId, int requiredApprovals, int approverId) {
        return connection.preparedQuery(
                        "INSERT INTO payment_cycle_approvals (payment_cycle_id, approver_id, approved_at) "
                                + "SELECT @p1, @p2, GETDATE() WHERE NOT EXISTS (SELECT 1 FROM payment_cycle_approvals "
                                + "WHERE payment_cycle_id=@p1 AND approver_id=@p2)")
                .execute(Tuple.of(cycleId, approverId))
                .compose(inserted -> inserted.rowCount() == 0
                        ? Future.<Outcome>failedFuture("You have already approved this payment cycle")
                        : finalizeIfThresholdMet(connection, cycleId, requiredApprovals, approverId));
    }

    private static Future<Outcome> finalizeIfThresholdMet(SqlClient connection, int cycleId, int requiredApprovals, int approverId) {
        return connection.preparedQuery("SELECT COUNT(*) AS cnt FROM payment_cycle_approvals WHERE payment_cycle_id=@p1")
                .execute(Tuple.of(cycleId))
                .compose(countRows -> {
                    int count = Rows.intVal(countRows.iterator().next(), "cnt");
                    if (count < requiredApprovals) {
                        return Future.succeededFuture(new Outcome(false, count, requiredApprovals));
                    }
                    return connection.preparedQuery(
                                    "UPDATE payment_cycles SET status='APPROVED', checker_id=@p1, checker_at=GETDATE(), "
                                            + "otp_verified=1, updated_at=GETDATE() WHERE id=@p2 AND status='PENDING_APPROVAL'")
                            .execute(Tuple.of(approverId, cycleId))
                            .compose(updated -> updated.rowCount() == 0
                                    ? Future.succeededFuture(new Outcome(true, count, requiredApprovals))
                                    : markPaymentsApproved(connection, cycleId, approverId, count, requiredApprovals));
                });
    }

    private static Future<Outcome> markPaymentsApproved(SqlClient connection, int cycleId, int approverId,
            int count, int requiredApprovals) {
        return connection.preparedQuery("UPDATE payments SET approved=1, approved_by=@p1, approved_at=GETDATE() "
                        + "WHERE payment_cycle_id=@p2 AND rejected=0")
                .execute(Tuple.of(approverId, cycleId))
                .map(v -> new Outcome(true, count, requiredApprovals));
    }
}
