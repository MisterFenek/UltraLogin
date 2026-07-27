package com.ultralogin.events;

import com.mojang.logging.LogUtils;
import com.ultralogin.UltraLogin;
import com.ultralogin.auth.PendingPlayer;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConnectionEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String name = player.getGameProfile().getName();
        String ip = player.getIpAddress();

        if (!isValidUsername(name)) {
            player.connection.disconnect(Messages.msg("misc.invalid_username_kick"));
            return;
        }

        if (UltraLogin.bruteforce().isBanned(ip)) {
            player.connection.disconnect(Messages.msg("login.banned_kick"));
            return;
        }

        if (!checkAdminIpBinding(player, name, ip)) {
            player.connection.disconnect(Messages.msg("admin.admin_ip_kick"));
            return;
        }

        if (UltraLogin.sessions().isValid(name, ip)) {
            UltraLogin.auth().beginPreLogin(player);
            UltraLogin.accounts().find(name).whenComplete((accountOpt, error) -> {
                if (error != null) {
                    LOGGER.error("[UltraLogin] Session verification failed for {}", name, error);
                    return;
                }
                player.server.execute(() -> {
                    PendingPlayer state = UltraLogin.auth().getPending(player);
                    if (state == null) {
                        return;
                    }
                    if (accountOpt.isPresent()) {
                        UltraLogin.auth().authenticate(player);
                        player.sendSystemMessage(Messages.msg("prelogin.session_restored"));
                        UltraLogin.accounts().updateLoginMeta(name, ip, System.currentTimeMillis());
                        remindEmailIfNeeded(player, name);
                    } else {
                        UltraLogin.sessions().invalidate(name);
                        state.registered = false;
                        player.sendSystemMessage(Messages.msg("prelogin.need_register"));
                    }
                });
            });
            return;
        }

        UltraLogin.auth().beginPreLogin(player);
        UltraLogin.accounts().find(name).whenComplete((account, error) -> {
            if (error != null) {
                LOGGER.error("[UltraLogin] Failed to look up account for {}", name, error);
                return;
            }
            player.server.execute(() -> {
                PendingPlayer state = UltraLogin.auth().getPending(player);
                if (state == null) {
                    return;
                }
                state.registered = account.isPresent();
                player.sendSystemMessage(Messages.msg(
                        state.registered ? "prelogin.need_login" : "prelogin.need_register"));
            });
        });
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (UltraLogin.auth().isAuthenticated(player)) {
            UltraLogin.sessions().store(name, player.getIpAddress());
        } else {
            UltraLogin.auth().handleLogoutWithoutAuth(player);
        }
    }

    private static boolean isValidUsername(String name) {
        try {
            return Pattern.matches(UltraLoginConfig.USERNAME_REGEX.get(), name);
        } catch (PatternSyntaxException e) {
            LOGGER.error("[UltraLogin] Invalid usernameRegex in config, falling back to default", e);
            return Pattern.matches("^[A-Za-z0-9_]{3,16}$", name);
        }
    }

    private static boolean checkAdminIpBinding(ServerPlayer player, String name, String ip) {
        List<? extends String> bindings = UltraLoginConfig.ADMIN_IP_BINDINGS.get();
        for (String binding : bindings) {
            int eq = binding.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String boundName = binding.substring(0, eq).trim();
            if (!boundName.equalsIgnoreCase(name)) {
                continue;
            }
            boolean isOp = player.server.getPlayerList().isOp(player.getGameProfile());
            if (!isOp) {
                return true;
            }
            String[] allowedIps = binding.substring(eq + 1).split(";");
            for (String allowed : allowedIps) {
                if (allowed.trim().equals(ip)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static void remindEmailIfNeeded(ServerPlayer player, String name) {
        if (!UltraLoginConfig.EMAIL_ENABLED.get()) {
            return;
        }
        UltraLogin.accounts().find(name.toLowerCase(Locale.ROOT)).thenAccept(account ->
                account.ifPresent(acc -> {
                    if (acc.email() == null || acc.email().isBlank()) {
                        player.server.execute(() -> player.sendSystemMessage(Messages.msg(
                                UltraLoginConfig.EMAIL_REQUIRED.get() ? "email.required" : "email.reminder")));
                    }
                }));
    }
}
