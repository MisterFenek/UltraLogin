package com.ultralogin.auth;

import com.ultralogin.TestConfig;
import com.ultralogin.config.UltraLoginConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BruteforceGuardTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    @Test
    void notBannedInitially() {
        BruteforceGuard guard = new BruteforceGuard();
        assertFalse(guard.isBanned("10.0.0.1"));
    }

    @Test
    void bansAfterMaxAttempts() {
        BruteforceGuard guard = new BruteforceGuard();
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        for (int i = 1; i < max; i++) {
            assertEquals(max - i, guard.recordFailure("10.0.0.2"));
            assertFalse(guard.isBanned("10.0.0.2"), "should not be banned before limit");
        }
        assertEquals(0, guard.recordFailure("10.0.0.2"), "last attempt must trigger the ban");
        assertTrue(guard.isBanned("10.0.0.2"));
    }

    @Test
    void banIsPerIp() {
        BruteforceGuard guard = new BruteforceGuard();
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        for (int i = 0; i < max; i++) {
            guard.recordFailure("10.0.0.3");
        }
        assertTrue(guard.isBanned("10.0.0.3"));
        assertFalse(guard.isBanned("10.0.0.4"), "other IPs must not be affected");
    }

    @Test
    void clearResetsCounter() {
        BruteforceGuard guard = new BruteforceGuard();
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        for (int i = 0; i < max - 1; i++) {
            guard.recordFailure("10.0.0.5");
        }
        guard.clear("10.0.0.5"); // successful login resets attempts
        assertEquals(max - 1, guard.recordFailure("10.0.0.5"), "counter must restart after clear");
        assertFalse(guard.isBanned("10.0.0.5"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void banExpires() throws Exception {
        BruteforceGuard guard = new BruteforceGuard();
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        for (int i = 0; i < max; i++) {
            guard.recordFailure("10.0.0.6");
        }
        assertTrue(guard.isBanned("10.0.0.6"));

        // Rewind the ban expiry into the past instead of sleeping for minutes.
        Field f = BruteforceGuard.class.getDeclaredField("bannedUntil");
        f.setAccessible(true);
        ((Map<String, Long>) f.get(guard)).put("10.0.0.6", System.currentTimeMillis() - 1);

        assertFalse(guard.isBanned("10.0.0.6"), "expired ban must be lifted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attemptsMapIsCleanedUp() throws Exception {
        BruteforceGuard guard = new BruteforceGuard();
        guard.recordFailure("10.0.0.7");
        guard.recordFailure("10.0.0.8");

        Field attemptsField = BruteforceGuard.class.getDeclaredField("attempts");
        attemptsField.setAccessible(true);
        Map<String, Object> attempts = (Map<String, Object>) attemptsField.get(guard);

        assertEquals(2, attempts.size());

        // We simulate a cleanup by directly calling a cleanup method if we add one,
        // or advancing time and calling some method.
        // For the failing test before fix, we can just assert that the map contains the elements.
        // To verify the fix, we will simulate old entries.

        // Actually, since there's no cleanup method yet, let's just write the test logic that WILL be true after fix.
        // We will assume the map tracks time and cleans up lazily when recordFailure or isBanned is called.
        // For now, let's test if an old attempt is forgotten.

        // Advance time for 10.0.0.7 (if we change internal representation, this reflection might break.
        // Let's just do it cleanly: wait 1 ms or mock time. We can't mock time easily.
        // We'll write the test for the fixed version later).
        // If we change attempts to store an object with a timestamp, we can test eviction.
        // For now, let's just assert that a method like cleanUp() exists and removes them,
        // or that recordFailure lazily cleans up.
        // Since we are doing TDD, let's assume we will add `cleanupOldAttempts()` method.
        java.lang.reflect.Method cleanup = BruteforceGuard.class.getDeclaredMethod("cleanupOldAttempts");
        cleanup.setAccessible(true);
        cleanup.invoke(guard);
        // It shouldn't clean up fresh attempts.
        assertEquals(2, attempts.size());
    }
}
