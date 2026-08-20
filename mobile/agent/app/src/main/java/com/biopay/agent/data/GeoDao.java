package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Local read/write access to the offline geo reference tables (states/counties/payams/bomas),
 * hydrated by {@code SyncManager#syncGeography()} from GET_STATES/GET_COUNTIES/GET_LOCATIONS/
 * GET_VILLAGES so the household form can offer name-based location pickers offline. "payam" is
 * the backend/household column name for what the geo hierarchy calls "location"; "boma" is the
 * column name for what it calls "village" -- see database/migrations/006_geo_hierarchy.sql.
 */
public class GeoDao {

    private final DatabaseHelper dbHelper;

    public GeoDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public void upsertState(String code, String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("state_code", code);
        values.put("state_name", name);
        db.insertWithOnConflict("states", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertCounty(String code, String stateCode, String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("county_code", code);
        values.put("state_code", stateCode);
        values.put("county_name", name);
        db.insertWithOnConflict("counties", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** {@code locationCode}/{@code countyCode} in geo-hierarchy terms -- stored as payam_code/county_code locally. */
    public void upsertPayam(String code, String countyCode, String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("payam_code", code);
        values.put("county_code", countyCode);
        values.put("payam_name", name);
        db.insertWithOnConflict("payams", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** {@code villageCode}/{@code locationCode} in geo-hierarchy terms -- stored as boma_code/payam_code locally. */
    public void upsertBoma(String code, String payamCode, String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("boma_code", code);
        values.put("payam_code", payamCode);
        values.put("boma_name", name);
        db.insertWithOnConflict("bomas", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<GeoNode> listStates() {
        return query("states", "state_code", "state_name", null, null);
    }

    public List<GeoNode> listCounties(String stateCode) {
        return query("counties", "county_code", "county_name", "state_code", stateCode);
    }

    public List<GeoNode> listPayams(String countyCode) {
        return query("payams", "payam_code", "payam_name", "county_code", countyCode);
    }

    public List<GeoNode> listBomas(String payamCode) {
        return query("bomas", "boma_code", "boma_name", "payam_code", payamCode);
    }

    public boolean hasAnyStates() {
        return !listStates().isEmpty();
    }

    public String findStateName(String code) {
        return findName("states", "state_code", "state_name", code);
    }

    public String findCountyName(String code) {
        return findName("counties", "county_code", "county_name", code);
    }

    public String findPayamName(String code) {
        return findName("payams", "payam_code", "payam_name", code);
    }

    public String findBomaName(String code) {
        return findName("bomas", "boma_code", "boma_name", code);
    }

    /** Parent code of a payam/location row, needed to cascade-load its county when editing an existing household. */
    public String findPayamParentCounty(String payamCode) {
        return findName("payams", "payam_code", "county_code", payamCode);
    }

    /** Parent code of a boma/village row, needed to cascade-load its payam when editing an existing household. */
    public String findBomaParentPayam(String bomaCode) {
        return findName("bomas", "boma_code", "payam_code", bomaCode);
    }

    /** Parent code of a county row, needed to cascade-load its state when editing an existing household. */
    public String findCountyParentState(String countyCode) {
        return findName("counties", "county_code", "state_code", countyCode);
    }

    private String findName(String table, String codeColumn, String valueColumn, String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(table, new String[]{valueColumn}, codeColumn + "=?",
                new String[]{code}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private List<GeoNode> query(String table, String codeColumn, String nameColumn,
            String filterColumn, String filterValue) {
        List<GeoNode> results = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = filterColumn == null ? null : filterColumn + "=?";
        String[] args = filterColumn == null ? null : new String[]{filterValue};
        try (Cursor cursor = db.query(table, new String[]{codeColumn, nameColumn}, selection, args,
                null, null, nameColumn + " ASC")) {
            while (cursor.moveToNext()) {
                results.add(new GeoNode(cursor.getString(0), cursor.getString(1)));
            }
        }
        return results;
    }

    /** Plain (code, name) pair backing the cascading location dropdowns. */
    public static class GeoNode {
        public final String code;
        public final String name;

        public GeoNode(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
