package com.ultralogin.crypto;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    @Test
    void hashAndVerifyRoundTrip() {
        String hash = PasswordHasher.hash("correct horse battery staple");
        assertTrue(PasswordHasher.verify("correct horse battery staple", hash));
    }

    @Test
    void wrongPasswordRejected() {
        String hash = PasswordHasher.hash("password123");
        assertFalse(PasswordHasher.verify("password124", hash));
        assertFalse(PasswordHasher.verify("", hash));
        assertFalse(PasswordHasher.verify("PASSWORD123", hash));
    }

    @Test
    void nullOrEmptyStoredHashRejected() {
        assertFalse(PasswordHasher.verify("anything", null));
        assertFalse(PasswordHasher.verify("anything", ""));
    }

    @Test
    void saltIsRandomPerHash() {
        String h1 = PasswordHasher.hash("same-password");
        String h2 = PasswordHasher.hash("same-password");
        assertNotEquals(h1, h2, "two hashes of the same password must differ (random salt)");
        assertTrue(PasswordHasher.verify("same-password", h1));
        assertTrue(PasswordHasher.verify("same-password", h2));
    }

    @Test
    void hashUsesBcryptFormat() {
        String hash = PasswordHasher.hash("abc123def");
        assertTrue(hash.startsWith("$2"), "expected BCrypt modular crypt format, got: " + hash);
        assertTrue(hash.length() >= 59, "BCrypt hash should be at least 59 chars");
    }

    @Test
    void unicodePasswordsSupported() {
        String hash = PasswordHasher.hash("пароль密码🔒");
        assertTrue(PasswordHasher.verify("пароль密码🔒", hash));
        assertFalse(PasswordHasher.verify("пароль密码", hash));
    }
}
