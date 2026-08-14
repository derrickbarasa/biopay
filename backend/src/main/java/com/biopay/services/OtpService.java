package com.biopay.services;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.biopay.utilities.Hashing;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;

/**
 * Email OTP challenges backing the payroll maker-checker flow (spec:
 * "generate/approve payroll ... TOTP or EMAIL OTP"). Only the EMAIL channel
 * is wired end-to-end here -- TOTP needs a per-user enrolled secret that
 * doesn't exist yet in this schema, so {@link #request} only accepts EMAIL
 * today. Adding TOTP later means enrolling a secret column on users/
 * supervisors and branching in {@link #verify}; the otp_verifications table
 * and processingCode contract already support a "channel" of either kind.
 */
final class OtpService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final MSSQLPool pool;
    private final EventBus eventBus;

    OtpService(MSSQLPool pool, EventBus eventBus) {
        this.pool = pool;
        this.eventBus = eventBus;
    }

    /** Generates and emails a 6-digit OTP, storing only its hash. */
    Future<Void> request(String referenceType, String referenceCode, int userId, String userScope, String emailTo) {
        String code = Utilities.generateOtp();
        String codeHash = Hashing.sha256Hex(code);

        String sql = "INSERT INTO otp_verifications (reference_type, reference_code, user_id, user_scope, channel, "
                + "code_hash, expires_at, verified, attempts, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, 'EMAIL', @p5, DATEADD(MINUTE, @p6, GETDATE()), 0, 0, GETDATE())";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(referenceType, referenceCode, userId, userScope, codeHash, OTP_TTL_MINUTES))
                .map(rows -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", emailTo)
                            .put("subject", "BioPay verification code")
                            .put("msg", "Your verification code is <strong>" + code + "</strong>. "
                                    + "It expires in " + OTP_TTL_MINUTES + " minutes. "
                                    + "Do not share this code with anyone.")
                            .toString());
                    return (Void) null;
                });
    }

    /**
     * Verifies the most recent unverified, unexpired OTP of this type for
     * this user. On success marks it verified (single-use); on a wrong code
     * increments attempts and locks it out after {@link #MAX_ATTEMPTS}.
     */
    Future<Boolean> verify(String referenceType, int userId, String code) {
        String codeHash = Hashing.sha256Hex(code);
        String sql = "SELECT TOP 1 * FROM otp_verifications WHERE reference_type=@p1 AND user_id=@p2 "
                + "AND verified=0 AND expires_at > GETDATE() ORDER BY created_at DESC";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(referenceType, userId))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.succeededFuture(Boolean.FALSE);
                    }
                    Row r = rows.iterator().next();
                    int id = Rows.intVal(r, "id");
                    int attempts = Rows.intVal(r, "attempts");
                    String storedHash = Rows.str(r, "code_hash");

                    if (attempts >= MAX_ATTEMPTS) {
                        return Future.succeededFuture(Boolean.FALSE);
                    }
                    if (!codeHash.equals(storedHash)) {
                        return pool.preparedQuery("UPDATE otp_verifications SET attempts=attempts+1 WHERE id=@p1")
                                .execute(Tuple.of(id))
                                .map(u -> Boolean.FALSE);
                    }
                    return pool.preparedQuery("UPDATE otp_verifications SET verified=1, verified_at=GETDATE() WHERE id=@p1")
                            .execute(Tuple.of(id))
                            .map(u -> Boolean.TRUE);
                });
    }
}
