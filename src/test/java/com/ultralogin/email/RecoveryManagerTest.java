package com.ultralogin.email;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryManagerTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    @Test
    void issuedCodeCanBeConsumedOnce() {
        RecoveryManager rm = new RecoveryManager();
        String code = rm.issueCode("Steve");
        assertTrue(rm.consume("Steve", code));
        assertFalse(rm.consume("Steve", code), "codes must be single-use");
    }

    @Test
    void wrongCodeRejectedButTokenSurvives() {
        RecoveryManager rm = new RecoveryManager();
        String code = rm.issueCode("Steve");
        assertFalse(rm.consume("Steve", "WRONGCOD"));
        assertTrue(rm.consume("Steve", code), "a wrong guess must not destroy the real code");
    }

    @Test
    void codeIsCaseInsensitiveAndTrimmed() {
        RecoveryManager rm = new RecoveryManager();
        String code = rm.issueCode("Steve");
        assertTrue(rm.consume("Steve", "  " + code.toLowerCase() + "  "));
    }

    @Test
    void codeBoundToAccount() {
        RecoveryManager rm = new RecoveryManager();
        String code = rm.issueCode("Steve");
        assertFalse(rm.consume("Alex", code), "codes must not work cross-account");
    }

    @Test
    void cooldownActiveAfterIssue() {
        RecoveryManager rm = new RecoveryManager();
        assertEquals(0, rm.cooldownMinutesLeft("Steve"));
        rm.issueCode("Steve");
        assertTrue(rm.cooldownMinutesLeft("Steve") > 0, "rate limit must kick in after a request");
        assertEquals(0, rm.cooldownMinutesLeft("Alex"), "cooldown is per-account");
    }

    @Test
    void codesUseUnambiguousAlphabetAndAreUnique() {
        RecoveryManager rm = new RecoveryManager();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = rm.issueCode("user" + i);
            assertEquals(8, code.length());
            assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"), "no 0/O/1/I ambiguity: " + code);
            seen.add(code);
        }
        assertTrue(seen.size() > 195, "codes must be effectively unique");
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredCodeRejected() throws Exception {
        RecoveryManager rm = new RecoveryManager();
        String code = rm.issueCode("Steve");

        Field f = RecoveryManager.class.getDeclaredField("tokens");
        f.setAccessible(true);
        Map<String, Object> tokens = (Map<String, Object>) f.get(rm);
        Class<?> tokenCls = tokens.get("steve").getClass();
        Constructor<?> ctor = tokenCls.getDeclaredConstructor(String.class, long.class);
        ctor.setAccessible(true);
        tokens.put("steve", ctor.newInstance(code, System.currentTimeMillis() - 1));

        assertFalse(rm.consume("Steve", code), "expired code must be rejected");
    }
}
