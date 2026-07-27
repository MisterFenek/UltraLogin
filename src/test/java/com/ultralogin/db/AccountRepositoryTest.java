package com.ultralogin.db;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests against a real (temporary) SQLite database — the exact
 * code path production uses with the default config.
 */
class AccountRepositoryTest {

    @TempDir
    static Path tempDir;

    static DatabaseManager db;
    static AccountRepository repo;

    @BeforeAll
    static void setup() throws Exception {
        TestConfig.load();
        db = new DatabaseManager();
        db.start(tempDir);
        repo = new AccountRepository(db);
    }

    @AfterAll
    static void teardown() {
        db.close();
    }

    private static Account account(String name) {
        return new Account(name, "$2a$04$fakehashfakehashfakehashfakehashfakehashfakehashfake", null,
                "1.2.3.4", "1.2.3.4", 1000L, 2000L);
    }

    @Test
    void insertAndFindRoundTrip() throws Exception {
        repo.insert(account("RoundTrip")).get();
        Optional<Account> found = repo.find("RoundTrip").get();
        assertTrue(found.isPresent());
        assertEquals("roundtrip", found.get().username(), "usernames are stored lowercase");
        assertEquals("1.2.3.4", found.get().regIp());
    }

    @Test
    void findIsCaseInsensitive() throws Exception {
        repo.insert(account("CaseTest")).get();
        assertTrue(repo.find("cAsEtEsT").get().isPresent());
        assertTrue(repo.find("CASETEST").get().isPresent());
    }

    @Test
    void unknownUserNotFound() throws Exception {
        assertTrue(repo.find("does_not_exist").get().isEmpty());
    }

    @Test
    void sqlInjectionInUsernameIsHarmless() throws Exception {
        repo.insert(account("Victim1")).get();

        // Classic injection payloads must be treated as literal (non-matching) strings.
        assertTrue(repo.find("' OR '1'='1").get().isEmpty());
        assertTrue(repo.find("victim1'; DROP TABLE ul_accounts;--").get().isEmpty());
        assertFalse(repo.delete("' OR '1'='1").get(), "injection must not delete anything");

        // Table still intact, victim still registered.
        assertTrue(repo.find("Victim1").get().isPresent());
    }

    @Test
    void updatePasswordPersists() throws Exception {
        repo.insert(account("PassChange")).get();
        assertTrue(repo.updatePassword("PassChange", "$2a$04$newhash").get());
        assertEquals("$2a$04$newhash", repo.find("PassChange").get().orElseThrow().passwordHash());
    }

    @Test
    void updateEmailAndRemove() throws Exception {
        repo.insert(account("MailUser")).get();
        assertTrue(repo.updateEmail("MailUser", "a@b.com").get());
        assertEquals("a@b.com", repo.find("MailUser").get().orElseThrow().email());
        assertTrue(repo.updateEmail("MailUser", null).get());
        assertNull(repo.find("MailUser").get().orElseThrow().email());
    }

    @Test
    void deleteRemovesAccount() throws Exception {
        repo.insert(account("Deleted")).get();
        assertTrue(repo.delete("Deleted").get());
        assertTrue(repo.find("Deleted").get().isEmpty());
        assertFalse(repo.delete("Deleted").get(), "second delete must report false");
    }

    @Test
    void duplicateRegistrationRejected() throws Exception {
        repo.insert(account("UniqueUser")).get();
        assertThrows(Exception.class, () -> repo.insert(account("uniqueuser")).get(),
                "primary key must prevent duplicate accounts (case-insensitive)");
    }

    @Test
    void countByRegIpCountsAlts() throws Exception {
        Account a = new Account("alt1", "h", null, "7.7.7.7", "7.7.7.7", 0, 0);
        Account b = new Account("alt2", "h", null, "7.7.7.7", "7.7.7.7", 0, 0);
        repo.insert(a).get();
        repo.insert(b).get();
        assertEquals(2, repo.countByRegIp("7.7.7.7").get());
        assertEquals(0, repo.countByRegIp("8.8.8.8").get());
    }

    @Test
    void findUsernamesByIpMatchesRegOrLastIp() throws Exception {
        Account a = new Account("ipuser1", "h", null, "6.6.6.6", "9.9.9.9", 0, 0);
        repo.insert(a).get();
        List<String> byReg = repo.findUsernamesByIp("6.6.6.6").get();
        List<String> byLast = repo.findUsernamesByIp("9.9.9.9").get();
        assertTrue(byReg.contains("ipuser1"));
        assertTrue(byLast.contains("ipuser1"));
    }

    @Test
    void updateLoginMetaPersists() throws Exception {
        repo.insert(account("MetaUser")).get();
        assertTrue(repo.updateLoginMeta("MetaUser", "42.42.42.42", 123456789L).get());
        Account updated = repo.find("MetaUser").get().orElseThrow();
        assertEquals("42.42.42.42", updated.lastIp());
        assertEquals(123456789L, updated.lastLogin());
    }
}
