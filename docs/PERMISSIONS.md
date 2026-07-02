# VSU Permissions Reference

Canonical permission node tree for Vonix Server Utilities v1.6.1. Every command in [COMMANDS.md](COMMANDS.md) is gated by exactly one node from this document.

## Table of contents

- [Naming convention](#naming-convention)
- [Full node table](#full-node-table)
- [Bypass nodes](#bypass-nodes)
- [Group recipes](#group-recipes)
  - [default-player](#default-player)
  - [trusted](#trusted)
  - [builder](#builder)
  - [mod](#mod)
  - [admin](#admin)
  - [owner](#owner)
- [Pitfalls](#pitfalls)

## Naming convention

```
vsu.<category>.<command>[.<scope>]
```

| Category | Meaning |
|---|---|
| `vsu.command.*` | Things any player can run on themselves (homes, warp, tpa, kits, messaging). |
| `vsu.admin.*` | Staff utilities (heal, fly, vanish, teleport-other, world manipulation). |
| `vsu.mod.*` | Moderation actions (ban, mute, kick, warn). |
| `vsu.bypass.*` | Exemptions from a restriction (cooldown, mute enforcement, ban enforcement). |

`PermissionGate.check(source, node, opFallback)` runs the check. **LP grant and op-fallback form a UNION** (v1.6.1+): the source passes if it holds `node` in LuckPerms **OR** meets the vanilla `opFallback` op-level. Console always passes regardless. This means an unconfigured LP install still lets ops use `vsu.admin.*`/`vsu.mod.*` commands, and every player still gets the `opFallback=0` commands out of the box — while explicit LP grants continue to open commands to non-op players.

> **v1.6.0 pitfall.** v1.6.0 shipped LP as authoritative (LP grant, no op fallback). If LP was installed without also running the `default-player` recipe below, every non-console source was denied every command. v1.6.1 restores the union semantics — upgrade if you're on 1.6.0.

## Full node table

Sorted alphabetically by command. Sub-variants (e.g. `/nick self` vs `/nick <player>`) are listed as separate rows because they have different nodes.

| Command | Node | Op fallback |
|---|---|---|
| `/accsee` | `vsu.admin.peek` | 2 |
| `/afk` | `vsu.command.utility` | 0 |
| `/anvil` | `vsu.command.utility` | 0 |
| `/back`, `/backdeath` | `vsu.command.back` | 0 |
| `/backsee` | `vsu.admin.peek` | 2 |
| `/ban`, `/tempban`, `/unban`, `/banlist` | `vsu.mod.ban` | 3 |
| `/bc`, `/broadcast`, `/gc` | `vsu.admin.broadcast` | 2 |
| `/clear` | `vsu.command.utility` | 0 |
| `/clearwarnings` | `vsu.mod.warn` | 2 |
| `/day`, `/night`, `/rain`, `/storm`, `/sun`, `/time`, `/weather` | `vsu.admin.world` | 2 |
| `/delhome`, `/sethome` | `vsu.command.sethome` | 0 |
| `/delwarp`, `/setwarp` | `vsu.admin.warp` | 3 |
| `/enderchest` (self) | `vsu.command.utility` | 0 |
| `/enderchest <other>` | `vsu.admin.peek` | 2 |
| `/ext` | `vsu.admin.world` | 2 |
| `/feed`, `/heal` | `vsu.admin.heal` | 2 |
| `/fly` | `vsu.admin.fly` | 2 |
| `/getpos` | `vsu.command.utility` | 0 |
| `/gm` | `vsu.admin.gamemode` | 2 |
| `/god` | `vsu.admin.god` | 2 |
| `/hat` | `vsu.command.utility` | 0 |
| `/home`, `/homes` | `vsu.command.home` | 0 |
| `/ignore`, `/msg`, `/r`, `/reply`, `/tell` | `vsu.command.message` | 0 |
| `/invsee` | `vsu.admin.peek` | 2 |
| `/kick` | `vsu.mod.kick` | 2 |
| `/kit`, `/kits` | `vsu.command.kit` | 0 |
| `/lag` | `vsu.admin.lag` | 2 |
| `/lightning`, `/smite` | `vsu.admin.smite` | 2 |
| `/link`, `/unlink` | `vsu.command.link` | 0 |
| `/list` | `vsu.command.utility` | 0 |
| `/more` | `vsu.command.utility` | 0 |
| `/mute`, `/tempmute`, `/unmute`, `/mutelist` | `vsu.mod.mute` | 3 |
| `/near` | `vsu.command.utility` | 0 |
| `/nick` (self) | `vsu.command.nick` | 0 |
| `/nick <player>` | `vsu.admin.nick` | 2 |
| `/ping` | `vsu.command.utility` | 0 |
| `/playtime` | `vsu.command.utility` | 0 |
| `/repair` | `vsu.command.utility` | 0 |
| `/seen` | `vsu.command.utility` | 0 |
| `/setspawn` | `vsu.admin.setspawn` | 2 |
| `/spawn` | `vsu.command.spawn` | 0 |
| `/suicide` | `vsu.command.utility` | 0 |
| `/tp`, `/tphere`, `/tpall`, `/tppos` | `vsu.admin.teleport` | 2 |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` | `vsu.command.tpa` | 0 |
| `/vanish` | `vsu.admin.vanish` | 2 |
| `/vonixsu`, `/vonixsu feature ...` | `vsu.admin.manage` | 3 |
| `/warn`, `/warnings` | `vsu.mod.warn` | 2 |
| `/warp`, `/warps` | `vsu.command.warp` | 0 |
| `/whois` | `vsu.command.utility` | 0 |
| `/workbench` | `vsu.command.utility` | 0 |

## Bypass nodes

Bypass nodes are **opt-in exemptions**. They are never required to use a command — they remove a restriction on someone who can.

| Node | Effect |
|---|---|
| `vsu.bypass.cooldown.home` | Ignore the cooldown between `/home` teleports. |
| `vsu.bypass.cooldown.tpa` | Ignore the cooldown between `/tpa` requests. |
| `vsu.bypass.mute` | Holder's chat is never cancelled by the mute hook, even if a `MUTE` row exists for them. |
| `vsu.bypass.ban` | Holder is exempt from the ban login check. **Do not grant casually** — see [Pitfalls](#pitfalls). |

## Group recipes

The recipes below are reference defaults. Adjust to your server's policy.

### default-player

Granted to everyone on first login. Covers basic player commands.

- Inherits: `default` (LuckPerms built-in)
- Granted:
  - `vsu.command.home`
  - `vsu.command.sethome`
  - `vsu.command.tpa`
  - `vsu.command.back`
  - `vsu.command.warp`
  - `vsu.command.kit`
  - `vsu.command.spawn`
  - `vsu.command.nick`
  - `vsu.command.message`
  - `vsu.command.utility`
  - `vsu.command.link`

```
lp creategroup default-player
lp group default-player parent add default
lp group default-player permission set vsu.command.home true
lp group default-player permission set vsu.command.sethome true
lp group default-player permission set vsu.command.tpa true
lp group default-player permission set vsu.command.back true
lp group default-player permission set vsu.command.warp true
lp group default-player permission set vsu.command.kit true
lp group default-player permission set vsu.command.spawn true
lp group default-player permission set vsu.command.nick true
lp group default-player permission set vsu.command.message true
lp group default-player permission set vsu.command.utility true
lp group default-player permission set vsu.command.link true
```

### trusted

Verified members. Same as `default-player` plus cooldown bypasses.

- Inherits: `default-player`
- Granted:
  - `vsu.bypass.cooldown.home`
  - `vsu.bypass.cooldown.tpa`

```
lp creategroup trusted
lp group trusted parent add default-player
lp group trusted permission set vsu.bypass.cooldown.home true
lp group trusted permission set vsu.bypass.cooldown.tpa true
```

### builder

Trusted plus world-shaping utilities.

- Inherits: `trusted`
- Granted:
  - `vsu.admin.fly`
  - `vsu.admin.gamemode`
  - `vsu.admin.heal`
  - `vsu.admin.teleport`

```
lp creategroup builder
lp group builder parent add trusted
lp group builder permission set vsu.admin.fly true
lp group builder permission set vsu.admin.gamemode true
lp group builder permission set vsu.admin.heal true
lp group builder permission set vsu.admin.teleport true
```

### mod

Moderation staff. Inherits builder utilities so they can investigate.

- Inherits: `builder`
- Granted:
  - `vsu.mod.kick`
  - `vsu.mod.warn`
  - `vsu.mod.mute`
  - `vsu.mod.ban`
  - `vsu.admin.peek`
  - `vsu.admin.vanish`
  - `vsu.admin.broadcast`
- Denied (explicit, for clarity):
  - `vsu.bypass.ban`
  - `vsu.bypass.mute`

```
lp creategroup mod
lp group mod parent add builder
lp group mod permission set vsu.mod.kick true
lp group mod permission set vsu.mod.warn true
lp group mod permission set vsu.mod.mute true
lp group mod permission set vsu.mod.ban true
lp group mod permission set vsu.admin.peek true
lp group mod permission set vsu.admin.vanish true
lp group mod permission set vsu.admin.broadcast true
lp group mod permission set vsu.bypass.ban false
lp group mod permission set vsu.bypass.mute false
```

### admin

Server admins. Full world + nick + warp authority.

- Inherits: `mod`
- Granted:
  - `vsu.admin.world`
  - `vsu.admin.smite`
  - `vsu.admin.god`
  - `vsu.admin.nick`
  - `vsu.admin.warp`
  - `vsu.admin.setspawn`
  - `vsu.admin.lag`

```
lp creategroup admin
lp group admin parent add mod
lp group admin permission set vsu.admin.world true
lp group admin permission set vsu.admin.smite true
lp group admin permission set vsu.admin.god true
lp group admin permission set vsu.admin.nick true
lp group admin permission set vsu.admin.warp true
lp group admin permission set vsu.admin.setspawn true
lp group admin permission set vsu.admin.lag true
```

### owner

Top-level. Adds VSU management and ban-bypass.

- Inherits: `admin`
- Granted:
  - `vsu.admin.manage`
  - `vsu.bypass.ban`
  - `vsu.bypass.mute`

```
lp creategroup owner
lp group owner parent add admin
lp group owner permission set vsu.admin.manage true
lp group owner permission set vsu.bypass.ban true
lp group owner permission set vsu.bypass.mute true
```

Assigning a player to a group:

```
lp user Steve parent add trusted
lp user Steve parent set owner
```

## Pitfalls

- **Bypass nodes are sticky.** `vsu.bypass.ban` makes the holder permanently un-bannable while the node is set. Grant it to a per-incident temporary group, then remove (see [MODERATION.md § LP integration](MODERATION.md#luckperms-integration)).
- **v1.6.0 lockout bug (fixed in v1.6.1).** v1.6.0 shipped LP as authoritative — when LP was present, op-level was never consulted, so unset nodes read as "deny" for every non-console source. The result: installing LP without running the `default-player` recipe locked every player out of every command. Upgrade to v1.6.1 or later. If you're stuck on 1.6.0, either run the recipes below or hotpatch with the wildcard grants documented in the next bullet.
- **Wildcard hotpatch for unconfigured LP installs.** LuckPerms treats `foo.bar.*` as a proper wildcard node during resolution — the exact node the mod queries reads as `true` when a wildcard parent is granted. Three lines cover the whole tree without touching per-command config:
  ```
  lp group default permission set vsu.command.* true
  lp group admin   permission set vsu.admin.*   true
  lp group admin   permission set vsu.mod.*     true
  ```
  Adjust `admin` to your staff group. Do NOT wildcard `vsu.bypass.*` on any group — bypass nodes are opt-in exemptions and should stay per-user.
- **PermissionGate is a UNION, not a fallback chain (v1.6.1+).** `PermissionGate.check()` returns true if the source holds `node` in LuckPerms OR meets the `opFallback` op-level — either alone is sufficient. This means:
  - Op-2 staff keep working the moment they OP, even with LP installed and no VSU nodes granted.
  - Non-op players still get every `opFallback=0` command by default.
  - Explicit LP grants extend commands to non-op players (the whole point of installing LP).
  - The only way to REVOKE an op's access to a command is to de-op them (or use LP's explicit-deny nodes on top).
- **`vsu.command.utility` is intentionally broad.** It gates a dozen low-risk player commands (`/hat`, `/repair`, `/seen`, `/playtime`, `/list`, etc.). Split it into per-command nodes by adding `vsu.command.utility.*` overrides in your own group config if you need finer control — `PermissionGate` will accept LuckPerms wildcard inheritance.
- **Console always passes.** Scripts and `function`/command-block calls bypass `PermissionGate` entirely. Don't rely on permission nodes for security against command blocks.

## Related docs

- [COMMANDS.md](COMMANDS.md) — full command reference
- [MODERATION.md](MODERATION.md) — moderation workflows
