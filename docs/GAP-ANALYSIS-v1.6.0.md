# VSU Command-Surface Gap Analysis (v1.6.0)

**Author:** Wave-1 Subagent C (research-only)
**Scope:** Compare VSU's current + wave-1 command surface against the industry
baseline of essential-commands mods/plugins, identify gaps, and produce a
prioritised "what to add in v1.6.1" shortlist.
**This document does NOT modify source.** It is input for the parent agent's
next dispatch decision.

Baselines compared:

1. **EssentialsX** (Bukkit/Paper) — the de-facto reference, 161 commands.
   Source: `essinfo.xeya.me/commands.html`, `essentialsx.net/commands`.
2. **FTB Essentials** (Fabric/NeoForge, modern) — what most modded servers
   ship today. Source: `docs.feed-the-beast.com/.../Essentials/Commands`.
3. **Essential Commands** (John-Paul-R, Fabric — often miscalled "Cyber's
   Essentials" in NeoForge ports). Source: `github.com/John-Paul-R/Essential-Commands`,
   Modrinth listing. Adds tpa/home/warp/spawn/back/nick/rtp + workbench.
4. **Forge Essentials** (legacy 1.7→1.12 reference for completeness).
5. **KubeJS Essentials** — not a command mod; mostly a scripting surface;
   omitted from per-command table but informs the "scripting/sudo" recommendation.

---

## 1. Command coverage matrix

Legend: ✅ exists · ❌ missing · ➖ N/A · 🆕 added in v1.6.0 wave-1 (moderation).
"VSU" column reflects the parent's inventory PLUS wave-1's moderation additions.

| Command (canonical) | VSU | EssX | FTB Ess | Essential Commands | Priority |
|---|---|---|---|---|---|
| /home, /sethome, /delhome, /homes | ✅ | ✅ | ✅ | ✅ | — |
| /warp, /setwarp, /delwarp, /warps | ✅ | ✅ | ✅ | ✅ | — |
| /spawn, /setspawn | ✅ | ✅ | ✅ | ✅ | — |
| /back, /backdeath | ✅ | ✅ | ✅ | ✅ | — |
| /tpa, /tpahere, /tpaccept, /tpdeny | ✅ | ✅ | ✅ | ✅ | — |
| /tp, /tphere, /tpall, /tppos | ✅ | ✅ | ✅ (partial) | ➖ | — |
| **/tpaall** (request all → you) | ❌ | ✅ | ❌ | ❌ | P3 |
| **/tpoffline** (TP to offline player's logout) | ❌ | ✅ | ❌ | ❌ | P2 |
| **/rtp** / /wild (random teleport) | ❌ | ✅ | ✅ | ✅ | **P0** |
| **/jump** (TP to looked-at block) | ❌ | ❌ | ✅ | ❌ | P2 |
| **/top** (TP to highest block in column) | ❌ | ✅ | ❌ | ❌ | P2 |
| **/bottom** (TP to lowest block) | ❌ | ✅ | ❌ | ❌ | P3 |
| /kit, /kits | ✅ | ✅ | ✅ | ✅ | — |
| /nick | ✅ | ✅ | ✅ | ✅ | — |
| /hat | ✅ | ✅ | ✅ | ❌ | — |
| /more | ✅ | ✅ | ❌ | ❌ | — |
| /repair | ✅ | ✅ | ❌ | ❌ | — |
| /clear (inv) | ✅ | ✅ | ❌ | ❌ | — |
| /feed, /heal | ✅ | ✅ | ✅ | ❌ | — |
| /god | ✅ | ❌ | ✅ | ❌ | — |
| /fly | ✅ | ✅ | ✅ | ❌ | — |
| **/speed** (walk/fly speed multiplier) | ❌ | ✅ | ✅ | ❌ | **P1** |
| /ext (extinguish) | ✅ | ✅ | ✅ | ❌ | — |
| /vanish | ✅ | ✅ (essx-extra) | ❌ | ❌ | — |
| /invsee, /enderchest, /backsee, /accsee | ✅ | ✅ | ✅ (invsee/echest) | ❌ | — |
| /workbench (anvil/sp etc.) | ✅ | ✅ (`/open …`) | ❌ | ✅ | — |
| **/disposal**, /trash, /trashcan | ❌ | ✅ | ✅ (`/trashcan`) | ❌ | **P0** |
| **/condense** / /compact | ❌ | ✅ | ❌ | ❌ | P2 |
| /msg, /tell, /r, /reply, /ignore | ✅ | ✅ | ✅ | ➖ | — |
| **/mail** (send/read/clear/sendall, offline) | ❌ | ✅ | ❌ | ❌ | **P0** |
| **/me** (action emote) | ❌ | ✅ | ❌ | ❌ | P2 |
| **/afk** (toggle AFK status) | ❌ | ✅ | ❌ | ❌ | **P0** |
| **/msgtoggle** / **/tptoggle** (privacy) | ❌ | ✅ | ❌ | ❌ | P1 |
| /broadcast, /bc, /gc (chat util) | ✅ | ✅ | ✅ | ❌ | — |
| **/motd** (server message of the day) | ❌ | ✅ (`/motd`) | ❌ | ❌ | **P1** |
| **/rules** (server rules pages) | ❌ | ✅ | ❌ | ❌ | **P0** |
| **/info** / /news (server info pages) | ❌ | ✅ | ❌ | ❌ | P1 |
| **/helpme** / /helpop (request staff) | ❌ | ✅ | ❌ | ❌ | **P1** |
| /weather, /time, /day, /night, /noon, /midnight, /sun, /rain, /storm, /thunder | ✅ | ✅ | ✅ | ❌ | — |
| /lightning, /smite | ✅ | ✅ | ❌ | ❌ | — |
| /gc, /lag, /ping | ✅ | ✅ (aliased gc/tps/uptime/mem) | ❌ | ❌ | — |
| **/tps** (explicit ticks-per-second) | ⚠️ as alias? | ✅ | ❌ | ❌ | P1 |
| **/uptime** (explicit) | ⚠️ as alias? | ✅ | ❌ | ❌ | P2 |
| /list, /near, /seen, /whois, /status, /getpos, /playtime | ✅ | ✅ | ✅ | ❌ | — |
| /version, /vonixsu, /reload, /feature, /enable, /disable, /set | ✅ | ✅ (essver/reload) | ➖ | ➖ | — |
| /link, /unlink (Venary) | ✅ | ✅ (Discord) | ❌ | ❌ | — |
| /suicide (/s) | ✅ | ✅ | ❌ | ❌ | — |
| 🆕 /ban, /tempban, /unban, /banlist | 🆕 | ✅ | ❌ | ❌ | (wave-1) |
| 🆕 /mute, /tempmute, /unmute, /mutelist | 🆕 | ✅ | ✅ (basic) | ❌ | (wave-1) |
| 🆕 /kick | 🆕 | ✅ | ❌ | ❌ | (wave-1) |
| 🆕 /warn, /warnings, /clearwarnings | 🆕 | ❌ (3rd-party) | ❌ | ❌ | (wave-1) |
| **/sudo** (run command as another player) | ❌ | ✅ | ❌ | ❌ | P2 |
| **/editsign** | ❌ | ✅ | ❌ | ❌ | P3 |
| **/firework** (apply meta to stack) | ❌ | ✅ | ❌ | ❌ | P3 |
| **/recipe** (show crafting recipe) | ❌ | ✅ | ❌ | ❌ | P3 (REI/JEI overlap) |
| **/butcher** / /remove (entity sweep) | ❌ | ✅ | ❌ | ❌ | P2 |
| **/spawnmob** | ❌ | ✅ | ❌ | ❌ | P3 (vanilla `/summon` covers) |
| **/kickall** | ❌ | ✅ | ❌ | ❌ | P2 |
| **/op /deop** | ➖ (vanilla) | ❌ | ❌ | ❌ | DO NOT ADD |
| **/jail, /setjail, /togglejail, /jails** | ❌ | ✅ | ❌ | ❌ | DO NOT ADD (own subsystem) |
| /spawner edit | ❌ | ✅ | ❌ | ❌ | DO NOT ADD (mod territory) |
| /pay /balance /sell /worth /economy | ❌ | ✅ | ❌ | ❌ | DO NOT ADD (economy out of scope) |
| /book editing | ❌ | ✅ | ❌ | ❌ | DO NOT ADD |
| /kittycannon, /nuke, /beezooka | ❌ | ✅ | ❌ | ❌ | DO NOT ADD (gag commands) |
| /powertool | ❌ | ✅ | ❌ | ❌ | DO NOT ADD (anti-grief risk) |

> Note on /tps and /uptime: VSU's `/gc` and `/lag` likely surface TPS/uptime
> internally, but EssentialsX exposes them as first-class commands. P1/P2 here
> is to add **command aliases** so muscle memory works.

---

## 2. P0 / P1 gap descriptions

### P0 — ship in v1.6.1

- **/rtp (random teleport, a.k.a. /wild).** Single most-requested missing
  command on every modded survival server. Players use it to start fresh
  bases without scanning the world map. FTB Ess and Essential Commands both
  ship it; absence is conspicuous. Needs: configurable bounds, biome
  blacklist (oceans, nether roof), per-player cooldown, dimension whitelist,
  fall-back attempts on unsafe location. LuckPerms node: `vsu.command.rtp`,
  bypass: `vsu.bypass.cooldown.rtp`.

- **/mail.** EssentialsX flagship. Asynchronous offline messaging:
  `/mail send <player> <message>`, `/mail read`, `/mail clear`, optional
  `/mail sendall`. SQLite-backed (the moderation subsystem already wires
  SQLite in wave-1, so the storage layer is "free"). High social value;
  zero behaviour-change risk. Node: `vsu.command.mail`, admin sendall
  `vsu.admin.mail.sendall`.

- **/afk.** Auto-toggling AFK status (manual `/afk` plus idle-timer trigger).
  Surfaces in `/list`, optionally kicks after N minutes, optionally disables
  damage. Currently parent's inventory shows no `/afk` — surprising for a
  utility mod and a frequent player complaint when missing. Node:
  `vsu.command.afk`, kick bypass: `vsu.bypass.afkkick`.

- **/disposal (a.k.a. /trash, /trashcan).** Opens a virtual void inventory.
  Players want it constantly; without it they "trash" items by dropping +
  burning, which is annoying and griefable. Trivial implementation
  (`SimpleContainer` GUI that discards on close). Node: `vsu.command.disposal`.

- **/rules.** Pageable server rules viewer (`rules.json` or `rules.txt`).
  Pairs naturally with the wave-1 moderation subsystem — `/warn` for "rule
  3" is much stronger when `/rules` exists. Node: `vsu.command.rules`,
  edit: `vsu.admin.rules`.

### P1 — strong candidates

- **/motd.** Login-time MOTD plus on-demand `/motd`. Trivial, expected.
  Often combined with `/rules` and `/info` into a "server text-commands"
  micro-system that EssentialsX literally calls *text commands*.

- **/info / /news.** Same engine as `/rules` and `/motd` — multi-chapter
  text store. Ship them together as one subsystem (`TextCommandsFeature`).

- **/helpme** / **/helpop** (a.k.a. mod-call). Player sends a flagged
  message that staff with `vsu.mod.helpme.receive` see. Pairs with the
  moderation surface. Reduces "DM the owner" Discord noise.

- **/speed [walk|fly] <n> [player]**. Adjusts walk-/fly-speed multiplier.
  Frequently requested by builders and event admins. Caps at 10x to prevent
  client desync. Node: `vsu.admin.speed`.

- **/msgtoggle**, **/tptoggle**. Privacy toggles — refuse incoming DMs /
  refuse incoming TPA requests. Important for popular servers; cheap to
  implement on top of existing `/msg` and `/tpa` plumbing. Nodes:
  `vsu.command.msgtoggle`, `vsu.command.tptoggle`.

- **/tps**, **/uptime** as first-class aliases of existing telemetry.
  Pure UX. EssentialsX users will tab-complete these reflexively.

---

## 3. Recommended P0/P1 add shortlist (parent: scan-and-dispatch)

Numbered for ease of triage; ordered by player-impact-per-engineering-hour.

1. **/rtp** (random teleport, configurable bounds + cooldown) — P0
2. **/mail** (offline inbox, SQLite, send/read/clear/sendall) — P0
3. **/afk** (manual + idle timer, list integration) — P0
4. **/disposal** (a.k.a. /trash, void inventory GUI) — P0
5. **/rules** (text-command, pages) — P0
6. **/motd** (text-command + login banner) — P1
7. **/info** (text-command, pages — shares engine with /rules and /motd) — P1
8. **/helpme** (player → staff mod-call) — P1
9. **/speed** (walk/fly multiplier, capped) — P1
10. **/msgtoggle** + **/tptoggle** (privacy toggles) — P1
11. **/tps** + **/uptime** (aliases / explicit telemetry commands) — P1

Recommendation: implement #1–5 as v1.6.1's headline feature ("Essentials
parity pass"), then bundle #6–8 as a single `TextCommandsFeature` (one
storage backend, three commands), then ship #9–11 as misc polish.

---

## 4. Recommended NOT to add (with reasoning)

- **/op, /deop** — vanilla already provides; LuckPerms users don't want VSU
  reaching into operator state. Active anti-feature.
- **/jail, /setjail, /togglejail, /jails** — jail is its own subsystem
  (region detection, escape prevention, sentence timers). Implement
  *correctly* as v1.7.x or punt to a sister mod. Half-implementing it is
  worse than not shipping it; warned players just dig out.
- **/spawner edit / /changems** — touches mob-spawner NBT, deeply mod-pack
  dependent, will fight Apotheosis / EnderIO / etc. Out of scope.
- **/pay, /balance, /sell, /worth, /eco** — economy is an entire subsystem.
  If Vonix ever wants economy, do it as a dedicated `vsu-economy` module,
  not bolted into the core utils jar.
- **/book** (edit sealed books) — niche, easy to abuse for sign-style chat
  spoofing. Not worth the surface area.
- **/powertool** — assigns commands to held items. Trivial griefing vector
  (`/powertool /tp …`). EssentialsX gets away with it because of per-command
  permissions; VSU's audience is smaller and less hardened.
- **/kittycannon, /nuke, /beezooka, /bigtree** — gag/admin-prank commands.
  EssentialsX legacy. Skip.
- **/spawnmob** — vanilla `/summon` already covers it; differs only in
  stack-on-mount syntax. Not worth maintaining a parser.
- **/sudo** — *tempting* but dangerous. If added later, must be gated to
  `vsu.admin.sudo` with explicit logging of every sudo'd command. P2 only,
  not in the P0/P1 shortlist for this reason.
- **/jump, /top, /bottom** — useful but trivially replaced by creative-mode
  workflows or vanilla `/tp ~ ~256 ~`. Defer to P2/P3.

---

## 5. Surprising findings

- **VSU has no /afk.** Every comparable mod ships one; players will
  notice. Strongly recommend P0.
- **No /mail.** EssentialsX users treat this as table-stakes. The wave-1
  SQLite plumbing makes it nearly free to add.
- **No /rtp.** FTB Essentials, Essential Commands, and EssentialsX all
  ship it. VSU's parent inventory has `/tpall` and `/tppos` but no random
  TP — surprising because VSU otherwise leans into survival-server UX.
- **No /disposal.** Universally expected; absence drives players to drop
  items into lava as the workaround, which is friction.
- **VSU has `/c` and `/s`** in the inventory with unclear semantics —
  parent should confirm whether these are chat-channel and suicide
  respectively. If `/c` is a chat channel, that's a *positive* gap closure
  against EssentialsX (which has no native channels).
- **VSU has both `/midnight` and `/noon`** but no `/dawn` or `/dusk`. Minor
  but easy parity win if/when world-time commands are touched again.
- **The wave-1 `/warn` subsystem is rare** — most of the modded baselines
  (FTB Ess, Essential Commands) don't ship warnings at all. EssentialsX
  relies on 3rd-party plugins. Wave-1 is therefore *ahead* of the modded
  baseline on moderation, which is worth marketing.
- **No "Cyber's Essentials" mod was found** in the public registries — the
  closest 1.21.1 NeoForge/Fabric analogue is John-Paul-R's *Essential
  Commands* (often confused). The parent's task description should be
  updated to clarify the reference if "Cyber's Essentials" was intended
  as a specific name.

---

## 6. Implementation hint for parent (storage layer)

#3 (/mail) and the existing wave-1 moderation work both want SQLite. If
v1.6.1 is dispatched, share the SQLite connection / migrator that Subagent
B introduced — don't open a second `mail.db`. Suggested table:

```sql
CREATE TABLE IF NOT EXISTS mail (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  sender    TEXT NOT NULL,        -- UUID
  recipient TEXT NOT NULL,        -- UUID
  body      TEXT NOT NULL,
  sent_at   INTEGER NOT NULL,     -- epoch millis
  read_at   INTEGER                -- NULL = unread
);
CREATE INDEX IF NOT EXISTS mail_recipient_idx ON mail(recipient, read_at);
```

— End of report —
