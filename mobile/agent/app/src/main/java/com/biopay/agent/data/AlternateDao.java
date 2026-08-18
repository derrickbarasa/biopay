package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Local read/write access to the offline `alternates` table. */
public class AlternateDao {

    private final DatabaseHelper dbHelper;

    public AlternateDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public long insert(ContentValues values) {
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        return dbHelper.getWritableDatabase()
                .insertWithOnConflict("alternates", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void setPhotoLocalPath(String alternateNumber, String path) {
        ContentValues values = new ContentValues();
        values.put("photo_local_path", path);
        dbHelper.getWritableDatabase().update("alternates", values, "alternate_number=?", new String[]{alternateNumber});
    }

    public List<Alternate> findByHousehold(String householdNumber) {
        return query("household_number=?", new String[]{householdNumber});
    }

    public List<Alternate> listPending() {
        return query("sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)});
    }

    public List<Alternate> search(String searchText) {
        String like = "%" + (searchText == null ? "" : searchText.trim()) + "%";
        return query("alternate_name LIKE ? OR alternate_number LIKE ? OR household_number LIKE ?",
                new String[]{like, like, like});
    }

    public int countAll() {
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                "alternates", new String[]{"COUNT(*)"}, null, null, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public void markSynced(String alternateNumber) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("alternates", values, "alternate_number=?", new String[]{alternateNumber});
    }

    private List<Alternate> query(String selection, String[] args) {
        List<Alternate> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("alternates", null,
                selection, args, null, null, "created_at DESC")) {
            while (cursor.moveToNext()) {
                Alternate a = new Alternate();
                a.alternateNumber = cursor.getString(cursor.getColumnIndexOrThrow("alternate_number"));
                a.householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                a.alternateName = cursor.getString(cursor.getColumnIndexOrThrow("alternate_name"));
                a.relationship = cursor.getString(cursor.getColumnIndexOrThrow("relationship"));
                a.phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow("phone_number"));
                a.gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
                int ageIdx = cursor.getColumnIndexOrThrow("age");
                a.age = cursor.isNull(ageIdx) ? null : cursor.getInt(ageIdx);
                results.add(a);
            }
        }
        return results;
    }

    public static class Alternate {
        public String alternateNumber;
        public String householdNumber;
        public String alternateName;
        public String relationship;
        public String phoneNumber;
        public String gender;
        public Integer age;
    }
}
