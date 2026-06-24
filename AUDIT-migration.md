# Audit: VonixCore → VonixServerUtilities Auto-Migration

Scope: Architectury monorepo at `/root/DEV/Vonix-Server-Utilities-Architechtury`.
README claim (README.md:44): *"Automatic migration from VonixCore databases is attempted on first launch."*

**Verdict up front:** The claim is **substantially TRUE** — code exists, is wired into the server startup lifecycle in all four version templates, and will attempt to import homes/warps/kit-cooldowns from a VonixCore SQLite DB on first run. However the implementation has several real safety/correctness gaps detailed below.

Path convention used in citations:
- `v18` = `vonix_server_utils-1.18.2-fabric-forge-template/common/src/main/java/network/vonix/serverutilities/`
- `v19` = `vonix_server_utils-1.19.2-fabric-forge-template/common/src/main/java/network/vonix/serverutilities/`
- `v20` = `vonix_server_utils-1.20.1-fabric-forge-template/common/src/main/java/network/vonix/serverutilities/`
- `v21` = `vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/`

---

## 1. Migration Code Path

The auto-migration is implemented in `database/Database.java`, kicked off from `listener/EventHandler.java` on the Architectury `LifecycleEvent.SERVER_STARTING` event.

Per-version citations (all four versions are present and structurally identical):

| Version | Trigger | Entry method | Per-table workers |
|---|---|---|---|
| 1.18.2 | `v18/listener/EventHandler.java:34-38` (`SERVER_STARTING` → `Database.init(server)`) | `v18/database/Database.java:33-55` (`init`), submits `attemptMigration` to `dbAsync` at line 54 | `attemptMigration` 110, `findVonixCoreDb` 144, `migrateHomes` 164, `migrateWarps` 195, `migrateKitCooldowns` 225, `markMigrated` 254 |
| 1.19.2 | `v19/listener/EventHandler.java:34-38` | `v19/database/Database.java:33-55` | Same line numbers as v18 (110/144/164/195/225/254) |
| 1.20.1 | `v20/listener/EventHandler.java:34-38` | `v20/database/Database.java:33-55` | Same |
| 1.21.1 | `v21/listener/EventHandler.java:34-38` | `v21/database/Database.java:33-55` | Same |

Sequence: `SERVER_STARTING` → `Database.init` opens dest SQLite (WAL mode), creates `vsu_*` tables synchronously, then queues `attemptMigration` on `VonixServerUtilities.dbAsync` (defined `v21/VonixServerUtilities.java:52`). Migration therefore runs off the main thread and does **not** block server startup.

The migration path is **present in all four version templates** — none are missing it.

---

## 2. Source DB Discovery

`findVonixCoreDb` (`Database.java:144-161`) is hardcoded, not config-driven. It probes three locations in order:

1. `<world_root>/vonixcore/vonixcore.db` — `Database.java:147` (uses `server.getWorldPath(LevelResource.ROOT)`).
2. `<server_dir>/vonixcore/vonixcore.db` — `Database.java:151`. On 1.18.2/1.19.2/1.20.1 the call is `server.getServerDirectory().toPath().resolve(...)`; on 1.21.1 it is `server.getServerDirectory().resolve(...)` directly (Mojang changed the return type from `File` to `Path` in 1.21). Functionally equivalent.
3. Filesystem scan of `<world_root>/vonixcore/` for any `*.db` file — `Database.java:155-159`.

There is no support for an admin-supplied path (no config key, no `-D` system property, no env var). Multiple candidate DBs in the scanned directory are NOT disambiguated: `dbs[0]` is returned (`Database.java:158`), which depends on `File.listFiles` order (filesystem-dependent, typically not sorted). A server with both `vonixcore.db` and a stale `vonixcore.db.bak` (or `*-shm`, `*-wal` — these end in `.db`? no, they end in `-shm`/`-wal`, so safe) could silently pick the wrong file.

---

## 3. Schema Mapping

