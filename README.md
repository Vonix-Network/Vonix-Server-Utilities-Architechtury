# Vonix Server Utilities — Monorepo

**Current version: 1.2.0**

Multi-version Architectury-based server utility mod for Minecraft.

## Supported Versions

| MC Version | Platform    | Java |
|-----------|-------------|------|
| 1.18.2    | Fabric + Forge | 17 |
| 1.19.2    | Fabric + Forge | 17 |
| 1.20.1    | Fabric + Forge | 17 |
| 1.21.1    | Fabric + NeoForge | 21 |

## Features

- `/home`, `/sethome`, `/delhome`, `/homes` — Per-player homes (SQLite-backed, configurable limit)
- `/warp`, `/setwarp`, `/delwarp`, `/warps` — Server warps (op-only creation)
- `/kit`, `/kits` — Predefined item kits with cooldowns
- `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` — TPA request system
- `/back`, `/backdeath` — Return to last teleport or death location
- `/spawn`, `/setspawn` — Global spawn management
- `/tp`, `/tphere`, `/tpall`, `/tppos` — Admin teleport commands
- `/weather`, `/sun`, `/rain`, `/storm`, `/time`, `/day`, `/night` — World commands
- `/fly`, `/god`, `/vanish`, `/heal`, `/feed`, `/gm` — Admin tools
- `/nick`, `/msg`, `/r`, `/ignore`, `/ping`, `/near`, `/whois` — Player utilities
- `/afk`, `/broadcast`, `/list`, `/playtime`, `/suicide` — Misc
- `/hat`, `/more`, `/repair`, `/clear`, `/invsee`, `/enderchest`, `/workbench`, `/anvil`
- `/invsee <player>` — Live view/edit of a player's inventory (Main/Hotbar/Armor/Offhand)
- `/backsee <player>` — View the contents of backpacks in a player's inventory, ender chest, curio/trinket slots, and armor
- `/accsee <player>` — View a player's accessories (Curios on Forge/NeoForge, Trinkets on Fabric)
- `/vonixsu version|status|reload` — Mod info

### Donation ranks & site integration (1.2.0+)

- `/link`, `/unlink` — Bind a Minecraft identity to a Venary site account
- `/feature enable|disable|list|reload|status` — Toggle subsystems against server-side config at runtime
- **Donation rank sync** — Auto-applies/removes LuckPerms groups on join, live, and on expiry.
  Requires [LuckPerms](https://luckperms.net) (optional dependency, `[5.0,)`); rank sync is
  skipped entirely if LuckPerms is not installed.
- **Venary integration** — Periodic player sync over an HTTP layer brought up from config
  on server start and shut down cleanly on stop.

## Building

Use the interactive build menu (requires Python 3.9+):

```bash
python build-menu.py
```

The build menu auto-detects your installed JDKs and selects the correct version for each
Minecraft target. Build logs are saved to `build_logs/`.

## Database

Data is stored in `config/vonix_server_utilities/data.db` (SQLite, WAL mode).
Automatic migration from VonixCore databases is attempted on first launch.

## Configuration

On first launch, `config/vonix_server_utilities.properties` is created with defaults:

```properties
max_homes=5
tpa_timeout_seconds=120
death_back_delay_seconds=0
```
