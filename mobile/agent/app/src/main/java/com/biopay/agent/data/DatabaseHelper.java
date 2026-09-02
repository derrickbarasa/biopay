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
    private static final int DB_VERSION = 14;

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
     * Counts every locally-created record handled by {@code SyncManager}, scoped to the
     * logged-in officer's own organisation -- keeping this aggregation beside the schema
     * prevents dashboard screens from presenting a false "synced" state when, for example, a
     * face, photo, attendance, or voucher is pending. {@code vouchers} carries no partner_code
     * of its own, so it's scoped through the household it belongs to instead.
     */
    public int countPendingSyncWork(String partnerCode) {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM households WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM alternates WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM fingerprints WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM faces WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM images WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM attendances WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM payments WHERE sync_status=0 AND partner_code=?) + " +
                "(SELECT COUNT(*) FROM vouchers v " +
                " WHERE v.status='REDEEMED' AND v.redemption_sync_status=0 " +
                " AND EXISTS (SELECT 1 FROM households h WHERE h.household_number=v.household_number AND h.partner_code=?))";
        String[] args = new String[]{partnerCode, partnerCode, partnerCode, partnerCode,
                partnerCode, partnerCode, partnerCode, partnerCode};
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Per-table breakdown backing the Sync Center screen's pending-record counts. */
    public int countPendingHouseholds(String partnerCode) {
        return countPendingIn("households", partnerCode);
    }

    public int countPendingMembers(String partnerCode) {
        return countPendingIn("alternates", partnerCode);
    }

    public int countPendingTransactions(String partnerCode) {
        return countPendingIn("payments", partnerCode);
    }

    private int countPendingIn(String table, String partnerCode) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE sync_status=0 AND partner_code=?",
                new String[]{partnerCode})) {
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
                "vulnerability_statuses VARCHAR," +
                "legal_status VARCHAR," +
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
                "village_code VARCHAR," +
                "amount NUMERIC," +
                "matched_fingerprint_uuid VARCHAR," +
                "matched_face_uuid VARCHAR," +
                "latitude VARCHAR," +
                "longitude VARCHAR," +
                "uuid VARCHAR NOT NULL UNIQUE," +
                "status INTEGER DEFAULT 0," +
                "cycle VARCHAR," +
                "sync_status INTEGER DEFAULT 0," +
                "intervention_type VARCHAR DEFAULT 'CASH'," +
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

        createGeoTables(db);

        createVerificationEventsTable(db);

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
        // Guarded with IF NOT EXISTS: devices that upgraded through version 3 before this table
        // was added to onCreate never got it, so this also backfills those installs, not just <4.
        if (oldVersion < 4) createGeoTables(db);
        if (oldVersion < 5) {
            // Any face row created before this migration (there should be none in practice --
            // embedding capture has never produced a real row, see FaceRecognitionEngine) was
            // necessarily plaintext; default 0 keeps FaceDao reading those correctly instead of
            // trying to decrypt data that was never encrypted.
            db.execSQL("ALTER TABLE faces ADD COLUMN embedding_encrypted INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 6) {
            // Face-verified field payments (PaymentVerificationActivity) need somewhere to record
            // which enrolled face embedding matched, parallel to the existing matched_fingerprint_uuid.
            db.execSQL("ALTER TABLE payments ADD COLUMN matched_face_uuid VARCHAR");
        }
        if (oldVersion < 7) {
            // The redesigned disbursement flow's intervention-type selector (Cash/Voucher/Food/
            // In-kind) is local-only for now -- RECORD_FIELD_PAYMENT has no matching backend
            // column yet, so this doesn't sync, but it does show on the on-device receipt.
            db.execSQL("ALTER TABLE payments ADD COLUMN intervention_type VARCHAR DEFAULT 'CASH'");
        }
        if (oldVersion < 8) {
            createVerificationEventsTable(db);
        }
        if (oldVersion < 9) {
            // Generated payments carry their household's village so the field payment picker
            // remains useful even when only the payment catalogue is available offline.
            db.execSQL("ALTER TABLE payments ADD COLUMN village_code VARCHAR");
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE households ADD COLUMN vulnerability_statuses VARCHAR");
            db.execSQL("ALTER TABLE households ADD COLUMN legal_status VARCHAR");
        }
        if (oldVersion < 11) {
            cleanInvalidGeoHierarchy(db);
        }
        if (oldVersion < 12) {
            cleanMisclassifiedStates(db);
        }
        if (oldVersion < 13) {
            cleanMisclassifiedStates(db);
        }
        if (oldVersion < 14) {
            // Scopes the Activity feed's verification entries to the logged-in officer's own
            // organisation, matching every other org-owned table -- see ActivityDao#listAll.
            db.execSQL("ALTER TABLE verification_events ADD COLUMN partner_code VARCHAR");
        }
    }

    /** Removes household codes and incomplete manual entries that older sync builds could insert
     * into the State picker. Authoritative geography is downloaded again on the next sync. */
    private static void cleanInvalidGeoHierarchy(SQLiteDatabase db) {
        db.execSQL("DELETE FROM states WHERE state_name IS NULL OR TRIM(state_name)='' "
                + "OR state_name NOT GLOB '*[A-Za-z]*'");
        db.execSQL("DELETE FROM counties WHERE state_code NOT IN (SELECT state_code FROM states)");
        db.execSQL("DELETE FROM payams WHERE county_code NOT IN (SELECT county_code FROM counties)");
        db.execSQL("DELETE FROM bomas WHERE payam_code NOT IN (SELECT payam_code FROM payams)");
        db.execSQL("DELETE FROM states WHERE state_code NOT IN "
                + "(SELECT DISTINCT state_code FROM counties WHERE state_code IS NOT NULL)");
    }

    /** A name that is already an active county in this local hierarchy cannot also be offered as
     * a state. Test fixtures are likewise not field-registration choices. */
    private static void cleanMisclassifiedStates(SQLiteDatabase db) {
        db.execSQL("DELETE FROM states WHERE LOWER(TRIM(state_name)) IN "
                + "(SELECT LOWER(TRIM(county_name)) FROM counties WHERE county_name IS NOT NULL "
                + "UNION SELECT LOWER(TRIM(county_code)) FROM households "
                + "WHERE county_code IS NOT NULL AND TRIM(county_code)<>'') "
                + "OR LOWER(TRIM(state_name)) LIKE 'e2e test%'");
        db.execSQL("DELETE FROM counties WHERE state_code NOT IN (SELECT state_code FROM states)");
        db.execSQL("DELETE FROM payams WHERE county_code NOT IN (SELECT county_code FROM counties)");
        db.execSQL("DELETE FROM bomas WHERE payam_code NOT IN (SELECT payam_code FROM payams)");
    }

    /**
     * Geo reference data (state/county/location(payam)/village(boma) names), synced read-only
     * from GET_STATES/GET_COUNTIES/GET_LOCATIONS/GET_VILLAGES so the household form can offer
     * name-based pickers offline instead of raw codes. See GeoDao.
     */
    private static void createGeoTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS states (state_code VARCHAR PRIMARY KEY, state_name VARCHAR);");
        db.execSQL("CREATE TABLE IF NOT EXISTS counties (county_code VARCHAR PRIMARY KEY, state_code VARCHAR, county_name VARCHAR);");
        db.execSQL("CREATE TABLE IF NOT EXISTS payams (payam_code VARCHAR PRIMARY KEY, county_code VARCHAR, payam_name VARCHAR);");
        db.execSQL("CREATE TABLE IF NOT EXISTS bomas (boma_code VARCHAR PRIMARY KEY, payam_code VARCHAR, boma_name VARCHAR);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_counties_state ON counties(state_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payams_county ON payams(county_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bomas_payam ON bomas(payam_code)");
    }

    /** A lightweight local log of successful identity verifications -- the redesigned Activity
     * feed's "Verification" filter needs something to show since {@code faces}/{@code
     * fingerprints} only record enrollment, not "this specific verify attempt succeeded".
     * Written to from {@link com.biopay.agent.payments.FingerprintVerifyActivity} and {@link
     * com.biopay.agent.payments.PaymentVerificationActivity}'s face-match path. Never synced --
     * it's purely a device-local activity record, not a server-facing entity. */
    private static void createVerificationEventsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS verification_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "partner_code VARCHAR," +
                "household_number VARCHAR," +
                "beneficiary_id VARCHAR," +
                "person_name VARCHAR," +
                "method VARCHAR NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_verification_events_household ON verification_events(household_number)");
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
                "embedding_encrypted INTEGER NOT NULL DEFAULT 0," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_faces_beneficiary ON faces(beneficiary_id,model_version)");
    }
}
