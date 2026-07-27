# UltraLogin — Server-Side Auth for Minecraft 1.21.1

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange)
![Java Version](https://img.shields.io/badge/Java-21%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)

**UltraLogin** is a modern, lightweight, high-performance server-side authentication mod for offline-mode (`online-mode=false`) Minecraft servers running **NeoForge 1.21.1**. Inspired by AuthMe, it provides pre-login player isolation, BCrypt password security, IP-based session auto-login, anti-bruteforce bans, anti-alt registration limits, operator IP binding, and optional SMTP email password recovery.

It is **100% server-side**: no client-side mod or installation is required for joining players.

---

## Features

* **Pre-Login Isolation (Sandbox):** While unauthenticated, players cannot move, interact with blocks or entities, open containers, chat, run unauthorized commands, take damage, drop items, or pick up items. Their inventory and position are safely stashed and restored upon authentication or disconnect.
* **BCrypt Password Hashing:** Passwords are hashed using BCrypt with configurable cost factors. All crypto and database operations run on Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`) to keep the Minecraft main server loop completely stutter-free.
* **IP-Bound Sessions:** Players rejoining from the same IP address within a configurable window (default 10 minutes) automatically skip `/login`.
* **Anti-Bruteforce Defense:** Automatically issues temporary IP bans (default 10 minutes) after exceeding maximum failed login attempts (default 4 attempts).
* **Anti-Alt Protection:** Limits the number of registered accounts per IP address (default 2 accounts).
* **Admin / Operator IP Binding:** Allows server administrators to bind specific OP player accounts to designated IP addresses.
* **SMTP Email Password Recovery:** Players can attach an email address to their account and receive one-time 8-character recovery codes via SMTP (Jakarta Mail with SSL/STARTTLS support and customizable HTML/TXT templates).
* **Dual Database Support:** Native support for local **SQLite** (default zero-config) and high-concurrency **MySQL / MariaDB** via HikariCP connection pooling.
* **Multi-Language Support:** Bundled with English (`en`) and Russian (`ru`) localizations. Custom languages automatically generate template configuration files.

---

<details>
<summary>Commands & Usage</summary>

### Player Commands
* `/register <password> <confirm>` (alias `/reg`) — Register a new account.
* `/login <password>` (alias `/l`) — Log into an existing account.
* `/changepassword <oldPassword> <newPassword>` (alias `/changepass`) — Change your password.
* `/unregister <password>` — Delete your account (if enabled in config).
* `/email add <email> <confirm>` — Bind an email address to your account.
* `/email change <oldEmail> <newEmail>` — Update your bound email address.
* `/recovery <email>` — Request a password recovery code via email.
* `/recovery confirm <code> <newPassword>` — Reset your password using a recovery code.
* `/ultralogin` (alias `/ul`, `/ulogin`) — View mod information and your current authentication status.

### Admin Commands (Requires OP / Permission Level 3)
* `/ul admin info [nickname]` — View database and account details for a player or server system status.
* `/ul admin register <nickname> <password>` — Force-register an account.
* `/ul admin unregister <nickname>` — Force-delete an account.
* `/ul admin changepassword <nickname> <newPassword>` — Reset a player's password.
* `/ul admin accounts <nickname|ip>` — List all accounts linked to an IP address or player.
* `/ul admin email show|set|remove <nickname> [email]` — Manage player email bindings.
* `/ul admin reload` — Reload configuration, localization files, and email templates.

</details>

---

<details>
<summary>Configuration Overview</summary>

The configuration file is located at `config/ultralogin-server.toml`. It uses `ModConfig.Type.COMMON` to prevent database and SMTP credentials from being synced to joining clients.

```toml
[general]
language = "en"
authTimeoutSeconds = 30
minPasswordLength = 6
maxPasswordLength = 64
usernameRegex = "^[A-Za-z0-9_]{3,16}$"
allowUnregister = true
bcryptCost = 12

[session]
enabled = true
sessionMinutes = 10

[security]
maxLoginAttempts = 4
ipBanMinutes = 10
maxAccountsPerIp = 2
adminIpBindings = []

[database]
type = "sqlite" # "sqlite" or "mysql"
host = "localhost"
port = 3306
name = "ultralogin"
user = "root"
password = ""
poolSize = 8

[email]
enabled = false
smtpHost = "smtp.gmail.com"
smtpPort = 465
ssl = true
starttls = false
username = ""
password = ""
from = ""
projectName = "UltraLogin Server"
recoveryCodeTtlMinutes = 15
recoveryCooldownMinutes = 15
requireEmail = false
```

</details>

---

## Building from Source

### Prerequisites
* **JDK 21** or newer
* **Git**

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/MisterFenek/UltraLogin.git
   cd UltraLogin
   ```

2. Build the project using the Gradle Wrapper:
   * **On Linux / macOS:**
     ```bash
     ./gradlew build
     ```
   * **On Windows (PowerShell / CMD):**
     ```cmd
     .\gradlew.bat build
     ```

3. Run tests (optional):
   ```bash
   ./gradlew test
   ```

The compiled artifact will be generated at `build/libs/ultralogin-1.0.0.jar`.

---

## License

UltraLogin is released under the **MIT License**. See the [LICENSE](LICENSE) file for full details.  
Created by **mrfenek**.
