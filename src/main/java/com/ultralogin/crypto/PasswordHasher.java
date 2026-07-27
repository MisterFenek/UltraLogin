package com.ultralogin.crypto;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ultralogin.config.UltraLoginConfig;

public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static String hash(String password) {
        int cost = UltraLoginConfig.BCRYPT_COST.get();
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray());
    }

    public static boolean verify(String password, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        return BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified;
    }
}
