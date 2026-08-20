package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.biopay.agent.face.FaceEmbeddingCipher;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline store for model-versioned face embeddings. Raw photographs are not stored here. The
 * {@code embedding} column is encrypted at rest via {@link FaceEmbeddingCipher} (Android
 * Keystore-backed AES-GCM) wherever the device supports it (API 23+); {@code embedding_encrypted}
 * records which rows are and aren't, so a device that can't encrypt still stores usable (if
 * unencrypted) data instead of losing the row.
 */
public class FaceDao {
    private final DatabaseHelper dbHelper;

    public FaceDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public void savePending(String supervisorId, String partnerCode, int beneficiaryType,
            String beneficiaryId, String uuid, JSONArray embedding, String modelVersion,
            double qualityScore) {
        ContentValues values = values(beneficiaryType, beneficiaryId, uuid, embedding,
                modelVersion, qualityScore);
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "faces", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Adds a server record only when that UUID is not already a pending local enrolment. */
    public void insertSynced(int beneficiaryType, String beneficiaryId, String uuid,
            JSONArray embedding, String modelVersion, double qualityScore) {
        ContentValues values = values(beneficiaryType, beneficiaryId, uuid, embedding,
                modelVersion, qualityScore);
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "faces", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static ContentValues values(int beneficiaryType, String beneficiaryId, String uuid,
            JSONArray embedding, String modelVersion, double qualityScore) {
        String plaintext = embedding.toString();
        String encrypted = FaceEmbeddingCipher.encrypt(plaintext);

        ContentValues values = new ContentValues();
        values.put("beneficiary_type", beneficiaryType);
        values.put("beneficiary_id", beneficiaryId);
        values.put("uuid", uuid);
        values.put("embedding", encrypted != null ? encrypted : plaintext);
        values.put("embedding_encrypted", encrypted != null ? 1 : 0);
        values.put("embedding_dimensions", embedding.length());
        values.put("model_version", modelVersion);
        values.put("quality_score", qualityScore);
        return values;
    }

    public List<FaceRecord> listPending() {
        List<FaceRecord> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("faces", null,
                "sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)},
                null, null, null)) {
            while (cursor.moveToNext()) results.add(fromCursor(cursor));
        }
        return results;
    }

    public List<FaceRecord> listForBeneficiary(String beneficiaryId, String modelVersion) {
        List<FaceRecord> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("faces", null,
                "beneficiary_id=? AND model_version=?", new String[]{beneficiaryId, modelVersion},
                null, null, null)) {
            while (cursor.moveToNext()) results.add(fromCursor(cursor));
        }
        return results;
    }

    private static FaceRecord fromCursor(Cursor cursor) {
        FaceRecord record = new FaceRecord();
        record.uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"));
        record.beneficiaryId = cursor.getString(cursor.getColumnIndexOrThrow("beneficiary_id"));
        record.beneficiaryType = cursor.getInt(cursor.getColumnIndexOrThrow("beneficiary_type"));
        String stored = cursor.getString(cursor.getColumnIndexOrThrow("embedding"));
        boolean encrypted = cursor.getInt(cursor.getColumnIndexOrThrow("embedding_encrypted")) != 0;
        record.embedding = encrypted ? FaceEmbeddingCipher.decrypt(stored) : stored;
        record.dimensions = cursor.getInt(cursor.getColumnIndexOrThrow("embedding_dimensions"));
        record.modelVersion = cursor.getString(cursor.getColumnIndexOrThrow("model_version"));
        record.qualityScore = cursor.getDouble(cursor.getColumnIndexOrThrow("quality_score"));
        return record;
    }

    public void markSynced(String uuid) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("faces", values, "uuid=?", new String[]{uuid});
    }

    public static class FaceRecord {
        public String uuid;
        public String beneficiaryId;
        public int beneficiaryType;
        public String embedding;
        public int dimensions;
        public String modelVersion;
        public double qualityScore;
    }
}
