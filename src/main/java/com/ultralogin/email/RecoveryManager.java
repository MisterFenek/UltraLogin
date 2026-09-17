package com.ultralogin.email;

import com.ultralogin.config.UltraLoginConfig;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RecoveryManager {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private record Token(String code, long expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequest = new ConcurrentHashMap<>();

    public long cooldownMinutesLeft(String username) {
        Long last = lastRequest.get(key(username));
        if (last == null) {
            return 0;
        }
        long cooldown = UltraLoginConfig.RECOVERY_COOLDOWN_MINUTES.get() * 60_000L;
        long left = last + cooldown - System.currentTimeMillis();
        return left <= 0 ? 0 : Math.max(1, left / 60_000L);
    }

    public String issueCode(String username) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        String code = sb.toString();
        long ttl = UltraLoginConfig.RECOVERY_CODE_TTL_MINUTES.get() * 60_000L;
        tokens.put(key(username), new Token(code, System.currentTimeMillis() + ttl));
        lastRequest.put(key(username), System.currentTimeMillis());
        return code;
    }

    public boolean consume(String username, String code) {
        Token token = tokens.get(key(username));
        if (token == null || System.currentTimeMillis() > token.expiresAt()) {
            tokens.remove(key(username));
            return false;
        }
        if (!token.code().equalsIgnoreCase(code.trim())) {
            tokens.remove(key(username)); // Invalidate token immediately on wrong guess
            return false;
        }
        tokens.remove(key(username));
        return true;
    }

    public void revokeCode(String username) {
        String k = key(username);
        tokens.remove(k);
        lastRequest.remove(k);
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
