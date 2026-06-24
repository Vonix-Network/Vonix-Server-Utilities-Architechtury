# FIX — Audit bugs in 1.21.1 template

Scope: `vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/`. The
porting agent will replicate these changes to 1.20/1.19/1.18. The
`venary/` package was NOT touched.

No compilation was attempted (no JDK/Gradle toolchain available on this
host). All changes are source-level only.

---

## A. Migration safety hardening

**File:** `common/.../database/Database.java` (full rewrite, ~485 lines)

Key sections:
- `attemptMigration(...)` (lines ~140–210): tracks `allOk` per-table,
  wraps the three `migrate*` calls in `connection.setAutoCommit(false)`
  → `commit()` on success, `rollback()` on any failure. `markMigrated()`
  is now ONLY called when `allOk == true`. Skipped-by-gate path logs
  `[VonixSU] VonixCore migration already completed — skipping.`
- Source DB is opened **read-only** via JDBC URL suffix `?open_mode=1`.
- A timestamped backup `vonixcore.db.bak-<epoch_ms>` is `Files.copy`'d
  into the source directory **before** the connection is opened.
- `migrateHomes` / `migrateWarps` / `migrateKitCooldowns` now throw
  SQLException so the wrapper can catch and rollback.
- `migrateKitCooldowns` now also tries `vonixcore_kit_cooldowns` and
  `kit_cooldowns` as legacy-name fallbacks (mirrors the homes/warps
  pattern).
- `migrateWarps` probes for `created_by` / `created_at` via
  `ResultSetMetaData` (new helper `columnsOf`) and preserves those
  values when present; legacy schemas without them fall back to NULL +
  `now()`.
- NEW tables added in `createTables()`:
  - `vsu_back_locations(uuid, world, x, y, z, yaw, pitch, kind, updated_at)` — PK (uuid, kind)
  - `vsu_nicknames(uuid PK, nickname, updated_at)`
  - `vsu_ignore_list(owner_uuid, target_uuid, created_at)` — PK (owner_uuid, target_uuid)
- NEW helper methods on `Database`:
  - `setBackLocation / getBackLocation / deleteBackLocation / getAllBackLocations`
  - `setNickname / getNickname / deleteNickname / getAllNicknames`
  - `addIgnore / removeIgnore / getAllIgnores`

**Verify:** start a fresh server with a populated VonixCore DB present.
Logs should show the backup line, then "Migration complete — H homes,
W warps, K cooldowns". Corrupt the source's `vc_warps` row mid-run
(e.g. drop the table after homes pass) and re-run on a clean target —
target rows should roll back and `vsu_migration` row should NOT be set.
On second start (gate already set) you should see the "skipping" log.

---

## B. `/vonixsu reload` actually reloads config

**Files:**
- `common/.../config/ModConfig.java` — added `private Path configDir` cached
  inside `load(Path)` and a new `public boolean reload()` that re-invokes
  `load(configDir)`.
- `common/.../command/ModCommands.java` — `reloadConfig(ctx)` (~L583):
  calls `ModConfig.INSTANCE.reload()`, re-initialises `VenaryClient`
  with the fresh settings, and (bonus) calls `KitManager.reloadFromJson`
  so kit definitions reload too. Replies `§a[VSU] Configuration reloaded.`
  on success or a `§c[VSU] Reload threw: …` failure.

**Verify:** run server, edit `config/vonix_server_utilities.properties`
(e.g. raise `max_homes` from 5 → 10), then run `/vonixsu reload`. The
chat reply should be the green "Configuration reloaded." line. Run
`/sethome` enough times to confirm the new limit is honoured without a
server restart. Server log should show the existing
`[VonixSU] Config loaded (max_homes=10, …)` line, proving the file was
re-read.

---

## C. `/back`, `/backdeath`, `/nick`, `/ignore` persistence

**Files:**
- `common/.../database/Database.java` — three new tables + helpers (see A).
- `common/.../teleport/TeleportManager.java`:
  - `saveLastLocation` / `saveDeathLocation` now write through to
    `vsu_back_locations` via `VonixServerUtilities.dbAsync(...)`.
  - NEW `hydrateFromDb()` repopulates `lastLocations` and
    `deathLocations` from the DB (called from `SERVER_STARTING`).
  - `clearPlayer(uuid)` no longer wipes lastLocations/deathLocations
    (those are persisted and survive sessions).
