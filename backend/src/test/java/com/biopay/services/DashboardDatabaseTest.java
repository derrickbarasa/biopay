package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.Rows;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, SELECT-only checks against the configured database. No fixtures are inserted. */
@EnabledIfSystemProperty(named = "biopay.dashboard.dbTest", matches = "true")
class DashboardDatabaseTest {
    static Vertx vertx;
    static JsonObject system() { return new JsonObject().put("actorRole", "SYSTEM"); }
    @BeforeAll static void start() throws Exception {
        vertx = Vertx.vertx();
        Datasource.init(vertx);
        vertx.deployVerticle(new Dashboard()).toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
    @AfterAll static void stop() throws Exception {
        if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
    static JsonObject request(String code, JsonObject payload) throws Exception {
        Object body = vertx.eventBus().request(code, payload.toString()).toCompletionStage().toCompletableFuture().get(25, TimeUnit.SECONDS).body();
        JsonObject result = new JsonObject(body.toString());
        assertEquals("000", result.getString("responseCode"), code);
        return result;
    }
    static RowSet<Row> query(String sql, Tuple args) throws Exception {
        assertTrue(sql.startsWith("SELECT "));
        return Datasource.pool().preparedQuery(sql).execute(args).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    }
    @Test void allHeadlineMetricsAndOrganisationTotalsReconcile() throws Exception {
        JsonObject m = request("DASHBOARD_METRICS", system()).getJsonObject("results");
        Map<String, String> counts = Map.of(
            "totalOrganizations", "organizations WHERE status=1",
            "totalHouseholds", "households h JOIN organizations o ON o.organization_code=h.organization_code WHERE h.status=1",
            "totalAlternates", "alternates a JOIN organizations o ON o.organization_code=a.organization_code WHERE a.status=1",
            "registeredFingerprints", "fingerprints f JOIN organizations o ON o.organization_code=f.organization_code WHERE f.status=1",
            "activeOfficers", "field_officers WHERE active='1'",
            "pendingPayrolls", "payment_cycles WHERE status='PENDING_APPROVAL'",
            "generatedCycles", "payment_cycles WHERE status<>'REJECTED'");
        for (var count : counts.entrySet()) {
            Row r = query("SELECT COUNT(*) AS v FROM " + count.getValue(), Tuple.tuple()).iterator().next();
            assertEquals(Rows.intVal(r, "v"), m.getInteger(count.getKey()), count.getKey());
        }
        Row generated = query("SELECT ISNULL(SUM(total_amount),0) AS v FROM payment_cycles WHERE status<>'REJECTED'", Tuple.tuple()).iterator().next();
        assertEquals(Rows.dbl(generated, "v"), m.getDouble("totalGeneratedAmount"), .001);
        double cash = 0, vouchers = 0;
        for (Object item : m.getJsonArray("amountsByOrganisation")) {
            JsonObject org = (JsonObject) item;
            cash += org.getDouble("paymentsAmount"); vouchers += org.getDouble("voucherAmount");
            assertEquals(org.getDouble("paymentsAmount") + org.getDouble("voucherAmount"), org.getDouble("totalAmount"), .001);
        }
        assertEquals(m.getDouble("totalPaymentsAmount"), cash, .001);
        assertEquals(m.getDouble("voucherRedeemedAmount"), vouchers, .001);
        assertEquals(cash + vouchers, m.getDouble("combinedAmount"), .001);
        int paymentCount = Rows.intVal(query("SELECT COUNT(*) AS v FROM payments pay LEFT JOIN payment_cycles pc ON pc.id=pay.payment_cycle_id "
                + "WHERE pay.rejected=0 AND (pay.payment_cycle_id IS NULL OR pc.status='DISBURSED')", Tuple.tuple()).iterator().next(), "v");
        assertEquals(Math.min(paymentCount, 10), m.getJsonArray("recentTransactions").size());
    }
    @Test void eachChartPeriodMatchesRecordedEvents() throws Exception {
        LocalDate date = LocalDate.now();
        for (String period : new String[]{"day", "week", "month", "year"}) {
            JsonObject payload = system().put("period", period).put("referenceDate", date.toString());
            JsonArray cash = request("DASHBOARD_PAYMENTS_CHART", payload).getJsonArray("results");
            verifySeries(cash, "cashAmount", "cashCount", "SELECT pay.amount AS amount, COALESCE(pay.verified_at, pc.disbursed_at, pay.created_at) AS recorded FROM payments pay LEFT JOIN payment_cycles pc ON pc.id=pay.payment_cycle_id WHERE pay.status=1 AND pay.rejected=0", period, date);
            verifySeries(cash, "voucherAmount", "voucherCount", "SELECT amount, COALESCE(redeemed_at,created_at) AS recorded FROM vouchers WHERE status='REDEEMED'", period, date);
            JsonArray registrations = request("DASHBOARD_HOUSEHOLDS_CHART", payload).getJsonArray("results");
            verifySeries(registrations, null, "householdCount", "SELECT h.created_at AS recorded FROM households h JOIN organizations o ON o.organization_code=h.organization_code", period, date);
            verifySeries(registrations, null, "alternateCount", "SELECT a.created_at AS recorded FROM alternates a JOIN organizations o ON o.organization_code=a.organization_code", period, date);
        }
    }
    static void verifySeries(JsonArray actual, String amountKey, String countKey, String sql, String period, LocalDate date) throws Exception {
        LocalDate start = switch(period) {
            case "week" -> date.minusDays(date.getDayOfWeek().getValue() - 1);
            case "month" -> date.withDayOfMonth(1);
            case "year" -> date.withDayOfYear(1);
            default -> date;
        };
        LocalDate end = switch(period) { case "week" -> start.plusDays(7); case "month" -> start.plusMonths(1); case "year" -> start.plusYears(1); default -> start.plusDays(1); };
        Map<String, double[]> expected = new HashMap<>();
        for (Row row : query(sql, Tuple.tuple())) {
            LocalDateTime recorded = LocalDateTime.parse(Rows.str(row, "recorded").replace(' ', 'T'));
            if (recorded.toLocalDate().isBefore(start) || !recorded.toLocalDate().isBefore(end)) continue;
            String key = period.equals("day") ? String.format("%02d", recorded.getHour()) : period.equals("year") ? recorded.toLocalDate().toString().substring(0, 7) : recorded.toLocalDate().toString();
            double[] sums = expected.computeIfAbsent(key, k -> new double[2]);
            sums[0]++; if (amountKey != null) sums[1] += Rows.dbl(row, "amount");
        }
        for (Object item : actual) {
            JsonObject row = (JsonObject) item;
            double[] sums = expected.remove(row.getString("period"));
            if (sums == null) sums = new double[2];
            assertEquals(sums[0], row.getInteger(countKey), .001, period + ": " + countKey);
            if (amountKey != null) assertEquals(sums[1], row.getDouble(amountKey), .001, period + ": " + amountKey);
        }
        assertTrue(expected.isEmpty(), "No date bucket may disappear from the response");
    }
    @Test void organisationAndAnchorMetricsStayInTheirScope() throws Exception {
        for (Row org : query("SELECT TOP 3 organization_code, anchor_id FROM organizations ORDER BY organization_code", Tuple.tuple())) {
            String code = Rows.str(org, "organization_code");
            JsonObject m = request("DASHBOARD_METRICS", new JsonObject().put("actorRole", "ORGANISATION").put("partnerCode", code)).getJsonObject("results");
            Row cash = query("SELECT COUNT(*) AS cnt, ISNULL(SUM(amount),0) AS amount FROM payments WHERE organization_code=@p1 AND status=1 AND rejected=0", Tuple.of(code)).iterator().next();
            assertEquals(Rows.intVal(cash, "cnt"), m.getInteger("totalPaymentsReceivedCount"));
            assertEquals(Rows.dbl(cash, "amount"), m.getDouble("totalPaymentsReceivedAmount"), .001);
            int anchorId = Rows.intVal(org, "anchor_id");
            if (anchorId <= 0) continue;
            JsonObject anchor = request("DASHBOARD_METRICS", new JsonObject().put("actorRole", "ANCHOR").put("anchorId", anchorId)).getJsonObject("results");
            Row anchorCash = query("SELECT ISNULL(SUM(amount),0) AS amount FROM payments WHERE anchor_id=@p1 AND status=1 AND rejected=0", Tuple.of(anchorId)).iterator().next();
            assertEquals(Rows.dbl(anchorCash, "amount"), anchor.getDouble("totalPaymentsAmount"), .001);
        }
    }
}
