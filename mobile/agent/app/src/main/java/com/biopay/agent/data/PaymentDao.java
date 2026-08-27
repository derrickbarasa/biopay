package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Local read/write access to the offline `payments` table. Rows here are
 * field-recorded payments (post biometric verification, see
 * RECORD_FIELD_PAYMENT on the backend) plus a local cache of payroll-cycle
 * payments downloaded via SYNC_PAYMENTS/BIOMETRIC_LOGIN for the officer's
 * organisation -- {@link #status} distinguishes PAID (1) from PENDING (0)
 * for the Reports screen's paid/unpaid split.
 */
public class PaymentDao {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;

    private final DatabaseHelper dbHelper;

    public PaymentDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    /** Exactly one of matchedFingerprintUuid/matchedFaceUuid should be non-null -- whichever
     *  method verified the beneficiary. interventionType (Cash/Voucher/Food/In-kind) is
     *  local-only -- RECORD_FIELD_PAYMENT has no matching backend column yet, see DatabaseHelper's
     *  version-7 upgrade note -- it shows on the on-device receipt but doesn't sync. */
    public long recordFieldPayment(String supervisorId, String partnerCode, String householdNumber,
            String householdName, double amount, String matchedFingerprintUuid, String matchedFaceUuid,
            String latitude, String longitude, String uuid, String interventionType) {
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("household_number", householdNumber);
        values.put("household_name", householdName);
        values.put("amount", amount);
        values.put("matched_fingerprint_uuid", matchedFingerprintUuid);
        values.put("matched_face_uuid", matchedFaceUuid);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("uuid", uuid);
        values.put("intervention_type", interventionType);
        values.put("status", STATUS_PAID);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        return dbHelper.getWritableDatabase().insert("payments", null, values);
    }

    /** Replaces the cached (server-side) payment list for this org -- called after a successful sync. */
    public void replaceRemoteCache(List<RemotePayment> payments, String partnerCode) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("payments", "remote_id IS NOT NULL AND partner_code=?", new String[]{partnerCode});
            for (RemotePayment p : payments) {
                ContentValues values = new ContentValues();
                values.put("remote_id", p.id);
                values.put("partner_code", partnerCode);
                values.put("household_number", p.householdNumber);
                values.put("amount", p.amount);
                values.put("status", p.status);
                values.put("uuid", "remote-" + p.id);
                values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
                db.insertWithOnConflict("payments", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<LocalPayment> listByStatus(Integer status) {
        List<LocalPayment> results = new ArrayList<>();
        String selection = status == null ? null : "status=?";
        String[] args = status == null ? null : new String[]{String.valueOf(status)};
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", null, selection, args,
                null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                LocalPayment p = new LocalPayment();
                p.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                p.householdName = cursor.getString(cursor.getColumnIndexOrThrow("household_name"));
                p.amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                p.status = cursor.getInt(cursor.getColumnIndexOrThrow("status"));
                results.add(p);
            }
        }
        return results;
    }

    /** Backs the households list's "Paid" status chip -- has any payment for this household
     * actually been marked paid, regardless of which cycle/session recorded it. */
    public boolean hasPaidPayment(String householdNumber) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"COUNT(*)"},
                "household_number=? AND status=?",
                new String[]{householdNumber, String.valueOf(STATUS_PAID)}, null, null, null)) {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
    }

    public int countByStatus(int status) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"COUNT(*)"},
                "status=?", new String[]{String.valueOf(status)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public double totalAmountByStatus(int status) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"IFNULL(SUM(amount),0)"},
                "status=?", new String[]{String.valueOf(status)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    /** Locally-recorded field payments awaiting RECORD_FIELD_PAYMENT upload -- excludes the
     * remote-cache rows from {@link #replaceRemoteCache}, which are inserted already SYNCED. */
    public List<PendingFieldPayment> listPendingFieldPayments() {
        List<PendingFieldPayment> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", null,
                "sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)}, null, null, null)) {
            while (cursor.moveToNext()) {
                PendingFieldPayment p = new PendingFieldPayment();
                p.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                p.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                p.amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                p.matchedFingerprintUuid = cursor.getString(cursor.getColumnIndexOrThrow("matched_fingerprint_uuid"));
                p.matchedFaceUuid = cursor.getString(cursor.getColumnIndexOrThrow("matched_face_uuid"));
                p.latitude = cursor.getString(cursor.getColumnIndexOrThrow("latitude"));
                p.longitude = cursor.getString(cursor.getColumnIndexOrThrow("longitude"));
                results.add(p);
            }
        }
        return results;
    }

    public void markSynced(long id) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("payments", values, "id=?", new String[]{String.valueOf(id)});
    }

    public static class PendingFieldPayment {
        public long id;
        public String householdNumber;
        public double amount;
        public String matchedFingerprintUuid;
        public String matchedFaceUuid;
        public String latitude;
        public String longitude;
    }

    public static class LocalPayment {
        public String householdNumber;
        public String householdName;
        public double amount;
        public int status;
    }

    public static class RemotePayment {
        public int id;
        public String householdNumber;
        public double amount;
        public int status;
    }
}
