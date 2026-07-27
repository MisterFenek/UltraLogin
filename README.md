![Mod banner](https://cdn.modrinth.com/data/cached_images/3cc2c5c84f9996bef19076a15da4f2e49b6874f2.png)

**UltraLogin** is a modern, lightweight, high-performance **100% server-side** authorization mod for Minecraft **1.21.X - 26.2 (NeoForge)** servers operating in `online-mode=false`. Inspired by legendary plugins like AuthMe, UltraLogin brings modern security standards, email recovery, and multi-database support to modern modded Minecraft servers, without any client-side mods.

## Features

* **Pre-Login Isolation (Sandbox):** Unauthenticated players cannot move, chat, use items, take damage, open containers, or execute unauthorized commands. 
* **Virtual Thread Async Engine:** All BCrypt password hashing, database operations, and SMTP email sending run off the main server thread on **Java 21 Virtual Threads**.
* **BCrypt Hashing:** Industry-standard password hashing with customizable work factors to resist offline cracking.
* **Smart IP Sessions:** Auto-login players returning from the same IP within a configurable time window (skips repetitive `/login`).
* **Anti-Bruteforce & Anti-Alt Protection:** 
  * Automatically bans malicious IP addresses after failed login attempts.
  * Restricts maximum registered accounts per IP to prevent multi-accounting.
* **OP Account IP Binding:** Bind operator (`/op`) accounts to specific IP addresses. Prevents administrative hijackings even if an OP password is compromised.
* **Email Password Recovery:** Integrated SMTP client (SSL & STARTTLS supported) allowing players to recover forgotten passwords via single-use 8-character codes with strict rate-limiting.
* **Flexible Database Backends:**
  * **SQLite** (Default) — Zero configuration required, automatic `.db` creation.
  * **MySQL / MariaDB** — High-performance connection pooling via **HikariCP**.
* **100% Customizable & Localized:** English (`en`) and Russian (`ru`) included out of the box. Fully customizable TOML message files and HTML/TXT email templates.

<details>
<summary>Commands & Permissions Reference</summary>

### Player Commands (Available to Everyone)
| Command | Aliases | Description |
| :--- | :--- | :--- |
| `/ul` | `/ulogin` | Show mod summary, your auth status, and available commands |
| `/register <password> <confirm>` | `/reg` | Register a new account |
| `/login <password>` | `/l` | Log in to an existing account |
| `/changepassword <old> <new>` | `/changepass` | Change account password |
| `/unregister <password>` | — | Delete your account (if enabled in config) |
| `/email add <email> <confirm>` | — | Bind a recovery email address |
| `/email change <old> <new>` | — | Update registered recovery email address |
| `/recovery <email>` | — | Request a password reset code via email |
| `/recovery confirm <code> <newPass>` | — | Reset password using the code received in email |

### Admin Commands (Requires OP Level 3)
| Command | Description |
| :--- | :--- |
| `/ul admin info` | View detailed system status (DB backend, sessions, email status) |
| `/ul admin info <nickname>` | View detailed account info (IPs, dates, email) |
| `/ul admin register <nickname> <password>` | Force-register an account |
| `/ul admin unregister <nickname>` | Delete a player's account and kick if online |
| `/ul admin changepassword <nickname> <newPass>` | Reset a player's password and invalidate sessions |
| `/ul admin accounts <ip/nickname>` | List all accounts registered or last seen from an IP |
| `/ul admin email <show\|set\|remove> <nick>` | Manage player's bound email |
| `/ul admin reload` | Reload configuration, messages, and email templates live |

</details>

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
