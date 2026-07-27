package com.ultralogin.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.ultralogin.UltraLogin;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import com.ultralogin.crypto.PasswordHasher;
import com.ultralogin.db.Account;
import com.ultralogin.email.EmailService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class AdminCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private AdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("ultralogin")
                .executes(ctx -> showInfo(ctx.getSource()))
                .then(PlayerCommands.buildRegisterNode())
                .then(PlayerCommands.buildRegNode())
                .then(PlayerCommands.buildLoginNode())
                .then(PlayerCommands.buildLNode())
                .then(PlayerCommands.buildChangePasswordNode())
                .then(PlayerCommands.buildChangePassNode())
                .then(PlayerCommands.buildUnregisterNode())
                .then(PlayerCommands.buildEmailNode())
                .then(PlayerCommands.buildRecoveryNode())
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(3) && (src.getPlayer() == null || UltraLogin.auth().isAuthenticated(src.getPlayer())))
                        .then(Commands.literal("register")
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .then(Commands.argument("password", StringArgumentType.word())
                                                .executes(ctx -> adminRegister(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "nickname"),
                                                        StringArgumentType.getString(ctx, "password"))))))
                        .then(Commands.literal("unregister")
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .executes(ctx -> adminUnregister(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "nickname")))))
                        .then(Commands.literal("changepassword")
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .then(Commands.argument("newPassword", StringArgumentType.word())
                                                .executes(ctx -> adminChangePassword(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "nickname"),
                                                        StringArgumentType.getString(ctx, "newPassword"))))))
                        .then(Commands.literal("info")
                                .executes(ctx -> adminServerInfo(ctx.getSource()))
                                .then(Commands.argument("nickname", StringArgumentType.word())
                                        .executes(ctx -> adminInfo(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "nickname")))))
                        .then(Commands.literal("accounts")
                                .then(Commands.argument("nicknameOrIp", StringArgumentType.word())
                                        .executes(ctx -> adminAccounts(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "nicknameOrIp")))))
                        .then(Commands.literal("reload")
                                .executes(ctx -> adminReload(ctx.getSource())))
                        .then(Commands.literal("email")
                                .then(Commands.literal("show")
                                        .then(Commands.argument("nickname", StringArgumentType.word())
                                                .executes(ctx -> adminEmailShow(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "nickname")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("nickname", StringArgumentType.word())
                                                .then(Commands.argument("email", StringArgumentType.word())
                                                        .executes(ctx -> adminEmailSet(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "nickname"),
                                                                StringArgumentType.getString(ctx, "email"))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("nickname", StringArgumentType.word())
                                                .executes(ctx -> adminEmailRemove(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "nickname")))))));

        var rootNode = dispatcher.register(root);
        dispatcher.register(Commands.literal("ulogin").executes(ctx -> showInfo(ctx.getSource())).redirect(rootNode));
        dispatcher.register(Commands.literal("ul").executes(ctx -> showInfo(ctx.getSource())).redirect(rootNode));
    }

    private static int showInfo(CommandSourceStack source) {
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();
        String status;
        if (player == null) {
            status = "Console";
        } else if (UltraLogin.auth().isAuthenticated(player)) {
            status = Messages.raw("misc.status_authenticated");
        } else {
            status = Messages.raw("misc.status_pending");
        }
        source.sendSystemMessage(Messages.msg("misc.mod_info", UltraLogin.VERSION, status));
        return 1;
    }

    private static int adminServerInfo(CommandSourceStack source) {
        String dbType = UltraLoginConfig.DB_TYPE.get().toUpperCase(Locale.ROOT);
        boolean sessions = UltraLoginConfig.SESSIONS_ENABLED.get();
        boolean email = UltraLoginConfig.EMAIL_ENABLED.get();
        int bcrypt = UltraLoginConfig.BCRYPT_COST.get();
        int timeout = UltraLoginConfig.AUTH_TIMEOUT_SECONDS.get();
        reply(source, Messages.msg("admin.server_info", UltraLogin.VERSION, dbType,
                sessions ? "ENABLED" : "DISABLED",
                email ? "ENABLED" : "DISABLED",
                bcrypt, timeout));
        return 1;
    }

    private static int adminRegister(CommandSourceStack source, String nickname, String password) {
        UltraLogin.accounts().find(nickname).thenAccept(existing -> {
            if (existing.isPresent()) {
                reply(source, Messages.msg("register.already_registered"));
                return;
            }
            String hash = PasswordHasher.hash(password);
            long now = System.currentTimeMillis();
            UltraLogin.accounts()
                    .insert(new Account(nickname.toLowerCase(Locale.ROOT), hash, null, "admin", "admin", now, 0))
                    .thenRun(() -> reply(source, Messages.msg("admin.registered", nickname)))
                    .exceptionally(e -> fail(source, "admin register", e));
        }).exceptionally(e -> fail(source, "admin register/find", e));
        return 1;
    }

    private static int adminUnregister(CommandSourceStack source, String nickname) {
        UltraLogin.accounts().delete(nickname).thenAccept(deleted -> {
            UltraLogin.sessions().invalidate(nickname);
            source.getServer().execute(() -> {
                net.minecraft.server.level.ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(nickname);
                if (player != null) {
                    UltraLogin.auth().revokeAuthentication(player);
                    player.connection.disconnect(Messages.msg("admin.kick_unregistered"));
                }
                reply(source, Messages.msg(deleted ? "admin.unregistered" : "admin.not_found", nickname));
            });
        }).exceptionally(e -> fail(source, "admin unregister", e));
        return 1;
    }

    private static int adminChangePassword(CommandSourceStack source, String nickname, String newPassword) {
        UltraLogin.db().runAsync(() -> {
            String hash = PasswordHasher.hash(newPassword);
            UltraLogin.accounts().updatePassword(nickname, hash).thenAccept(updated -> {
                if (updated) {
                    UltraLogin.sessions().invalidate(nickname);
                    source.getServer().execute(() -> {
                        net.minecraft.server.level.ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(nickname);
                        if (player != null) {
                            UltraLogin.auth().revokeAuthentication(player);
                            player.connection.disconnect(Messages.msg("admin.kick_password_reset"));
                        }
                        reply(source, Messages.msg("admin.password_set", nickname));
                    });
                } else {
                    reply(source, Messages.msg("admin.not_found", nickname));
                }
            }).exceptionally(e -> fail(source, "admin changepassword", e));
        });
        return 1;
    }

    private static int adminInfo(CommandSourceStack source, String nickname) {
        UltraLogin.accounts().find(nickname).thenAccept(accountOpt -> {
            if (accountOpt.isEmpty()) {
                reply(source, Messages.msg("admin.not_found", nickname));
                return;
            }
            Account acc = accountOpt.get();
            reply(source, Messages.msg("admin.info",
                    acc.username(),
                    formatTime(acc.registeredAt()),
                    formatTime(acc.lastLogin()),
                    orDash(acc.regIp()),
                    orDash(acc.lastIp()),
                    acc.email() == null || acc.email().isBlank() ? "-" : acc.email()));
        }).exceptionally(e -> fail(source, "admin info", e));
        return 1;
    }

    private static int adminAccounts(CommandSourceStack source, String nicknameOrIp) {
        if (looksLikeIp(nicknameOrIp)) {
            listAccountsByIp(source, nicknameOrIp);
        } else {
            UltraLogin.accounts().find(nicknameOrIp).thenAccept(accountOpt -> {
                String ip = accountOpt.map(Account::lastIp).orElse(null);
                if (ip == null || ip.isBlank()) {
                    reply(source, Messages.msg("admin.not_found", nicknameOrIp));
                    return;
                }
                listAccountsByIp(source, ip);
            }).exceptionally(e -> fail(source, "admin accounts", e));
        }
        return 1;
    }

    private static void listAccountsByIp(CommandSourceStack source, String ip) {
        UltraLogin.accounts().findUsernamesByIp(ip).thenAccept(names -> {
            List<String> list = names.isEmpty() ? List.of("-") : names;
            reply(source, Messages.msg("admin.accounts", ip, String.join(", ", list)));
        }).exceptionally(e -> fail(source, "admin accounts/list", e));
    }

    private static int adminReload(CommandSourceStack source) {
        Messages.load(FMLPaths.CONFIGDIR.get());
        if (UltraLogin.email() != null) {
            UltraLogin.email().loadTemplates(FMLPaths.CONFIGDIR.get());
        }
        source.sendSystemMessage(Messages.msg("admin.reloaded"));
        return 1;
    }

    private static int adminEmailShow(CommandSourceStack source, String nickname) {
        UltraLogin.accounts().find(nickname).thenAccept(accountOpt -> {
            if (accountOpt.isEmpty()) {
                reply(source, Messages.msg("admin.not_found", nickname));
                return;
            }
            String email = accountOpt.get().email();
            reply(source, Messages.msg("admin.email_shown", nickname,
                    email == null || email.isBlank() ? "-" : email));
        }).exceptionally(e -> fail(source, "admin email show", e));
        return 1;
    }

    private static int adminEmailSet(CommandSourceStack source, String nickname, String email) {
        if (!EmailService.isValidEmail(email)) {
            source.sendFailure(Messages.msg("email.invalid"));
            return 0;
        }
        UltraLogin.accounts().updateEmail(nickname, email.toLowerCase(Locale.ROOT)).thenAccept(updated ->
                        reply(source, Messages.msg(updated ? "admin.email_set" : "admin.not_found", nickname, email)))
                .exceptionally(e -> fail(source, "admin email set", e));
        return 1;
    }

    private static int adminEmailRemove(CommandSourceStack source, String nickname) {
        UltraLogin.accounts().updateEmail(nickname, null).thenAccept(updated ->
                        reply(source, Messages.msg(updated ? "admin.email_removed" : "admin.not_found", nickname)))
                .exceptionally(e -> fail(source, "admin email remove", e));
        return 1;
    }

    private static boolean looksLikeIp(String value) {
        return value.contains(".") || value.contains(":");
    }

    private static String formatTime(long millis) {
        return millis <= 0 ? "-" : DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void reply(CommandSourceStack source, net.minecraft.network.chat.Component message) {
        source.getServer().execute(() -> source.sendSystemMessage(message));
    }

    private static Void fail(CommandSourceStack source, String where, Throwable e) {
        LOGGER.error("[UltraLogin] Async failure in {}", where, e);
        reply(source, Messages.msg("misc.db_error"));
        return null;
    }
}
