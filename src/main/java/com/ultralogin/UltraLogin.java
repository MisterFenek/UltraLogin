package com.ultralogin;

import com.mojang.logging.LogUtils;
import com.ultralogin.auth.AuthManager;
import com.ultralogin.auth.BruteforceGuard;
import com.ultralogin.auth.SessionManager;
import com.ultralogin.commands.AdminCommands;
import com.ultralogin.commands.PlayerCommands;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import com.ultralogin.db.AccountRepository;
import com.ultralogin.db.DatabaseManager;
import com.ultralogin.email.EmailService;
import com.ultralogin.email.RecoveryManager;
import com.ultralogin.events.ConnectionEvents;
import com.ultralogin.events.ProtectionEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(UltraLogin.MOD_ID)
public final class UltraLogin {

    public static final String MOD_ID = "ultralogin";
    public static final String VERSION = "1.0.0";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AuthManager AUTH = new AuthManager();
    private static final SessionManager SESSIONS = new SessionManager();
    private static final BruteforceGuard BRUTEFORCE = new BruteforceGuard();
    private static final RecoveryManager RECOVERY = new RecoveryManager();

    private static DatabaseManager database;
    private static AccountRepository accounts;
    private static EmailService email;

    public UltraLogin(IEventBus modBus, ModContainer container) {
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 21) {
            throw new IllegalStateException(
                    "[UltraLogin] Java 21 or newer is required (found Java " + javaVersion + "). "
                    + "Update your server's JVM.");
        }

        container.registerConfig(ModConfig.Type.COMMON, UltraLoginConfig.SPEC, "ultralogin-server.toml");

        NeoForge.EVENT_BUS.register(new ConnectionEvents());
        NeoForge.EVENT_BUS.register(new ProtectionEvents());
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onServerStarting(ServerStartingEvent event) {
        Messages.load(FMLPaths.CONFIGDIR.get());
        try {
            database = new DatabaseManager();
            database.start(FMLPaths.GAMEDIR.get());
            accounts = new AccountRepository(database);
            email = new EmailService(database.executor());
            email.loadTemplates(FMLPaths.CONFIGDIR.get());
        } catch (Exception e) {
            LOGGER.error("[UltraLogin] FATAL: failed to initialize database", e);
            throw new IllegalStateException("UltraLogin could not start its database", e);
        }
        LOGGER.info("[UltraLogin] v{} ready", VERSION);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PlayerCommands.register(event.getDispatcher());
        AdminCommands.register(event.getDispatcher());
    }

    public static AuthManager auth() {
        return AUTH;
    }

    public static SessionManager sessions() {
        return SESSIONS;
    }

    public static BruteforceGuard bruteforce() {
        return BRUTEFORCE;
    }

    public static RecoveryManager recovery() {
        return RECOVERY;
    }

    public static DatabaseManager db() {
        return database;
    }

    public static AccountRepository accounts() {
        return accounts;
    }

    public static EmailService email() {
        return email;
    }
}
