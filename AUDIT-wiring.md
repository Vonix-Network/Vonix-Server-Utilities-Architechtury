# Vonix Server Utilities — Wiring + Cross-Version Parity Audit

Read-only audit. Scope = 4 Architectury templates (1.18.2/1.19.2/1.20.1 = Forge+Fabric, J17; 1.21.1 = NeoForge+Fabric, J21).
Common code path: `<ver>/common/src/main/java/network/vonix/serverutilities/`.

Legend: ✅ present · ❌ missing · ⚠️ present but flagged.
Versions abbreviated below as **18/19/20/21**.

---

## 1. Command Registration Matrix

Registered = a `Commands.literal("<name>")` is passed to `dispatcher.register(...)` at file root (top-level command). Subcommands of `/time set …`, `/gm 0|1|…`, `/vonixsu …` are noted in the handler row only. All four versions register the *same* set of top-level commands (literal grep is identical across versions) unless flagged.

| Command | README? | 18 | 19 | 20 | 21 | Handler | Notes |
|---|---|---|---|---|---|---|---|
| /home | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerHome | uses HomeManager + TeleportManager |
| /sethome | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerHome | enforces ModConfig.maxHomes |
| /delhome | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerHome | |
| /homes | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.listHomes | |
| /warp | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerWarps | |
| /setwarp | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerWarps | op-only (`hasPermission(3)`) ⚠️ README says op-only ok |
| /delwarp | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerWarps | op-only (3) |
| /warps | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.listWarps | |
| /kit | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerKits | KitManager — 3 hard-coded kits, no `/kit list` data path beyond `/kits` |
| /kits | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.listKits | |
| /tpa | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerTpa | |
| /tpahere | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerTpa | |
| /tpaccept | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.tpAccept | |
| /tpdeny | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.tpDeny | |
| /back | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.back | |
| /backdeath | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.backDeath | honours `death_back_delay_seconds` |
| /spawn | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerSpawn | uses overworld().getSharedSpawnPos() |
| /setspawn | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.setSpawn | op-only (2) |
| /tp | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.registerTeleportCommands | op (2) |
| /tphere | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands | op (2) |
| /tpall | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands | op (2) |
| /tppos | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands | op (2) |
| /weather | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | op (2) |
| /sun | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | op (2) |
| /rain | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | duplicate of `/weather rain` ⚠️ |
| /storm | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | duplicate of `/weather storm` ⚠️ |
| /time | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands.registerTime | subcmds set/add day/night/noon/midnight |
| /day | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | op (2) |
| /night | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands | op (2) |
| /fly | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerAdmin → AdminManager.toggleFly | op (2) |
| /god | ✅ | ✅ | ✅ | ✅ | ✅ | AdminManager.toggleGodMode | op (2) |
| /vanish | ✅ | ✅ | ✅ | ✅ | ✅ | AdminManager.toggleVanish | op (2) |
| /heal | ✅ | ✅ | ✅ | ✅ | ✅ | AdminManager.healPlayer | op (2) |
| /feed | ✅ | ✅ | ✅ | ✅ | ✅ | AdminManager.feedPlayer | op (2) |
| /gm | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.setGameMode | op (2); subcmds 0/1/2/3 + s/c/a/sp |
| /nick | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.setNickname | in-memory only ⚠️ not persisted |
| /msg | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.sendMessage | |
| /tell | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands | alias of /msg, undocumented ⚠️ |
| /r | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.replyMessage | |
| /reply | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands | alias of /r, undocumented ⚠️ |
| /ignore | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.toggleIgnore | in-memory only ⚠️ |
| /ping | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showPing | |
| /near | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showNear | |
| /whois | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showWhois | op (2) — README doesn't say op-only ⚠️ |
| /seen | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showSeen | undocumented; in-memory only ⚠️ |
| /afk | ✅ | ✅ | ✅ | ✅ | ✅ | WorldCommands.toggleAfk | |
| /broadcast | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.broadcast | op (2) |
| /bc | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands | alias, undocumented |
| /list | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showPlayerList | |
| /playtime | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showPlaytime | uses MC stat |
| /suicide | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.suicide | |
| /hat | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.wearHat | |
| /more | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.moreItems | op (2) |
| /repair | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.repairItem | op (2) |
| /clear | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.clearInventory | op (2) |
| /invsee | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.openInventory | op (2); InvseeContainer |
| /enderchest | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.openEnderChest | op (2) |
| /workbench | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.openWorkbench | |
| /anvil | ✅ | ✅ | ✅ | ✅ | ✅ | UtilityCommands.openAnvil | |
| /backsee | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands.openBackpack | "backpack" view — undocumented ⚠️ |
| /accsee | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands (AccessoryHelper) | accessories slot view — undocumented; AccessoryHelper@expect/actual stubs ⚠️ |
| /gc | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showServerStats | server stats, op (2) |
| /lag | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands.showLag | |
| /lightning | ❌ README | ✅ | ✅ | ✅ | ✅ | WorldCommands.lightningAtPlayer | op (2) |
| /smite | ❌ README | ✅ | ✅ | ✅ | ✅ | WorldCommands | alias of /lightning |
| /ext | ❌ README | ✅ | ✅ | ✅ | ✅ | WorldCommands.extinguish* | op (2) |
| /getpos | ❌ README | ✅ | ✅ | ✅ | ✅ | UtilityCommands.getPos | |
| /vonixsu | ✅ | ✅ | ✅ | ✅ | ✅ | ModCommands.registerVonixSu | op (3); `/vonixsu reload` is a no-op stub ⚠️ |

