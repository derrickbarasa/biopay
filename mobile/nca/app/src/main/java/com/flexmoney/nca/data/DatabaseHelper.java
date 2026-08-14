package com.flexmoney.nca.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.flexmoney.nca.data.BiometricsContract.AlternateEntry;
import com.flexmoney.nca.data.BiometricsContract.AssignmentsEntry;
import com.flexmoney.nca.data.BiometricsContract.AttendanceEntry;
import com.flexmoney.nca.data.BiometricsContract.BomasEntry;
import com.flexmoney.nca.data.BiometricsContract.CountiesEntry;
import com.flexmoney.nca.data.BiometricsContract.FingerprintEntry;
import com.flexmoney.nca.data.BiometricsContract.HouseholdEntry;
import com.flexmoney.nca.data.BiometricsContract.ImageEntry;
import com.flexmoney.nca.data.BiometricsContract.NomineeEntry;
import com.flexmoney.nca.data.BiometricsContract.PayamsEntry;
import com.flexmoney.nca.data.BiometricsContract.PaymentsEntry;
import com.flexmoney.nca.data.BiometricsContract.PermissionsEntry;
import com.flexmoney.nca.data.BiometricsContract.RegionsEntry;
import com.flexmoney.nca.data.BiometricsContract.StatesEntry;
import com.flexmoney.nca.data.BiometricsContract.SupervisorsEntry;

public class DatabaseHelper extends SQLiteOpenHelper {
    //database name
    public static final String DB_NAME = "snsop.db";
    //database version
    private static final int DB_VERSION = 1;

    /**
     * Constructors
     */
    public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    //Create the tables
    private static final String TB_SUPERVISORS = "CREATE TABLE " + SupervisorsEntry.TABLE_NAME + " ( " +
            SupervisorsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            SupervisorsEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR NOT NULL UNIQUE," +
            SupervisorsEntry.COLUMN_NAME_USERNAME + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_PASSWORD + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_FIRSTNAME + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_OTHERNAMES + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_PARTNER_CODE + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_ROLE_ID + " VARCHAR," +
            SupervisorsEntry.COLUMN_NAME_ACTIVE + " TINYINT," +
            SupervisorsEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            SupervisorsEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    //Create the tables
    private static final String TB_REGIONS = "CREATE TABLE " + RegionsEntry.TABLE_NAME + " ( " +
            RegionsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            RegionsEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR NOT NULL," +
            RegionsEntry.COLUMN_NAME_STATE + " int," +
            RegionsEntry.COLUMN_NAME_COUNTY + " int," +
            RegionsEntry.COLUMN_NAME_PAYAM + " int," +
            RegionsEntry.COLUMN_NAME_BOMA + " int NOT NULL UNIQUE," +
            RegionsEntry.COLUMN_NAME_STATUS + " TINYINT," +
            RegionsEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            RegionsEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    //Create the tables
    private static final String TB_HOUSEHOLD = "CREATE TABLE " + HouseholdEntry.TABLE_NAME + " ( " +
            HouseholdEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            HouseholdEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_PARTNER_CODE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR NOT NULL UNIQUE," +
            HouseholdEntry.COLUMN_NAME_BENEFICIARY_TYPE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_GROUP_NUMBER + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_MEMBER_NUMBER + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_HOUSEHOLD_HEAD_RELATIONSHIP + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_ID_NUMBER + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_PHONE_NUMBER + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_HOUSEHOLD_SIZE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_AGE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_MARITAL_STATUS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_SPOUSE_NAME + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_GENDER + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_MALE_DEPENDANTS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_FEMALE_DEPENDANTS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_CHRONICALLY_ILL_MEMBERS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DISABLED_MEMBERS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_ZERO_TWO + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_THREE_FIVE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIX_SEVENTEEN + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_EIGHTEEN_THIRTY_FIVE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_THIRTY_SIX_SIXTY_FOUR + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_DEPENDANTS_SIXTY_FIVE_PLUS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_INCOME_SOURCE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_OTHER_INCOME_SOURCE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_AVERAGE_INCOME + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_LITERACY + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_YOUTH_LITERACY+ " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_ELIGIBILITY + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_INELIGIBILITY + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_INELIGIBILITY_OTHER_REASON + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_NOMINEE_REASON + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_NOMINEE_OTHER_REASON + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_STATE_CODE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_COUNTY_CODE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_PAYAM_CODE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_BOMA_CODE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_LATITUDE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_LONGITUDE + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_LEGAL_STATUS + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_SELECTION_CRITERIA + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_SELECTION_REASON + " VARCHAR," +
            HouseholdEntry.COLUMN_NAME_STATUS + " TINYINT DEFAULT 0," +
            HouseholdEntry.COLUMN_NAME_PICKED + " TINYINT DEFAULT 0," +
            HouseholdEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            HouseholdEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";

