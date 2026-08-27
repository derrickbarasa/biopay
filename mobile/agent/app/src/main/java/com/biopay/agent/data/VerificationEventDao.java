package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;

/** Write side of the local verification-events log -- see {@link DatabaseHelper}'s
 * createVerificationEventsTable javadoc for why this exists. */
public class VerificationEventDao {

    private final DatabaseHelper dbHelper;

    public VerificationEventDao(Context context) {
        dbHelper = DatabaseHelper.get(context);
    }

    public void record(String householdNumber, String beneficiaryId, String personName, String method) {
        ContentValues values = new ContentValues();
        values.put("household_number", householdNumber);
        values.put("beneficiary_id", beneficiaryId);
        values.put("person_name", personName);
        values.put("method", method);
        dbHelper.getWritableDatabase().insert("verification_events", null, values);
    }
}
