package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

import com.biopay.agent.session.SessionManager;

/** Local read/write access to the offline `households` table. Reads are scoped to the
 *  logged-in officer's own organisation -- see {@link #partnerCode} -- so a device that was
 *  ever used for another organisation never surfaces that organisation's beneficiaries. */
public class HouseholdDao {

    private final DatabaseHelper dbHelper;
    private final String partnerCode;

    public HouseholdDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
        partnerCode = new SessionManager(context).getPartnerCode();
    }

    public long insert(ContentValues values) {
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insertWithOnConflict("households", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Upserts a server copy unless this device still has an unsent edit for the same household. */
    public void upsertSynced(JSONObject row, String supervisorId, String partnerCode) {
        String number = row.optString("householdNumber", "").trim();
        if (number.isEmpty() || isPending(number)) return;
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("household_number", number);
        put(values, "household_name", row, "householdName");
        put(values, "registration_method", row, "registrationMethod");
        put(values, "id_number", row, "idNumber");
        put(values, "phone_number", row, "phoneNumber");
        putInt(values, "age", row, "age");
        put(values, "gender", row, "gender");
        putInt(values, "household_size", row, "householdSize");
        putInt(values, "male_dependants", row, "maleDependants");
        putInt(values, "female_dependants", row, "femaleDependants");
        putInt(values, "disabled_members", row, "disabledMembers");
        put(values, "literacy", row, "literacy");
        put(values, "eligibility", row, "eligibility");
        put(values, "vulnerability_statuses", row, "vulnerabilityStatuses");
        put(values, "legal_status", row, "legalStatus");
        put(values, "state_code", row, "stateCode");
        put(values, "county_code", row, "countyCode");
        put(values, "payam_code", row, "payamCode");
        put(values, "boma_code", row, "bomaCode");
        put(values, "latitude", row, "latitude");
        put(values, "longitude", row, "longitude");
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int updated = db.update("households", values, "household_number=?", new String[]{number});
        if (updated == 0) db.insert("households", null, values);
    }

    private boolean isPending(String householdNumber) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("households", new String[]{"sync_status"},
                "household_number=?", new String[]{householdNumber}, null, null, null)) {
            return cursor.moveToFirst() && cursor.getInt(0) == DatabaseHelper.SYNC_PENDING;
        }
    }

    private static void put(ContentValues values, String column, JSONObject row, String key) {
        if (row.isNull(key)) values.putNull(column); else values.put(column, row.optString(key, null));
    }

    private static void putInt(ContentValues values, String column, JSONObject row, String key) {
        if (row.isNull(key)) values.putNull(column); else values.put(column, row.optInt(key));
    }

    public int update(String householdNumber, ContentValues values) {
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update("households", values, "household_number=?", new String[]{householdNumber});
    }

    public void setPhotoLocalPath(String householdNumber, String path) {
        ContentValues values = new ContentValues();
        values.put("photo_local_path", path);
        update(householdNumber, values);
    }

    public List<Household> search(String query) {
        List<Household> results = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String like = "%" + (query == null ? "" : query) + "%";
        try (Cursor cursor = db.query("households", null,
                "partner_code=? AND (household_name LIKE ? OR household_number LIKE ? OR id_number LIKE ?)",
                new String[]{partnerCode, like, like, like}, null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                results.add(Household.fromCursor(cursor));
            }
        }
        return results;
    }

    public Household findByNumber(String householdNumber) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("households", null, "household_number=? AND partner_code=?",
                new String[]{householdNumber, partnerCode}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return Household.fromCursor(cursor);
            }
        }
        return null;
    }

    public int countAll() {
        return countWhere("partner_code=?", new String[]{partnerCode});
    }

    public int countPendingSync() {
        return countWhere("sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)});
    }

    public List<Household> listPending() {
        List<Household> results = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("households", null, "sync_status=?",
                new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)}, null, null, null)) {
            while (cursor.moveToNext()) {
                results.add(Household.fromCursor(cursor));
            }
        }
        return results;
    }

    public void markSynced(String householdNumber) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("households", values, "household_number=?", new String[]{householdNumber});
    }

    private int countWhere(String selection, String[] args) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("households", new String[]{"COUNT(*)"}, selection, args, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Plain data holder -- deliberately not the ContentValues/Cursor pair dca/nca passed around everywhere. */
    public static class Household {
        public String householdNumber;
        public String householdName;
        public String registrationMethod;
        public String idNumber;
        public String phoneNumber;
        public String gender;
        public Integer age;
        public String stateCode;
        public String countyCode;
        public String payamCode;
        public String bomaCode;
        public Integer householdSize;
        public Integer maleDependants;
        public Integer femaleDependants;
        public boolean disabledMembers;
        public boolean literate;
        public boolean eligible;
        public String vulnerabilityStatuses;
        public String legalStatus;
        public String photoLocalPath;
        public String latitude;
        public String longitude;
        public int syncStatus;

        static Household fromCursor(Cursor c) {
            Household h = new Household();
            h.householdNumber = c.getString(c.getColumnIndexOrThrow("household_number"));
            h.householdName = c.getString(c.getColumnIndexOrThrow("household_name"));
            h.registrationMethod = c.getString(c.getColumnIndexOrThrow("registration_method"));
            h.idNumber = c.getString(c.getColumnIndexOrThrow("id_number"));
            h.phoneNumber = c.getString(c.getColumnIndexOrThrow("phone_number"));
            h.gender = c.getString(c.getColumnIndexOrThrow("gender"));
            int ageIdx = c.getColumnIndexOrThrow("age");
            h.age = c.isNull(ageIdx) ? null : c.getInt(ageIdx);
            h.stateCode = c.getString(c.getColumnIndexOrThrow("state_code"));
            h.countyCode = c.getString(c.getColumnIndexOrThrow("county_code"));
            h.payamCode = c.getString(c.getColumnIndexOrThrow("payam_code"));
            h.bomaCode = c.getString(c.getColumnIndexOrThrow("boma_code"));
            int sizeIdx = c.getColumnIndexOrThrow("household_size");
            h.householdSize = c.isNull(sizeIdx) ? null : c.getInt(sizeIdx);
            int maleIdx = c.getColumnIndexOrThrow("male_dependants");
            h.maleDependants = c.isNull(maleIdx) ? null : c.getInt(maleIdx);
            int femaleIdx = c.getColumnIndexOrThrow("female_dependants");
            h.femaleDependants = c.isNull(femaleIdx) ? null : c.getInt(femaleIdx);
            h.disabledMembers = c.getInt(c.getColumnIndexOrThrow("disabled_members")) != 0;
            h.literate = "Y".equals(c.getString(c.getColumnIndexOrThrow("literacy")));
            h.eligible = "Y".equals(c.getString(c.getColumnIndexOrThrow("eligibility")));
            h.vulnerabilityStatuses = c.getString(c.getColumnIndexOrThrow("vulnerability_statuses"));
            h.legalStatus = c.getString(c.getColumnIndexOrThrow("legal_status"));
            h.photoLocalPath = c.getString(c.getColumnIndexOrThrow("photo_local_path"));
            h.latitude = c.getString(c.getColumnIndexOrThrow("latitude"));
            h.longitude = c.getString(c.getColumnIndexOrThrow("longitude"));
            h.syncStatus = c.getInt(c.getColumnIndexOrThrow("sync_status"));
            return h;
        }
    }
}
