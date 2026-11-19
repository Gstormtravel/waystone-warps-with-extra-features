# WaystoneWarps (Enhanced & Hardened Edition)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B%20%2F%2026.2%2B-blue?logo=minecraft)](https://papermc.io)
[![Paper / Purpur](https://img.shields.io/badge/Server-Paper%20%7C%20Purpur-informational?logo=curseforge)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Geyser / Floodgate](https://img.shields.io/badge/CrossPlay-Geyser%20%26%20Floodgate-2ba84a)](https://geysermc.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An enterprise-grade, high-performance teleportation system for Paper/Purpur (1.21+ / 26.2+) Minecraft servers, featuring native Bedrock support, anti-corruption database protection, automated backups, and advanced waystone management.

---

## 🌟 Credits & Provenance

This repository is an optimized and hardened fork of:
* **Upstream Fork:** [Gaming-pandatje/waystone-warps-with-extra-features](https://github.com/Gaming-pandatje/waystone-warps-with-extra-features)
* **Original Project:** [mizarc/waystone-warps](https://github.com/mizarc/waystone-warps) by [mizarc](https://github.com/mizarc).

All original intellectual property rights and core concepts belong to their respective authors. This fork maintains compatibility while bringing critical fixes for production, high-concurrency, and cross-play environments.

---

## 🚀 What's New / Improvements Over Upstream

This hardened edition introduces substantial bug fixes, structural improvements, and concurrency/stability optimizations:

### 1. 🛡️ SQLite Hardening & Anti-Corruption Engine
* **WAL Mode & Single-Connection Pool:** Upgraded SQLite connection options to single-writer mode (`maxConnections(1)`) with `PRAGMA journal_mode=WAL;`, `PRAGMA synchronous=NORMAL;`, and `PRAGMA busy_timeout=5000;`, eliminating `SQLITE_BUSY` database lock crashes under concurrent writes.
* **Automatic Rotating Backups:** Backs up `waystonewarps.db` (along with active `-wal` and `-shm` journals) on plugin startup to `backups/waystonewarps-YYYY-MM-DD_HH-mm-ss.db`, strictly retaining the 5 most recent snapshots to safeguard server disk space.
* **Integrity Self-Healing (`PRAGMA integrity_check`):** Validates database integrity at boot. In the event of file corruption, the damaged database is quarantined (`waystonewarps.db.corrupted-<timestamp>`) and the system automatically restores and tests the latest valid backup.
* **Referential Orphan Sanitization:** Performs safe, transactional cleanup of orphaned records (`discoveries`, `whitelist`, `co_owners`, `warp_notifications` pointing to deleted warps, and unlinked group IDs) on startup.
* **Graceful Shutdown Checkpoint:** Forces `PRAGMA wal_checkpoint(TRUNCATE);` inside `onDisable()` to consolidate WAL journals into the primary database file before pool shutdown.
* **Consistent State Updates:** Ensures that disk operations (`INSERT`, `UPDATE`, `DELETE`) succeed before updating in-memory cache structures, logging explicit SQL errors and preventing state desynchronization.

### 2. 📱 Full Bedrock / Geyser / Floodgate Integration
* **Cumulus Form Support:** Replaced AnvilGUI dependencies with native Geyser/Cumulus `CustomForm` dialogs for Bedrock players on group creation, group renaming, and co-owner management, eliminating input stalls and crashes on Bedrock clients.
* **Safe Profile Fallbacks:** Null-safe handling (`name ?: "Unknown"`) for Bedrock player heads, skulls, and offline profiles across all GUI views, preventing lore crashes.

### 3. 🖥️ GUI & Pagination Bug Fixes
* **21+ Warps Layout Fix:** Resolved a double-offset bug in `WarpMenu.kt` where pages beyond the first were pushed out of view (`Slot.fromXY(1, 2)` corrected to `Slot.fromXY(0, 0)`).
* **Paper Main-Thread Scheduler:** Replaced unsafe asynchronous threads (`Thread.sleep`) in `WarpIconMenu.kt` with synchronous Bukkit scheduled tasks to safely update cursor items without triggerring Paper concurrency exceptions.

### 4. 🧰 Waystone Lifecycle & Gameplay Enhancements
* **Move Tool Consumption & Safety:** Properly cancels block placement (`event.isCancelled = true`), clears the movement tool from the player hand upon relocation, and enforces co-owner permissions (`canRelocate`).
* **Destruction Cleanups:** Prevents double destruction triggers on two-block high waystones, safely drops and resets co-owner registrations (`coOwnerRepository.removeByWarp`), and sweeps dropped key tools on player death (`removeIf { isKeyItem(it) }`).
* **Non-Destructive Teleport Landing:** `clearArea()` now protects the waystone structure base itself, preventing landing clear routines from destroying the waystone block.
* **Safe Material Validation:** Protects against invalid material crashes when interacting with waystone bases using `runCatching { Material.valueOf(...) }` and prevents unnecessary consumption when items match the current base skin.

---

## 📋 Requirements

* **Server Software:** Paper, Purpur, or compatible fork (Minecraft 1.21+ / 26.2+)
* **Java:** JDK 21 or Java 25
* **Optional / Recommended:** [Floodgate](https://geysermc.org) & [Geyser](https://geysermc.org) for Bedrock cross-play, [Vault](https://www.spigotmc.org/resources/vault.34315/) for economy support.

---

## 🛠️ Building from Source

```bash
# Clone the repository
git clone https://github.com/Gstormtravel/waystone-warps-with-extra-features.git
cd waystone-warps-with-extra-features

# Build with Gradle
./gradlew clean build
```

The compiled plugin jar will be located at:
```
build/libs/waystone-warps-with-extra-features.jar
```

---

## 📥 Installation

1. Download the latest release from the [Releases](https://github.com/Gstormtravel/waystone-warps-with-extra-features/releases) page.
2. Place `waystone-warps-with-extra-features.jar` into your server's `plugins/` directory.
3. Restart or start your server.
4. Enjoy rock-solid, production-ready waystone teleportation!

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
