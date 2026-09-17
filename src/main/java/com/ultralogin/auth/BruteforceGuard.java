package com.ultralogin.auth;

import com.ultralogin.config.UltraLoginConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BruteforceGuard {

    private record AttemptRecord(AtomicInteger count, long lastAttempt) {}

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> bannedUntil = new ConcurrentHashMap<>();

    public int recordFailure(String ip) {
        cleanupOldAttempts();
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        long now = System.currentTimeMillis();

        AttemptRecord record = attempts.compute(ip, (k, v) -> {
            if (v == null || now - v.lastAttempt > 600_000L) { // 10 minutes expiry for attempts
                return new AttemptRecord(new AtomicInteger(1), now);
            }
            v.count.incrementAndGet();
            return new AttemptRecord(v.count, now); // Update last attempt time
        });

        int used = record.count.get();
        int left = max - used;
        if (left <= 0) {
            long banMillis = UltraLoginConfig.IP_BAN_MINUTES.get() * 60_000L;
            bannedUntil.put(ip, now + banMillis);
            attempts.remove(ip);
            return 0;
        }
        return left;
    }

    void cleanupOldAttempts() {
        long now = System.currentTimeMillis();
        // Remove attempts older than 10 minutes
        attempts.entrySet().removeIf(e -> now - e.getValue().lastAttempt > 600_000L);
        // Remove expired bans
        bannedUntil.entrySet().removeIf(e -> now > e.getValue());
    }

    public void clear(String ip) {
        attempts.remove(ip);
    }

    public boolean isBanned(String ip) {
        Long until = bannedUntil.get(ip);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            bannedUntil.remove(ip);
            return false;
        }
        return true;
    }
}
