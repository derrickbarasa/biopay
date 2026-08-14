package com.alphabank.dca.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class BiometricsContract {
    public BiometricsContract() {
    }

    public static final String CONTENT_AUTHORITY = "com.alphabank.dca";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    public static final String PATH_SUPERVISORS = "supervisors";
    public static final String PATH_ATTENDANCE = "attendance";
    public static final String PATH_HOUSEHOLD = "households";
    public static final String PATH_ALTERNATE = "alternates";
    public static final String PATH_FINGERPRINTS = "fingerprints";
    public static final String PATH_IMAGES = "images";
    public static final String PATH_PAYMENTS = "payments";
    public static final String PATH_REGIONS = "regions";
    public static final String PATH_STATES = "states";
    public static final String PATH_COUNTIES = "counties";
    public static final String PATH_PAYAMS = "payams";
    public static final String PATH_BOMAS = "bomas";
    public static final String PATH_PERMISSIONS = "permissions";
    public static final String PATH_ASSIGNMENTS = "assignments";


    public static class SupervisorsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_SUPERVISORS);

        public static final String TABLE_NAME = "supervisors";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_USERNAME = "username";
        public static final String COLUMN_NAME_PASSWORD = "password";
        public static final String COLUMN_NAME_FIRSTNAME = "firstname";
        public static final String COLUMN_NAME_OTHERNAMES = "othernames";
        public static final String COLUMN_NAME_ROLE_ID = "role_id";
        public static final String COLUMN_NAME_PARTNER_CODE = "partner_code";
        public static final String COLUMN_NAME_ACTIVE = "active";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_SUPERVISORS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_SUPERVISORS;
    }

    public static class RegionsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_REGIONS);

        public static final String TABLE_NAME = "regions";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_STATE = "state";
        public static final String COLUMN_NAME_COUNTY = "county";
        public static final String COLUMN_NAME_PAYAM = "payam";
        public static final String COLUMN_NAME_BOMA = "boma";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_REGIONS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_REGIONS;
    }

    public static class PermissionsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_PERMISSIONS);

        public static final String TABLE_NAME = "permissions";
        public static final String COLUMN_NAME_ROLE_ID = "role_id";
        public static final String COLUMN_NAME_PERMISSION_ID = "permission_id";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PERMISSIONS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PERMISSIONS;
    }

    public static class AssignmentsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ASSIGNMENTS);

        public static final String TABLE_NAME = "assignments";
        public static final String COLUMN_NAME_ENROLMENT_NUMBER = "enrolment_number";
        public static final String COLUMN_NAME_BEN_ID = "ben_id";
        public static final String COLUMN_NAME_WORK_ID = "work_id";
        public static final String COLUMN_NAME_WORK_CODE = "work_code";
        public static final String COLUMN_NAME_HOUSEHOLD_NUMBER = "household_number";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ASSIGNMENTS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ASSIGNMENTS;
    }

    public static class AttendanceEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ATTENDANCE);


        public static final String TABLE_NAME = "attendance";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_HOUSEHOLD_NUMBER = "household_number";
        public static final String COLUMN_NAME_BENEFICIARY_TYPE = "beneficiary_type";
        public static final String COLUMN_NAME_BENEFICIARY_ID = "beneficiary_id";
        public static final String COLUMN_NAME_CLOCK = "clock";
        public static final String COLUMN_NAME_TIME = "time";
        public static final String COLUMN_NAME_ATTENDANCE_DATE = "attendance_date";
        public static final String COLUMN_NAME_UUID = "uuid";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ATTENDANCE;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ATTENDANCE;
    }

    public static class HouseholdEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_HOUSEHOLD);


        public static final String TABLE_NAME = "households";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_PARTNER_CODE = "partner_code";
        public static final String COLUMN_NAME_HOUSEHOLD_NUMBER = "household_number";
        public static final String COLUMN_NAME_BENEFICIARY_TYPE = "beneficiary_type";
        public static final String COLUMN_NAME_GROUP_NUMBER = "group_number";
        public static final String COLUMN_NAME_MEMBER_NUMBER = "member_number";
        public static final String COLUMN_NAME_HOUSEHOLD_NAME = "household_name";
        public static final String COLUMN_NAME_HOUSEHOLD_SIZE = "household_size";
        public static final String COLUMN_NAME_AGE = "age";
        public static final String COLUMN_NAME_MARITAL_STATUS = "marital_status";
        public static final String COLUMN_NAME_SPOUSE_NAME = "spouse";
        public static final String COLUMN_NAME_ID_NUMBER = "id_number";
        public static final String COLUMN_NAME_PHONE_NUMBER = "phone_number";
        public static final String COLUMN_NAME_GENDER = "gender";
        public static final String COLUMN_NAME_LEGAL_STATUS = "legal_status";
        public static final String COLUMN_NAME_INCOME_HOUSEHOLD = "income_household";
        public static final String COLUMN_NAME_AVERAGE_INCOME = "average_household";

        public static final String COLUMN_NAME_MALE_DEPENDANTS = "male_dependants";
        public static final String COLUMN_NAME_FEMALE_DEPENDANTS = "female_dependants";
        public static final String COLUMN_NAME_DEPENDANTS_ZERO_FIVE = "zero_five";
        public static final String COLUMN_NAME_DEPENDANTS_SIX_EIGHTEEN = "six_eighteen";
        public static final String COLUMN_NAME_DEPENDANTS_NINETEEN_FORTY_FIVE = "nineteen_forty_five";
        public static final String COLUMN_NAME_DEPENDANTS_FORTY_SIX_SIXTY_FIVE = "forty_six_sixty_five";
        public static final String COLUMN_NAME_DEPENDANTS_SIXTY_SIX = "sixty_six";
        public static final String COLUMN_NAME_STATE_CODE = "state_code";
        public static final String COLUMN_NAME_COUNTY_CODE = "county_code";
        public static final String COLUMN_NAME_PAYAM_CODE = "payam_code";
        public static final String COLUMN_NAME_BOMA_CODE = "boma_code";
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        public static final String COLUMN_NAME_SELECTION_CRITERIA = "selection_criteria";
        public static final String COLUMN_NAME_SELECTION_REASON = "selection_reason";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_PICKED = "picked";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_HOUSEHOLD;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_HOUSEHOLD;
    }

    public static class AlternateEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALTERNATE);


        public static final String TABLE_NAME = "alternates";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_HOUSEHOLD_NUMBER = "household_number";
        public static final String COLUMN_NAME_ALTERNATE_NUMBER = "alternate_number";
        public static final String COLUMN_NAME_ALTERNATE_NAME = "alternate_name";
        public static final String COLUMN_NAME_AGE = "age";
        public static final String COLUMN_NAME_ID_NUMBER = "id_number";
        public static final String COLUMN_NAME_PHONE_NUMBER = "phone_number";
        public static final String COLUMN_NAME_GENDER = "gender";
        public static final String COLUMN_NAME_RELATIONSHIP = "relationship";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_PICKED = "picked";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALTERNATE;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALTERNATE;
    }
    public static class FingerprintEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_FINGERPRINTS);


        public static final String TABLE_NAME = "fingerprints";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_BENEFICIARY_TYPE = "beneficiary_type";
        public static final String COLUMN_NAME_BENEFICIARY_ID = "beneficiary_id";
        public static final String COLUMN_NAME_FINGERPRINT_NUMBER = "fingerprint_number";
        public static final String COLUMN_NAME_UUID = "uuid";
        public static final String COLUMN_NAME_FINGERPRINT = "fingerprint";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FINGERPRINTS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_FINGERPRINTS;
    }

    public static class ImageEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_IMAGES);


        public static final String TABLE_NAME = "images";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_BENEFICIARY_TYPE = "beneficiary_type";
        public static final String COLUMN_NAME_BENEFICIARY_ID = "beneficiary_id";
        public static final String COLUMN_NAME_PHOTO_URL = "photo_url";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_IMAGES;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_IMAGES;
    }
    public static class PaymentsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_PAYMENTS);


        public static final String TABLE_NAME = "payments";
        public static final String COLUMN_NAME_SUPERVISOR_ID = "supervisor_id";
        public static final String COLUMN_NAME_HOUSEHOLD_NUMBER = "household_number";
        public static final String COLUMN_NAME_BENEFICIARY_TYPE = "beneficiary_type";
        public static final String COLUMN_NAME_ENROLMENT_NUMBER = "enrolment_number";
        public static final String COLUMN_NAME_ATTENDANCE = "attendance";
        public static final String COLUMN_NAME_EXCHANGE_RATE = "exchange_rate";
        public static final String COLUMN_NAME_POUNDS = "pounds";
        public static final String COLUMN_NAME_AMOUNT = "amount";
        public static final String COLUMN_NAME_UUID = "uuid";
        public static final String COLUMN_NAME_PAID_TO = "paid_to";
        public static final String COLUMN_NAME_STATUS = "status";

        public static final String COLUMN_NAME_SYNC = "sync";
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        public static final String COLUMN_NAME_PAYMENT_CENTRE = "payment_centre";
        public static final String COLUMN_NAME_MATCHED_FP = "matched_fp";
        public static final String COLUMN_NAME_CREATED_AT = "created_at";
        public static final String COLUMN_NAME_UPDATED_AT = "updated_at";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PAYMENTS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PAYMENTS;
    }

    public static class StatesEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_STATES);


        public static final String TABLE_NAME = "states";
        public static final String COLUMN_NAME_STATE_CODE = "state_code";
        public static final String COLUMN_NAME_STATE_NAME = "state_name";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_STATES;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_STATES;
    }

    public static class CountiesEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_COUNTIES);


        public static final String TABLE_NAME = "counties";
        public static final String COLUMN_NAME_STATE_CODE = "state_code";
        public static final String COLUMN_NAME_COUNTY_CODE = "county_code";
        public static final String COLUMN_NAME_COUNTY_NAME = "county_name";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_COUNTIES;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_COUNTIES;
    }

    public static class PayamsEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_PAYAMS);


        public static final String TABLE_NAME = "payams";
        public static final String COLUMN_NAME_STATE_CODE = "state_code";
        public static final String COLUMN_NAME_COUNTY_CODE = "county_code";
        public static final String COLUMN_NAME_PAYAM_CODE = "payam_code";
        public static final String COLUMN_NAME_PAYAM_NAME = "payam_name";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PAYAMS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_PAYAMS;
    }

    public static class BomasEntry implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_BOMAS);


        public static final String TABLE_NAME = "bomas";
        public static final String COLUMN_NAME_STATE_CODE = "state_code";
        public static final String COLUMN_NAME_COUNTY_CODE = "county_code";
        public static final String COLUMN_NAME_PAYAM_CODE = "payam_code";
        public static final String COLUMN_NAME_BOMA_CODE = "boma_code";
        public static final String COLUMN_NAME_BOMA_NAME = "boma_name";

        public static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOMAS;
        public static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOMAS;
    }
}