| Source (VonixCore) | Destination (VSU) | Row transform | Notes |
|---|---|---|---|
| `vc_homes` (fallback `vonixcore_homes`) — `Database.java:165` | `vsu_homes(uuid,name,world,x,y,z,yaw,pitch)` (`Database.java:62-73`) | `SELECT uuid,name,world,x,y,z,yaw,pitch` → `INSERT OR IGNORE` (`Database.java:169-184`) | 1:1 column map. UUID copied as `TEXT` — no validation/normalisation. yaw/pitch narrowed from `getFloat` (the dest is `REAL` so no loss). Cross-version field renames between `vc_*` and `vonixcore_*` are handled only for table name, **not column names** — if VonixCore ever shipped a schema where columns differ, migration breaks. |
| `vc_warps` (fallback `vonixcore_warps`) — `Database.java:196` | `vsu_warps(name,world,x,y,z,yaw,pitch,created_by,created_at)` (`Database.java:77-88`) | `SELECT name,world,x,y,z,yaw,pitch` (creator/timestamp are **not** read from source) → INSERT with `created_by=NULL` (`Database.java:211`) and `created_at=System.currentTimeMillis()/1000L` (`Database.java:212`) | **Data loss**: if VonixCore stores creator/created-at columns, they are silently dropped. `created_at` is overwritten with *now*, so historical timestamps are lost. |
| `vc_kit_cooldowns` (no fallback) — `Database.java:226` | `vsu_kit_cooldowns(uuid,kit_name,last_used)` (`Database.java:91-97`) | `SELECT uuid,kit_name,last_used` → `INSERT OR IGNORE` (`Database.java:230-237`) | 1:1. **No legacy table-name fallback** — if the older VonixCore name was `vonixcore_kit_cooldowns` or `kit_cooldowns`, kits will NOT migrate. |

Other VonixCore data domains (player nicks, balances, mail, jail times, anything else VonixCore may have stored) are **not migrated** — the README's "VonixCore databases" phrasing is broader than the actual scope (homes/warps/kits only).

---

## 4. Idempotency & Safety

**First-launch flag.** Gated on a row in the destination DB: table `vsu_migration(key, value)` created at `Database.java:100-104`. The gate read is at `Database.java:113-117`; mark is at `Database.java:254-258` (`INSERT OR REPLACE INTO vsu_migration VALUES('vonixcore_migrated','true')`). So the flag lives **inside the destination SQLite**, not on disk or in config. Deleting `data.db` resets the gate; copying `data.db` from another server carries the flag with it.

**Duplicate-key behaviour.** All inserts use `INSERT OR IGNORE` (`Database.java:171`, `:201`, `:230`). If a destination row already exists for the same primary key, the source row is **silently dropped** — no logging, no count of skips. For homes PK is `(uuid,name)`, warps PK is `name`, kit-cooldowns PK is `(uuid,kit_name)`. A re-import (after manually clearing `vsu_migration`) would not overwrite admin-edited rows, which is arguably safe but invisible.

**Source DB open mode.** Opened with plain `DriverManager.getConnection("jdbc:sqlite:" + ...)` at `Database.java:129`. **Not opened read-only.** SQLite JDBC defaults to read/write; the migration only issues `SELECT`s, but if SQLite needs to recover the WAL it can mutate the source. There is no `?open_mode=1` / `SQLiteConfig.setReadOnly(true)` flag.

**Backup of source DB.** None. No copy, no rename, no snapshot before reading.

**Partial failure mid-migration.** The destination connection is in autocommit mode (no `setAutoCommit(false)` anywhere in `Database.java`), so each `executeUpdate` (`:182`, `:213`, `:236`) commits immediately. If `migrateWarps` throws halfway through, the homes already written stay, the partial warps written stay, and the catch block at `Database.java:187-189` / `:218-220` / `:239-241` **logs a warning and returns the partial `count`**. The outer `attemptMigration` proceeds to `markMigrated()` (`Database.java:138`) — meaning a **partial migration is marked as complete and will never re-run**. This is the single biggest correctness issue.

If a more severe exception escapes a per-table method, control jumps to `Database.java:139-141` which logs `"Migration non-fatal error"` and **skips** the `markMigrated()` call, leaving the migration to retry next launch — but with whatever partial rows were already committed still in the destination. There is no `ROLLBACK`.

---

## 5. Error Handling & Logging

Log lines a server admin will see (logger `VonixServerUtilities.LOGGER`, prefix `[VonixSU]`):

| Event | Line | Source |
|---|---|---|
| DB opened | `[VonixSU] Database ready at <path>` | `Database.java:47` |
| No source DB found | `[VonixSU] No VonixCore database found — skipping migration.` | `Database.java:122` |
| Migration starting | `[VonixSU] Migrating data from VonixCore database at <path> …` | `Database.java:126-127` |
| Per-table success | `[VonixSU] Migrated N homes from 'vc_homes'.` etc. | `Database.java:185`, `:216` |
| Overall success | `[VonixSU] Migration complete — N homes, M warps, K kit-cooldowns imported.` | `Database.java:133-135` |
| Per-table SQL error | `[VonixSU] homes migration error on 'vc_homes': <msg>` (WARN) | `Database.java:188`, `:219`, `:240` |
| Top-level error | `[VonixSU] Migration non-fatal error: <msg>` (WARN) | `Database.java:140` |
| DB init failure | `[VonixSU] Database initialisation failed` (ERROR, with stacktrace) | `Database.java:49` |

