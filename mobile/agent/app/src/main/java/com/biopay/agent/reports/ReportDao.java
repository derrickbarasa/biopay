package com.biopay.agent.reports;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.session.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only, organisation-scoped aggregates for the offline Field reports screen. */
final class ReportDao {
    enum Range { TODAY, SEVEN_DAYS, THIRTY_DAYS, ALL }

    static final class Summary {
        int registrations, verifications, payments, attendances;
        int paidCount, pendingCount, failedCount;
        double paidAmount, pendingAmount;
        int totalHouseholds, readyHouseholds, incompleteHouseholds, waitingSync;
        final int[] sevenDayActivity = new int[7];
        final List<LocationCount> locations = new ArrayList<>();
        final List<RecentItem> recent = new ArrayList<>();
    }

    static final class LocationCount {
        final String name;
        final int count;
        LocationCount(String name, int count) { this.name = name; this.count = count; }
    }

    static final class RecentItem {
        final String title, subtitle, createdAt;
        RecentItem(String title, String subtitle, String createdAt) {
            this.title = title; this.subtitle = subtitle; this.createdAt = createdAt;
        }
    }

    private static final class TimeWindow {
        final String since, before;
        TimeWindow(String since, String before) { this.since = since; this.before = before; }
    }

    private final DatabaseHelper helper;
    private final String partnerCode;

    ReportDao(Context context) {
        helper = DatabaseHelper.get(context);
        partnerCode = new SessionManager(context).getPartnerCode();
    }

    Summary load(Range range) {
        Summary result = new Summary();
        SQLiteDatabase db = helper.getReadableDatabase();
        TimeWindow window = window(range);
        result.registrations = count(db, "households", "partner_code=?" + dated(window), args(window));
        result.verifications = count(db, "verification_events", "partner_code=?" + dated(window), args(window));
        result.payments = count(db, "payments", "partner_code=? AND status=1" + dated(window), args(window));
        result.attendances = count(db, "attendances", "partner_code=?" + dated(window), args(window));
        readPaymentSummary(db, window, result);
        readReadiness(db, window, result);
        result.waitingSync = helper.countPendingSyncWork(partnerCode);
        readSevenDayActivity(db, window, result.sevenDayActivity);
        readLocations(db, window, result.locations, 5);
        readRecent(db, window, result.recent);
        return result;
    }

    List<LocationCount> loadAllLocations(Range range) {
        List<LocationCount> result = new ArrayList<>();
        readLocations(helper.getReadableDatabase(), window(range), result, 0);
        return result;
    }

    private void readPaymentSummary(SQLiteDatabase db, TimeWindow window, Summary out) {
        String sql = "SELECT status, COUNT(*), IFNULL(SUM(amount),0) FROM payments "
                + "WHERE partner_code=?" + dated(window) + " GROUP BY status";
        try (Cursor cursor = db.rawQuery(sql, args(window))) {
            while (cursor.moveToNext()) {
                int status = cursor.getInt(0);
                if (status == 1) {
                    out.paidCount = cursor.getInt(1);
                    out.paidAmount = cursor.getDouble(2);
                } else if (status == 0) {
                    out.pendingCount = cursor.getInt(1);
                    out.pendingAmount = cursor.getDouble(2);
                } else if (status == 2) {
                    out.failedCount = cursor.getInt(1);
                }
            }
        }
    }

    private void readReadiness(SQLiteDatabase db, TimeWindow window, Summary out) {
        String verifiable = "(EXISTS(SELECT 1 FROM fingerprints f WHERE f.beneficiary_id=h.household_number) "
                + "OR EXISTS(SELECT 1 FROM faces f WHERE f.beneficiary_id=h.household_number) "
                + "OR EXISTS(SELECT 1 FROM alternates a WHERE a.household_number=h.household_number AND ("
                + "EXISTS(SELECT 1 FROM fingerprints af WHERE af.beneficiary_id=a.alternate_number) "
                + "OR EXISTS(SELECT 1 FROM faces ax WHERE ax.beneficiary_id=a.alternate_number))))";
        out.totalHouseholds = scalar(db, "SELECT COUNT(*) FROM households h WHERE h.partner_code=?" + dated(window, "h.created_at"),
                args(window));
        out.readyHouseholds = scalar(db,
                "SELECT COUNT(*) FROM households h WHERE h.partner_code=?" + dated(window, "h.created_at") + " AND " + verifiable,
                args(window));
        out.incompleteHouseholds = scalar(db,
                "SELECT COUNT(*) FROM households h WHERE h.partner_code=?" + dated(window, "h.created_at") + " AND ("
                        + "TRIM(IFNULL(h.household_name,''))='' OR TRIM(IFNULL(h.boma_code,''))='' "
                        + "OR TRIM(IFNULL(h.latitude,''))='' OR TRIM(IFNULL(h.longitude,''))='')",
                args(window));
    }

