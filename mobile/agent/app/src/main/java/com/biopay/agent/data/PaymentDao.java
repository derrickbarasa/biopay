package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import com.biopay.agent.session.SessionManager;

/**
 * Local read/write access to the offline `payments` table. Rows here are
 * field-recorded payments (post biometric verification, see
 * RECORD_FIELD_PAYMENT on the backend) plus a local cache of payroll-cycle
 * payments downloaded via SYNC_PAYMENTS/BIOMETRIC_LOGIN for the officer's
 * organisation -- {@link #status} distinguishes PENDING (0), PAID (1) and
 * FAILED (2) for the Reports screen's paid/unpaid split. Listing/aggregate
 * reads are scoped to the logged-in officer's own organisation (see
 * {@link HouseholdDao} for why).
 */
public class PaymentDao {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    /** Field verification failed (fingerprint/face mismatch or error) -- see
     *  {@link #markGeneratedPaymentFailed}. Recoverable from the dashboard via "Pay Online". */
    public static final int STATUS_FAILED = 2;

    private final DatabaseHelper dbHelper;
    private final String partnerCode;

    public PaymentDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
        partnerCode = new SessionManager(context).getPartnerCode();
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
            db.delete("payments", "remote_id IS NOT NULL AND partner_code=? AND sync_status=?",
                    new String[]{partnerCode, String.valueOf(DatabaseHelper.SYNC_SYNCED)});
            for (RemotePayment p : payments) {
                try (Cursor pending = db.query("payments", new String[]{"id"},
                        "remote_id=? AND sync_status=?",
                        new String[]{String.valueOf(p.id), String.valueOf(DatabaseHelper.SYNC_PENDING)},
                        null, null, null)) {
                    if (pending.moveToFirst()) continue;
                }
                ContentValues values = new ContentValues();
                values.put("remote_id", p.id);
                values.put("partner_code", partnerCode);
                values.put("household_number", p.householdNumber);
                values.put("household_name", p.householdName);
                values.put("village_code", p.villageCode);
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
        String where = status == null ? " WHERE p.partner_code=?" : " WHERE p.partner_code=? AND p.status=?";
        String[] args = status == null ? new String[]{partnerCode} : new String[]{partnerCode, String.valueOf(status)};
        String sql = "SELECT p.*, COALESCE(NULLIF(p.household_name,''), h.household_name, p.household_number) display_name, "
                + "COALESCE(b.boma_name, NULLIF(p.village_code,''), NULLIF(h.boma_code,''), '') display_village "
                + "FROM payments p LEFT JOIN households h ON h.household_number=p.household_number "
                + "LEFT JOIN bomas b ON b.boma_code=COALESCE(NULLIF(p.village_code,''), h.boma_code)"
                + where + " ORDER BY p.created_at DESC";
        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                LocalPayment p = new LocalPayment();
                int remoteIdx = cursor.getColumnIndexOrThrow("remote_id");
                p.remoteId = cursor.isNull(remoteIdx) ? null : cursor.getInt(remoteIdx);
                p.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                p.householdName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"));
                p.village = cursor.getString(cursor.getColumnIndexOrThrow("display_village"));
                p.amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                p.status = cursor.getInt(cursor.getColumnIndexOrThrow("status"));
                results.add(p);
            }
        }
        return results;
    }

    /** Generated, unpaid entitlements are the only rows eligible for the Payment tab. */
    public List<LocalPayment> listPendingGeneratedPayments() {
        List<LocalPayment> pending = listByStatus(STATUS_PENDING);
        List<LocalPayment> generated = new ArrayList<>();
        for (LocalPayment payment : pending) {
            if (payment.remoteId != null && hasVerifiableBeneficiary(payment.householdNumber)) {
                generated.add(payment);
            }
        }
        return generated;
    }

    private boolean hasVerifiableBeneficiary(String householdNumber) {
        String sql = "SELECT 1 FROM households h WHERE h.household_number=? AND ("
                + "EXISTS(SELECT 1 FROM fingerprints f WHERE f.beneficiary_id=h.household_number) OR "
                + "EXISTS(SELECT 1 FROM faces f WHERE f.beneficiary_id=h.household_number) OR "
                + "EXISTS(SELECT 1 FROM alternates a WHERE a.household_number=h.household_number AND ("
                + "EXISTS(SELECT 1 FROM fingerprints fp WHERE fp.beneficiary_id=a.alternate_number) OR "
                + "EXISTS(SELECT 1 FROM faces fa WHERE fa.beneficiary_id=a.alternate_number)))) LIMIT 1";
        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(sql, new String[]{householdNumber})) {
            return cursor.moveToFirst();
        }
    }

    /** Completes the cached generated row in-place instead of inserting a duplicate payment. */
    public int completeGeneratedPayment(int remoteId, String supervisorId, String householdName,
            String matchedFingerprintUuid, String matchedFaceUuid, String latitude, String longitude) {
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("household_name", householdName);
        values.put("matched_fingerprint_uuid", matchedFingerprintUuid);
        values.put("matched_face_uuid", matchedFaceUuid);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("status", STATUS_PAID);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        return dbHelper.getWritableDatabase().update("payments", values,
                "remote_id=? AND status=?", new String[]{String.valueOf(remoteId), String.valueOf(STATUS_PENDING)});
    }

    /** Field verification failed for a generated payment -- moves it to FAILED locally and
     *  queues the change for sync, so the dashboard's System Owner can recover it with "Pay
     *  Online" instead of it silently sitting PENDING forever. */
    public int markGeneratedPaymentFailed(int remoteId) {
        ContentValues values = new ContentValues();
        values.put("status", STATUS_FAILED);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        return dbHelper.getWritableDatabase().update("payments", values,
                "remote_id=? AND status=?", new String[]{String.valueOf(remoteId), String.valueOf(STATUS_PENDING)});
    }

    /** Backs the households list's "Paid" status chip -- has any payment for this household
     * actually been marked paid, regardless of which cycle/session recorded it. */
    public boolean hasPaidPayment(String householdNumber) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"COUNT(*)"},
                "household_number=? AND status=? AND partner_code=?",
                new String[]{householdNumber, String.valueOf(STATUS_PAID), partnerCode}, null, null, null)) {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
    }

    public int countByStatus(int status) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"COUNT(*)"},
                "status=? AND partner_code=?", new String[]{String.valueOf(status), partnerCode}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public double totalAmountByStatus(int status) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("payments", new String[]{"IFNULL(SUM(amount),0)"},
                "status=? AND partner_code=?", new String[]{String.valueOf(status), partnerCode}, null, null, null)) {
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
                int remoteIdx = cursor.getColumnIndexOrThrow("remote_id");
                p.remoteId = cursor.isNull(remoteIdx) ? null : cursor.getInt(remoteIdx);
                p.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                p.amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                p.matchedFingerprintUuid = cursor.getString(cursor.getColumnIndexOrThrow("matched_fingerprint_uuid"));
                p.matchedFaceUuid = cursor.getString(cursor.getColumnIndexOrThrow("matched_face_uuid"));
                p.latitude = cursor.getString(cursor.getColumnIndexOrThrow("latitude"));
                p.longitude = cursor.getString(cursor.getColumnIndexOrThrow("longitude"));
                p.status = cursor.getInt(cursor.getColumnIndexOrThrow("status"));
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
        public Integer remoteId;
        public String householdNumber;
        public double amount;
        public String matchedFingerprintUuid;
        public String matchedFaceUuid;
        public String latitude;
        public String longitude;
        public int status;
    }

    public static class LocalPayment {
        public Integer remoteId;
        public String householdNumber;
        public String householdName;
        public String village;
        public double amount;
        public int status;

        @Override public String toString() {
            return householdName + "  ·  " + householdNumber;
        }
    }

    public static class RemotePayment {
        public int id;
        public String householdNumber;
        public String householdName;
        public String villageCode;
        public double amount;
        public int status;
    }
}
