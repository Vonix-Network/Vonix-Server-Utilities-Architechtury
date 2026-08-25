# Vonix Server Utilities (VSU)

A server-side essentials mod for Minecraft — homes, warps, kits, teleport, social, admin tooling, moderation, and Venary site integration — built on **Architectury** for **Forge, NeoForge, and Fabric** across **four Minecraft versions**.

**Current version:** 1.7.1 · See [CHANGELOG.md](CHANGELOG.md) · License: All Rights Reserved (Vonix Network)

---

## What VSU does

- **Player QoL** — `/home`, `/warp`, `/kit`, `/tpa`, `/back`, `/spawn`, chat & messaging.
- **Admin toolkit** — vanish, god, fly, heal, peek (`/invsee`, `/enderchest`, `/backsee`, `/accsee`), weather/time control.
- **Moderation (v1.7.1)** — SQLite-backed `/tempban`, `/mute`, `/kick`, `/warn`, `/banlist` with duration parser, race-safe mute enforcement, and expiry sweeper.
- **LuckPerms-aware permissions (v1.6.0)** — every command has a `vsu.*` permission node, with graceful vanilla op-level fallback when LuckPerms is absent.
- **Venary site integration** — account linking (`/link`), periodic player sync, and automatic donation-rank → LuckPerms group sync.

---

## Supported versions

| MC Version | Forge | NeoForge | Fabric | Java |
|---|---|---|---|---|
| 1.18.2 | ✅ | — | ✅ | 17 |
| 1.19.2 | ✅ | — | ✅ | 17 |
| 1.20.1 | ✅ | — | ✅ | 17 |
| 1.21.1 | — | ✅ | ✅ | 21 |

Each MC version lives in its own Architectury template directory (`vonix_server_utils-<mc>-...-template/`) with shared `common/` source and loader-specific `fabric/`, `forge/`, or `neoforge/` modules. Source parity across versions is maintained by the porting workflow (`port.py`); wave-1 changes land in 1.21.1 first, then port to the older trees.

---

## Install

