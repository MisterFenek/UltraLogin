package com.ultralogin.db;

import com.mojang.logging.LogUtils;
import com.ultralogin.config.UltraLoginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseManager implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private boolean mysql;

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    public void start(Path gameDir) throws Exception {
        this.mysql = "mysql".equalsIgnoreCase(UltraLoginConfig.DB_TYPE.get());

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("UltraLogin-Pool");
        if (mysql) {
            cfg.setDriverClassName("org.mariadb.jdbc.Driver");
            cfg.setJdbcUrl("jdbc:mariadb://%s:%d/%s".formatted(
                    UltraLoginConfig.DB_HOST.get(),
                    UltraLoginConfig.DB_PORT.get(),
                    UltraLoginConfig.DB_NAME.get()));
            cfg.setUsername(UltraLoginConfig.DB_USER.get());
            cfg.setPassword(UltraLoginConfig.DB_PASSWORD.get());
            cfg.setMaximumPoolSize(UltraLoginConfig.DB_POOL_SIZE.get());
        } else {
            Path dir = gameDir.resolve("ultralogin");
            Files.createDirectories(dir);
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setJdbcUrl("jdbc:sqlite:" + dir.resolve("ultralogin.db").toAbsolutePath());
            cfg.setMaximumPoolSize(1);
        }
        cfg.setConnectionTimeout(10_000);

        this.dataSource = new HikariDataSource(cfg);
        createSchema();
        LOGGER.info("[UltraLogin] Database ready ({})", mysql ? "MySQL/MariaDB" : "SQLite");
    }

    private void createSchema() throws SQLException {
        String ddl = mysql
                ? """
                CREATE TABLE IF NOT EXISTS ul_accounts (
                    username      VARCHAR(16)  NOT NULL PRIMARY KEY,
                    password_hash VARCHAR(100) NOT NULL,
                    email         VARCHAR(255),
                    reg_ip        VARCHAR(45),
                    last_ip       VARCHAR(45),
                    registered_at BIGINT NOT NULL DEFAULT 0,
                    last_login    BIGINT NOT NULL DEFAULT 0
                )"""
                : """
                CREATE TABLE IF NOT EXISTS ul_accounts (
                    username      TEXT    NOT NULL PRIMARY KEY,
                    password_hash TEXT    NOT NULL,
                    email         TEXT,
                    reg_ip        TEXT,
                    last_ip       TEXT,
                    registered_at INTEGER NOT NULL DEFAULT 0,
                    last_login    INTEGER NOT NULL DEFAULT 0
                )""";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(ddl);
        }
    }

    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return work.apply(conn);
            } catch (SQLException e) {
                throw new RuntimeException("Database error", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> runAsync(Runnable work) {
        return CompletableFuture.runAsync(work, executor);
    }

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("[UltraLogin] Background tasks did not finish in 5 s — forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
