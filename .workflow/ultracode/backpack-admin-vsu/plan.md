# Plan — `/backpackadmin` for Vonix Server Utilities

## Goal
Add a `/backpackadmin` command tree to VSU that audits and recovers Sophisticated Backpacks state on any server that has the mod installed. SBP is a soft-dep — the command is registered only when present; VSU still loads cleanly without it.

## Success criteria
- `/backpackadmin audit` — scans loaded chunks + player inventories, cross-references against `BackpackStorage`, reports:
  - **ghosts** (item has `contentsUuid` tag but no matching entry in `BackpackStorage`)
  - **orphans** (entry in `BackpackStorage` but no live item referencing it; broken down by "accessed in last 30d" vs older)
  - **duplicate UUIDs** (two+ live items pointing at the same `BackpackStorage` entry)
- `/backpackadmin show <player>` — list all backpacks in a player's inventory + their UUIDs + storage-entry sizes.
- `/backpackadmin link <player> <slot> <uuid>` — re-attach a known UUID to an item that lost it (recovery).
- `/backpackadmin orphans purge [--older-than 30d]` — bounded admin command for prune.
- Feature-gated via Venary `FeatureRegistry` (`backpack_admin` key), defaults to off, op-only.
- Works on every MC version VSU ships (1.18.2 / 1.19.2 / 1.20.1 / 1.21.1) and on both Forge and Fabric where SBP exists.

## Constraints
- **SBP is All-Rights-Reserved** — we cannot link against its compiled API at compile time on a redistributable jar (per `patching-arr-mods-via-mixin-coremod` reference). Solution: pure-reflection bridge, mirroring the `LuckPermsBridge` pattern. No `compileOnly libs/sbp.jar`, no API imports. Reflection target classes:
  - `net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage` — `get()`, `getOrCreateBackpackContents(UUID)`, `removeBackpackContents(UUID)`, internal `backpackContents` map (reflective field access), `getAccessLogs()`.
  - `net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper.getCapabilityInstance()` — to read `contentsUuid` from item capability.
  - Fallback: read the raw NBT tag `contentsUuid` directly off the item — no reflection needed for the UUID read.
- Class names DO change across the 1.18.x → 1.21.x SBP versions; bridge maps each via a per-MC-version constants class.
- Loaded chunks only (no force-loading entire world).
- No write operations to `BackpackStorage` except through SBP's own `removeBackpackContents` — never reach into the map directly to write.
- License-safe shipping: VSU stays MIT, reflection ≠ derivation of SBP code.

## Risk: **medium**
- Reflection breakage between SBP versions (acceptable: bridge fails closed, feature disables, VSU keeps working).
- Map iteration on `BackpackStorage.backpackContents` during a tick (mitigation: snapshot to ArrayList before iterating).
- Recovery command — `link` writes to player NBT, must run on server thread.
- Audit on a large world: 30k+ chunks scanned. Mitigation: only LOADED chunks, work-throttle with `Executor.submit` and report when done.

## Approval gates
- ✅ User signs off on this plan before any code is written (we are here).
- Production deploy is the user's call, not auto-deployed by Hermes.

## Mode: **delegated** (4 parallel write-capable packets after core lands in common/)

## Work packets

### Packet 0 — Core common/ scaffolding (parent session, sequential, blocks everything)
**Owner:** parent (Hermes)
**Files:**
- `common/src/main/java/network/vonix/serverutilities/backpack/SbpBridge.java` (new) — reflection bridge, soft-dep, mirrors `LuckPermsBridge` shape.
- `common/src/main/java/network/vonix/serverutilities/backpack/BackpackAudit.java` (new) — audit data model + scan logic.
- `common/src/main/java/network/vonix/serverutilities/backpack/BackpackAdminCommand.java` (new) — command tree.
- `common/src/main/java/network/vonix/serverutilities/features/FeatureKeys.java` (edit if exists, or create constant) — add `BACKPACK_ADMIN`.
- `common/src/main/java/network/vonix/serverutilities/command/ModCommands.java` (edit) — register `BackpackAdminCommand` under feature gate.

This is the 1.18.2 template's `common/` — gets ported to the other 3 templates via parallel packets.

**Verification:** `./gradlew :forge:compileJava` on 1.18.2 template, must compile clean.

### Packets 1-3 — Port to other MC version templates (parallel, delegated)
After packet 0 builds clean on 1.18.2, fan three parallel write-capable subagents at:
- **Packet 1:** 1.19.2 template
- **Packet 2:** 1.20.1 template
- **Packet 3:** 1.21.1 template (neoforge — different APIs, biggest delta)

Each packet copies the 4 new files from 1.18.2 common/, adjusts:
- `Component`/`TextComponent` API drift (1.18 → 1.19+)
- `Player.getInventory()` shape stable, but capability lookup name changed in 1.20+
- 1.21.1 uses `DataComponents` not item NBT — bridge has to read `DataComponents.CUSTOM_DATA` and dig out `contentsUuid`. This packet gets the heaviest port.

**Each subagent's prompt includes:**
- Absolute path to template root.
- The 4 files from packet 0 as ground truth (read from `.workflow/.../packets/`).
- API drift table (provided per-MC-version).
- "You are not alone in the codebase. Other agents may be editing other templates. Do not touch files outside your owned template directory."

**Verification:** each subagent must run `./gradlew :forge:compileJava` (or `:neoforge:compileJava` for 1.21.1) in its template and return the exit code + last 20 lines of output. No release until all 4 templates compile.

### Packet 4 — Integration (parent session, sequential)
- Run `./gradlew build` across all 4 templates to confirm no break.
- Update root `CHANGELOG.md` + per-template `CHANGELOG.md` per the standing Keep-a-Changelog rule.
- Bump `mod_version` across all 4 templates.
- README addition under "Commands" section.

**No commit, no push, no release** — hand the diff + the matrix to WeedMeister for sign-off and the Berk maintenance window.

## Eval contract (inline)
- **Outcome:** All 4 templates compile clean with the new feature; SBP-absent boot works on every template; SBP-present boot exposes `/backpackadmin audit` returning ≥0 results without crash.
- **Shared surfaces:** `network.vonix.serverutilities.backpack.*` package introduced in `common/` of each template — each template's copy must be functionally identical to packet 0's reference, with only API-drift adjustments.
- **Required checks:** `gradlew :{forge|neoforge}:compileJava` per template; visual diff of the 4 `SbpBridge.java` copies to confirm only API-drift differences.
- **Blocking conditions:** any template fails to compile; bridge throws on SBP-absent boot.
- **Handoff evidence:** each subagent returns the absolute path of each file it wrote + the gradle exit code + log tail.

## Integration policy
- Parent reviews every subagent's reported file list.
- Parent diffs each template's `SbpBridge.java` against 1.18.2's to confirm structural equivalence (only API-drift hunks differ).
- Parent runs the gradle build itself for verification — does not trust subagent self-reports for the final go/no-go.

## Verification plan
- Per-template `gradlew :forge:compileJava` (or `:neoforge:`) on each template.
- Boot a test server in `/root/test-berk` without SBP installed → feature should disable cleanly, no NPE in log.
- Boot the test server WITH SBP → `/backpackadmin audit` runs to completion with all-zeros on a fresh world.
- Insert a known-ghost backpack via `/give`-then-clobber, re-run audit, confirm it's flagged.

## Completion criteria
- All 4 templates ship jars that boot in both modes.
- `final-report.md` lists every file changed + verification matrix.
- Hand release decision back to WeedMeister.
