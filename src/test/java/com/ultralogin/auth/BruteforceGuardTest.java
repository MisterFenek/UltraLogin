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
}
