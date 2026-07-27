package com.ultralogin.db;

import org.jetbrains.annotations.Nullable;

public record Account(
        String username,
        String passwordHash,
        @Nullable String email,
        @Nullable String regIp,
        @Nullable String lastIp,
        long registeredAt,
        long lastLogin
) {
}