1. Install the matching Minecraft + loader (Forge / NeoForge / Fabric) for your server.
2. Drop the matching `vonix_server_utilities-<loader>-<version>.jar` into your server's `mods/` folder.
3. (Optional) Install **[LuckPerms](https://luckperms.net)** to use the full `vsu.*` permission tree. Without LuckPerms, VSU falls back to vanilla op levels — the mod still works, players just can't be granted individual commands without op.
4. Start the server once to generate `config/vonix_server_utilities.properties` and `config/vonix_server_utilities/data.db`.

VSU is server-side only. Clients do not need it installed.

---

## Quick-start commands

| Command | Use |
|---|---|
| `/sethome base` then `/home base` | Save and teleport to a personal home (limit configurable, default 5). |
| `/tpa <player>` → `/tpaccept` | Request a teleport to another player. |
| `/spawn`, `/back` | Go to spawn, or undo your last teleport / death. |
| `/warp <name>` (`/setwarp` is op) | Travel to a server-defined location. |
| `/tempban Steve 7d griefing` | Issue a 7-day ban with reason. See [docs/MODERATION.md](docs/MODERATION.md). |

Full command reference: **[docs/COMMANDS.md](docs/COMMANDS.md)**.

---

## Documentation

- **[docs/COMMANDS.md](docs/COMMANDS.md)** — every command, usage, permission node, op-fallback, example.
- **[docs/PERMISSIONS.md](docs/PERMISSIONS.md)** — full `vsu.*` permission tree and LuckPerms group recipes.
- **[docs/MODERATION.md](docs/MODERATION.md)** — duration syntax, escalation, audit, bypass nodes, restoring a wrongful ban.
- **[docs/GAP-ANALYSIS-v1.6.0.md](docs/GAP-ANALYSIS-v1.6.0.md)** — v1.6.0 scope vs. industry essentials baselines.
- **[docs/V1.6.0-SPEC.md](docs/V1.6.0-SPEC.md)** — authoritative implementation spec for the v1.6.0 release.
- **[CHANGELOG.md](CHANGELOG.md)** — release history (Keep-a-Changelog format).

---

## Features overview

**Movement** — `/home`, `/sethome`, `/delhome`, `/homes`, `/warp`, `/setwarp`, `/delwarp`, `/warps`, `/spawn`, `/setspawn`, `/back`, `/backdeath`, `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`. SQLite-backed, per-player limits, configurable cooldowns and TPA timeout.

**Social** — `/msg`, `/tell`, `/r`, `/reply`, `/ignore`, `/broadcast`, `/bc`, `/me`, `/nick`, `/near`, `/list`. Muted players are blocked from all chat-style commands automatically.

**Utility** — `/kit`, `/kits`, `/hat`, `/more`, `/repair`, `/clear`, `/enderchest`, `/workbench`, `/anvil`, `/afk`, `/ping`, `/playtime`, `/whois`, `/suicide`.

**Admin** — `/vanish`, `/god`, `/fly`, `/heal`, `/feed`, `/gm`, `/tp`, `/tphere`, `/tpall`, `/tppos`, `/invsee`, `/backsee`, `/accsee`, `/lag`, `/smite`. `/invsee` is live-editable; `/accsee` covers Curios on Forge/NeoForge and Trinkets on Fabric.

**Moderation (v1.6.0)** — `/tempban`, `/ban`, `/unban`, `/banlist`, `/tempmute`, `/mute`, `/unmute`, `/kick`, `/warn`, `/warnings`, `/clearwarnings`. Punishments persist in the same SQLite DB as homes/warps; expiry is swept every 60s; in-flight tempbans send a `tellraw` reason before the disconnect.

**World** — `/weather`, `/sun`, `/rain`, `/storm`, `/time`, `/day`, `/night`.

**Venary integration** — `/link` and `/unlink` bind a Minecraft identity to a Vonix Network site account. An HTTP client started from config periodically syncs linked players.

**Donation rank sync** — When LuckPerms is present, site-side donation ranks are mapped to LP groups and applied on join, refreshed live, and removed on expiry. Skipped entirely if LuckPerms is not installed.

---

## Configuration

Config file: `config/vonix_server_utilities.properties` (generated on first launch).

```properties
max_homes=5
tpa_timeout_seconds=120
death_back_delay_seconds=0
```

Subsystems can be toggled at runtime with `/feature enable|disable|list|reload|status <key>` — every feature category is gated by a `FeatureGate` key, so admins can disable moderation, Venary, or any feature group without uninstalling the mod.

Database: `config/vonix_server_utilities/data.db` (SQLite, WAL mode). VonixCore databases are auto-migrated on first launch.

---

## Building

VSU uses a separate Gradle project per MC version, all Architectury-based:

- `vonix_server_utils-1.18.2-fabric-forge-template/` — MC 1.18.2, Java 17
- `vonix_server_utils-1.19.2-fabric-forge-template/` — MC 1.19.2, Java 17
- `vonix_server_utils-1.20.1-fabric-forge-template/` — MC 1.20.1, Java 17
- `vonix_server_utils-1.21.1-fabric-neoforgetemplate/` — MC 1.21.1, Java 21

Build a single target manually:

```bash
cd vonix_server_utils-1.21.1-fabric-neoforgetemplate
./gradlew --no-daemon :common:build :neoforge:build :fabric:build
```

Or use the interactive multi-version build menu (auto-detects JDKs, writes logs to `build_logs/`):

```bash
python build-menu.py
```

JDK requirements: install JDK 17 (1.18.2/1.19.2/1.20.1) and JDK 21 (1.21.1) and point `JAVA_HOME` at the right one per target. The build menu handles JDK selection automatically.

---

## Versioning

VSU follows **Semantic Versioning** (`MAJOR.MINOR.PATCH`):

- **MAJOR** — breaking config / schema / API changes.
- **MINOR** — new features, no removals, no breaking schema (e.g. 1.5.2 → 1.6.0 added moderation).
- **PATCH** — bug fixes only.

Every release ships notes in **[CHANGELOG.md](CHANGELOG.md)** (Keep-a-Changelog format).

---

## License & credits

© Vonix Network. All Rights Reserved. Distribution of unmodified release jars from the official Vonix channels is permitted; redistribution of modified builds is not. Built and maintained by the Vonix Network team for vonix.network.

Bug reports, command requests, and moderation policy feedback: file against this repository or ping WeedMeister in the Vonix staff channels.
