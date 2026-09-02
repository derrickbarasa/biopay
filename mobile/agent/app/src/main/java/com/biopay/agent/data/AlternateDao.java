package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

import com.biopay.agent.session.SessionManager;

/** Local read/write access to the offline `alternates` table, scoped to the logged-in
 *  officer's own organisation (see {@link HouseholdDao} for why). */
public class AlternateDao {

    private final DatabaseHelper dbHelper;
    private final String partnerCode;

    public AlternateDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
        partnerCode = new SessionManager(context).getPartnerCode();
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
        return query("household_number=? AND partner_code=?", new String[]{householdNumber, partnerCode});
    }

    public List<Alternate> listPending() {
        return query("sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)});
    }

    public void upsertSynced(JSONObject row, String supervisorId, String partnerCode) {
        String number = row.optString("alternateNumber", "").trim();
        if (number.isEmpty()) return;
        try (Cursor cursor = dbHelper.getReadableDatabase().query("alternates", new String[]{"sync_status"},
                "alternate_number=?", new String[]{number}, null, null, null)) {
            if (cursor.moveToFirst() && cursor.getInt(0) == DatabaseHelper.SYNC_PENDING) return;
        }
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("alternate_number", number);
        values.put("household_number", row.optString("householdNumber", null));
        values.put("alternate_name", row.optString("alternateName", null));
        values.put("registration_method", row.optString("registrationMethod", "FINGERPRINT"));
        values.put("relationship", row.optString("relationship", null));
        values.put("id_number", row.optString("idNumber", null));
        values.put("phone_number", row.optString("phoneNumber", null));
        if (row.isNull("age")) values.putNull("age"); else values.put("age", row.optInt("age"));
        values.put("gender", row.optString("gender", null));
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int updated = db.update("alternates", values, "alternate_number=?", new String[]{number});
        if (updated == 0) db.insert("alternates", null, values);
    }

    public List<Alternate> search(String searchText) {
        String like = "%" + (searchText == null ? "" : searchText.trim()) + "%";
        return query("partner_code=? AND (alternate_name LIKE ? OR alternate_number LIKE ? OR household_number LIKE ?)",
                new String[]{partnerCode, like, like, like});
    }

    public int countAll() {
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                "alternates", new String[]{"COUNT(*)"}, "partner_code=?", new String[]{partnerCode}, null, null, null)) {
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
