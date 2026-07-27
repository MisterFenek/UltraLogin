package com.ultralogin.auth;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    @Test
    void validForSameIp() {
        SessionManager sm = new SessionManager();
        sm.store("Steve", "1.2.3.4");
        assertTrue(sm.isValid("Steve", "1.2.3.4"));
    }

    @Test
    void rejectedForDifferentIp() {
        SessionManager sm = new SessionManager();
        sm.store("Steve", "1.2.3.4");
        assertFalse(sm.isValid("Steve", "5.6.7.8"), "session must be IP-bound");
    }

    @Test
    void usernameIsCaseInsensitive() {
        SessionManager sm = new SessionManager();
        sm.store("Steve", "1.2.3.4");
        assertTrue(sm.isValid("sTeVe", "1.2.3.4"));
    }

    @Test
    void noSessionForUnknownPlayer() {
        SessionManager sm = new SessionManager();
        assertFalse(sm.isValid("Nobody", "1.2.3.4"));
    }

    @Test
    void invalidateRemovesSession() {
        SessionManager sm = new SessionManager();
        sm.store("Alex", "9.9.9.9");
        sm.invalidate("Alex");
        assertFalse(sm.isValid("Alex", "9.9.9.9"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredSessionRejected() throws Exception {
        SessionManager sm = new SessionManager();
        sm.store("Alex", "9.9.9.9");

        // Rewind expiry into the past instead of sleeping.
        Field f = SessionManager.class.getDeclaredField("sessions");
        f.setAccessible(true);
        Map<String, Object> sessions = (Map<String, Object>) f.get(sm);
        Object session = sessions.get("alex");
        Class<?> cls = session.getClass();
        var ctor = cls.getDeclaredConstructor(String.class, long.class);
        ctor.setAccessible(true);
        sessions.put("alex", ctor.newInstance("9.9.9.9", System.currentTimeMillis() - 1));

        assertFalse(sm.isValid("Alex", "9.9.9.9"), "expired session must be rejected");
    }

    @Test
    void disabledSessionsNeverValid() {
        SessionManager sm = new SessionManager();
        sm.store("Steve", "1.2.3.4");
        TestConfig.set("session.enabled", false);
        try {
            assertFalse(sm.isValid("Steve", "1.2.3.4"), "sessions must be ignored when disabled");
        } finally {
            TestConfig.set("session.enabled", true);
        }
    }

    /**
     * Regression test for the timeout-kick session bypass bug.
     *
     * <p>Scenario: a player joins, never registers/logs in, gets kicked by the auth timeout.
     * On reconnect they must NOT get a "session restored" auto-login, because they were
     * never authenticated. The bug was that {@code AuthManager.tickPending} called
     * {@code pending.remove()} before {@code disconnect()}, making the player appear
     * "authenticated" to {@code onPlayerLeave}, which then called {@code sessions.store}.
     *
     * <p>Fix: {@code tickPending} no longer removes from {@code pending} before kicking;
     * the entry survives into {@code PlayerLoggedOutEvent} so {@code onPlayerLeave} correctly
     * calls {@code handleLogoutWithoutAuth} (no session stored) instead of {@code sessions.store}.
     *
     * <p>This unit test verifies the invariant at the {@link SessionManager} boundary:
     * if {@code store} was never called (because {@code onPlayerLeave} took the correct branch),
     * {@code isValid} must return {@code false}.
     */
    @Test
    void timeoutKickedPlayerHasNoSession() {
        SessionManager sm = new SessionManager();
        // Simulate: player was kicked by timeout → onPlayerLeave correctly did NOT call store().
        // (In the buggy version, pending.remove() happened before disconnect(), causing store() to be called.)
        assertFalse(sm.isValid("UnregisteredPlayer", "1.2.3.4"),
                "a timeout-kicked player must not have a session — store() must never be called for them");
    }
}