    private static final String TB_NOMINEE = "CREATE TABLE "+ NomineeEntry.TABLE_NAME+" ( "+
            NomineeEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            NomineeEntry.COLUMN_NAME_NOMINEE_NUMBER + " VARCHAR NOT NULL UNIQUE," +
            NomineeEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_NAME+ " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_AGE + " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_GENDER + " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_RELATIONSHIP + " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_OCCUPATION+ " VARCHAR," +
            NomineeEntry.COLUMN_NAME_NOMINEE_OTHER_OCCUPATION + " VARCHAR," +
            NomineeEntry.COLUMN_NAME_STATUS + " TINYINT  DEFAULT 0," +
            NomineeEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            NomineeEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    private static final String TB_ALTERNATE = "CREATE TABLE " + AlternateEntry.TABLE_NAME + " ( " +
            AlternateEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            AlternateEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER + " VARCHAR NOT NULL UNIQUE," +
            AlternateEntry.COLUMN_NAME_ALTERNATE_NAME + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_ID_NUMBER + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_PHONE_NUMBER + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_AGE + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_GENDER + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_RELATIONSHIP + " VARCHAR," +
            AlternateEntry.COLUMN_NAME_STATUS + " TINYINT  DEFAULT 0," +
            AlternateEntry.COLUMN_NAME_PICKED + " TINYINT  DEFAULT 0," +
            AlternateEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            AlternateEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    //Create the tables

    private static final String TB_PAYMENTS = "CREATE TABLE " + PaymentsEntry.TABLE_NAME + " ( " +
            PaymentsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_ENROLMENT_NUMBER + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE + " TINYINT," +
            PaymentsEntry.COLUMN_NAME_PAID_TO + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_MATCHED_FP + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_ATTENDANCE + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_EXCHANGE_RATE + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_POUNDS + " NUMERIC," +
            PaymentsEntry.COLUMN_NAME_AMOUNT + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_UUID + " VARCHAR NOT NULL UNIQUE," +
            PaymentsEntry.COLUMN_NAME_STATUS + " TINYINT," +
            PaymentsEntry.COLUMN_NAME_SYNC + " TINYINT," +
            PaymentsEntry.COLUMN_NAME_LONGITUDE + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_LATITUDE + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE + " VARCHAR," +
            PaymentsEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            PaymentsEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";

    private static final String TB_ATTENDANCE = "CREATE TABLE " + AttendanceEntry.TABLE_NAME + " ( " +
            AttendanceEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            AttendanceEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            AttendanceEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR," +
            AttendanceEntry.COLUMN_NAME_BENEFICIARY_TYPE + " TINYINT," +
            AttendanceEntry.COLUMN_NAME_BENEFICIARY_ID + " VARCHAR," +
            AttendanceEntry.COLUMN_NAME_CLOCK + " TINYINT DEFAULT 0," +
            AttendanceEntry.COLUMN_NAME_TIME + " TIME," +
            AttendanceEntry.COLUMN_NAME_ATTENDANCE_DATE + " DATE," +
            AttendanceEntry.COLUMN_NAME_UUID + " VARCHAR NOT NULL UNIQUE," +
            AttendanceEntry.COLUMN_NAME_STATUS + " TINYINT DEFAULT 0," +
            AttendanceEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            AttendanceEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";

    private static final String TB_STATES = "CREATE TABLE " + StatesEntry.TABLE_NAME + " ( " +
            StatesEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            StatesEntry.COLUMN_NAME_STATE_CODE + " int NOT NULL UNIQUE," +
            StatesEntry.COLUMN_NAME_STATE_NAME + " VARCHAR);";
    private static final String TB_COUNTIES = "CREATE TABLE " + CountiesEntry.TABLE_NAME + " ( " +
            CountiesEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            CountiesEntry.COLUMN_NAME_STATE_CODE + " int," +
            CountiesEntry.COLUMN_NAME_COUNTY_CODE + " int NOT NULL UNIQUE," +
            CountiesEntry.COLUMN_NAME_COUNTY_NAME + " VARCHAR);";
    private static final String TB_PAYAMS = "CREATE TABLE " + PayamsEntry.TABLE_NAME + " ( " +
            PayamsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PayamsEntry.COLUMN_NAME_STATE_CODE + " int," +
            PayamsEntry.COLUMN_NAME_COUNTY_CODE + " int," +
            PayamsEntry.COLUMN_NAME_PAYAM_CODE + " int NOT NULL UNIQUE," +
            PayamsEntry.COLUMN_NAME_PAYAM_NAME + " VARCHAR);";
    private static final String TB_BOMAS = "CREATE TABLE " + BomasEntry.TABLE_NAME + " ( " +
            BomasEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            BomasEntry.COLUMN_NAME_STATE_CODE + " int," +
            BomasEntry.COLUMN_NAME_COUNTY_CODE + " int," +
            BomasEntry.COLUMN_NAME_PAYAM_CODE + " int," +
            BomasEntry.COLUMN_NAME_BOMA_CODE + " int NOT NULL UNIQUE," +
            BomasEntry.COLUMN_NAME_BOMA_NAME + " VARCHAR);";
    private static final String TB_PERMISSIONS = "CREATE TABLE " + PermissionsEntry.TABLE_NAME + " ( " +
            PermissionsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PermissionsEntry.COLUMN_NAME_ROLE_ID + " int," +
            PermissionsEntry.COLUMN_NAME_PERMISSION_ID + " int," +
            PermissionsEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            PermissionsEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    private static final String TB_ASSIGNMENTS = "CREATE TABLE " + AssignmentsEntry.TABLE_NAME + " ( " +
            AssignmentsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            AssignmentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + " VARCHAR," +
            AssignmentsEntry.COLUMN_NAME_ENROLMENT_NUMBER + " VARCHAR," +
            AssignmentsEntry.COLUMN_NAME_BEN_ID + " VARCHAR," +
            AssignmentsEntry.COLUMN_NAME_WORK_ID + " VARCHAR," +
            AssignmentsEntry.COLUMN_NAME_WORK_CODE + " VARCHAR," +
            AssignmentsEntry.COLUMN_NAME_STATUS + " int," +
            AssignmentsEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            AssignmentsEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    private static final String TB_IMAGES = "CREATE TABLE " + ImageEntry.TABLE_NAME + " ( " +
            ImageEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            ImageEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            ImageEntry.COLUMN_NAME_BENEFICIARY_TYPE + " TINYINT," +
            ImageEntry.COLUMN_NAME_BENEFICIARY_ID + " VARCHAR NOT NULL UNIQUE," +
            ImageEntry.COLUMN_NAME_PHOTO_URL + " VARCHAR," +

