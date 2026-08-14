package com.biopay.agent.attendance;

/** A household head or alternate, merged into one shape for the attendance beneficiary list.
 * {@code beneficiaryType} 1 = household head, 2 = alternate -- this is the first place in the app
 * that needs the convention, since fingerprints/images/attendances all store it as a bare int
 * with no shared constant defined anywhere else yet. */
public class Beneficiary {

    public static final int TYPE_HOUSEHOLD_HEAD = 1;
    public static final int TYPE_ALTERNATE = 2;

    public final String beneficiaryId;
    public final String householdNumber;
    public final int beneficiaryType;
    public final String name;
    public final String subtitle;

    public Beneficiary(String beneficiaryId, String householdNumber, int beneficiaryType, String name, String subtitle) {
        this.beneficiaryId = beneficiaryId;
        this.householdNumber = householdNumber;
        this.beneficiaryType = beneficiaryType;
        this.name = name;
        this.subtitle = subtitle;
    }
}
