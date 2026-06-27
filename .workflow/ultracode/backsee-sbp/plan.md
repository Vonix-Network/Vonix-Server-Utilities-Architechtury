# Plan — `/backsee` SBP extension for VSU

## Goal
Extend the existing `/backsee <target> [slot]` so it transparently opens **Sophisticated Backpacks** in addition to the vanilla/shulker NBT formats it already supports. Admins keep one command for "look in their backpack" regardless of which backpack mod the modpack ships.

## Success criteria
- `/backsee <target>` on a server WITHOUT SBP installed: behaves exactly as today.
- `/backsee <target>` on a server WITH SBP installed: opens an SBP backpack if no legacy-NBT-format backpack is found first.
- `/backsee <target> <slot>` opens the backpack in the named hotbar/inventory slot specifically (new optional arg).
- Writes through the GUI persist (edits land in `BackpackStorage`, marked dirty, saved at next world save tick).
- VSU still compiles + boots clean on every template (1.18.2 / 1.19.2 / 1.20.1 / 1.21.1).
- No `compileOnly` on SBP — license-safe pure-reflection bridge.

## Constraints
- SBP is All-Rights-Reserved. No SBP imports, no SBP bytecode redistributed. Reflection only.
- 1.21.1 path: SBP for 1.21.1 may have moved to `DataComponents` instead of legacy NBT — bridge has to probe both, fall back gracefully.
- Run on server thread only (capability/NBT access).
- Existing `/backsee` semantics preserved exactly when SBP path isn't reachable.

## Risk: **low**
- Single command extended; one new helper file.
- Failure mode: SBP reflection fails → log-WARN-once → falls through to "No backpack found" message. No crash, no NPE.

## Work packets

### P0 — Implement on 1.18.2 template (parent session, sequential)
**Files:**
- `common/src/main/java/network/vonix/serverutilities/inventory/SbpBackpackBridge.java` (NEW) — reflection bridge:
  - `static boolean isAvailable()` — checks for `net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage` on classpath.
  - `static Optional<UUID> readContentsUuid(ItemStack)` — reads the `contentsUuid` NBT tag (no reflection; just NBT).
  - `static Optional<CompoundTag> getContentsTag(ServerLevel level, UUID)` — reflectively calls `BackpackStorage.get().getOrCreateBackpackContents(uuid)`.
  - `static void markDirty(ServerLevel level)` — reflectively calls `BackpackStorage.get().setDirty()`.
  - All methods log-WARN-once on `NoClassDefFoundError` / `NoSuchMethodException` / `IllegalAccessException` and return Optional.empty() / false.
- `common/src/main/java/network/vonix/serverutilities/command/UtilityCommands.java` (EDIT):
  - Add optional `slot` arg to `/backsee` command tree (backward compatible — no slot = old behavior).
  - In `openBackpack`: after the existing legacy-NBT loop fails, check `SbpBackpackBridge.isAvailable()` and run the SBP fallback:
    1. Iterate target inventory (or jump to specified slot if arg provided).
    2. For each stack, `readContentsUuid(stack)`.
    3. If present, `getContentsTag(level, uuid)` → read its `inventory` CompoundTag → `ListTag` of items.
    4. Build a `SimpleContainer(54)` populated from that ListTag.
    5. `setChanged` override writes back through the same CompoundTag and calls `markDirty`.
    6. Open as chest GUI titled "SBP Backpack: <player>".

**Verification:** `./gradlew :forge:compileJava` clean on 1.18.2 template.

### P1, P2, P3 — Port to other templates (3 parallel write-capable subagents)
- **P1:** 1.19.2 template
- **P2:** 1.20.1 template
- **P3:** 1.21.1 template (NeoForge — needs `DataComponents.CUSTOM_DATA` probe alongside legacy NBT)

Each subagent:
- Reads the canonical 1.18.2 implementation from `.workflow/ultracode/backsee-sbp/packets/p0_1182_files.md` (parent will write this after P0 builds).
- Copies the two file changes into the owned template directory, adjusts for API drift (`Component.literal` vs `new TextComponent`; capability lookup name; 1.21.1 DataComponents probe).
- Runs `./gradlew :forge:compileJava` or `:neoforge:compileJava`, returns exit code + log tail.
- Does NOT touch any file outside its own template directory.

### P4 — Integration (parent session, sequential)
- Diff each template's `SbpBackpackBridge.java` against 1.18.2 reference; only API-drift hunks should differ.
- Run full `./gradlew build` on each template.
- Update root + per-template `CHANGELOG.md` (Keep-a-Changelog).
- Bump `mod_version` in every template's `gradle.properties` (minor — new feature, backwards-compatible).
- README addition: "/backsee now opens Sophisticated Backpacks when present."
- **No commit, no push, no release.** Hand the diff and the per-template build matrix to WeedMeister for sign-off.

## Eval contract (inline)
- **Outcome:** `/backsee` works for SBP-bearing inventory on all 4 templates; no regression on legacy path; no boot break SBP-absent.
- **Shared surfaces:** `SbpBackpackBridge` (new), `UtilityCommands.openBackpack` (extended). Bridge contract is `isAvailable` / `readContentsUuid` / `getContentsTag` / `markDirty`.
- **Required checks:** per-template `compileJava`; cross-template diff of `SbpBackpackBridge`; SBP-absent boot smoke test.
- **Blocking conditions:** any template fails compile; SBP-absent boot logs error or fails.
- **Handoff evidence:** subagents return file paths written + gradle exit code + last 20 lines of log.

## Integration policy
- Parent owns final compile check, doesn't trust subagent self-reports for the go/no-go.
- Parent diffs every template's bridge against the 1.18.2 reference before declaring port-equivalent.

## Verification plan
- Compile every template.
- (Optional, time-permitting) Boot test in `/root/test-berk` (which already has SBP installed) — `/backsee` should open a real SBP backpack.

## Completion criteria
- All 4 templates compile clean.
- `final-report.md` lists files changed + verification matrix.
- WeedMeister decides on deploy/release.