    private void readSevenDayActivity(SQLiteDatabase db, TimeWindow window, int[] values) {
        String sevenDayStart = startOfDayDaysAgo(6);
        String start = window == null || window.since.compareTo(sevenDayStart) < 0 ? sevenDayStart : window.since;
        String before = window == null ? startOfDayDaysAgo(-1) : window.before;
        String union = "SELECT created_at FROM households WHERE partner_code=? AND created_at>=? AND created_at<? "
                + "UNION ALL SELECT created_at FROM verification_events WHERE partner_code=? AND created_at>=? AND created_at<? "
                + "UNION ALL SELECT created_at FROM payments WHERE partner_code=? AND status=1 AND created_at>=? AND created_at<? "
                + "UNION ALL SELECT created_at FROM attendances WHERE partner_code=? AND created_at>=? AND created_at<?";
        String sql = "SELECT date(created_at,'localtime'), COUNT(*) FROM (" + union
                + ") GROUP BY date(created_at,'localtime')";
        String[] queryArgs = {partnerCode, start, before, partnerCode, start, before,
                partnerCode, start, before, partnerCode, start, before};
        try (Cursor cursor = db.rawQuery(sql, queryArgs)) {
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                for (int i = 0; i < 7; i++) {
                    if (dayKey(6 - i).equals(date)) { values[i] = cursor.getInt(1); break; }
                }
            }
        }
    }

    private void readLocations(SQLiteDatabase db, TimeWindow window, List<LocationCount> out, int limit) {
        String sql = "SELECT COALESCE(NULLIF(b.boma_name,''), NULLIF(h.boma_code,''), 'Unknown location'), COUNT(*) "
                + "FROM households h LEFT JOIN bomas b ON b.boma_code=h.boma_code "
                + "WHERE h.partner_code=?" + dated(window, "h.created_at")
                + " GROUP BY COALESCE(NULLIF(b.boma_name,''), NULLIF(h.boma_code,''), 'Unknown location') "
                + "ORDER BY COUNT(*) DESC, 1 ASC" + (limit > 0 ? " LIMIT " + limit : "");
        try (Cursor cursor = db.rawQuery(sql, args(window))) {
            while (cursor.moveToNext()) out.add(new LocationCount(cursor.getString(0), cursor.getInt(1)));
        }
    }

    private void readRecent(SQLiteDatabase db, TimeWindow window, List<RecentItem> out) {
        String dc = dated(window);
        String sql = "SELECT title, subtitle, created_at FROM ("
                + "SELECT 'Household registered' title, COALESCE(NULLIF(household_name,''), household_number) subtitle, created_at FROM households WHERE partner_code=?" + dc
                + " UNION ALL SELECT COALESCE(NULLIF(method,''),'Identity') || ' verified', COALESCE(NULLIF(person_name,''), household_number), created_at FROM verification_events WHERE partner_code=?" + dc
                + " UNION ALL SELECT 'Payment completed', COALESCE(NULLIF(household_name,''), household_number), created_at FROM payments WHERE partner_code=? AND status=1" + dc
                + " UNION ALL SELECT CASE WHEN clock='IN' THEN 'Attendance check-in' ELSE 'Attendance check-out' END, beneficiary_id, created_at FROM attendances WHERE partner_code=?" + dc
                + ") ORDER BY created_at DESC LIMIT 5";
        List<String> queryArgs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            queryArgs.add(partnerCode);
            if (window != null) { queryArgs.add(window.since); queryArgs.add(window.before); }
        }
        try (Cursor cursor = db.rawQuery(sql, queryArgs.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                out.add(new RecentItem(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
            }
        }
    }

    private int count(SQLiteDatabase db, String table, String where, String[] queryArgs) {
        return scalar(db, "SELECT COUNT(*) FROM " + table + " WHERE " + where, queryArgs);
    }

    private int scalar(SQLiteDatabase db, String sql, String[] queryArgs) {
        try (Cursor cursor = db.rawQuery(sql, queryArgs)) { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
    }

    private String[] args(TimeWindow window) {
        return window == null ? new String[]{partnerCode}
                : new String[]{partnerCode, window.since, window.before};
    }

    private static String dated(TimeWindow window) { return dated(window, "created_at"); }
    private static String dated(TimeWindow window, String column) {
        return window == null ? "" : " AND " + column + ">=? AND " + column + "<?";
    }

    private static TimeWindow window(Range range) {
        if (range == Range.ALL) return null;
        return new TimeWindow(
                startOfDayDaysAgo(range == Range.TODAY ? 0 : range == Range.SEVEN_DAYS ? 6 : 29),
                startOfDayDaysAgo(-1));
    }

    private static String startOfDayDaysAgo(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(calendar.getTime());
    }

    private static String dayKey(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }
}
