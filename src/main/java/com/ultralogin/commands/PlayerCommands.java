package com.ultralogin.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.ultralogin.UltraLogin;
import com.ultralogin.auth.PendingPlayer;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import com.ultralogin.crypto.PasswordHasher;
import com.ultralogin.db.Account;
import com.ultralogin.email.EmailService;
import com.ultralogin.events.ConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class PlayerCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRegisterNode());
        dispatcher.register(buildRegNode());
        dispatcher.register(buildLoginNode());
        dispatcher.register(buildLNode());
        dispatcher.register(buildChangePasswordNode());
        dispatcher.register(buildChangePassNode());
        dispatcher.register(buildUnregisterNode());
        dispatcher.register(buildEmailNode());
        dispatcher.register(buildRecoveryNode());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildRegisterNode() {
        return Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                                .executes(ctx -> doRegister(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "password"),
                                        StringArgumentType.getString(ctx, "confirm")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildRegNode() {
        return Commands.literal("reg")
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                                .executes(ctx -> doRegister(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "password"),
                                        StringArgumentType.getString(ctx, "confirm")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildLoginNode() {
        return Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(ctx -> doLogin(ctx.getSource(),
                                StringArgumentType.getString(ctx, "password"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildLNode() {
        return Commands.literal("l")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(ctx -> doLogin(ctx.getSource(),
                                StringArgumentType.getString(ctx, "password"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildChangePasswordNode() {
        return Commands.literal("changepassword")
                .then(Commands.argument("old", StringArgumentType.word())
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(ctx -> doChangePassword(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "old"),
                                        StringArgumentType.getString(ctx, "new")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildChangePassNode() {
        return Commands.literal("changepass")
                .then(Commands.argument("old", StringArgumentType.word())
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(ctx -> doChangePassword(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "old"),
                                        StringArgumentType.getString(ctx, "new")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnregisterNode() {
        return Commands.literal("unregister")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(ctx -> doUnregister(ctx.getSource(),
                                StringArgumentType.getString(ctx, "password"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildEmailNode() {
        return Commands.literal("email")
                .then(Commands.literal("add")
                        .then(Commands.argument("email", StringArgumentType.word())
                                .then(Commands.argument("confirm", StringArgumentType.word())
                                        .executes(ctx -> doEmailAdd(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "email"),
                                                StringArgumentType.getString(ctx, "confirm"))))))
                .then(Commands.literal("change")
                        .then(Commands.argument("old", StringArgumentType.word())
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(ctx -> doEmailChange(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "old"),
                                                StringArgumentType.getString(ctx, "new"))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildRecoveryNode() {
        return Commands.literal("recovery")
                .then(Commands.literal("confirm")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .then(Commands.argument("newPassword", StringArgumentType.word())
                                        .executes(ctx -> doRecoveryConfirm(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "code"),
                                                StringArgumentType.getString(ctx, "newPassword"))))))
                .then(Commands.argument("email", StringArgumentType.word())
                        .executes(ctx -> doRecoveryRequest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "email"))));
    }

    private static int doRegister(CommandSourceStack source, String password, String confirm) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Messages.msg("misc.players_only"));
            return 0;
        }
        PendingPlayer state = UltraLogin.auth().getPending(player);
        if (state == null) {
            player.sendSystemMessage(Messages.msg("login.already_logged_in"));
            return 0;
        }
        if (state.registered) {
            player.sendSystemMessage(Messages.msg("register.already_registered"));
            return 0;
        }
        if (!password.equals(confirm)) {
            player.sendSystemMessage(Messages.msg("register.passwords_mismatch"));
            return 0;
        }
        if (!checkPasswordPolicy(player, password)) {
            return 0;
        }

        String name = player.getGameProfile().getName();
        String ip = player.getIpAddress();
        int maxPerIp = UltraLoginConfig.MAX_ACCOUNTS_PER_IP.get();

        UltraLogin.accounts().countByRegIp(ip).thenAccept(count -> {
            if (maxPerIp > 0 && count >= maxPerIp) {
                onMain(player, () -> player.sendSystemMessage(
                        Messages.msg("register.too_many_accounts", maxPerIp)));
                return;
            }
            String hash = PasswordHasher.hash(password);
            long now = System.currentTimeMillis();
            UltraLogin.accounts()
                    .insert(new Account(name.toLowerCase(Locale.ROOT), hash, null, ip, ip, now, now))
                    .thenRun(() -> onMain(player, () -> {
                        if (UltraLogin.auth().getPending(player) == null) {
                            return;
                        }
                        UltraLogin.auth().authenticate(player);
                        UltraLogin.sessions().store(name, ip);
                        player.sendSystemMessage(Messages.msg("register.success"));
                        ConnectionEvents.remindEmailIfNeeded(player, name);
                    }))
                    .exceptionally(e -> dbError(player, "register", e));
        }).exceptionally(e -> dbError(player, "register/count", e));
        return 1;
    }

    private static int doLogin(CommandSourceStack source, String password) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Messages.msg("misc.players_only"));
            return 0;
        }
        if (UltraLogin.auth().isAuthenticated(player)) {
            player.sendSystemMessage(Messages.msg("login.already_logged_in"));
            return 0;
        }
        String name = player.getGameProfile().getName();
        String ip = player.getIpAddress();

        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            if (accountOpt.isEmpty()) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("login.not_registered")));
                return;
            }
            boolean ok = PasswordHasher.verify(password, accountOpt.get().passwordHash());
            onMain(player, () -> {
                if (UltraLogin.auth().getPending(player) == null) {
                    return;
                }
                if (ok) {
                    UltraLogin.bruteforce().clear(ip);
                    UltraLogin.auth().authenticate(player);
                    UltraLogin.sessions().store(name, ip);
                    UltraLogin.accounts().updateLoginMeta(name, ip, System.currentTimeMillis());
                    player.sendSystemMessage(Messages.msg("login.success"));
                    ConnectionEvents.remindEmailIfNeeded(player, name);
                } else {
                    int left = UltraLogin.bruteforce().recordFailure(ip);
                    if (left <= 0) {
                        player.connection.disconnect(Messages.msg(
                                "login.bruteforce_ban", UltraLoginConfig.IP_BAN_MINUTES.get()));
                    } else {
                        player.sendSystemMessage(Messages.msg("login.wrong_password", left));
                    }
                }
            });
        }).exceptionally(e -> dbError(player, "login", e));
        return 1;
    }

    private static int doChangePassword(CommandSourceStack source, String oldPassword, String newPassword) {
        ServerPlayer player = requireAuthenticated(source);
        if (player == null) {
            return 0;
        }
        if (!checkPasswordPolicy(player, newPassword)) {
            return 0;
        }
        String name = player.getGameProfile().getName();
        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            if (accountOpt.isEmpty() || !PasswordHasher.verify(oldPassword, accountOpt.get().passwordHash())) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("password.wrong_old")));
                return;
            }
            String newHash = PasswordHasher.hash(newPassword);
            UltraLogin.accounts().updatePassword(name, newHash)
                    .thenRun(() -> onMain(player, () -> {
                        UltraLogin.sessions().invalidate(name);
                        player.sendSystemMessage(Messages.msg("password.changed"));
                    }))
                    .exceptionally(e -> dbError(player, "changepassword", e));
        }).exceptionally(e -> dbError(player, "changepassword/find", e));
        return 1;
    }

    private static int doUnregister(CommandSourceStack source, String password) {
        ServerPlayer player = requireAuthenticated(source);
        if (player == null) {
            return 0;
        }
        if (!UltraLoginConfig.ALLOW_UNREGISTER.get()) {
            player.sendSystemMessage(Messages.msg("unregister.disabled"));
            return 0;
        }
        String name = player.getGameProfile().getName();
        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            if (accountOpt.isEmpty() || !PasswordHasher.verify(password, accountOpt.get().passwordHash())) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("unregister.wrong_password")));
                return;
            }
            UltraLogin.accounts().delete(name)
                    .thenRun(() -> onMain(player, () -> {
                        UltraLogin.sessions().invalidate(name);
                        UltraLogin.auth().revokeAuthentication(player);
                        player.connection.disconnect(Messages.msg("unregister.success"));
                    }))
                    .exceptionally(e -> dbError(player, "unregister", e));
        }).exceptionally(e -> dbError(player, "unregister/find", e));
        return 1;
    }

    private static int doEmailAdd(CommandSourceStack source, String email, String confirm) {
        ServerPlayer player = requireAuthenticated(source);
        if (player == null || !requireEmailEnabled(player)) {
            return 0;
        }
        if (!email.equalsIgnoreCase(confirm)) {
            player.sendSystemMessage(Messages.msg("email.mismatch"));
            return 0;
        }
        if (!EmailService.isValidEmail(email)) {
            player.sendSystemMessage(Messages.msg("email.invalid"));
            return 0;
        }
        String name = player.getGameProfile().getName();
        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            Optional<Account> acc = accountOpt;
            if (acc.isPresent() && acc.get().email() != null && !acc.get().email().isBlank()) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("email.already_set")));
                return;
            }
            UltraLogin.accounts().updateEmail(name, email.toLowerCase(Locale.ROOT))
                    .thenRun(() -> onMain(player,
                            () -> player.sendSystemMessage(Messages.msg("email.added", email))))
                    .exceptionally(e -> dbError(player, "email add", e));
        }).exceptionally(e -> dbError(player, "email add/find", e));
        return 1;
    }

    private static int doEmailChange(CommandSourceStack source, String oldEmail, String newEmail) {
        ServerPlayer player = requireAuthenticated(source);
        if (player == null || !requireEmailEnabled(player)) {
            return 0;
        }
        if (!EmailService.isValidEmail(newEmail)) {
            player.sendSystemMessage(Messages.msg("email.invalid"));
            return 0;
        }
        String name = player.getGameProfile().getName();
        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            String current = accountOpt.map(Account::email).orElse(null);
            if (current == null || current.isBlank()) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("email.not_set")));
                return;
            }
            if (!current.equalsIgnoreCase(oldEmail)) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("email.wrong_old")));
                return;
            }
            UltraLogin.accounts().updateEmail(name, newEmail.toLowerCase(Locale.ROOT))
                    .thenRun(() -> onMain(player,
                            () -> player.sendSystemMessage(Messages.msg("email.changed", newEmail))))
                    .exceptionally(e -> dbError(player, "email change", e));
        }).exceptionally(e -> dbError(player, "email change/find", e));
        return 1;
    }

    private static int doRecoveryRequest(CommandSourceStack source, String email) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Messages.msg("misc.players_only"));
            return 0;
        }
        if (!requireEmailEnabled(player)) {
            return 0;
        }
        String name = player.getGameProfile().getName();

        long cooldown = UltraLogin.recovery().cooldownMinutesLeft(name);
        if (cooldown > 0) {
            player.sendSystemMessage(Messages.msg("recovery.cooldown", cooldown));
            return 0;
        }

        UltraLogin.accounts().find(name).thenAccept(accountOpt -> {
            String bound = accountOpt.map(Account::email).orElse(null);
            if (bound == null || bound.isBlank()) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("email.not_set")));
                return;
            }
            if (!bound.equalsIgnoreCase(email)) {
                onMain(player, () -> player.sendSystemMessage(Messages.msg("recovery.wrong_email")));
                return;
            }
            String code = UltraLogin.recovery().issueCode(name);
            UltraLogin.email().sendRecoveryCode(bound, name, code).thenAccept(sent -> {
                if (!sent) {
                    UltraLogin.recovery().revokeCode(name);
                }
                onMain(player, () -> player.sendSystemMessage(
                        Messages.msg(sent ? "recovery.sent" : "recovery.send_failed")));
            });
        }).exceptionally(e -> dbError(player, "recovery", e));
        return 1;
    }

    private static int doRecoveryConfirm(CommandSourceStack source, String code, String newPassword) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Messages.msg("misc.players_only"));
            return 0;
        }
        if (!requireEmailEnabled(player) || !checkPasswordPolicy(player, newPassword)) {
            return 0;
        }
        String name = player.getGameProfile().getName();
        if (!UltraLogin.recovery().consume(name, code)) {
            player.sendSystemMessage(Messages.msg("recovery.wrong_code"));
            return 0;
        }
        UltraLogin.db().runAsync(() -> {
            String hash = PasswordHasher.hash(newPassword);
            UltraLogin.accounts().updatePassword(name, hash)
                    .thenRun(() -> onMain(player, () -> {
                        UltraLogin.sessions().invalidate(name);
                        player.sendSystemMessage(Messages.msg("recovery.success"));
                    }))
                    .exceptionally(e -> dbError(player, "recovery confirm", e));
        });
        return 1;
    }

    private static ServerPlayer requireAuthenticated(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Messages.msg("misc.players_only"));
            return null;
        }
        if (!UltraLogin.auth().isAuthenticated(player)) {
            player.sendSystemMessage(Messages.msg("misc.not_authenticated"));
            return null;
        }
        return player;
    }

    private static boolean requireEmailEnabled(ServerPlayer player) {
        if (!UltraLoginConfig.EMAIL_ENABLED.get()) {
            player.sendSystemMessage(Messages.msg("email.disabled"));
            return false;
        }
        return true;
    }

    private static boolean checkPasswordPolicy(ServerPlayer player, String password) {
        int min = UltraLoginConfig.MIN_PASSWORD_LENGTH.get();
        int max = UltraLoginConfig.MAX_PASSWORD_LENGTH.get();
        if (password.length() < min) {
            player.sendSystemMessage(Messages.msg("register.password_too_short", min));
            return false;
        }
        if (password.length() > max) {
            player.sendSystemMessage(Messages.msg("register.password_too_long", max));
            return false;
        }
        return true;
    }

    private static void onMain(ServerPlayer player, Runnable task) {
        player.server.execute(task);
    }

    private static Void dbError(ServerPlayer player, String where, Throwable e) {
        LOGGER.error("[UltraLogin] Async failure in {}", where, e);
        onMain(player, () -> player.sendSystemMessage(Messages.msg("misc.db_error")));
        return null;
    }
}
