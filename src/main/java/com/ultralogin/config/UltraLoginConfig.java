package com.ultralogin.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class UltraLoginConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> LANGUAGE;
    public static final ModConfigSpec.IntValue AUTH_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue MIN_PASSWORD_LENGTH;
    public static final ModConfigSpec.IntValue MAX_PASSWORD_LENGTH;
    public static final ModConfigSpec.ConfigValue<String> USERNAME_REGEX;
    public static final ModConfigSpec.BooleanValue ALLOW_UNREGISTER;
    public static final ModConfigSpec.IntValue BCRYPT_COST;

    public static final ModConfigSpec.BooleanValue SESSIONS_ENABLED;
    public static final ModConfigSpec.IntValue SESSION_MINUTES;

    public static final ModConfigSpec.IntValue MAX_LOGIN_ATTEMPTS;
    public static final ModConfigSpec.IntValue IP_BAN_MINUTES;
    public static final ModConfigSpec.IntValue MAX_ACCOUNTS_PER_IP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_IP_BINDINGS;

    public static final ModConfigSpec.ConfigValue<String> DB_TYPE;
    public static final ModConfigSpec.ConfigValue<String> DB_HOST;
    public static final ModConfigSpec.IntValue DB_PORT;
    public static final ModConfigSpec.ConfigValue<String> DB_NAME;
    public static final ModConfigSpec.ConfigValue<String> DB_USER;
    public static final ModConfigSpec.ConfigValue<String> DB_PASSWORD;
    public static final ModConfigSpec.IntValue DB_POOL_SIZE;

    public static final ModConfigSpec.BooleanValue EMAIL_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> SMTP_HOST;
    public static final ModConfigSpec.IntValue SMTP_PORT;
    public static final ModConfigSpec.BooleanValue SMTP_SSL;
    public static final ModConfigSpec.BooleanValue SMTP_STARTTLS;
    public static final ModConfigSpec.ConfigValue<String> SMTP_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> SMTP_PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> SMTP_FROM;
    public static final ModConfigSpec.ConfigValue<String> PROJECT_NAME;
    public static final ModConfigSpec.IntValue RECOVERY_CODE_TTL_MINUTES;
    public static final ModConfigSpec.IntValue RECOVERY_COOLDOWN_MINUTES;
    public static final ModConfigSpec.BooleanValue EMAIL_REQUIRED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        LANGUAGE = b
                .comment("Language for all messages and email templates.",
                        "Bundled locales: \"en\" (English), \"ru\" (Russian). Default is \"en\".",
                        "If you set a custom language (e.g. \"de\", \"es\"), the mod will automatically create",
                        "config/ultralogin/messages_<lang>.toml using default templates so you can fully translate it.",
                        "----------------------------------------------------------------",
                        "Язык сообщений и шаблонов писем.",
                        "Встроенные локализации: \"en\" (Английский), \"ru\" (Русский). По умолчанию \"en\".",
                        "При указании своего кода (например, \"de\", \"es\"), мод автоматически создаст",
                        "файл config/ultralogin/messages_<lang>.toml для полного перевода.")
                .define("language", "en");
        AUTH_TIMEOUT_SECONDS = b
                .comment("Seconds a player has to authenticate before being kicked.",
                        "Время в секундах, отведённое игроку на авторизацию до автоматического кика.")
                .defineInRange("authTimeoutSeconds", 30, 5, 600);
        MIN_PASSWORD_LENGTH = b
                .comment("Minimum required password length.",
                        "Минимальная длина пароля при регистрации.")
                .defineInRange("minPasswordLength", 6, 1, 64);
        MAX_PASSWORD_LENGTH = b
                .comment("Maximum allowed password length (protects against BCrypt DoS).",
                        "Максимальная длина пароля (защита процессора от нагрузки при хэшировании).")
                .defineInRange("maxPasswordLength", 64, 8, 128);
        USERNAME_REGEX = b
                .comment("Regular expression a username must match to be allowed on the server.",
                        "Регулярное выражение для проверки ника игрока при входе.")
                .define("usernameRegex", "^[A-Za-z0-9_]{3,16}$");
        ALLOW_UNREGISTER = b
                .comment("Allow players to delete their own account with /unregister.",
                        "Разрешить игрокам самостоятельно удалять свой аккаунт командой /unregister.")
                .define("allowUnregister", true);
        BCRYPT_COST = b
                .comment("BCrypt hashing cost factor (work factor). 10-14 recommended.",
                        "Сложность хэширования BCrypt (work factor). Рекомендуется 10-14.")
                .defineInRange("bcryptCost", 12, 4, 31);
        b.pop();

        b.push("session");
        SESSIONS_ENABLED = b
                .comment("Skip /login when a player rejoins from the same IP within the session window.",
                        "Включить IP-сессии: авто-вход при повторном подключении с того же IP.")
                .define("enabled", true);
        SESSION_MINUTES = b
                .comment("Duration of an IP session in minutes.",
                        "Длительность IP-сессии в минутах.")
                .defineInRange("sessionMinutes", 10, 1, 1440);
        b.pop();

        b.push("security");
        MAX_LOGIN_ATTEMPTS = b
                .comment("Failed /login attempts allowed before the IP is temporarily banned.",
                        "Количество неудачных попыток входа до временной блокировки IP.")
                .defineInRange("maxLoginAttempts", 4, 1, 20);
        IP_BAN_MINUTES = b
                .comment("Duration of the temporary IP ban in minutes after maximum failed login attempts.",
                        "Длительность временной блокировки IP-адреса в минутах.")
                .defineInRange("ipBanMinutes", 10, 1, 1440);
        MAX_ACCOUNTS_PER_IP = b
                .comment("Maximum registered accounts allowed per IP address (anti-alt system). 0 = unlimited.",
                        "Максимальное количество аккаунтов на один IP (защита от твинков). 0 = без ограничений.")
                .defineInRange("maxAccountsPerIp", 2, 0, 100);
        ADMIN_IP_BINDINGS = b
                .comment("Bind OP accounts to IPs. Format: \"Nickname=1.2.3.4\" or \"Nickname=1.2.3.4;5.6.7.8\".",
                        "OP players with a binding may only join from a listed IP.",
                        "----------------------------------------------------------------",
                        "Привязка OP-аккаунтов к IP. Формат: \"Ник=1.2.3.4\" или \"Ник=1.2.3.4;5.6.7.8\".",
                        "Операторы с привязкой смогут заходить только с указанных IP-адресов.")
                .defineList("adminIpBindings", List.of(), () -> "PlayerName=1.2.3.4",
                        o -> o instanceof String s && s.contains("="));
        b.pop();

        b.push("database");
        DB_TYPE = b
                .comment("Database backend: \"sqlite\" (local file) or \"mysql\" (MySQL/MariaDB via HikariCP).",
                        "Тип базы данных: \"sqlite\" (локальный файл) или \"mysql\" (MySQL/MariaDB).")
                .define("type", "sqlite");
        DB_HOST = b
                .comment("Database host (for MySQL/MariaDB).",
                        "Хост базы данных (для MySQL/MariaDB).")
                .define("host", "localhost");
        DB_PORT = b
                .comment("Database port (for MySQL/MariaDB).",
                        "Порт базы данных (для MySQL/MariaDB).")
                .defineInRange("port", 3306, 1, 65535);
        DB_NAME = b
                .comment("Database name.",
                        "Имя базы данных.")
                .define("name", "ultralogin");
        DB_USER = b
                .comment("Database user.",
                        "Пользователь базы данных.")
                .define("user", "root");
        DB_PASSWORD = b
                .comment("Database password.",
                        "Пароль пользователя базы данных.")
                .define("password", "");
        DB_POOL_SIZE = b
                .comment("HikariCP connection pool size.",
                        "Размер пула соединений HikariCP.")
                .defineInRange("poolSize", 8, 1, 64);
        b.pop();

        b.push("email");
        EMAIL_ENABLED = b
                .comment("Enable SMTP email features (/email, /recovery).",
                        "Включить почтовые функции (команды /email, /recovery и сброс пароля).")
                .define("enabled", false);
        SMTP_HOST = b
                .comment("SMTP server hostname (e.g. smtp.gmail.com).",
                        "Адрес SMTP-сервера (например, smtp.gmail.com).")
                .define("smtpHost", "smtp.gmail.com");
        SMTP_PORT = b
                .comment("SMTP server port (usually 465 for SSL, 587 for STARTTLS).",
                        "Порт SMTP-сервера (обычно 465 для SSL, 587 для STARTTLS).")
                .defineInRange("smtpPort", 465, 1, 65535);
        SMTP_SSL = b
                .comment("Use implicit SSL (usually port 465).",
                        "Использовать прямое SSL-подключение (обычно порт 465).")
                .define("ssl", true);
        SMTP_STARTTLS = b
                .comment("Use STARTTLS (usually port 587).",
                        "Использовать шифрование STARTTLS (обычно порт 587).")
                .define("starttls", false);
        SMTP_USERNAME = b
                .comment("SMTP account username / email.",
                        "Логин / почта для авторизации на SMTP-сервере.")
                .define("username", "");
        SMTP_PASSWORD = b
                .comment("SMTP account password or app password.",
                        "Пароль от почты или пароль приложения для SMTP.")
                .define("password", "");
        SMTP_FROM = b
                .comment("From address; empty = use username.",
                        "Адрес отправителя. Оставьте пустым, чтобы использовать username.")
                .define("from", "");
        PROJECT_NAME = b
                .comment("Project or server name displayed in emails.",
                        "Название сервера/проекта, отображаемое в письмах.")
                .define("projectName", "UltraLogin Server");
        RECOVERY_CODE_TTL_MINUTES = b
                .comment("Lifetime of a recovery code in minutes.",
                        "Срок действия одноразового кода восстановления в минутах.")
                .defineInRange("recoveryCodeTtlMinutes", 15, 1, 120);
        RECOVERY_COOLDOWN_MINUTES = b
                .comment("Minimum minutes between /recovery requests per account.",
                        "Задержка в минутах между запросами кода восстановления (защита от спама).")
                .defineInRange("recoveryCooldownMinutes", 15, 1, 720);
        EMAIL_REQUIRED = b
                .comment("Remind players in chat to bind an email after logging in.",
                        "Напоминать игроку привязать почту после успешного входа.")
                .define("requireEmail", false);
        b.pop();

        SPEC = b.build();
    }

    private UltraLoginConfig() {
    }
}