**Summary.** Every advertised command from the README is registered in all four versions. The set is *identical* across versions (top-level literals match exactly). However ~12 commands exist that the README doesn't mention; conversely, the README never mentions `/seen`, `/gc`, `/lag`, `/lightning`, `/smite`, `/ext`, `/getpos`, `/backsee`, `/accsee`, `/tell`, `/bc`, `/reply` — these are real, registered, but undocumented.

Worth fixing: README claims "op-only creation" for warps — ✅ true. README does NOT call out `/whois` as op-only but code gates it at perm-level 2 (`UtilityCommands.java:173`) — mismatch.

`/vonixsu reload` prints "Config reloads require a server restart." — no actual reload (`ModCommands.java:580-583`). It still mutates nothing.

---

## 2. Manager Wiring

| Manager | Pattern | Instantiated at | Used by | Per-version notes |
|---|---|---|---|---|
| **VonixServerUtilities** (root) | singleton, `init()` | Fabric: `VonixServerUtilitiesFabric.onInitialize()`; Forge/NeoForge: ctor of `@Mod` class. | EventHandler, all managers via `getDatabase()`. | identical across all 4. NeoForge entrypoint does NOT call `EventBuses.registerModEventBus` (it isn't needed on NeoForge — correct). |
| **Database** | field of `VonixServerUtilities` (`new Database()` at line 22) | `EventHandler.SERVER_STARTING` calls `getDatabase().init(server)` | HomeManager, WarpManager, KitManager via `conn()` helper. | identical. Single persistent connection; all reads/writes funnel through `VonixServerUtilities.dbAsync(...)` — TeleportManager + AdminManager do not touch the DB (in-memory only). |
| **ModConfig** | `public static final INSTANCE` | `EventHandler.SERVER_STARTING` calls `.load(configDir)` | ModCommands (maxHomes, deathBackDelay), HomeManager (maxHomes), TeleportManager (tpaTimeoutMs). | identical. |
| **HomeManager** | singleton (`INSTANCE`) | static init at class load | ModCommands.registerHome only | identical body across versions, except 1.18.2 uses `player.level` field, 1.19.2/1.20.1/1.21.1 use `player.level()` method, plus chat API differences. |
| **WarpManager** | singleton | static | ModCommands.registerWarps only | identical body, version-specific text APIs |
| **KitManager** | singleton | static, `loadDefaultKits()` in ctor | ModCommands.registerKits only | hard-coded 3 kits (starter/tools/food). No data-driven config; `register(Kit)` API is public but unused externally ⚠️ |
| **TeleportManager** | singleton | static | ModCommands (TPA, /back, /spawn, home/warp tp), UtilityCommands (saveLastLocation on /tp*), EventHandler (LIVING_DEATH, PLAYER_QUIT, SERVER_STOPPED) | identical wiring. clear() called on server stop. |
| **AdminManager** | singleton | static | ModCommands (heal, feed, fly, god, vanish) only | clear() called on server stop. |
| **EventHandler** | `public static void init()` only | `VonixServerUtilities.init()` calls it | Registers `CommandRegistrationEvent`, `SERVER_STARTING/STOPPED`, `PLAYER_JOIN/QUIT`, `LIVING_DEATH`. | identical across all 4. Only entry point that wires events. |

**Cross-version notes.**
- `AdminManager.java` differs across versions only due to minor MC API churn (component / `level()` getter); manager *contract* is identical.
- 1.21.1 `VonixServerUtilities.java` is byte-identical to 1.20.1 except a trailing newline (`diff` shows `69d68`).
- `AccessoryHelper` is an Architectury `@ExpectPlatform` shell (11 LOC). The fabric/neoforge impls live at `<ver>/<loader>/.../inventory/<loader>/AccessoryHelperImpl.java` — all four versions ship both impls. 1.18.2 ships Forge impl (correct).

---

## 3. DB Schema Coverage

All four versions create the **same four tables** — identical SQL (Database.java:63/78/92/101 in 1.20.1 and equivalents elsewhere).

| Table | Feature | Write path | Read path | Status |
|---|---|---|---|---|
| `vsu_homes` (pk uuid+name) | /sethome /delhome /home /homes | HomeManager.setHome / .deleteHome | HomeManager.getHome, .getHomes, .count, .exists | ✅ both paths exist |
| `vsu_warps` (pk name) | /setwarp /delwarp /warp /warps | WarpManager.setWarp / .deleteWarp | WarpManager.getWarp, .getWarps | ✅ |
| `vsu_kit_cooldowns` (pk uuid+kit) | /kit cooldown enforcement | KitManager.setLastUsed | KitManager.getLastUsed | ✅ |
| `vsu_migration` (key/value) | migration bookmark | Database.markMigrated() | Database.attemptMigration() | ✅ internal only |

**Orphan tables:** none.

**Missing tables (advertised but no persistence):**
- **Nicknames** (`/nick`): kept in `UtilityCommands.nicknames` (ConcurrentHashMap, line 37) — wiped on restart ❌
- **Ignore lists** (`/ignore`): in-memory `ignoreList` map (line 40) — wiped on restart ❌
- **Last-seen / playtime extras** (`/seen`): `lastSeen` map (line 38) — only "since join" data, no DB ❌
- **AFK state** (`/afk`): in-memory `afkTime` / `afkMessage` maps in `WorldCommands.java:32-33` — wiped on restart (acceptable but worth noting) ⚠️
- **Last-location / death-location** (`/back`, `/backdeath`): kept in `TeleportManager.lastLocations` + `deathLocations` HashMaps — wiped on restart ❌ (README implies these survive)
- **Spawn point**: README "/setspawn" — uses vanilla `setDefaultSpawn`; no extra table needed ✅

Migration from VonixCore covers `vc_homes`/`vonixcore_homes`, `vc_warps`/`vonixcore_warps`, `vc_kit_cooldowns` only — nicknames/back/ignore are not migrated even from upstream.

---

## 4. ModConfig Keys

`config/vonix_server_utilities.properties`. All four `ModConfig.java` files are byte-identical (`diff` shows only a trailing newline).

| Key | Default | Reader | Missing behavior |
|---|---|---|---|
| `max_homes` | 5 | `HomeManager.setHome` (line 37); `ModCommands.setHome` (l.111), `.listHomes` (l.145) | `intOf` returns default 5 |
| `tpa_timeout_seconds` | 120 | `TeleportManager.isExpired` (l.153, exposed via `getTpaTimeoutMs`) | default 120 |
| `death_back_delay_seconds` | 0 | `ModCommands.backDeath` (l.264) | default 0 |

No declared-but-unread keys. README documents these three exactly. ✅

No reload at runtime — file is read once in `SERVER_STARTING` (`EventHandler.java:35`) and `/vonixsu reload` is a stub.

---

## 5. Cross-Version Parity (`diff -rq common/`)

### 18 vs 19 (common/src/main)
- `build.gradle`: differ (loader/arch versions)
- `admin/AdminManager.java`: MC API churn
- `command/ModCommands.java`: chat-API churn (`Component.literal` vs `new TextComponent`)
- `command/UtilityCommands.java`: same
- `command/WorldCommands.java`: same
- `inventory/AccessoryHelper.java`: differ (stubs but slight)
- `listener/EventHandler.java`: differ
- `teleport/TeleportManager.java`: differ
- **Only in 19:** `resources/vonix_server_utilities.mixins.json` (new naming) — 18 still uses the old `vonix_server_utils.mixins.json` ⚠️

### 18 vs 20
Same set plus `homes/HomeManager.java`, `kits/KitManager.java`, `warps/WarpManager.java`, `inventory/InvseeContainer.java` (`level` field → `level()` accessor change in MC 1.20).

### 18 vs 21 — biggest drift
- All common files differ.
- **`config/ModConfig.java`** differs (only by a trailing newline — effectively identical, despite J21 target).
- **`database/Database.java`** differs (text-only — same schema/logic).
- **Only in 21:** `command/TestMe.java` — see Bug #1 below.
- **mixins.json**: 21 uses `JAVA_21`; 18/19/20 use `JAVA_17`. ✅ correct per version.
- **Naming:** 1.18.2 still has `vonix_server_utils.mixins.json` (old), 1.19.2/1.20.1 ship **both** `vonix_server_utils.mixins.json` (old, points to non-existent `network.vonix.utils.mixin` package) AND `vonix_server_utilities.mixins.json` (current) — duplicate/stale mixin config ⚠️ See Bug #3.
- 1.21.1 ships only `vonix_server_utilities.mixins.json` — clean.

### Command-set drift (top-level literals)
Top-level command set is byte-identical across all four versions (per `grep -hoE 'literal("…")'` sort -u). The only delta:
- 19/20/21 also have `literal("Anvil")` and `literal("Crafting")` — these are **GUI titles** passed to `Component.literal(...)` inside `openAnvil`/`openWorkbench`, not registered commands (the regex matched the same call shape). 18 uses the older `new TextComponent("Anvil")` form so they don't match — that's the only "missing literal" and it's spurious.

So **no command-set drift**. Manager method signatures inside `HomeManager.setHome` etc. are identical across versions. Drift is purely Mojang-API churn for chat/components/world access.

### Forked-but-shouldn't-have / should-have-been-forked-but-wasn't
- 1.21.1's `VonixServerUtilities.java`, `ModConfig.java`, `Database.java` are essentially identical to 1.20.1's — fine, no Java-21 features are exploited.
- `KitManager.buildStack()` uses `BuiltInRegistries.ITEM.get(loc)` in 1.20+ but the same path in 1.18.2 references `Registry.ITEM` (visible in diff) — handled correctly per version.
- 1.21.1 `inventory/InvseeContainer.java` differs significantly — likely uses `ItemContainerContents` (component-based inventory in 1.20.5+), which is precisely what `TestMe.java` plays with. Should be inlined into InvseeContainer (Bug #1).

---

## 6. Build Files

### gradle.properties (mc / loader / arch / fabric-api / forge|neoforge)

| Ver | minecraft | fabric_loader | architectury | fabric_api | forge/neoforge | enabled_platforms |
|---|---|---|---|---|---|---|
| 18 | 1.18.2 | 0.19.2 | 4.12.94 | 0.77.0+1.18.2 | forge 1.18.2-40.3.11 | fabric,forge |
| 19 | 1.19.2 | 0.19.2 | 6.6.92 | 0.77.0+1.19.2 | forge 1.19.2-43.5.1 | fabric,forge |
| 20 | 1.20.1 | 0.19.2 | 9.2.14 | 0.92.9+1.20.1 | forge 1.20.1-47.4.10 | fabric,forge |
| 21 | 1.21.1 | 0.18.4 | 13.0.8 | 0.116.10+1.21.1 | neoforge 21.1.215 | fabric,neoforge |

`mod_version = 1.1.0` in all four; archives_name = `vonix_server_utilities`.

**Stale / odd:**
- 1.21.1 has `fabric_loader_version = 0.18.4` — **older** than 18/19/20's `0.19.2` despite being the newest MC. Fabric Loader 0.18.4 predates MC 1.21 support, and current 1.21.1 fabric-loader is ~0.16.x in new numbering. Suspect copy-paste typo (should likely be `0.16.x`). ⚠️ Verify against your build.
- 1.18.2 jvmargs `-Xmx2G` but 1.19/1.20 use `-Xmx3G` — inconsistent (low risk).

### settings.gradle
All four identical pattern: `include 'common'`, `include 'fabric'`, plus `'forge'` for 18/19/20 and `'neoforge'` for 21. ✅

### fabric.mod.json

| Field | 18 | 19 | 20 | 21 |
|---|---|---|---|---|
| `id` | vonix_server_utilities | same | same | same |
| `mixins` ref | `vonix_server_utils.mixins.json` ⚠️ stale | `vonix_server_utilities.mixins.json` | same | same |
| `entrypoints.main` | `…fabric.VonixServerUtilitiesFabric` | same | same | same |
| `depends.fabricloader` | `>=0.19.2` | `>=0.19.2` | `>=0.19.2` | `>=0.18.4` (suspect) |
| `depends.minecraft` | `~1.18.2` | `~1.19.2` | `~1.20.1` | `~1.21.1` |
| `depends.java` | `>=17` | `>=17` | `>=17` | `>=21` |
| `depends.architectury` | `>=4.12.94` | `>=6.6.92` | `>=9.2.14` | `>=13.0.8` |
| `icon` | `assets/vonix_server_utils/icon.png` ⚠️ wrong namespace | absent | absent | absent |
| `authors`/`contact`/`description` | `"Me!"`, `FabricMC/fabric-example-mod` ⚠️ template leftovers in 18 | template (19/20) | template (19/20) | clean ("Vonix Network") |

**Critical:** 1.18.2 fabric.mod.json **`mixins` references `vonix_server_utils.mixins.json`** — that file's `package` is `network.vonix.utils.mixin`, which **does not exist** in the source tree. Mixin loader will scan an empty package — harmless today (no mixins listed) but the package path is wrong and will break the first time someone adds a mixin. Same wrong-package mixins JSON also present in 19/20 (left over alongside the correct one). 21 is clean.

### forge/mods.toml & neoforge.mods.toml

| Field | 18 | 19 | 20 | 21 (neoforge) |
|---|---|---|---|---|
| modId | `vonix_server_utils` ⚠️ MISMATCH | `vonix_server_utilities` | `vonix_server_utilities` | `vonix_server_utilities` |
| loaderVersion | `[40,)` | `[40,)` ⚠️ should be `[43,)` for 1.19.2 | `[40,)` ⚠️ should be `[47,)` for 1.20.1 | `[4,)` |
| arch dep range | `[4.12.94,)` | `[6.6.92,)` | `[9.2.14,)` | `[13.0.8,)` |
| mc range | `[1.18.2,)` | `[1.18.2,)` ⚠️ wrong | `[1.18.2,)` ⚠️ wrong (likely; pls verify) | `[1.21.1,)` |
| mixins block | none | none | none | present ✅ |
| description | template "Insert License Here" | template | template | clean |

**Critical bugs (forge/neoforge metadata):**
- 1.18.2 **`modId = "vonix_server_utils"`** mismatches the Java `MOD_ID = "vonix_server_utilities"` (used by `@Mod` and `EventBuses.registerModEventBus`). Forge will refuse to load or load with mismatched id ⚠️ See Bug #2.
- 19 & 20 forge mods.toml use `loaderVersion = "[40,)"` (1.18 Forge era). 1.19.2 needs `[43,)`, 1.20.1 needs `[47,)`. ⚠️
- 19 & 20 forge mods.toml `minecraft versionRange` still says `[1.18.2,)` (copy-paste). Will accept newer too but is wrong intent. ⚠️
- 18/19/20 forge mods.toml has **no mixins block** — fine if `vonix_server_utilities.mixins.json` is referenced via classpath but Forge requires explicit `[[mixins]] config=` entries. 21 has them; 18/19/20 do not. Mixin auto-loading on Forge would silently no-op. ⚠️

---

## 7. Top 10 Wiring Bugs (ranked, ruthless)

1. **1.18.2 Forge modId mismatch.** `vonix_server_utils-1.18.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml:7` declares `modId = "vonix_server_utils"`, but `VonixServerUtilities.MOD_ID = "vonix_server_utilities"` (`common/.../VonixServerUtilities.java:17`) is what `@Mod(...)` registers. Forge will fail to find the registered mod or crash on duplicate. **Fix:** change mods.toml to `modId = "vonix_server_utilities"` (matches dependencies block which already uses that key).

2. **Stale mixin JSON pointing at a non-existent package.** `vonix_server_utils-1.18.2-fabric-forge-template/common/src/main/resources/vonix_server_utils.mixins.json` declares `package = network.vonix.utils.mixin` — no such source root exists. 18 fabric.mod.json (line ~24) references this file. 19 & 20 ship both this stale file AND the correct `vonix_server_utilities.mixins.json`. **Fix:** in 1.18.2 swap the reference + file to `vonix_server_utilities.mixins.json` with `package = network.vonix.serverutilities.mixin`; in 19/20 delete the stale file.

3. **Forge mods.toml `loaderVersion` and `minecraft versionRange` are stuck at 1.18.2 values in 1.19 and 1.20.** `vonix_server_utils-1.19.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml:2` (and 1.20.1 equivalent) still say `loaderVersion = "[40,)"` and the minecraft range is `[1.18.2,)`. Will boot but is wrong; flag installer compat. **Fix:** set `[43,)` / `[1.19.2,)` for 19 and `[47,)` / `[1.20.1,)` for 20.

4. **Forge does not load the mixin config.** None of 18/19/20 forge mods.toml include a `[[mixins]] config=` entry. Only 1.21.1 neoforge.mods.toml does. **Fix:** add `[[mixins]]\nconfig = "vonix_server_utilities.mixins.json"` to each forge mods.toml. (Currently no mixins are defined so the bug is dormant — first added mixin will silently no-op on Forge.)

5. **`TestMe.java` leaked into the 1.21.1 common tree.** `vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/command/TestMe.java` is an experimental ItemContainerContents copy-test, public, unreferenced. Pollutes the build, signals the InvseeContainer port to component-inventory isn't finished. **Fix:** delete TestMe.java and integrate its `copyInto(NonNullList)` snippet into `InvseeContainer` if 1.21 inventory access requires it; else delete outright.

6. **`/vonixsu reload` is a no-op.** `ModCommands.java:580-583` (all four versions): prints "Config reloads require a server restart." but the command is documented in README as `version|status|reload`. Operators will think it works. **Fix:** call `ModConfig.INSTANCE.load(server.getServerDirectory().toPath().resolve("config"))` and reply ok/fail.

7. **`/back`, `/backdeath`, `/nick`, `/ignore`, `/seen` lose state on restart.** `TeleportManager.lastLocations`/`deathLocations` are `ConcurrentHashMap`s (`TeleportManager.java:30-31` area) cleared by `clearPlayer` on QUIT and never persisted. Same for `UtilityCommands.nicknames` (l.37), `ignoreList` (l.40), `lastSeen` (l.38). README implies death/back persist. **Fix:** add tables `vsu_back_locations`, `vsu_death_locations`, `vsu_nicknames`, `vsu_ignores` in `Database.createTables()` and save on write / load on PLAYER_JOIN.

8. **`/whois` op-gate not in README.** `UtilityCommands.java:173` gates `/whois` at perm 2. README lists it as a player utility. Either drop the gate or update README. **Fix:** remove `.requires(s -> s.hasPermission(2))` on l.173.

9. **`/rain` and `/storm` registered both as `/weather rain|storm` subcommands AND as top-level aliases.** `WorldCommands.java:40-43` registers them under `/weather`, and `:51-58` also registers them as top-level. Brigadier permits this but produces a help-menu mess. **Fix:** keep top-level aliases only if README documents them; otherwise drop one path.

10. **`KitManager` has hard-coded kits and no reload mechanism.** `KitManager.loadDefaultKits()` (l.29-50) is called once in the static ctor. `register(Kit)` is public but never called from anywhere. There is no JSON/config file watched. **Fix:** load kits from `config/vonix_server_utilities/kits.json` on `SERVER_STARTING` (call from `EventHandler` alongside `ModConfig.load`), reparse on `/vonixsu reload`.

Bonus (not in top-10 but worth noting):
- `Database.findVonixCoreDb` scans only `<world>/vonixcore/*.db` — won't find DBs at server-root `world/<dim>/data/` etc. Low risk for migration but worth a comment.
- 1.21.1 fabric_loader_version `0.18.4` is older than the 0.19.2 used by 1.18.2 — almost certainly a typo. Verify against fabricmc.net for current 1.21.1 loader (should be ~0.16.x).
- `EventHandler` registers all events but does not register a SERVER_STARTED hook — DB migration kicks off in SERVER_STARTING async, players could join before it finishes. Low risk because the schema is created synchronously.
- `AccessoryHelper` is a 10-line stub on all platforms; the `/accsee` command will silently no-op if the impl returns null.

---

*Audit complete. No files were modified. Source citations use the 1.20.1 tree for line numbers (mirrors are within 1–2 lines across versions).*