            ImageEntry.COLUMN_NAME_STATUS + " TINYINT DEFAULT 0," +
            ImageEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            ImageEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";
    private static final String TB_FINGERPRINTS = "CREATE TABLE " + FingerprintEntry.TABLE_NAME + " ( " +
            FingerprintEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID + " VARCHAR," +
            FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE + " TINYINT," +
            FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + " VARCHAR," +
            FingerprintEntry.COLUMN_NAME_UUID + " VARCHAR," +
            FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER + " TINYINT," +
            FingerprintEntry.COLUMN_NAME_FINGERPRINT + " VARCHAR," +
            FingerprintEntry.COLUMN_NAME_STATUS + " TINYINT DEFAULT 0," +
            FingerprintEntry.COLUMN_NAME_CREATED_AT + " TIMESTAMP," +
            FingerprintEntry.COLUMN_NAME_UPDATED_AT + " TIMESTAMP);";


    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TB_SUPERVISORS);
        db.execSQL(TB_ATTENDANCE);
        db.execSQL(TB_HOUSEHOLD);
        db.execSQL("CREATE INDEX idx_household_number ON households(household_number)");
        db.execSQL(TB_NOMINEE);
        db.execSQL(TB_ALTERNATE);
        db.execSQL(TB_PAYMENTS);
        db.execSQL("CREATE INDEX idx_payment_household_number ON payments(household_number)");
        db.execSQL(TB_REGIONS);
        db.execSQL(TB_STATES);
        db.execSQL(TB_COUNTIES);
        db.execSQL(TB_PAYAMS);
        db.execSQL(TB_BOMAS);
        db.execSQL(TB_PERMISSIONS);
        db.execSQL(TB_ASSIGNMENTS);
        db.execSQL(TB_FINGERPRINTS);
        db.execSQL(TB_IMAGES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + SupervisorsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + AttendanceEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + HouseholdEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + NomineeEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + AlternateEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PaymentsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + RegionsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + StatesEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + CountiesEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PayamsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + BomasEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + PermissionsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + AssignmentsEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + FingerprintEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ImageEntry.TABLE_NAME);
    }

    public Cursor getUnpaidPayments() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        try {
            String sql = "SELECT household_number,households.household_name FROM households\n" +
                    " WHERE household_number NOT IN(SELECT household_number FROM payments)\n"+
                    " ORDER BY household_name ASC";

            c = db.rawQuery(sql, null);
        } catch (Exception e) {
            System.err.println("getUnpaidPayments: " + e.getMessage());
        } finally {


        }
        return c;
    }

    public Cursor getPayments() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        try {
            String sql = "SELECT payments.household_number,payments.enrolment_number,households.group_number,households.household_name,sum(payments.pounds) as pounds,payments.status FROM payments\n" +
                    "LEFT JOIN households ON payments.household_number = households.household_number\n" +
                    "GROUP BY payments.household_number,households.household_name,payments.enrolment_number,payments.status,households.group_number\n" +
                    "ORDER BY households.group_number,payments.enrolment_number ASC";

            c = db.rawQuery(sql, null);
        } catch (Exception e) {
            System.err.println("getPayments: " + e.getMessage());
        } finally {


        }

        return c;
    }
}

