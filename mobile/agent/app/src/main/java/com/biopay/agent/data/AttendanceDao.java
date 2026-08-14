package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/** Local read/write access to the offline `attendances` table (biometric-verified clock-in/out). */
public class AttendanceDao {

    private final DatabaseHelper dbHelper;

    public AttendanceDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public long record(String supervisorId, String partnerCode, String householdNumber, int beneficiaryType,
            String beneficiaryId, String matchedFingerprintUuid, String clock, String workCode,
            String latitude, String longitude, String uuid) {
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("household_number", householdNumber);
        values.put("beneficiary_type", beneficiaryType);
        values.put("beneficiary_id", beneficiaryId);
        values.put("matched_fingerprint_uuid", matchedFingerprintUuid);
        values.put("clock", clock);
        values.put("work_code", workCode);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("uuid", uuid);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        return dbHelper.getWritableDatabase().insert("attendances", null, values);
    }

    public List<PendingAttendance> listPending() {
        List<PendingAttendance> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("attendances", null,
                "sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)}, null, null, null)) {
            while (cursor.moveToNext()) {
                PendingAttendance p = new PendingAttendance();
                p.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                p.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                p.beneficiaryType = cursor.getInt(cursor.getColumnIndexOrThrow("beneficiary_type"));
                p.beneficiaryId = cursor.getString(cursor.getColumnIndexOrThrow("beneficiary_id"));
                p.matchedFingerprintUuid = cursor.getString(cursor.getColumnIndexOrThrow("matched_fingerprint_uuid"));
                p.clock = cursor.getString(cursor.getColumnIndexOrThrow("clock"));
                p.workCode = cursor.getString(cursor.getColumnIndexOrThrow("work_code"));
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
        dbHelper.getWritableDatabase().update("attendances", values, "id=?", new String[]{String.valueOf(id)});
    }

    public static class PendingAttendance {
        public long id;
        public String householdNumber;
        public int beneficiaryType;
        public String beneficiaryId;
        public String matchedFingerprintUuid;
        public String clock;
        public String workCode;
        public String latitude;
        public String longitude;
    }
}
