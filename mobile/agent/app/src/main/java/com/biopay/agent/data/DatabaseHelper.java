package com.biopay.agent.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local offline-first store. Schema is nca's superset (household breakdown,
 * vulnerability, literacy, nominee fields) -- the merged app standardizes
 * on it per the modernization plan. Unlike dca/nca this is a plain
 * SQLiteOpenHelper with no ContentProvider/UriMatcher wrapper: nothing
 * outside this app ever reads this database, so that indirection bought
 * nothing but boilerplate. Table/column names deliberately mirror the
 * biopay backend's own column names (see database/migrations/*.sql) so a
 * synced row maps to an upload payload with no translation layer.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "biopay_agent.db";
    private static final int DB_VERSION = 3;

    /** Every offline-captured row starts PENDING and flips to SYNCED once the server accepts it. */
    public static final int SYNC_PENDING = 0;
    public static final int SYNC_SYNCED = 1;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper get(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * Counts every locally-created record handled by {@code SyncManager}. Keeping this
     * aggregation beside the schema prevents dashboard screens from presenting a false
     * "synced" state when, for example, a face, photo, attendance, or voucher is pending.
     */
    public int countPendingSyncWork() {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM households WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM alternates WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM fingerprints WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM faces WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM images WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM attendances WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM payments WHERE sync_status=0) + " +
                "(SELECT COUNT(*) FROM vouchers " +
                " WHERE status='REDEEMED' AND redemption_sync_status=0)";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE households (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "household_number VARCHAR NOT NULL UNIQUE," +
                "beneficiary_type VARCHAR DEFAULT '1'," +
                "registration_method VARCHAR DEFAULT 'FINGERPRINT'," +
                "household_name VARCHAR," +
                "household_head_relationship VARCHAR," +
                "id_number VARCHAR," +
                "phone_number VARCHAR," +
                "household_size INTEGER," +
                "age INTEGER," +
                "gender VARCHAR," +
                "marital_status VARCHAR," +
                "spouse_name VARCHAR," +
                "male_dependants INTEGER," +
                "female_dependants INTEGER," +
                "chronically_ill_members INTEGER," +
                "disabled_members INTEGER," +
                "zero_two INTEGER," +
                "three_five INTEGER," +
                "six_seventeen INTEGER," +
                "eighteen_thirty_five INTEGER," +
                "thirty_six_sixty_four INTEGER," +
                "sixty_five_plus INTEGER," +
                "income_source VARCHAR," +
                "other_income_source VARCHAR," +
                "average_household NUMERIC," +
                "literacy VARCHAR," +
                "youth_literacy VARCHAR," +
                "eligibility VARCHAR," +
                "ineligibility VARCHAR," +
                "ineligibility_other_reason VARCHAR," +
                "nominee_reason VARCHAR," +
                "nominee_other_reason VARCHAR," +
                "state_code VARCHAR," +
                "county_code VARCHAR," +
                "payam_code VARCHAR," +
                "boma_code VARCHAR," +
                "latitude VARCHAR," +
                "longitude VARCHAR," +
                "photo_local_path VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE nominees (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nominee_number VARCHAR NOT NULL UNIQUE," +
                "household_number VARCHAR NOT NULL," +
                "nominee_name VARCHAR," +
                "nominee_age INTEGER," +
                "nominee_gender VARCHAR," +
                "nominee_relationship VARCHAR," +
                "nominee_occupation VARCHAR," +
                "nominee_other_occupation VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE alternates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "household_number VARCHAR NOT NULL," +
                "alternate_number VARCHAR NOT NULL UNIQUE," +
                "alternate_name VARCHAR," +
                "registration_method VARCHAR DEFAULT 'FINGERPRINT'," +
                "relationship VARCHAR," +
                "id_number VARCHAR," +
                "phone_number VARCHAR," +
                "age INTEGER," +
                "gender VARCHAR," +
                "photo_local_path VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE fingerprints (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "beneficiary_type INTEGER," +
                "beneficiary_id VARCHAR," +
                "fingerprint_number INTEGER," +
                "uuid VARCHAR," +
                "fingerprint_template VARCHAR," +
                "device_id VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE images (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "beneficiary_type INTEGER," +
                "beneficiary_id VARCHAR NOT NULL," +
                "local_path VARCHAR," +
                "remote_url VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "remote_id INTEGER," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "household_number VARCHAR," +
                "household_name VARCHAR," +
                "amount NUMERIC," +
                "matched_fingerprint_uuid VARCHAR," +
                "latitude VARCHAR," +
                "longitude VARCHAR," +
                "uuid VARCHAR NOT NULL UNIQUE," +
                "status INTEGER DEFAULT 0," +
                "cycle VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        db.execSQL("CREATE TABLE attendances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "household_number VARCHAR," +
                "beneficiary_type INTEGER," +
                "beneficiary_id VARCHAR," +
                "matched_fingerprint_uuid VARCHAR," +
                "clock VARCHAR," +
                "work_code VARCHAR," +
                "latitude VARCHAR," +
                "longitude VARCHAR," +
                "uuid VARCHAR NOT NULL UNIQUE," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

        createFaceTable(db);

        createVoucherTable(db);

        // Geo reference data, synced read-only from the server at login for offline dropdowns.
        db.execSQL("CREATE TABLE states (state_code VARCHAR PRIMARY KEY, state_name VARCHAR);");
        db.execSQL("CREATE TABLE counties (county_code VARCHAR PRIMARY KEY, state_code VARCHAR, county_name VARCHAR);");
        db.execSQL("CREATE TABLE payams (payam_code VARCHAR PRIMARY KEY, county_code VARCHAR, payam_name VARCHAR);");
        db.execSQL("CREATE TABLE bomas (boma_code VARCHAR PRIMARY KEY, payam_code VARCHAR, boma_name VARCHAR);");

        db.execSQL("CREATE INDEX idx_alternates_household ON alternates(household_number)");
        db.execSQL("CREATE INDEX idx_fingerprints_beneficiary ON fingerprints(beneficiary_id)");
        db.execSQL("CREATE INDEX idx_images_beneficiary ON images(beneficiary_id)");
        db.execSQL("CREATE INDEX idx_payments_household ON payments(household_number)");
        db.execSQL("CREATE INDEX idx_attendances_household ON attendances(household_number)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createVoucherTable(db);
        if (oldVersion < 3) {
            createFaceTable(db);
            db.execSQL("ALTER TABLE households ADD COLUMN registration_method VARCHAR DEFAULT 'FINGERPRINT'");
            db.execSQL("ALTER TABLE alternates ADD COLUMN registration_method VARCHAR DEFAULT 'FINGERPRINT'");
        }
    }

    private static void createVoucherTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vouchers (" +
                "voucher_code VARCHAR PRIMARY KEY," +
                "household_number VARCHAR NOT NULL," +
                "amount NUMERIC NOT NULL," +
                "purpose VARCHAR," +
                "expires_at VARCHAR," +
                "status VARCHAR NOT NULL DEFAULT 'ISSUED'," +
                "matched_fingerprint_uuid VARCHAR," +
                "latitude VARCHAR," +
                "longitude VARCHAR," +
                "redemption_sync_status INTEGER DEFAULT 1," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vouchers_household ON vouchers(household_number,status)");
    }

    private static void createFaceTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS faces (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "supervisor_id VARCHAR," +
                "partner_code VARCHAR," +
                "beneficiary_type INTEGER NOT NULL," +
                "beneficiary_id VARCHAR NOT NULL," +
                "uuid VARCHAR NOT NULL UNIQUE," +
                "embedding TEXT NOT NULL," +
                "embedding_dimensions INTEGER NOT NULL," +
                "model_version VARCHAR NOT NULL," +
                "quality_score NUMERIC," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_faces_beneficiary ON faces(beneficiary_id,model_version)");
    }
}
