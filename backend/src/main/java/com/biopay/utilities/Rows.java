package com.biopay.utilities;

import io.vertx.sqlclient.Row;

/** Small helpers for reading columns out of a reactive {@link Row}. */
public final class Rows {

    private Rows() {
    }

    /** Value as text (null-safe). */
    public static String str(Row row, String column) {
        Object value = row.getValue(column);
        return value == null ? null : value.toString();
    }

    /** Value as an Integer (null-safe). A SQL Server BIT column (e.g. payments.rejected) comes
     *  back from the MSSQL client as a Java Boolean, not a Number -- without this check,
     *  Integer.parseInt(value.toString()) on "true"/"false" threw NumberFormatException, which
     *  (uncaught inside a Vert.x future callback) silently dropped the eventBus reply entirely,
     *  surfacing to callers as a ~20s request timeout instead of any actual error. */
    public static Integer intVal(Row row, String column) {
        Object value = row.getValue(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        return Integer.parseInt(value.toString());
    }

    /** Value as a Double (null-safe). Works for any numeric SQL type. */
    public static Double dbl(Row row, String column) {
        Object value = row.getValue(column);
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}
