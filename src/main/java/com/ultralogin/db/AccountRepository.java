package com.ultralogin.db;

import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AccountRepository {

    private final DatabaseManager db;

    public AccountRepository(DatabaseManager db) {
        this.db = db;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<Optional<Account>> find(String username) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT username, password_hash, email, reg_ip, last_ip, registered_at, last_login FROM ul_accounts WHERE username = ?")) {
                ps.setString(1, key(username));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Void> insert(Account account) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ul_accounts (username, password_hash, email, reg_ip, last_ip, registered_at, last_login) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, key(account.username()));
                ps.setString(2, account.passwordHash());
                ps.setString(3, account.email());
                ps.setString(4, account.regIp());
                ps.setString(5, account.lastIp());
                ps.setLong(6, account.registeredAt());
                ps.setLong(7, account.lastLogin());
                ps.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> updatePassword(String username, String newHash) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ul_accounts SET password_hash = ? WHERE username = ?")) {
                ps.setString(1, newHash);
                ps.setString(2, key(username));
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> updateEmail(String username, @Nullable String email) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ul_accounts SET email = ? WHERE username = ?")) {
                ps.setString(1, email);
                ps.setString(2, key(username));
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> updateLoginMeta(String username, String ip, long timestamp) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ul_accounts SET last_ip = ?, last_login = ? WHERE username = ?")) {
                ps.setString(1, ip);
                ps.setLong(2, timestamp);
                ps.setString(3, key(username));
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> delete(String username) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ul_accounts WHERE username = ?")) {
                ps.setString(1, key(username));
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Integer> countByRegIp(String ip) {
        return db.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ul_accounts WHERE reg_ip = ?")) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<List<String>> findUsernamesByIp(String ip) {
        return db.supplyAsync(conn -> {
            List<String> names = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT username FROM ul_accounts WHERE reg_ip = ? OR last_ip = ?")) {
                ps.setString(1, ip);
                ps.setString(2, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.add(rs.getString(1));
                    }
                }
            }
            return names;
        });
    }

    private static Account read(ResultSet rs) throws java.sql.SQLException {
        return new Account(
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("email"),
                rs.getString("reg_ip"),
                rs.getString("last_ip"),
                rs.getLong("registered_at"),
                rs.getLong("last_login"));
    }
}
