package com.biopay.agent.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.biopay.agent.session.SessionManager;

/**
 * Read side of the Activity tab's unified feed. There's no single audit-log table to query --
 * this pulls registrations (households/alternates), disbursements (payments) and redemptions
 * (vouchers) from their own tables, plus verification events from the small local log {@link
 * VerificationEventDao} writes to, then merges and sorts by created_at in Java. created_at is
 * SQLite's default TIMESTAMP text format (YYYY-MM-DD HH:MM:SS), which sorts correctly as a plain
 * string, so no date parsing is needed. Every source table is scoped to the logged-in officer's
 * own organisation (see {@link HouseholdDao} for why); `vouchers` carries no partner_code of its
 * own, so it's scoped via the household it belongs to instead.
 */
public class ActivityDao {

    public enum Category { REGISTRATION, VERIFICATION, DISBURSEMENT }

    public static class Event {
        public final Category category;
        public final String title;
        public final String subtitle;
        public final String createdAt;
        public final boolean pendingSync;

        Event(Category category, String title, String subtitle, String createdAt, boolean pendingSync) {
            this.category = category;
            this.title = title;
            this.subtitle = subtitle;
            this.createdAt = createdAt;
            this.pendingSync = pendingSync;
        }
    }

    private final DatabaseHelper dbHelper;
    private final String partnerCode;

    public ActivityDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
        partnerCode = new SessionManager(context).getPartnerCode();
    }

    public List<Event> listAll() {
        List<Event> events = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try (Cursor c = db.query("households", new String[]{"household_name", "household_number", "created_at", "sync_status"},
                "partner_code=?", new String[]{partnerCode}, null, null, null)) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                events.add(new Event(Category.REGISTRATION, "Household registered",
                        (name == null || name.isEmpty() ? c.getString(1) : name), c.getString(2), c.getInt(3) == 0));
            }
        }

        try (Cursor c = db.query("alternates", new String[]{"alternate_name", "household_number", "created_at", "sync_status"},
                "partner_code=?", new String[]{partnerCode}, null, null, null)) {
            while (c.moveToNext()) {
                events.add(new Event(Category.REGISTRATION, "Member registered", c.getString(0), c.getString(2), c.getInt(3) == 0));
            }
        }

        try (Cursor c = db.query("payments", new String[]{"household_name", "household_number", "amount", "created_at", "sync_status"},
                "partner_code=?", new String[]{partnerCode}, null, null, null)) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                double amount = c.getDouble(2);
                events.add(new Event(Category.DISBURSEMENT, "Disbursement completed",
                        (name == null || name.isEmpty() ? c.getString(1) : name) + " · " + java.text.NumberFormat.getNumberInstance().format(amount),
                        c.getString(3), c.getInt(4) == 0));
            }
        }

        try (Cursor c = db.rawQuery("SELECT v.household_number, v.amount, v.created_at, v.redemption_sync_status FROM vouchers v "
                        + "WHERE v.status='REDEEMED' AND EXISTS (SELECT 1 FROM households h WHERE h.household_number=v.household_number AND h.partner_code=?)",
                new String[]{partnerCode})) {
            while (c.moveToNext()) {
                events.add(new Event(Category.DISBURSEMENT, "Voucher redeemed",
                        c.getString(0) + " · " + java.text.NumberFormat.getNumberInstance().format(c.getDouble(1)),
                        c.getString(2), c.getInt(3) == 0));
            }
        }

        try (Cursor c = db.query("verification_events", new String[]{"person_name", "household_number", "method", "created_at"},
                "partner_code=?", new String[]{partnerCode}, null, null, null)) {
            while (c.moveToNext()) {
                events.add(new Event(Category.VERIFICATION, c.getString(2) + " verified", c.getString(0), c.getString(3), false));
            }
        }

        Collections.sort(events, (a, b) -> {
            if (a.createdAt == null || b.createdAt == null) return 0;
            return b.createdAt.compareTo(a.createdAt);
        });
        return events;
    }
}
