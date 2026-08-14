package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/** Local read/write access to the offline `images` table (photos are stored on disk; this tracks the path). */
public class ImageDao {

    private final DatabaseHelper dbHelper;

    public ImageDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public void save(String supervisorId, String partnerCode, int beneficiaryType, String beneficiaryId, String localPath) {
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("beneficiary_type", beneficiaryType);
        values.put("beneficiary_id", beneficiaryId);
        values.put("local_path", localPath);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        dbHelper.getWritableDatabase().insertWithOnConflict("images", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<PendingImage> listPending() {
        List<PendingImage> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("images", null,
                "sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)}, null, null, null)) {
            while (cursor.moveToNext()) {
                PendingImage p = new PendingImage();
                p.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                p.beneficiaryId = cursor.getString(cursor.getColumnIndexOrThrow("beneficiary_id"));
                p.beneficiaryType = cursor.getInt(cursor.getColumnIndexOrThrow("beneficiary_type"));
                p.localPath = cursor.getString(cursor.getColumnIndexOrThrow("local_path"));
                results.add(p);
            }
        }
        return results;
    }

    public void markSynced(long id) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("images", values, "id=?", new String[]{String.valueOf(id)});
    }

    public static class PendingImage {
        public long id;
        public String beneficiaryId;
        public int beneficiaryType;
        public String localPath;
    }
}
