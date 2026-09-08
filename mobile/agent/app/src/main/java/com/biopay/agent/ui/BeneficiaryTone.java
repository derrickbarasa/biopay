package com.biopay.agent.ui;

import androidx.annotation.ColorRes;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;

/** One restrained identity language for every screen where an officer chooses who to verify. */
public final class BeneficiaryTone {
    private BeneficiaryTone() { }

    @ColorRes
    public static int background(Beneficiary beneficiary) {
        return background(beneficiary.beneficiaryType, beneficiary.gender);
    }

    @ColorRes
    public static int background(int beneficiaryType, String gender) {
        if (beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD) {
            return R.color.bp_person_head_container;
        }
        if ("male".equalsIgnoreCase(gender)) {
            return R.color.bp_person_male_container;
        }
        if ("female".equalsIgnoreCase(gender)) {
            return R.color.bp_person_female_container;
        }
        return R.color.bp_person_neutral_container;
    }

    @ColorRes
    public static int outline(Beneficiary beneficiary) {
        if (beneficiary.beneficiaryType == Beneficiary.TYPE_HOUSEHOLD_HEAD) {
            return R.color.bp_person_head_outline;
        }
        if ("male".equalsIgnoreCase(beneficiary.gender)) {
            return R.color.bp_person_male_outline;
        }
        if ("female".equalsIgnoreCase(beneficiary.gender)) {
            return R.color.bp_person_female_outline;
        }
        return R.color.bp_outline_variant;
    }
}
