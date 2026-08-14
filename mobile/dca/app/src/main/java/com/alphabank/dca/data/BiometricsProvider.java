package com.alphabank.dca.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public class BiometricsProvider extends ContentProvider {

    private static final int SUPERVISOR = 100;
    private static final int ATTENDANCE = 200;
    private static final int HOUSEHOLD = 300;
    private static final int ALTERNATE = 400;
    private static final int PAYMENTS = 500;
    private static final int REGIONS = 600;
    private static final int STATES = 700;
    private static final int COUNTIES = 800;
    private static final int PAYAMS = 900;
    private static final int BOMAS = 1000;
    private static final int PERMISSIONS = 1001;
    private static final int ASSIGNMENTS = 1012;
    private static final int FINGERPRINTS = 1013;
    private static final int IMAGES = 1014;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    public DatabaseHelper dbHelper;

    static {

        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_SUPERVISORS, SUPERVISOR);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_ATTENDANCE, ATTENDANCE);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_HOUSEHOLD, HOUSEHOLD);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_ALTERNATE, ALTERNATE);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_PAYMENTS, PAYMENTS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_REGIONS, REGIONS);

        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_STATES, STATES);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_COUNTIES, COUNTIES);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_PAYAMS, PAYAMS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_BOMAS, BOMAS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_PERMISSIONS, PERMISSIONS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_ASSIGNMENTS, ASSIGNMENTS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_FINGERPRINTS, FINGERPRINTS);
        sUriMatcher.addURI(BiometricsContract.CONTENT_AUTHORITY, BiometricsContract.PATH_IMAGES, IMAGES);
    }


    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext(), null, null, 1);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;
        switch (sUriMatcher.match(uri)) {

            case SUPERVISOR:
                cursor = db.query(
                        BiometricsContract.SupervisorsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case ATTENDANCE:
                cursor = db.query(
                        BiometricsContract.AttendanceEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case HOUSEHOLD:
                cursor = db.query(
                        BiometricsContract.HouseholdEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case ALTERNATE:
                cursor = db.query(
                        BiometricsContract.AlternateEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case PAYMENTS:
                cursor = db.query(
                        BiometricsContract.PaymentsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case REGIONS:
                cursor = db.query(
                        BiometricsContract.RegionsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case STATES:
                cursor = db.query(
                        BiometricsContract.StatesEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case COUNTIES:
                cursor = db.query(
                        BiometricsContract.CountiesEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case PAYAMS:
                cursor = db.query(
                        BiometricsContract.PayamsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case BOMAS:
                cursor = db.query(
                        BiometricsContract.BomasEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case PERMISSIONS:
                cursor = db.query(
                        BiometricsContract.PermissionsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case ASSIGNMENTS:
                cursor = db.query(
                        BiometricsContract.AssignmentsEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case FINGERPRINTS:
                cursor = db.query(
                        BiometricsContract.FingerprintEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            case IMAGES:
                cursor = db.query(
                        BiometricsContract.ImageEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            default:
                throw new UnsupportedOperationException("Not yet implemented");
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        final int match = sUriMatcher.match(uri);
        Uri returnUri;
        long id;
        switch (match) {
            case SUPERVISOR:
                id = db.insertWithOnConflict(BiometricsContract.SupervisorsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.SupervisorsEntry.CONTENT_URI, id);

                break;
            case HOUSEHOLD:
                id = db.insertWithOnConflict(BiometricsContract.HouseholdEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.HouseholdEntry.CONTENT_URI, id);

                break;
            case ALTERNATE:
                id = db.insertWithOnConflict(BiometricsContract.AlternateEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.AlternateEntry.CONTENT_URI, id);

                break;
            case ATTENDANCE:
                id = db.insertWithOnConflict(BiometricsContract.AttendanceEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.AttendanceEntry.CONTENT_URI, id);

                break;
            case PAYMENTS:
                id = db.insertWithOnConflict(BiometricsContract.PaymentsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.PaymentsEntry.CONTENT_URI, id);

                break;
            case REGIONS:
                id = db.insertWithOnConflict(BiometricsContract.RegionsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.RegionsEntry.CONTENT_URI, id);

                break;

            case STATES:
                id = db.insertWithOnConflict(BiometricsContract.StatesEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.StatesEntry.CONTENT_URI, id);

                break;
            case COUNTIES:
                id = db.insertWithOnConflict(BiometricsContract.CountiesEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.CountiesEntry.CONTENT_URI, id);

                break;
            case PAYAMS:
                id = db.insertWithOnConflict(BiometricsContract.PayamsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

                returnUri = ContentUris.withAppendedId(BiometricsContract.PayamsEntry.CONTENT_URI, id);

                break;
            case BOMAS:
                id = db.insertWithOnConflict(BiometricsContract.BomasEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                returnUri = ContentUris.withAppendedId(BiometricsContract.BomasEntry.CONTENT_URI, id);

                break;
            case PERMISSIONS:
                id = db.insertWithOnConflict(BiometricsContract.PermissionsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                returnUri = ContentUris.withAppendedId(BiometricsContract.PermissionsEntry.CONTENT_URI, id);

                break;
            case ASSIGNMENTS:
                id = db.insertWithOnConflict(BiometricsContract.AssignmentsEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                returnUri = ContentUris.withAppendedId(BiometricsContract.AssignmentsEntry.CONTENT_URI, id);

                break;
            case FINGERPRINTS:
                id = db.insertWithOnConflict(BiometricsContract.FingerprintEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                returnUri = ContentUris.withAppendedId(BiometricsContract.FingerprintEntry.CONTENT_URI, id);

                break;
            case IMAGES:
                id = db.insertWithOnConflict(BiometricsContract.ImageEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                returnUri = ContentUris.withAppendedId(BiometricsContract.ImageEntry.CONTENT_URI, id);

                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        db.close();
        getContext().getContentResolver().notifyChange(uri, null);

        return returnUri;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowDeleted;

        switch (match) {
            case SUPERVISOR:
                rowDeleted = db.delete(
                        BiometricsContract.SupervisorsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case HOUSEHOLD:
                rowDeleted = db.delete(
                        BiometricsContract.HouseholdEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case ALTERNATE:
                rowDeleted = db.delete(
                        BiometricsContract.AlternateEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case PAYMENTS:
                rowDeleted = db.delete(
                        BiometricsContract.PaymentsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case ATTENDANCE:
                rowDeleted = db.delete(
                        BiometricsContract.AttendanceEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case REGIONS:
                rowDeleted = db.delete(
                        BiometricsContract.RegionsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case STATES:
                rowDeleted = db.delete(
                        BiometricsContract.StatesEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case COUNTIES:
                rowDeleted = db.delete(
                        BiometricsContract.CountiesEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case PAYAMS:
                rowDeleted = db.delete(
                        BiometricsContract.PayamsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case BOMAS:
                rowDeleted = db.delete(
                        BiometricsContract.BomasEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case PERMISSIONS:
                rowDeleted = db.delete(
                        BiometricsContract.PermissionsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case ASSIGNMENTS:
                rowDeleted = db.delete(
                        BiometricsContract.AssignmentsEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case FINGERPRINTS:
                rowDeleted = db.delete(
                        BiometricsContract.FingerprintEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            case IMAGES:
                rowDeleted = db.delete(
                        BiometricsContract.ImageEntry.TABLE_NAME,
                        selection,
                        selectionArgs
                );
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        // Because a null deletes all rows
        if (selection == null || rowDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowDeleted;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowUpdated;

        switch (match) {
            case SUPERVISOR:
                rowUpdated = db.update(
                        BiometricsContract.SupervisorsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case HOUSEHOLD:
                rowUpdated = db.update(
                        BiometricsContract.HouseholdEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case ALTERNATE:
                rowUpdated = db.update(
                        BiometricsContract.AlternateEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case PAYMENTS:
                rowUpdated = db.update(
                        BiometricsContract.PaymentsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case ATTENDANCE:
                rowUpdated = db.update(
                        BiometricsContract.AttendanceEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case REGIONS:
                rowUpdated = db.update(
                        BiometricsContract.RegionsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;

            case STATES:
                rowUpdated = db.update(
                        BiometricsContract.StatesEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case COUNTIES:
                rowUpdated = db.update(
                        BiometricsContract.CountiesEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case PAYAMS:
                rowUpdated = db.update(
                        BiometricsContract.PayamsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case BOMAS:
                rowUpdated = db.update(
                        BiometricsContract.BomasEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case PERMISSIONS:
                rowUpdated = db.update(
                        BiometricsContract.PermissionsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case ASSIGNMENTS:
                rowUpdated = db.update(
                        BiometricsContract.AssignmentsEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case FINGERPRINTS:
                rowUpdated = db.update(
                        BiometricsContract.FingerprintEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            case IMAGES:
                rowUpdated = db.update(
                        BiometricsContract.ImageEntry.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        // Because a null updates all rows
        if (rowUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowUpdated;
    }
}

