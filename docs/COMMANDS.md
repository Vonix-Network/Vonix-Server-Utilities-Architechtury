# VSU Command Reference

Alphabetical reference for every command shipped with Vonix Server Utilities v1.6.0.

Every command goes through `PermissionGate`: if LuckPerms is installed the listed **node** is checked; if not, the listed **op level** is required. Console always passes.

## Categories

- [Movement](#movement) — `/home`, `/warp`, `/tpa`, `/back`, `/spawn`, teleport admin
- [Social](#social) — `/msg`, `/r`, `/ignore`, `/broadcast`, `/nick`
- [Utility](#utility) — `/hat`, `/repair`, `/seen`, `/playtime`, `/list`, inventory peeks
- [Admin](#admin) — `/heal`, `/fly`, `/god`, `/vanish`, `/gm`, `/vonixsu`
- [Moderation](#moderation) — `/ban`, `/mute`, `/kick`, `/warn`, `/banlist`
- [World](#world) — `/weather`, `/time`, `/day`, `/night`, `/lightning`, `/smite`
- [Venary](#venary) — `/link`, `/unlink`

---

## Movement

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/back` | `/back` | `vsu.command.back` | 0 | Return to your previous location (last teleport origin). |
| `/backdeath` | `/backdeath` | `vsu.command.back` | 0 | Return to where you last died. |
| `/delhome` | `/delhome <name>` | `vsu.command.sethome` | 0 | Delete one of your homes. |
| `/delwarp` | `/delwarp <name>` | `vsu.admin.warp` | 3 | Delete a server warp. |
| `/home` | `/home [name]` | `vsu.command.home` | 0 | Teleport to a home. With no argument, uses `home`. |
| `/homes` | `/homes` | `vsu.command.home` | 0 | List your homes. |
| `/setwarp` | `/setwarp <name>` | `vsu.admin.warp` | 3 | Create a server warp at your location. |
| `/sethome` | `/sethome [name]` | `vsu.command.sethome` | 0 | Save your current location as a home. |
| `/spawn` | `/spawn` | `vsu.command.spawn` | 0 | Teleport to world spawn. |
| `/setspawn` | `/setspawn` | `vsu.admin.setspawn` | 2 | Set world spawn to your location. |
| `/tp` | `/tp <player>` or `/tp <player> <target>` | `vsu.admin.teleport` | 2 | Teleport yourself or one player to another. |
| `/tpa` | `/tpa <player>` | `vsu.command.tpa` | 0 | Request to teleport to a player. |
| `/tpaccept` | `/tpaccept` | `vsu.command.tpa` | 0 | Accept a pending teleport request. |
| `/tpahere` | `/tpahere <player>` | `vsu.command.tpa` | 0 | Request a player teleport to you. |
| `/tpall` | `/tpall` | `vsu.admin.teleport` | 2 | Teleport all online players to you. |
| `/tpdeny` | `/tpdeny` | `vsu.command.tpa` | 0 | Deny a pending teleport request. |
| `/tphere` | `/tphere <player>` | `vsu.admin.teleport` | 2 | Teleport a player to you. |
| `/tppos` | `/tppos <x> <y> <z>` | `vsu.admin.teleport` | 2 | Teleport to coordinates. |
| `/warp` | `/warp <name>` | `vsu.command.warp` | 0 | Teleport to a server warp. |
| `/warps` | `/warps` | `vsu.command.warp` | 0 | List available warps. |

**Examples**

```
/sethome base
/home base
/tpa Steve
/warp shop
```

---

## Social

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/bc` | `/bc <message...>` | `vsu.admin.broadcast` | 2 | Alias for `/broadcast`. |
| `/broadcast` | `/broadcast <message...>` | `vsu.admin.broadcast` | 2 | Send a server-wide formatted message. |
| `/gc` | `/gc <message...>` | `vsu.admin.broadcast` | 2 | Alias for `/broadcast`. |
| `/ignore` | `/ignore <player>` | `vsu.command.message` | 0 | Toggle ignoring a player's private messages. |
| `/msg` | `/msg <player> <message...>` | `vsu.command.message` | 0 | Send a private message. |
| `/nick` | `/nick <nickname>` | `vsu.command.nick` | 0 | Set your own display nickname (color codes supported). |
| `/nick <player> <nick>` | `/nick <player> <nickname>` | `vsu.admin.nick` | 2 | Set another player's nickname. |
| `/r` | `/r <message...>` | `vsu.command.message` | 0 | Reply to your last `/msg` partner. |
| `/reply` | `/reply <message...>` | `vsu.command.message` | 0 | Alias for `/r`. |
| `/tell` | `/tell <player> <message...>` | `vsu.command.message` | 0 | Alias for `/msg`. |

**Example**

```
/msg Steve meet me at /warp shop
/broadcast Server restart in 10 minutes
```

---

## Utility

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/accsee` | `/accsee <player>` | `vsu.admin.peek` | 2 | Open a player's accessories / curio inventory. |
| `/afk` | `/afk` | `vsu.command.utility` | 0 | Toggle AFK status. |
| `/anvil` | `/anvil` | `vsu.command.utility` | 0 | Open a virtual anvil. |
| `/backsee` | `/backsee <player>` | `vsu.admin.peek` | 2 | Open a player's back-slot inventory. |
| `/clear` | `/clear [player]` | `vsu.command.utility` | 0 | Clear your (or a target's) inventory. |
| `/enderchest` | `/enderchest [player]` | `vsu.admin.peek` | 2 | Open your ender chest, or another player's. |
| `/ext` | `/ext [player]` | `vsu.admin.world` | 2 | Extinguish fire on a player. |
| `/getpos` | `/getpos` | `vsu.command.utility` | 0 | Show your current coordinates. |
| `/hat` | `/hat` | `vsu.command.utility` | 0 | Wear the item in your hand as a hat. |
| `/invsee` | `/invsee <player>` | `vsu.admin.peek` | 2 | Open a player's main inventory. |
| `/kit` | `/kit <name>` / `/kit reload` | `vsu.command.kit` | 0 | Claim a kit, or (admin) reload kits.json. |
| `/kits` | `/kits` | `vsu.command.kit` | 0 | List available kits. |
| `/lag` | `/lag` | `vsu.admin.lag` | 2 | Show server memory / thread / TPS info. |
| `/list` | `/list` | `vsu.command.utility` | 0 | List online players with count. |
| `/more` | `/more` | `vsu.command.utility` | 0 | Refill the stack in your hand to max size. |
| `/near` | `/near [radius]` | `vsu.command.utility` | 0 | List players within radius (default 100). |
| `/ping` | `/ping [player]` | `vsu.command.utility` | 0 | Show your ping, or another player's. |
| `/playtime` | `/playtime [player]` | `vsu.command.utility` | 0 | Show total playtime. |
| `/repair` | `/repair` | `vsu.command.utility` | 0 | Repair the item in your hand to full durability. |
| `/seen` | `/seen <player>` | `vsu.command.utility` | 0 | Show when a player was last online. |
| `/suicide` | `/suicide` | `vsu.command.utility` | 0 | Kill yourself. |
| `/whois` | `/whois <player>` | `vsu.command.utility` | 0 | Show display name, UUID, and ping for a player. |
| `/workbench` | `/workbench` | `vsu.command.utility` | 0 | Open a virtual crafting table. |

**Example**

```
/kit starter
/invsee Steve
/seen Steve
```

---

## Admin

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/feed` | `/feed [player]` | `vsu.admin.heal` | 2 | Restore food and saturation. |
| `/fly` | `/fly [player]` | `vsu.admin.fly` | 2 | Toggle creative flight. |
| `/gm` | `/gm <0\|1\|2\|3\|s\|c\|a\|sp> [player]` | `vsu.admin.gamemode` | 2 | Set gamemode (numeric or letter shortcut). |
| `/god` | `/god [player]` | `vsu.admin.god` | 2 | Toggle invulnerability. |
| `/heal` | `/heal [player]` | `vsu.admin.heal` | 2 | Restore full health. |
| `/vanish` | `/vanish [player]` | `vsu.admin.vanish` | 2 | Toggle invisibility from other players. |
| `/vonixsu` | `/vonixsu <version\|status\|reload\|feature ...>` | `vsu.admin.manage` | 3 | VSU root command — info and mod management. |
| `/vonixsu feature` | `/vonixsu feature <list\|enable\|disable\|reload\|status> [feature]` | `vsu.admin.manage` | 3 | Manage feature flags at runtime. |

**Example**

```
/heal Steve
/gm c
/vonixsu feature disable homes
```

---

## Moderation

See [MODERATION.md](MODERATION.md) for the full operator workflow, duration syntax, and audit-log queries.

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/ban` | `/ban <player> [reason...]` | `vsu.mod.ban` | 3 | Permanently ban a player. |
| `/banlist` | `/banlist [page]` | `vsu.mod.ban` | 3 | List active bans, 10 per page. |
| `/clearwarnings` | `/clearwarnings <player>` | `vsu.mod.warn` | 2 | Clear a player's active warnings (history retained for audit). |
| `/kick` | `/kick <player> [reason...]` | `vsu.mod.kick` | 2 | Disconnect a player; one-shot, logged to `punishments`. |
| `/mute` | `/mute <player> [reason...]` | `vsu.mod.mute` | 3 | Permanently mute a player from chat. |
| `/mutelist` | `/mutelist [page]` | `vsu.mod.mute` | 3 | List active mutes, 10 per page. |
| `/tempban` | `/tempban <player> <duration> [reason...]` | `vsu.mod.ban` | 3 | Ban for the given duration. Auto-kicks if online. |
| `/tempmute` | `/tempmute <player> <duration> [reason...]` | `vsu.mod.mute` | 3 | Mute for the given duration. Works on offline players. |
| `/unban` | `/unban <player>` | `vsu.mod.ban` | 3 | Lift the active ban (sets `active=0`, row retained). |
| `/unmute` | `/unmute <player>` | `vsu.mod.mute` | 3 | Lift the active mute. |
| `/warn` | `/warn <player> <reason...>` | `vsu.mod.warn` | 2 | Issue a warning. Persists in the audit log. |
| `/warnings` | `/warnings <player> [page]` | `vsu.mod.warn` | 2 | Show a player's warning history. |

**Duration syntax**: `30s`, `5m`, `2h`, `7d`, `4w`, `1mo`, `1y`, `perm`. Compose without spaces: `1d12h`, `7d6h30m`.

**Examples**

```
/tempban Steve 7d griefing in spawn
/tempmute Steve 1h chat spam
/warn Steve please keep chat in English
/unban Steve
/banlist 2
```

---

## World

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/day` | `/day` | `vsu.admin.world` | 2 | Set time to day (1000 ticks). |
| `/lightning` | `/lightning [player\|x y z]` | `vsu.admin.smite` | 2 | Strike lightning at a target or position. |
| `/night` | `/night` | `vsu.admin.world` | 2 | Set time to night (13000 ticks). |
| `/rain` | `/rain` | `vsu.admin.world` | 2 | Force rain weather. |
| `/smite` | `/smite <player>` | `vsu.admin.smite` | 2 | Strike lightning on a player. |
| `/storm` | `/storm` | `vsu.admin.world` | 2 | Force thunderstorm. |
| `/sun` | `/sun` | `vsu.admin.world` | 2 | Clear weather. |
| `/time` | `/time set <day\|night\|noon\|midnight>` / `/time add <ticks>` | `vsu.admin.world` | 2 | Set or advance world time. |
| `/weather` | `/weather <clear\|rain\|storm\|thunder>` | `vsu.admin.world` | 2 | Set weather. `thunder` is an alias of `storm`. |

**Example**

```
/weather clear
/time set day
/smite Steve
```

---

## Venary

| Command | Usage | Node | Op fallback | Description |
|---|---|---|---|---|
| `/link` | `/link` | `vsu.command.link` | 0 | Begin the link flow to associate your in-game account with Venary. |
| `/unlink` | `/unlink` | `vsu.command.link` | 0 | Disconnect your Venary link. |

---

## Related docs

- [PERMISSIONS.md](PERMISSIONS.md) — full permission node tree, LuckPerms group recipes
- [MODERATION.md](MODERATION.md) — duration syntax, escalation policy, audit queries
