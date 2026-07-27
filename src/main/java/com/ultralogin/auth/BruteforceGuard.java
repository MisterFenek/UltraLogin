package com.ultralogin.auth;

import com.ultralogin.config.UltraLoginConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BruteforceGuard {

    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> bannedUntil = new ConcurrentHashMap<>();

    public int recordFailure(String ip) {
        int max = UltraLoginConfig.MAX_LOGIN_ATTEMPTS.get();
        int used = attempts.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
        int left = max - used;
        if (left <= 0) {
            long banMillis = UltraLoginConfig.IP_BAN_MINUTES.get() * 60_000L;
            bannedUntil.put(ip, System.currentTimeMillis() + banMillis);
            attempts.remove(ip);
            return 0;
        }
        return left;
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
