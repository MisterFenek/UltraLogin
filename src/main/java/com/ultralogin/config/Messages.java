package com.ultralogin.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Messages {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FALLBACK_LANGUAGE = "en";

    private static volatile FileConfig config;
    private static volatile String language = FALLBACK_LANGUAGE;

    private Messages() {
    }

    public static String language() {
        return language;
    }

    public static void load(Path configDir) {
        String lang = sanitizeLanguage(UltraLoginConfig.LANGUAGE.get());
        try {
            Path dir = configDir.resolve("ultralogin");
            Files.createDirectories(dir);
            Path file = dir.resolve("messages_" + lang + ".toml");
            if (Files.notExists(file)) {
                String res = "/ultralogin/messages_" + lang + ".toml";
                if (Messages.class.getResource(res) == null) {
                    LOGGER.info("[UltraLogin] No bundled locale for '{}', creating template from '{}'", lang, FALLBACK_LANGUAGE);
                    res = "/ultralogin/messages_" + FALLBACK_LANGUAGE + ".toml";
                }
                copyResource(res, file);
            }
            FileConfig cfg = FileConfig.of(file);
            cfg.load();
            config = cfg;
            language = lang;
            LOGGER.info("[UltraLogin] Loaded messages ({}) from {}", lang, file);
        } catch (IOException e) {
            LOGGER.error("[UltraLogin] Failed to load messages_{}.toml", lang, e);
        }
    }

    public static String sanitizeLanguage(String requested) {
        String lang = requested == null ? "" : requested.trim().toLowerCase(java.util.Locale.ROOT);
        if (!lang.matches("[a-z0-9_-]{1,16}")) {
            return FALLBACK_LANGUAGE;
        }
        return lang;
    }

    public static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = Messages.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String raw(String key, Object... args) {
        FileConfig cfg = config;
        String value = cfg != null ? cfg.get(key) : null;
        if (value == null) {
            value = "<missing message: " + key + ">";
        }
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value.replace('&', '\u00a7');
    }

    public static Component msg(String key, Object... args) {
        return Component.literal(raw(key, args));
    }

    public static String plain(String key, Object... args) {
        return raw(key, args).replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
    }
}
