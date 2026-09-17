package com.ultralogin.db;

import com.ultralogin.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @BeforeAll
    static void setup() {
        TestConfig.load();
    }

    @Test
    void executorShutsDownBeforeConnectionPool() throws Exception {
        Path tempDir = Files.createTempDirectory("ultralogin_test");
        DatabaseManager db = new DatabaseManager();
        db.start(tempDir);

        // Submit a long-running task that holds the connection
        CompletableFuture<Boolean> task = db.supplyAsync(conn -> {
            try {
                Thread.sleep(500); // simulate slow query
                return !conn.isClosed();
            } catch (Exception e) {
                return false;
            }
        });

        // Call close in another thread so we don't block
        Thread closer = new Thread(db::close);
        // Ensure task has started
        Thread.sleep(100);
        closer.start();

        // The buggy code closes dataSource immediately, then shuts down executor.
        // If executor shut down is called first (and awaited), the task completes before dataSource closes.
        Boolean success = task.join();
        closer.join();

        assertTrue(success, "Task submitted before/during shutdown should get a valid connection");
    }
}
