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
 * Role-scoped dashboard KPIs and time-series chart data. An anchor admin
 * sees everything under their anchor_id (joining through partners.anchor_id
 * where a table -- households, alternates, fingerprints -- only carries
 * partner_code, not anchor_id, directly); an organisation admin sees only
 * their own partner_code.
 */
public class Dashboard extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Dashboard =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("DASHBOARD_METRICS", this::metrics);
        eventBus.consumer("DASHBOARD_PAYMENTS_CHART", this::paymentsChart);
        eventBus.consumer("DASHBOARD_HOUSEHOLDS_CHART", this::householdsChart);
        startPromise.complete();
    }

    private static void reply(Message<Object> message, JsonObject obj) {
        message.reply(obj.toString().trim());
    }

    private void onDbError(Message<Object> message, Throwable err) {
        Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3);
        reply(message, new JsonObject().put("responseCode", "999").put("responseMessage", "Failed with an error"));
    }

    private static boolean isAnchor(JsonObject payload) {
        return "ANCHOR".equalsIgnoreCase(payload.getString("actorRole", ""));
    }

    // ---- DASHBOARD_METRICS --------------------------------------------------------

    private void metrics(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (isAnchor(payload)) {
            anchorMetrics(message, payload);
        } else {
            organisationMetrics(message, payload);
        }
    }

    private void anchorMetrics(Message<Object> message, JsonObject payload) {
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        Future<Integer> totalOrganizations = scalarInt(
                "SELECT COUNT(*) AS v FROM partners WHERE anchor_id=@p1 AND status=1", Tuple.of(anchorId));
        Future<Integer> totalHouseholds = scalarInt(
                "SELECT COUNT(*) AS v FROM households h JOIN partners p ON p.partner_id=h.partner_code "
                        + "WHERE p.anchor_id=@p1 AND h.status=1", Tuple.of(anchorId));
        Future<Integer> totalAlternates = scalarInt(
                "SELECT COUNT(*) AS v FROM alternates a JOIN partners p ON p.partner_id=a.partner_code "
                        + "WHERE p.anchor_id=@p1 AND a.status=1", Tuple.of(anchorId));
        Future<Row> paymentsAgg = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total FROM payments WHERE anchor_id=@p1")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.iterator().next());
        Future<Row> voucherAgg = pool.preparedQuery(
                        "SELECT SUM(CASE WHEN status='REDEEMED' THEN 1 ELSE 0 END) AS cnt, "
                                + "ISNULL(SUM(CASE WHEN status='REDEEMED' THEN amount ELSE 0 END),0) AS total "
                                + "FROM vouchers WHERE anchor_id=@p1")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.iterator().next());
        Future<Integer> activeOfficers = scalarInt(
                "SELECT COUNT(*) AS v FROM supervisors WHERE anchor_id=@p1 AND active='1'", Tuple.of(anchorId));
        Future<Integer> pendingPayrolls = scalarInt(
                "SELECT COUNT(*) AS v FROM payroll_cycles WHERE anchor_id=@p1 AND status='PENDING_APPROVAL'", Tuple.of(anchorId));
        Future<JsonArray> recentTransactions = pool.preparedQuery(
                        "SELECT TOP 10 pay.*, h.household_name AS resolved_household_name, "
                                + "p.name AS organisation_name FROM payments pay "
                                + "LEFT JOIN households h ON h.household_number=pay.household_number "
                                + "LEFT JOIN partners p ON p.partner_id=pay.partner_code "
                                + "WHERE pay.anchor_id=@p1 ORDER BY pay.created_at DESC")
                .execute(Tuple.of(anchorId))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("id", Rows.intVal(r, "id"))
                                .put("householdName", Rows.str(r, "resolved_household_name"))
                                .put("organisationCode", Rows.str(r, "partner_code"))
                                .put("organisationName", Rows.str(r, "organisation_name"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.intVal(r, "status"))
                                .put("createdAt", Rows.str(r, "created_at")));
                    }
                    return arr;
                });
        Future<JsonArray> amountsByOrganisation = pool.preparedQuery(
                        "SELECT p.partner_id AS code, p.name AS name, "
                                + "ISNULL(pay.total,0) AS paymentsAmount, ISNULL(vch.total,0) AS voucherAmount "
                                + "FROM partners p "
                                + "LEFT JOIN (SELECT partner_code, SUM(amount) AS total FROM payments WHERE anchor_id=@p1 GROUP BY partner_code) pay "
                                + "  ON pay.partner_code = p.partner_id "
                                + "LEFT JOIN (SELECT partner_code, SUM(amount) AS total FROM vouchers WHERE anchor_id=@p1 AND status='REDEEMED' GROUP BY partner_code) vch "
                                + "  ON vch.partner_code = p.partner_id "
                                + "WHERE p.anchor_id=@p1 AND p.status=1 ORDER BY p.name")
                .execute(Tuple.of(anchorId))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        double paymentsAmount = Rows.dbl(r, "paymentsAmount");
                        double voucherAmount = Rows.dbl(r, "voucherAmount");
                        arr.add(new JsonObject()
                                .put("organisationCode", Rows.str(r, "code"))
                                .put("organisationName", Rows.str(r, "name"))
                                .put("paymentsAmount", paymentsAmount)
                                .put("voucherAmount", voucherAmount)
                                .put("totalAmount", paymentsAmount + voucherAmount));
                    }
                    return arr;
                });

        Future.all(java.util.List.of(totalOrganizations, totalHouseholds, totalAlternates, paymentsAgg, voucherAgg,
                        activeOfficers, pendingPayrolls, recentTransactions, amountsByOrganisation))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    Row payments = cf.resultAt(3);
                    Row vouchers = cf.resultAt(4);
                    double paymentsAmount = Rows.dbl(payments, "total");
                    double voucherAmount = Rows.dbl(vouchers, "total");
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "OK")
                            .put("results", new JsonObject()
                                    .put("totalOrganizations", (Integer) cf.resultAt(0))
                                    .put("totalHouseholds", (Integer) cf.resultAt(1))
                                    .put("totalAlternates", (Integer) cf.resultAt(2))
                                    .put("totalPaymentsCount", Rows.intVal(payments, "cnt"))
                                    .put("totalPaymentsAmount", paymentsAmount)
                                    .put("voucherRedeemedCount", Rows.intVal(vouchers, "cnt"))
                                    .put("voucherRedeemedAmount", voucherAmount)
                                    .put("combinedAmount", paymentsAmount + voucherAmount)
                                    .put("activeOfficers", (Integer) cf.resultAt(5))
                                    .put("pendingPayrolls", (Integer) cf.resultAt(6))
                                    .put("recentTransactions", (JsonArray) cf.resultAt(7))
                                    .put("amountsByOrganisation", (JsonArray) cf.resultAt(8))));
                });
    }

    private void organisationMetrics(Message<Object> message, JsonObject payload) {
        String partnerCode = payload.getString("partnerCode", "");

        Future<Integer> totalHouseholds = scalarInt(
                "SELECT COUNT(*) AS v FROM households WHERE partner_code=@p1 AND status=1", Tuple.of(partnerCode));
        Future<Integer> totalAlternates = scalarInt(
                "SELECT COUNT(*) AS v FROM alternates WHERE partner_code=@p1 AND status=1", Tuple.of(partnerCode));
        Future<Integer> registeredFingerprints = scalarInt(
                "SELECT COUNT(*) AS v FROM fingerprints WHERE partner_code=@p1", Tuple.of(partnerCode));
        Future<Row> paymentsAgg = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total FROM payments WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.iterator().next());
        Future<Row> voucherAgg = pool.preparedQuery(
                        "SELECT SUM(CASE WHEN status='REDEEMED' THEN 1 ELSE 0 END) AS cnt, "
                                + "ISNULL(SUM(CASE WHEN status='REDEEMED' THEN amount ELSE 0 END),0) AS total "
                                + "FROM vouchers WHERE partner_code=@p1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.iterator().next());
        Future<Row> pendingPayroll = pool.preparedQuery(
                        "SELECT TOP 1 * FROM payroll_cycles WHERE partner_code=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.size() == 0 ? null : rows.iterator().next());

        Future.all(java.util.List.of(totalHouseholds, totalAlternates, registeredFingerprints, paymentsAgg, voucherAgg, pendingPayroll))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    Row payments = cf.resultAt(3);
                    Row vouchers = cf.resultAt(4);
                    Row latestCycle = cf.resultAt(5);
                    double paymentsAmount = Rows.dbl(payments, "total");
                    double voucherAmount = Rows.dbl(vouchers, "total");
                    JsonObject latestPayroll = latestCycle == null ? null : new JsonObject()
                            .put("cycleCode", Rows.str(latestCycle, "cycle_code"))
                            .put("status", Rows.str(latestCycle, "status"))
                            .put("totalAmount", Rows.dbl(latestCycle, "total_amount"));

                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "OK")
                            .put("results", new JsonObject()
                                    .put("totalHouseholds", (Integer) cf.resultAt(0))
                                    .put("totalAlternates", (Integer) cf.resultAt(1))
                                    .put("registeredFingerprints", (Integer) cf.resultAt(2))
                                    .put("totalPaymentsReceivedCount", Rows.intVal(payments, "cnt"))
                                    .put("totalPaymentsReceivedAmount", paymentsAmount)
                                    .put("voucherRedeemedCount", Rows.intVal(vouchers, "cnt"))
                                    .put("voucherRedeemedAmount", voucherAmount)
                                    .put("combinedAmount", paymentsAmount + voucherAmount)
                                    .put("latestPayroll", latestPayroll)));
                });
    }

    private Future<Integer> scalarInt(String sql, Tuple params) {
        return pool.preparedQuery(sql)
                .execute(params)
                .map(rows -> rows.size() == 0 ? 0 : Rows.intVal(rows.iterator().next(), "v"));
    }

    // ---- DASHBOARD_PAYMENTS_CHART (monthly volume + count) -------------------------

    private void paymentsChart(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = isAnchor(payload) ? null : payload.getString("partnerCode", "");
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        String sql = isAnchor(payload)
                ? "SELECT CONVERT(VARCHAR(7), created_at, 120) AS bucket, COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total "
                        + "FROM payments WHERE anchor_id=@p1 GROUP BY CONVERT(VARCHAR(7), created_at, 120) ORDER BY bucket"
                : "SELECT CONVERT(VARCHAR(7), created_at, 120) AS bucket, COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total "
                        + "FROM payments WHERE partner_code=@p1 GROUP BY CONVERT(VARCHAR(7), created_at, 120) ORDER BY bucket";

        pool.preparedQuery(sql)
                .execute(isAnchor(payload) ? Tuple.of(anchorId) : Tuple.of(partnerCode))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray series = new JsonArray();
                    for (Row r : rows) {
                        series.add(new JsonObject()
                                .put("period", Rows.str(r, "bucket"))
                                .put("count", Rows.intVal(r, "cnt"))
                                .put("amount", Rows.dbl(r, "total")));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", series));
                });
    }

    // ---- DASHBOARD_HOUSEHOLDS_CHART (monthly registration trend) -------------------

    private void householdsChart(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());

        String sql = isAnchor(payload)
                ? "SELECT CONVERT(VARCHAR(7), h.created_at, 120) AS bucket, COUNT(*) AS cnt "
                        + "FROM households h JOIN partners p ON p.partner_id=h.partner_code "
                        + "WHERE p.anchor_id=@p1 GROUP BY CONVERT(VARCHAR(7), h.created_at, 120) ORDER BY bucket"
                : "SELECT CONVERT(VARCHAR(7), created_at, 120) AS bucket, COUNT(*) AS cnt "
                        + "FROM households WHERE partner_code=@p1 GROUP BY CONVERT(VARCHAR(7), created_at, 120) ORDER BY bucket";
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        pool.preparedQuery(sql)
                .execute(isAnchor(payload) ? Tuple.of(anchorId) : Tuple.of(payload.getString("partnerCode", "")))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray series = new JsonArray();
                    for (Row r : rows) {
                        series.add(new JsonObject()
                                .put("period", Rows.str(r, "bucket"))
                                .put("count", Rows.intVal(r, "cnt")));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", series));
                });
    }
}
