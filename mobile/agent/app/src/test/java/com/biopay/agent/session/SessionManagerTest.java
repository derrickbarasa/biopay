package com.biopay.agent.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionManagerTest {

    private static final long ONE_MINUTE_MS = 60_000L;

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
}