- `common/.../command/UtilityCommands.java`:
  - `setNickname` and `clearNickname` write through to `vsu_nicknames`.
  - `toggleIgnore` writes through to `vsu_ignore_list`.
  - NEW `hydrateFromDb()` repopulates `nicknames` and `ignoreList`.
  - NEW `onPlayerJoin(ServerPlayer)` re-applies persisted nicknames on
    join (cosmetic display + tablist update). Old `onPlayerJoin(UUID)`
    kept as a no-op overload for legacy callers.
  - `onPlayerLeave` no longer drops the ignoreList for the player
    (it's persistent).
- `common/.../listener/EventHandler.java`:
  - After `Database.init`, schedules `TeleportManager.hydrateFromDb` +
    `UtilityCommands.hydrateFromDb` on the DB executor.
  - `PLAYER_JOIN` now calls `UtilityCommands.onPlayerJoin(player)`
    (the ServerPlayer overload, so nicknames get re-applied).
  - Death-event listener still calls `saveDeathLocation` (which is now
    write-through), so kind='death' is persisted automatically.
  - Every `teleportPlayer` call already invokes `saveLastLocation`,
    which is now write-through with kind='tp'.

**Verify:** set a nickname with `/nick Foo`, `/ignore <player>`, then
teleport once and die once. Stop the server, restart, log back in:
- Nickname should still appear over your head & in tablist.
- `/back` should still teleport to the pre-restart location.
- `/backdeath` should still teleport to the pre-restart death spot.
- Try `/msg`ing the ignored player — you should still be blocked.

Inspect `config/vonix_server_utilities/data.db` via sqlite3; the three
new tables should contain the expected rows.

---

## D. `/rain` and `/storm` "duplicate" warning

**File:** `common/.../command/WorldCommands.java` (lines ~46–60).

After reading the code: both `/weather rain|storm` and the top-level
`/rain`, `/storm` aliases call the same `setWeather(ctx, "rain"|"storm", 6000)`
handler — there's no behavioural drift. Decision: **leave both paths in
place** (operator asked to keep them) and just rewrote the comment to
make it clear the duplication is intentional and the audit warning is
informational only. No functional change.

**Verify:** `/weather rain` and `/rain` should both start rain;
`/weather storm` and `/storm` should both start a thunderstorm.

---

## E. KitManager — hard-coded → JSON

**Files:**
- `common/.../kits/KitManager.java` (full rewrite, ~300 lines)
- `common/.../command/ModCommands.java` — `/kit reload` subcommand
  added (op level 3).
- `common/.../listener/EventHandler.java` — at `SERVER_STARTING`,
  schedules `KitManager.loadFromJson(server)` on the DB executor.

What changed:
- Kit defs live in `config/vonix_server_utilities/kits.json`.
- On first launch, if the file doesn't exist, the manager writes the
  three legacy kits (starter / tools / food) as defaults using
  Gson's pretty-print. Schema matches the task spec:
  `{"kits":[{"name":"starter","cooldown_seconds":3600,"one_time":false,"items":[{"item":"minecraft:bread","count":16}, …]}]}`.
- `parseKitsFile()` validates every item ID via `BuiltInRegistries.ITEM`;
  unresolved items log a warning and are skipped (the kit still loads
  with whatever items DID resolve).
- If `kits.json` is structurally broken, falls back to in-memory
  hard-coded defaults so the server stays usable.
- `reloadFromJson(server)` re-reads the file in place.
- The public `register(Kit)` API plus a new test-friendly
  `register(String name, ItemStack... stacks)` overload are preserved.
- `/kit reload` (op 3) runs the reload on the DB executor and replies
  `§a[VSU] Reloaded kits.json — N kits loaded.`.
- `/vonixsu reload` also calls `KitManager.reloadFromJson` so a single
  reload command refreshes everything.

**Verify:**
1. Wipe `config/vonix_server_utilities/kits.json`, start server. File
   should appear with three default kits. `/kits` should list
   `starter, tools, food`.
2. Edit `kits.json`: change `bread` count to 64, add a kit
   `{"name":"vip","cooldown_seconds":600,"items":[{"item":"minecraft:diamond","count":3}]}`,
   include a bogus `{"item":"minecraft:not_a_real_item","count":1}` to
   confirm the warning.
3. Run `/kit reload`. Reply should show kit count = 4. Server log
   should contain `[VonixSU] kits.json: kit 'vip' references unknown
   item 'minecraft:not_a_real_item' — skipping that item.` followed by
   `[VonixSU] Loaded 4 kits from kits.json.`
4. `/kit starter` should now give 64 bread. `/kit vip` should give 3
   diamonds. `/kits` lists all 4.

---

## Files touched (1.21.1 only)

```
common/src/main/java/network/vonix/serverutilities/
    config/ModConfig.java                   (B)
    database/Database.java                  (A, C — full rewrite)
    teleport/TeleportManager.java           (C)
    command/ModCommands.java                (B, C-via-EventHandler, E)
    command/UtilityCommands.java            (C)
    command/WorldCommands.java              (D — comment only)
    kits/KitManager.java                    (E — full rewrite)
    listener/EventHandler.java              (C, E wiring)
```

`venary/` was NOT touched per instructions. No Gradle deps were added
(Gson + SQLite JDBC were already on the classpath). Porting agent
should replicate the equivalent changes into 1.20.1, 1.19.2, and
1.18.2 templates, watching for these per-version pitfalls:

- 1.20.1 and earlier use the `addParticle`-era `EntityType.LIGHTNING_BOLT.create(level)`
  signature differences (not relevant here — KitManager doesn't spawn entities).
- `ResourceLocation.tryParse` exists across all four MC versions.
- `PlayerEvent.PLAYER_JOIN` from architectury is the same; the new
  `UtilityCommands.onPlayerJoin(ServerPlayer)` overload should map
  cleanly. Keep the legacy `onPlayerJoin(UUID)` overload to avoid
  source breaks for other callers.
- `ResultSetMetaData` is JDBC stdlib — no version concern.
- The SQLite `?open_mode=1` URL parameter is supported by the standard
  Xerial SQLite-JDBC driver used across all four versions.
