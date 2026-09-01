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
import com.biopay.utilities.TenantScope;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Role-scoped dashboard KPIs and time-series chart data. An anchor admin
 * sees everything under their anchor_id (joining through organizations.anchor_id
 * where a table -- households, alternates, fingerprints -- only carries
 * organization_code, not anchor_id, directly); an organisation admin sees only
 * their own organization_code.
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
        return TenantScope.managesOrganisations(payload);
    }

    /** The one designated cross-anchor operator (admin@biopay.com) -- sees every
     *  anchor's totals instead of being scoped to just their own. */
    private static boolean isSystemAdmin(JsonObject payload) {
        return payload.getBoolean("systemAdmin", false);
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
        // A system admin's anchorId param becomes NULL, and every "anchor_id=@p1" below
        // is written as "(@p1 IS NULL OR anchor_id=@p1)" so NULL means "every anchor".
        Integer anchorId = isSystemAdmin(payload) || anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());

        Future<Integer> totalOrganizations = scalarInt(
                "SELECT COUNT(*) AS v FROM organizations WHERE (@p1 IS NULL OR anchor_id=@p1) AND status=1", Tuple.of(anchorId));
        Future<Integer> totalHouseholds = scalarInt(
                "SELECT COUNT(*) AS v FROM households h JOIN organizations p ON p.organization_code=h.organization_code "
                        + "WHERE (@p1 IS NULL OR p.anchor_id=@p1) AND h.status=1", Tuple.of(anchorId));
        Future<Integer> totalAlternates = scalarInt(
                "SELECT COUNT(*) AS v FROM alternates a JOIN organizations p ON p.organization_code=a.organization_code "
                        + "WHERE (@p1 IS NULL OR p.anchor_id=@p1) AND a.status=1", Tuple.of(anchorId));
        Future<Row> paymentsAgg = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total FROM payments WHERE (@p1 IS NULL OR anchor_id=@p1)")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.iterator().next());
        Future<Row> voucherAgg = pool.preparedQuery(
                        "SELECT SUM(CASE WHEN status='REDEEMED' THEN 1 ELSE 0 END) AS cnt, "
                                + "ISNULL(SUM(CASE WHEN status='REDEEMED' THEN amount ELSE 0 END),0) AS total "
                                + "FROM vouchers WHERE (@p1 IS NULL OR anchor_id=@p1)")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.iterator().next());
        Future<Integer> activeOfficers = scalarInt(
                "SELECT COUNT(*) AS v FROM field_officers WHERE (@p1 IS NULL OR anchor_id=@p1) AND active='1'", Tuple.of(anchorId));
        Future<Integer> registeredFingerprints = scalarInt(
                "SELECT COUNT(*) AS v FROM fingerprints f JOIN organizations p ON p.organization_code=f.organization_code "
                        + "WHERE (@p1 IS NULL OR p.anchor_id=@p1)", Tuple.of(anchorId));
        Future<Integer> pendingPayrolls = scalarInt(
                "SELECT COUNT(*) AS v FROM payment_cycles WHERE (@p1 IS NULL OR anchor_id=@p1) AND status='PENDING_APPROVAL'", Tuple.of(anchorId));
        Future<Row> generatedPayrolls = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(total_amount),0) AS total FROM payment_cycles "
                                + "WHERE (@p1 IS NULL OR anchor_id=@p1) AND status<>'REJECTED'")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.iterator().next());
        Future<Row> latestPayroll = pool.preparedQuery(
                        "SELECT TOP 1 * FROM payment_cycles WHERE (@p1 IS NULL OR anchor_id=@p1) ORDER BY created_at DESC")
                .execute(Tuple.of(anchorId))
                .map(rows -> rows.size() == 0 ? null : rows.iterator().next());
        Future<JsonArray> recentTransactions = pool.preparedQuery(
                        "SELECT TOP 10 pay.*, h.household_name AS resolved_household_name, "
                                + "p.name AS organisation_name FROM payments pay "
                                + "LEFT JOIN households h ON h.household_number=pay.household_number "
                                + "LEFT JOIN organizations p ON p.organization_code=pay.organization_code "
                                + "WHERE (@p1 IS NULL OR pay.anchor_id=@p1) ORDER BY pay.created_at DESC")
                .execute(Tuple.of(anchorId))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("id", Rows.intVal(r, "id"))
                                .put("householdName", Rows.str(r, "resolved_household_name"))
                                .put("organisationCode", Rows.str(r, "organization_code"))
                                .put("organisationName", Rows.str(r, "organisation_name"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.intVal(r, "status"))
                                .put("createdAt", Rows.str(r, "created_at")));
                    }
                    return arr;
                });
        Future<JsonArray> amountsByOrganisation = pool.preparedQuery(
                        "SELECT p.organization_code AS code, p.name AS name, "
                                + "ISNULL(pay.total,0) AS paymentsAmount, ISNULL(vch.total,0) AS voucherAmount "
                                + "FROM organizations p "
                                + "LEFT JOIN (SELECT organization_code, SUM(amount) AS total FROM payments WHERE (@p1 IS NULL OR anchor_id=@p1) GROUP BY organization_code) pay "
                                + "  ON pay.organization_code = p.organization_code "
                                + "LEFT JOIN (SELECT organization_code, SUM(amount) AS total FROM vouchers WHERE (@p1 IS NULL OR anchor_id=@p1) AND status='REDEEMED' GROUP BY organization_code) vch "
                                + "  ON vch.organization_code = p.organization_code "
                                + "WHERE (@p1 IS NULL OR p.anchor_id=@p1) AND p.status=1 ORDER BY p.name")
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
                        activeOfficers, registeredFingerprints, pendingPayrolls, generatedPayrolls, latestPayroll,
                        recentTransactions, amountsByOrganisation))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    Row payments = cf.resultAt(3);
                    Row vouchers = cf.resultAt(4);
                    Row generated = cf.resultAt(8);
                    Row latestCycle = cf.resultAt(9);
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
                                    .put("registeredFingerprints", (Integer) cf.resultAt(6))
                                    .put("pendingPayrolls", (Integer) cf.resultAt(7))
                                    .put("generatedCycles", Rows.intVal(generated, "cnt"))
                                    .put("totalGeneratedAmount", Rows.dbl(generated, "total"))
                                    .put("latestPayroll", latestCycle == null ? null : new JsonObject()
                                            .put("cycleCode", Rows.str(latestCycle, "cycle_code"))
                                            .put("status", Rows.str(latestCycle, "status"))
                                            .put("totalAmount", Rows.dbl(latestCycle, "total_amount")))
                                    .put("recentTransactions", (JsonArray) cf.resultAt(10))
                                    .put("amountsByOrganisation", (JsonArray) cf.resultAt(11))));
                });
    }

    private void organisationMetrics(Message<Object> message, JsonObject payload) {
        String partnerCode = payload.getString("partnerCode", "");

        Future<Integer> totalHouseholds = scalarInt(
                "SELECT COUNT(*) AS v FROM households WHERE organization_code=@p1 AND status=1", Tuple.of(partnerCode));
        Future<Integer> totalAlternates = scalarInt(
                "SELECT COUNT(*) AS v FROM alternates WHERE organization_code=@p1 AND status=1", Tuple.of(partnerCode));
        Future<Integer> registeredFingerprints = scalarInt(
                "SELECT COUNT(*) AS v FROM fingerprints WHERE organization_code=@p1", Tuple.of(partnerCode));
        Future<Row> paymentsAgg = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS total FROM payments WHERE organization_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.iterator().next());
        Future<Row> voucherAgg = pool.preparedQuery(
                        "SELECT SUM(CASE WHEN status='REDEEMED' THEN 1 ELSE 0 END) AS cnt, "
                                + "ISNULL(SUM(CASE WHEN status='REDEEMED' THEN amount ELSE 0 END),0) AS total "
                                + "FROM vouchers WHERE organization_code=@p1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.iterator().next());
        Future<Row> pendingPayroll = pool.preparedQuery(
                        "SELECT TOP 1 * FROM payment_cycles WHERE organization_code=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.size() == 0 ? null : rows.iterator().next());
        Future<Row> generatedPayrolls = pool.preparedQuery(
                        "SELECT COUNT(*) AS cnt, ISNULL(SUM(total_amount),0) AS total FROM payment_cycles "
                                + "WHERE organization_code=@p1 AND status<>'REJECTED'")
                .execute(Tuple.of(partnerCode))
                .map(rows -> rows.iterator().next());

        Future.all(java.util.List.of(totalHouseholds, totalAlternates, registeredFingerprints, paymentsAgg, voucherAgg,
                        pendingPayroll, generatedPayrolls))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> {
                    Row payments = cf.resultAt(3);
                    Row vouchers = cf.resultAt(4);
                    Row latestCycle = cf.resultAt(5);
                    Row generated = cf.resultAt(6);
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
                                    .put("generatedCycles", Rows.intVal(generated, "cnt"))
                                    .put("totalGeneratedAmount", Rows.dbl(generated, "total"))
                                    .put("latestPayroll", latestPayroll)));
                });
    }

    private Future<Integer> scalarInt(String sql, Tuple params) {
        return pool.preparedQuery(sql)
                .execute(params)
                .map(rows -> rows.size() == 0 ? 0 : Rows.intVal(rows.iterator().next(), "v"));
    }

    // ---- Dashboard charts ----------------------------------------------------------

    private static String chartPeriod(JsonObject payload) {
        String period = payload.getString("period", "month").toLowerCase();
        return switch (period) {
            case "day", "week", "year" -> period;
            default -> "month";
        };
    }

    private static String chartReferenceDate(JsonObject payload) {
        try {
            return LocalDate.parse(payload.getString("referenceDate", "")).toString();
        } catch (RuntimeException ignored) {
            return LocalDate.now().toString();
        }
    }

    private static String chartBucket(String period, String column) {
        return switch (period) {
            case "day" -> "RIGHT('0' + CAST(DATEPART(hour, " + column + ") AS VARCHAR(2)), 2)";
            case "year" -> "CONVERT(VARCHAR(7), " + column + ", 120)";
            default -> "CONVERT(VARCHAR(10), " + column + ", 120)";
        };
    }

    private static String chartRange(String period, String column) {
        String selectedDate = "CONVERT(date, @p2)";
        String monthStart = "DATEFROMPARTS(YEAR(" + selectedDate + "), MONTH(" + selectedDate + "), 1)";
        String yearStart = "DATEFROMPARTS(YEAR(" + selectedDate + "), 1, 1)";
        String weekStart = "DATEADD(day, -(DATEDIFF(day, '19000101', " + selectedDate + ") % 7), " + selectedDate + ")";
        return switch (period) {
            case "day" -> column + " >= " + selectedDate + " AND " + column + " < DATEADD(day, 1, " + selectedDate + ")";
            case "week" -> column + " >= " + weekStart + " AND " + column + " < DATEADD(day, 7, " + weekStart + ")";
            case "year" -> column + " >= " + yearStart + " AND " + column + " < DATEADD(year, 1, " + yearStart + ")";
            default -> column + " >= " + monthStart + " AND " + column + " < DATEADD(month, 1, " + monthStart + ")";
        };
    }

    private Future<JsonArray> amountSeries(String sql, Tuple params) {
        return pool.preparedQuery(sql).execute(params).map(rows -> {
            JsonArray series = new JsonArray();
            for (Row row : rows) {
                series.add(new JsonObject()
                        .put("period", Rows.str(row, "bucket"))
                        .put("count", Rows.intVal(row, "cnt"))
                        .put("amount", Rows.dbl(row, "total")));
            }
            return series;
        });
    }

    private Future<JsonArray> countSeries(String sql, Tuple params) {
        return pool.preparedQuery(sql).execute(params).map(rows -> {
            JsonArray series = new JsonArray();
            for (Row row : rows) {
                series.add(new JsonObject()
                        .put("period", Rows.str(row, "bucket"))
                        .put("count", Rows.intVal(row, "cnt")));
            }
            return series;
        });
    }

    private static JsonObject bucket(Map<String, JsonObject> buckets, String period) {
        return buckets.computeIfAbsent(period, key -> new JsonObject().put("period", key));
    }

    // ---- DASHBOARD_PAYMENTS_CHART (cash + redeemed voucher value) -----------------

    private void paymentsChart(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String period = chartPeriod(payload);
        String referenceDate = chartReferenceDate(payload);
        String partnerCode = isAnchor(payload) ? null : payload.getString("partnerCode", "");
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = isSystemAdmin(payload) || anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        Tuple params = isAnchor(payload) ? Tuple.of(anchorId, referenceDate) : Tuple.of(partnerCode, referenceDate);

        String cashBucket = chartBucket(period, "pay.created_at");
        String cashScope = isAnchor(payload) ? "(@p1 IS NULL OR pay.anchor_id=@p1)" : "pay.organization_code=@p1";
        String cashSql = "SELECT " + cashBucket + " AS bucket, COUNT(*) AS cnt, ISNULL(SUM(pay.amount),0) AS total "
                + "FROM payments pay WHERE " + cashScope + " AND " + chartRange(period, "pay.created_at")
                + " GROUP BY " + cashBucket + " ORDER BY bucket";

        String voucherDate = "COALESCE(v.redeemed_at, v.created_at)";
        String voucherBucket = chartBucket(period, voucherDate);
        String voucherScope = isAnchor(payload) ? "(@p1 IS NULL OR v.anchor_id=@p1)" : "v.organization_code=@p1";
        String voucherSql = "SELECT " + voucherBucket + " AS bucket, COUNT(*) AS cnt, ISNULL(SUM(v.amount),0) AS total "
                + "FROM vouchers v WHERE " + voucherScope + " AND v.status='REDEEMED' AND " + chartRange(period, voucherDate)
                + " GROUP BY " + voucherBucket + " ORDER BY bucket";

        Future.all(amountSeries(cashSql, params), amountSeries(voucherSql, params))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(result -> {
                    Map<String, JsonObject> buckets = new TreeMap<>();
                    for (Object item : (JsonArray) result.resultAt(0)) {
                        JsonObject row = (JsonObject) item;
                        bucket(buckets, row.getString("period"))
                                .put("cashCount", row.getInteger("count", 0))
                                .put("cashAmount", row.getDouble("amount", 0d));
                    }
                    for (Object item : (JsonArray) result.resultAt(1)) {
                        JsonObject row = (JsonObject) item;
                        bucket(buckets, row.getString("period"))
                                .put("voucherCount", row.getInteger("count", 0))
                                .put("voucherAmount", row.getDouble("amount", 0d));
                    }
                    JsonArray series = new JsonArray();
                    buckets.values().forEach(row -> series.add(row
                            .put("cashCount", row.getInteger("cashCount", 0))
                            .put("cashAmount", row.getDouble("cashAmount", 0d))
                            .put("voucherCount", row.getInteger("voucherCount", 0))
                            .put("voucherAmount", row.getDouble("voucherAmount", 0d))));
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", series));
                });
    }

    // ---- DASHBOARD_HOUSEHOLDS_CHART (households + alternates) ----------------------

    private void householdsChart(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String period = chartPeriod(payload);
        String referenceDate = chartReferenceDate(payload);
        Object anchorIdVal = payload.getValue("anchorId");
        Integer anchorId = isSystemAdmin(payload) || anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
        Tuple params = isAnchor(payload)
                ? Tuple.of(anchorId, referenceDate)
                : Tuple.of(payload.getString("partnerCode", ""), referenceDate);

        String householdBucket = chartBucket(period, "h.created_at");
        String householdFrom = isAnchor(payload)
                ? "FROM households h JOIN organizations p ON p.organization_code=h.organization_code "
                : "FROM households h ";
        String householdScope = isAnchor(payload) ? "(@p1 IS NULL OR p.anchor_id=@p1)" : "h.organization_code=@p1";
        String householdSql = "SELECT " + householdBucket + " AS bucket, COUNT(*) AS cnt " + householdFrom
                + "WHERE " + householdScope + " AND " + chartRange(period, "h.created_at")
                + " GROUP BY " + householdBucket + " ORDER BY bucket";

        String alternateBucket = chartBucket(period, "a.created_at");
        String alternateFrom = isAnchor(payload)
                ? "FROM alternates a JOIN organizations p ON p.organization_code=a.organization_code "
                : "FROM alternates a ";
        String alternateScope = isAnchor(payload) ? "(@p1 IS NULL OR p.anchor_id=@p1)" : "a.organization_code=@p1";
        String alternateSql = "SELECT " + alternateBucket + " AS bucket, COUNT(*) AS cnt " + alternateFrom
                + "WHERE " + alternateScope + " AND " + chartRange(period, "a.created_at")
                + " GROUP BY " + alternateBucket + " ORDER BY bucket";

        Future.all(countSeries(householdSql, params), countSeries(alternateSql, params))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(result -> {
                    Map<String, JsonObject> buckets = new TreeMap<>();
                    for (Object item : (JsonArray) result.resultAt(0)) {
                        JsonObject row = (JsonObject) item;
                        bucket(buckets, row.getString("period")).put("householdCount", row.getInteger("count", 0));
                    }
                    for (Object item : (JsonArray) result.resultAt(1)) {
                        JsonObject row = (JsonObject) item;
                        bucket(buckets, row.getString("period")).put("alternateCount", row.getInteger("count", 0));
                    }
                    JsonArray series = new JsonArray();
                    buckets.values().forEach(row -> series.add(row
                            .put("householdCount", row.getInteger("householdCount", 0))
                            .put("alternateCount", row.getInteger("alternateCount", 0))));
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", series));
                });
    }
}
