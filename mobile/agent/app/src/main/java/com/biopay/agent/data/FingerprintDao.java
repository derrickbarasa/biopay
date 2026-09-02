package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;

/** Local read/write access to the offline `fingerprints` table (Base64-encoded raw templates). */
public class FingerprintDao {

    private final DatabaseHelper dbHelper;

    public FingerprintDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public void save(String supervisorId, String partnerCode, int beneficiaryType, String beneficiaryId,
            int fingerNumber, String uuid, byte[] templateBytes, String deviceId) {
        ContentValues values = new ContentValues();
        values.put("supervisor_id", supervisorId);
        values.put("partner_code", partnerCode);
        values.put("beneficiary_type", beneficiaryType);
        values.put("beneficiary_id", beneficiaryId);
        values.put("fingerprint_number", fingerNumber);
        values.put("uuid", uuid);
        values.put("fingerprint_template", Base64.encodeToString(templateBytes, Base64.NO_WRAP));
        values.put("device_id", deviceId);
        values.put("sync_status", DatabaseHelper.SYNC_PENDING);
        dbHelper.getWritableDatabase().insert("fingerprints", null, values);
    }

    public void insertSynced(int beneficiaryType, String beneficiaryId, int fingerNumber,
            String uuid, byte[] templateBytes) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints", new String[]{"1"},
                "uuid=?", new String[]{uuid}, null, null, null)) {
            if (cursor.moveToFirst()) return;
        }
        ContentValues values = new ContentValues();
        values.put("beneficiary_type", beneficiaryType);
        values.put("beneficiary_id", beneficiaryId);
        values.put("fingerprint_number", fingerNumber);
        values.put("uuid", uuid);
        values.put("fingerprint_template", Base64.encodeToString(templateBytes, Base64.NO_WRAP));
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().insert("fingerprints", null, values);
    }

    public int countForBeneficiary(String beneficiaryId) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints", new String[]{"COUNT(*)"},
                "beneficiary_id=?", new String[]{beneficiaryId}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<byte[]> templatesForBeneficiary(String beneficiaryId) {
        List<byte[]> templates = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints", new String[]{"fingerprint_template"},
                "beneficiary_id=?", new String[]{beneficiaryId}, null, null, null)) {
            while (cursor.moveToNext()) {
                templates.add(Base64.decode(cursor.getString(0), Base64.NO_WRAP));
            }
        }
        return templates;
    }

    /** Like {@link #templatesForBeneficiary}, but also carries each template's uuid -- needed to
     * record which specific enrolled fingerprint a live verify matched against. */
    public List<StoredTemplate> templatesWithUuidForBeneficiary(String beneficiaryId) {
        List<StoredTemplate> templates = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints", new String[]{"uuid", "fingerprint_template"},
                "beneficiary_id=?", new String[]{beneficiaryId}, null, null, null)) {
            while (cursor.moveToNext()) {
                StoredTemplate t = new StoredTemplate();
                t.uuid = cursor.getString(0);
                t.template = Base64.decode(cursor.getString(1), Base64.NO_WRAP);
                templates.add(t);
            }
        }
        return templates;
    }

    public static class StoredTemplate {
        public String uuid;
        public byte[] template;
    }

    /** Every stored template belonging to someone other than {@code beneficiaryId} -- the
     * candidate pool for the "is this finger already enrolled under a different person?" check
     * run right after a fresh enrollment capture. */
    public List<BeneficiaryTemplate> templatesExcludingBeneficiary(String beneficiaryId) {
        List<BeneficiaryTemplate> templates = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints",
                new String[]{"beneficiary_id", "fingerprint_template"},
                "beneficiary_id<>?", new String[]{beneficiaryId}, null, null, null)) {
            while (cursor.moveToNext()) {
                BeneficiaryTemplate t = new BeneficiaryTemplate();
                t.beneficiaryId = cursor.getString(0);
                t.template = Base64.decode(cursor.getString(1), Base64.NO_WRAP);
                templates.add(t);
            }
        }
        return templates;
    }

    public static class BeneficiaryTemplate {
        public String beneficiaryId;
        public byte[] template;
    }

    public List<PendingFingerprint> listPending() {
        List<PendingFingerprint> results = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query("fingerprints", null,
                "sync_status=?", new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)}, null, null, null)) {
            while (cursor.moveToNext()) {
                PendingFingerprint p = new PendingFingerprint();
                p.uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"));
                p.beneficiaryId = cursor.getString(cursor.getColumnIndexOrThrow("beneficiary_id"));
                p.beneficiaryType = cursor.getInt(cursor.getColumnIndexOrThrow("beneficiary_type"));
                p.fingerNumber = cursor.getInt(cursor.getColumnIndexOrThrow("fingerprint_number"));
                p.template = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow("fingerprint_template")), Base64.NO_WRAP);
                results.add(p);
            }
        }
        return results;
    }

    public void markSynced(String uuid) {
        ContentValues values = new ContentValues();
        values.put("sync_status", DatabaseHelper.SYNC_SYNCED);
        dbHelper.getWritableDatabase().update("fingerprints", values, "uuid=?", new String[]{uuid});
    }

    public static class PendingFingerprint {
        public String uuid;
        public String beneficiaryId;
        public int beneficiaryType;
        public int fingerNumber;
        public byte[] template;
    }
}
