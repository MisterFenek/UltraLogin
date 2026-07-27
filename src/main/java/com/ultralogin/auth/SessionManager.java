package com.ultralogin.auth;

import com.ultralogin.config.UltraLoginConfig;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private record Session(String ip, long expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void store(String username, String ip) {
        if (!UltraLoginConfig.SESSIONS_ENABLED.get()) {
            return;
        }
        long ttl = UltraLoginConfig.SESSION_MINUTES.get() * 60_000L;
        sessions.put(username.toLowerCase(Locale.ROOT), new Session(ip, System.currentTimeMillis() + ttl));
    }

    public boolean isValid(String username, String ip) {
        if (!UltraLoginConfig.SESSIONS_ENABLED.get()) {
            return false;
        }
        Session s = sessions.get(username.toLowerCase(Locale.ROOT));
        if (s == null) {
            return false;
        }
        if (System.currentTimeMillis() > s.expiresAt()) {
            sessions.remove(username.toLowerCase(Locale.ROOT));
            return false;
        }
        return s.ip().equals(ip);
    }

    public void invalidate(String username) {
        sessions.remove(username.toLowerCase(Locale.ROOT));
    }
}
