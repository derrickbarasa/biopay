package com.biopay.agent.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionManagerTest {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long SIXTY_DAYS_MS = 60L * 24L * 60L * 60L * 1000L;

    @Test
    public void keepsSessionBeforeOneMinuteInBackground() {
        assertFalse(SessionManager.isBackgroundLogoutDue(1_000L, 60_999L, ONE_MINUTE_MS));
    }

    @Test
    public void expiresSessionAtOneMinuteInBackground() {
        assertTrue(SessionManager.isBackgroundLogoutDue(1_000L, 61_000L, ONE_MINUTE_MS));
    }

    @Test
    public void doesNotExpireWithoutABackgroundTimestamp() {
        assertFalse(SessionManager.isBackgroundLogoutDue(-1L, 120_000L, ONE_MINUTE_MS));
    }

    @Test
    public void offlineAuthorizationIsValidThroughSixtyDays() {
        assertTrue(OfflineAccessManager.isLeaseValid(
                1_000L, 1_000L + SIXTY_DAYS_MS, SIXTY_DAYS_MS));
    }

    @Test
    public void offlineAuthorizationExpiresAfterSixtyDays() {
        assertFalse(OfflineAccessManager.isLeaseValid(
                1_000L, 1_001L + SIXTY_DAYS_MS, SIXTY_DAYS_MS));
    }

    @Test
    public void clockRollbackCannotExtendOfflineAuthorization() {
        assertFalse(OfflineAccessManager.isLeaseValid(10_000L, 9_999L, SIXTY_DAYS_MS));
    }

    @Test
    public void configuredDaysConvertToAnExactLease() {
        assertTrue(OfflineAccessManager.leaseMilliseconds(60) == SIXTY_DAYS_MS);
    }
}
