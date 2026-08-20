package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Local read/write access to the offline `households` table. */
public class HouseholdDao {

    private final DatabaseHelper dbHelper;

    public HouseholdDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public long insert(ContentValues values) {
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insertWithOnConflict("households", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int update(String householdNumber, ContentValues values) {
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
                "household_name LIKE ? OR household_number LIKE ? OR id_number LIKE ?",
                new String[]{like, like, like}, null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                results.add(Household.fromCursor(cursor));
            }
        }
        return results;
    }

    public Household findByNumber(String householdNumber) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("households", null, "household_number=?",
                new String[]{householdNumber}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return Household.fromCursor(cursor);
            }
        }
        return null;
    }

    public int countAll() {
        return countWhere(null, null);
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
        public String photoLocalPath;
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
            h.photoLocalPath = c.getString(c.getColumnIndexOrThrow("photo_local_path"));
            h.syncStatus = c.getInt(c.getColumnIndexOrThrow("sync_status"));
            return h;
        }
    }
}