**Subtle gaps:**
- No "skipping (already migrated)" log when the gate trips — the early `return` at `Database.java:116` is silent. An operator cannot tell from the log whether migration ran-and-skipped, ran-and-found-nothing, or never executed.
- Kit-cooldowns has **no success log line** (only the aggregate at `:134`).
- Per-table catch blocks log only `e.getMessage()`, not the stacktrace.

**Does failure block startup?** No. `Database.init` returns early on init failure (`Database.java:50`) and leaves `connection == null`, which would NPE any later DB op but the server itself continues. Migration runs entirely on the `dbAsync` executor, so any migration failure is warn-and-continue. README's "attempted" wording is accurate; "succeeds" is not promised.

---

## 6. Per-Version Parity

Diffed all four `Database.java` files. The migration logic is **byte-identical** across 1.18.2 / 1.19.2 / 1.20.1 / 1.21.1 except for:
1. A single API call at line 151: `getServerDirectory().toPath().resolve(...)` on 1.18/1.19/1.20 vs `getServerDirectory().resolve(...)` on 1.21.1 — Mojang changed the return type from `File` to `Path` in 1.21. Functionally equivalent.
2. Mojibake of em-dashes / ellipses / box-drawing characters in 1.18/1.19/1.20 source files (UTF-8 sequences were saved as CP1252 garbage, e.g. `â€”` instead of `—`). Cosmetic in log output only; not a correctness issue, but worth fixing for log readability.

No version is missing the migration. No version has divergent table mapping or behaviour.

---

## 7. Verdict & Top Fixes

**Will players' homes/warps/kits survive a fresh launch against a real VonixCore DB?**

**Mostly yes**, with caveats:
- **Homes**: yes, if the source table is named `vc_homes` or `vonixcore_homes` and has columns `(uuid,name,world,x,y,z,yaw,pitch)`. UUIDs are preserved as strings.
- **Warps**: yes for the position data, but **creator and original creation timestamp are lost** — `created_by` becomes NULL and `created_at` becomes the migration moment.
- **Kits**: yes **only if** the legacy table is named exactly `vc_kit_cooldowns`. The fallback name list is empty here (unlike homes/warps).
- Everything else VonixCore stored (nicks, balances, mail, anything beyond those three tables) is **not migrated** despite the README implying a whole-DB migration.
- A partial mid-migration crash will be **marked complete and never retried**, silently leaving holes.

### Top fixes (ranked)

1. **`Database.java:138` — Don't `markMigrated()` after partial failure.** Track whether each per-table method threw; only mark the gate if all three succeeded fully. Today a SQL error mid-`migrateHomes` returns a partial count and the outer code happily writes `vonixcore_migrated=true`.
2. **`Database.java:129` — Open source DB read-only.** Append `?open_mode=1` or use `SQLiteConfig.setReadOnly(true)` so SQLite WAL recovery can't mutate the legacy file. Also take a `.bak` copy of `oldDb` before opening (one-liner with `Files.copy`).
3. **`Database.java:130-132` — Wrap the three `migrate*` calls in a single transaction.** `connection.setAutoCommit(false)` before, `commit()` on success, `rollback()` in the catch. Today every insert autocommits, so failure leaves a half-imported destination.
4. **`Database.java:226` — Add legacy-name fallback for kit-cooldowns**, mirroring homes/warps: `new String[]{"vc_kit_cooldowns","vonixcore_kit_cooldowns","kit_cooldowns"}`. Otherwise kits will silently fail to migrate on any pre-rename VonixCore install.
5. **`Database.java:195-222` — Preserve warp creator/created_at.** `SELECT` `created_by` / `created_at` from source if those columns exist (use `ResultSetMetaData` to probe), fall back to the current behaviour only when absent. Today historical metadata is destroyed.

Honourable mentions:
- `Database.java:116` — emit an INFO log `"[VonixSU] VonixCore migration already completed — skipping."` so operators can confirm gate state.
- `Database.java:144-161` — make the path admin-overridable via `ModConfig` (`vonixcore_db_path=`) for non-standard installs; warn if `listFiles` returns >1 match instead of grabbing `[0]`.
- Re-save 1.18.2/1.19.2/1.20.1 `Database.java` as UTF-8 to fix mojibake in log strings (`â€”` → `—`, `â€¦` → `…`).
- README.md:44 — tighten wording to "homes, warps, and kit-cooldowns are migrated" rather than implying full-DB migration.
