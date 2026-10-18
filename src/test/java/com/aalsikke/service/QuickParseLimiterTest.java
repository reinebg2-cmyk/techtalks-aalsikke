package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-372: each user gets a limited number of AI quick-adds per hour. Pure, no Spring. */
class QuickParseLimiterTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    void allowsUpToTheLimit_thenBlocks() {
        QuickParseLimiter limiter = new QuickParseLimiter();
        for (int i = 0; i < QuickParseLimiter.MAX_PER_HOUR; i++) {
            assertTrue(limiter.tryAcquire("a@x.com", NOW));
        }
        assertFalse(limiter.tryAcquire("a@x.com", NOW));
    }

    @Test
    void afterAnHour_isAllowedAgain() {
        QuickParseLimiter limiter = new QuickParseLimiter();
        for (int i = 0; i < QuickParseLimiter.MAX_PER_HOUR; i++) {
            limiter.tryAcquire("a@x.com", NOW);
        }
        assertTrue(limiter.tryAcquire("a@x.com", NOW.plus(Duration.ofMinutes(61))));
    }

    @Test
    void usersAreCountedSeparately() {
        QuickParseLimiter limiter = new QuickParseLimiter();
        for (int i = 0; i < QuickParseLimiter.MAX_PER_HOUR; i++) {
            limiter.tryAcquire("a@x.com", NOW);
        }
        assertTrue(limiter.tryAcquire("b@x.com", NOW));
    }
}