package com.ultralogin.config;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the default input-filtering rules shipped in the config. */
class ValidationTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    private static boolean nameAllowed(String name) {
        return name.matches(UltraLoginConfig.USERNAME_REGEX.get());
    }

    @Test
    void normalUsernamesAllowed() {
        assertTrue(nameAllowed("Steve"));
        assertTrue(nameAllowed("Alex_123"));
        assertTrue(nameAllowed("xXx_Pro_xXx"));
    }

    @Test
    void maliciousUsernamesRejected() {
        assertFalse(nameAllowed("ab"), "too short");
        assertFalse(nameAllowed("ThisNameIsWayTooLong123"), "too long");
        assertFalse(nameAllowed("Steve'; DROP TABLE--"), "SQL metacharacters");
        assertFalse(nameAllowed("Steve<script>"), "HTML/JS injection chars");
        assertFalse(nameAllowed("Steve Admin"), "spaces / impersonation padding");
        assertFalse(nameAllowed("Стив"), "non-ASCII homoglyphs");
        assertFalse(nameAllowed(""), "empty");
    }

    @Test
    void passwordLengthBoundsAreSane() {
        int min = UltraLoginConfig.MIN_PASSWORD_LENGTH.get();
        int max = UltraLoginConfig.MAX_PASSWORD_LENGTH.get();
        assertTrue(min >= 6, "default minimum password length must be at least 6");
        assertTrue(max <= 128, "cap must prevent BCrypt DoS via megabyte passwords");
        assertTrue(min < max);
    }

    @Test
    void bcryptCostDefaultIsStrong() {
        // The test config overrides cost to 4 for speed; the spec default must stay strong.
        Object defaultCost = UltraLoginConfig.SPEC.getSpec()
                .<com.electronwill.nightconfig.core.UnmodifiableConfig>get("general")
                .<net.neoforged.neoforge.common.ModConfigSpec.ValueSpec>get("bcryptCost")
                .getDefault();
        assertEquals(12, defaultCost, "production BCrypt cost default must be 12");
    }
}
