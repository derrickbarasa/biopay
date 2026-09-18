package com.biopay.utilities;

import io.vertx.core.Future;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Tuple;

/**
 * How many of an organisation's own active users currently hold CHECK_PAYMENT_CYCLES -- i.e.
 * how many people could ever approve one of that organisation's payment cycles. The cycle's
 * maker is eligible too when they hold this permission.
 *
 * <p>Used to warn -- never to block -- when a required-approvals policy, or a specific cycle,
 * would leave too few eligible people to ever clear its threshold. An organisation should still
 * be free to set a policy (or generate a cycle) ahead of hiring/granting the access it implies.
 */
public final class EligibleApprovers {
    private EligibleApprovers() {}

    public static Future<Integer> count(MSSQLPool pool, String organizationCode, Integer excludeUserId) {
        return pool.preparedQuery("SELECT COUNT(DISTINCT u.id) AS cnt FROM users u "
                        + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                        + "JOIN permissions p ON p.id=rp.permission_id AND p.permission_name='CHECK_PAYMENT_CYCLES' "
                        + "WHERE u.active=1 AND u.status=1 AND u.user_scope='ORGANISATION' "
                        + "AND u.organization_code=@p1 AND (@p2 IS NULL OR u.id<>@p2)")
                .execute(Tuple.of(organizationCode, excludeUserId))
                .map(rows -> {
                    Integer cnt = Rows.intVal(rows.iterator().next(), "cnt");
                    return cnt == null ? 0 : cnt;
                });
    }
}
