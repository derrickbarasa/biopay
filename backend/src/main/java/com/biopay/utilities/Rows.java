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

    /** Value as an Integer (null-safe). */
    public static Integer intVal(Row row, String column) {
        Object value = row.getValue(column);
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
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
