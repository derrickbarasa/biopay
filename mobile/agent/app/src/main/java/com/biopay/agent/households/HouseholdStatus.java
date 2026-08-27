package com.biopay.agent.households;

import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;

/**
 * The households list's status chip/filter derivation. There's no single "status" column, so
 * this combines what already exists: a paid payment record beats biometric-capture completeness
 * (checked against the head, keyed by household number -- the existing convention used
 * throughout the payment-verification flow), which beats "incomplete". Sync pending/synced is
 * tracked separately (see {@code household.syncStatus}) since a household can be, for example,
 * both Ready and still waiting to sync -- it isn't folded into this enum.
 */
public enum HouseholdStatus {
    PAID,
    READY,
    INCOMPLETE;

    public static HouseholdStatus compute(HouseholdDao.Household household, FingerprintDao fingerprintDao,
            FaceDao faceDao, PaymentDao paymentDao) {
        if (paymentDao.hasPaidPayment(household.householdNumber)) {
            return PAID;
        }
        boolean hasFingerprint = fingerprintDao.countForBeneficiary(household.householdNumber) > 0;
        boolean hasFace = faceDao.existsForBeneficiary(household.householdNumber);
        return (hasFingerprint || hasFace) ? READY : INCOMPLETE;
    }
}
