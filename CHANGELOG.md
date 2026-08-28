# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-08-28

Common-generation repository release. This release starts the shared repository/layout and embedded version line at `2.0.0` without rewriting historical release tags.

### Added
- Added the Minecraft **26.1.2 / NeoForge 26.1.2.93** lane using ModDevGradle 2.0.140 and Java 25.
- Carried the 1.7.1 TempMute hydration, optimistic enforcement, persistence-failure, and expiry-safety fixes into the 26.1.2 target.
- Added target-specific `ServerPlayer.nameAndId()` adaptation for the NeoForge 26.1 `PlayerList.isOp` API and executable regression probes.
- Documented the five-lane, one-repository layout in `docs/COMMON-V2-REPOSITORY.md`.

### Build
- All supported lanes embed `2.0.0`; the 26.1.2 artifact is `vonix_server_utilities-2.0.0.jar`.
- The tag-triggered CI workflow is the source of build/package evidence for this release. Earlier R14 static evidence is not reused after the embedded version metadata change.
- CI runs Loom under Java 21 for the 1.18.2–1.21.1 lanes and Java 25/Gradle 9.2.0 for 26.1.2.

## [Unreleased]

## [1.7.1] - 2026-08-25

### Fixed
- **TempMute enforcement races** across all four Minecraft templates:
  - apply mute state before asynchronous persistence so the first chat/command is blocked;
  - preserve pending writes during startup hydration;
  - fail closed when active-mute hydration cannot read SQLite;
  - reconcile overlapping active mutes before clearing enforcement on expiry or revocation;
  - report persistence and reconciliation uncertainty instead of claiming success.
- Added common-module regression coverage for pre-hydration fail-closed enforcement, hydration readiness recovery, optimistic enforcement, pending persistence, failed writes, and expiry-safe state removal.

### Build
- Rebuilt the eight supported Fabric/Forge/NeoForge artifacts for Minecraft 1.18.2, 1.19.2, 1.20.1, and 1.21.1 with Java 17/21 as applicable.

## [1.7.0] - 2026-08-16

### Fixed
- **Crate command MC API ports** so production loaders compile with crate
  features retained (`CrateCommands.java` per template):
  - **1.20.1 Forge:** replaced removed `TextComponent` with
    `Component.literal`; `sendSuccess` uses `Supplier<Component>`; prize
    dispatch uses `performPrefixedCommand` (returns `int`).
  - **1.21.1 NeoForge:** `sendSuccess` uses `Supplier<Component>`;
    `performPrefixedCommand` is `void` — claim complete/refund is driven by
    `CommandResultCallback` on the command source stack.
  - **1.19.2 Forge:** prize dispatch uses `performPrefixedCommand` (returns
    `int`); chat already used `Component.literal`.
  - **1.18.2 Forge:** unchanged crate APIs (`TextComponent` +
    `performCommand`); kept compiling as regression check.

### Build
- Production jars for **1.18.2 Forge**, **1.19.2 Forge**, **1.20.1 Forge**,
  and **1.21.1 NeoForge** report `version = "1.7.0"` in
  `META-INF/mods.toml` / `neoforge.mods.toml`, embed
  `META-INF/jarjar/sqlite-jdbc-3.46.1.0.jar`, and do not leak `org/sqlite`
  at the jar root.
- Independent `clean :*:build --rerun-tasks` evidence (Grok 4.6):
  `compileJava` / `jar` / `remapJar` executed (not `UP-TO-DATE`) for
  1.18.2 Forge, 1.20.1 Forge, 1.21.1 NeoForge, and optional 1.19.2 Forge.

## [1.6.1] - 2026-07-02

### Fixed
- **Critical: v1.6.0 permission lockout.** `PermissionGate.check()` in
  v1.6.0 treated LuckPerms as *authoritative* — when LP was present the
  vanilla op-level fallback was never consulted, so any node that wasn't
  explicitly granted read as "deny" for every non-console source.
  Result: every server that installed LP without also running the
  `default-player` recipe from `docs/PERMISSIONS.md` locked all players
  (including ops) out of every VSU command after upgrading. v1.6.1
  changes the composition so **LP grant and op-fallback form a UNION**
  — the source passes if it holds the LP node OR meets the vanilla op
  level, either alone is sufficient. Ops keep working the moment they
  OP, non-op players still get every `opFallback=0` command by default,
  and explicit LP grants continue to extend commands to non-op players.
  Applies to all four templates (1.18.2, 1.19.2, 1.20.1, 1.21.1).

### Docs
- **`docs/PERMISSIONS.md`** — documented the v1.6.0 lockout, the new
  UNION semantics, and a three-line wildcard hotpatch
  (`lp group default permission set vsu.command.* true` +
  `vsu.admin.*` / `vsu.mod.*` on staff) for operators stuck on 1.6.0
  who need to unblock their server before rolling the 1.6.1 jar.

## [1.6.0] - 2026-06-30

### Added
- **Moderation subsystem** — 11 new commands shipped across all 4 templates:
  `/tempban`, `/ban`, `/unban`, `/banlist`, `/tempmute`, `/mute`, `/unmute`,
  `/kick`, `/warn`, `/warnings`, `/clearwarnings`. SQLite-backed via the
  new `punishments` table (lazy schema bootstrap on first call), with an
  in-memory `MuteState` cache and a 60-second `ExpirySweeper` that promotes
  expired rows from `active=1` to `active=0` automatically. Tellraw +
  delayed disconnect for online tempbans so the player reads the reason
  before the kick lands. Tab-complete suggests `1h / 6h / 1d / 7d / 30d /
  perm` for `<duration>`; online players ∪ historical names from
  `punishments.target_name` for `<player>`.
- **`DurationParser`** — accepts `30s`, `5m`, `2h`, `7d`, `4w`, `1mo`, `1y`,
  `perm/permanent/never`. Composes (`1d12h`, `7d6h30m`). Rejects negative,
  unitless, or `> 100y`.
- **`PermissionGate`** — composes `FeatureGate` + LuckPerms node check +
  vanilla op-level fallback. Console source always passes. The single
  helper every command uses for `.requires(...)`.
- **`LuckPermsBridge.hasPermission(UUID, String)`** — synchronous LP node
  check via `User.getCachedData().getPermissionData(...).checkPermission`.
  Returns `false` on any LP failure (fail-closed; callers check
  `isPresent()` first).
- **LP bypass nodes** — `vsu.bypass.mute` exempts a player from chat-mute
  enforcement (checked at `MuteState.isMuted` read-time, so revoking the
  node re-enforces an existing mute without an admin action).
  `vsu.bypass.ban` skips login-ban enforcement (logs a `bypassed` entry;
  active row stays in the DB).
- **Per-loader enforcement listeners** — NeoForge / Forge: `PlayerEvent.
  PlayerLoggedInEvent` (login-ban), `ServerChatEvent` (chat-mute),
  `CommandEvent` (chat-style command intercept for `/me /msg /tell /w /r
  /reply /broadcast /bc /gc`). Fabric: `ServerPlayConnectionEvents.JOIN`
  + `ServerMessageEvents.ALLOW_CHAT_MESSAGE` + `ALLOW_COMMAND_MESSAGE`
  on 1.19.2+. **1.18.2 Fabric** uses a Mixin into
  `ServerGamePacketListenerImpl.handleChat` because
  `fabric-message-api-v1` isn't present in fabric-api `0.77.0+1.18.2`.
- **Permission node taxonomy** — `vsu.command.X` (basic player commands),
  `vsu.admin.X` (op-grade), `vsu.mod.X` (moderation), `vsu.bypass.X`
  (escape hatches). See `docs/PERMISSIONS.md` for the canonical table.
- **Documentation** — `docs/V1.6.0-SPEC.md` (authoritative implementation
  contract), `docs/COMMANDS.md` (alphabetical command reference),
  `docs/PERMISSIONS.md` (node tree + LuckPerms recipe groups),
  `docs/MODERATION.md` (operator workflow, duration syntax, audit
  queries, escalation policy template), `docs/GAP-ANALYSIS-v1.6.0.md`
  (research deliverable comparing VSU's command surface to FTB
  Essentials / EssentialsX / Essential Commands — P0 shortlist for
  v1.6.1: `/rtp`, `/mail`, `/afk`, `/disposal`, `/rules`).

### Changed
- **Every existing command refit to real permission nodes.** Bare
  `s -> s.hasPermission(N)` predicates replaced with
  `PermissionGate.requires(featureKey, "vsu.X.Y", N)`. LP-managed servers
  now get proper node-based access control; servers without LP fall back
  to the original vanilla op-level behaviour automatically.
- **`/setwarp` / `/delwarp`** — moved to `vsu.admin.warp` (op level 3).
  Previously op-only via inline `hasPermission(3)`.
- **`/link` / `/unlink`** — now gated on `vsu.command.link` (op 0 / LP
  defaulted everyone). Previously ungated.
- **README rewritten** to represent the whole project — feature
  highlights, per-MC-version support matrix, install + quick-start +
  docs index, building + versioning + license. 7.4 KB.

### Notes
- API drift across the four templates is hidden behind the
  `PermissionGate` boundary. 1.18.2 uses `getEntity() instanceof
  ServerPlayer` because `CommandSourceStack.isPlayer()` / `.getPlayer()`
  don't exist; the API contract is identical regardless.
- The new `punishments` SQLite table is created lazily on the first
  moderation command — no migration step required.
- LuckPerms remains optional (soft-dep). When absent, every `.requires`
  falls back to the listed op level; `vsu.bypass.*` nodes are inert.

### Compile matrix
| MC | Loader | JDK |
|---|---|---|
| 1.18.2 | Forge 40.x | 17 |
| 1.18.2 | Fabric | 17 |
| 1.19.2 | Forge 43.x | 17 |
| 1.19.2 | Fabric | 17 |
| 1.20.1 | Forge 47.x | 17 |
| 1.20.1 | Fabric | 17 |
| 1.21.1 | NeoForge 21.x | 21 |
| 1.21.1 | Fabric | 21 |

All 8 build targets: `BUILD SUCCESSFUL`.

## [1.5.2] - 2026-06-30

### Fixed
- JPMS module-layer collisions on `org.sqlite.date` / `org.sqlite.jdbc3` /
  `org.sqlite.jdbc4` with mods like biolith that also depend on sqlite-jdbc.
  Hardened the v1.5.1 JarInJar approach so sqlite-jdbc only ships via JiJ
  (Forge/NeoForge) or loom `include` (Fabric) — never via `shadowBundle`.
- Fabric jar no longer relocates `org.sqlite` (relocation broke JNI binding
  to `libsqlitejdbc.so`). Fabric now uses loom's `include` configuration,
  same dedup story as Forge/NeoForge JiJ.

### Changed
- `common/build.gradle`: `org.xerial:sqlite-jdbc` moved from `implementation`
  to `compileOnly` across all 4 templates. The `:common` project compiles
  against sqlite types but does not pipe the artifact through to the
  per-loader `shadowBundle`.
- `shadowJar`: belt-and-braces `exclude 'org/sqlite/**'` added to every
  loader subproject as defence-in-depth.

## [1.5.1] - 2026-06-29

### Fixed
- **JPMS `ResolutionException` at boot when VSU and VonixGuardian are both installed.**
  VSU 1.2.0–1.5.0 shaded `org.xerial:sqlite-jdbc:3.45.1.0` directly into the
  Forge/NeoForge jar at the original `org.sqlite.*` package. VonixGuardian 1.0.1
  ships sqlite-jdbc as a Forge JarInJar nested artifact. With both mods present,
  the JVM module layer saw two distinct modules both exporting
  `org.sqlite.jdbc3` and refused to resolve:
  ```
  java.lang.module.ResolutionException: Modules vonix_server_utilities and
  org.xerial.sqlitejdbc export package org.sqlite.jdbc3 to module create_wizardry
  ```
  The server bootstrap aborted silently (no crash report — JVM is killed during
  `ModuleLayer` construction).
- Shade+relocate is NOT a viable fix: the bundled `libsqlitejdbc.{so,dylib,dll}`
  has `FindClass("org/sqlite/core/NativeDB")` baked into `JNI_OnLoad`, so any
  attempt to relocate `org.sqlite` → `network.vonix.serverutilities.shadow.sqlite`
  causes `NoClassDefFoundError: org/sqlite/core/NativeDB` on the first DB call
  (empirically verified on HTTYD 1.18.2 + Sunlit Valley 1.20.1).

### Changed
- **All four Forge/NeoForge templates now ship `org.xerial:sqlite-jdbc` via
  Forge JarInJar (nested jar) at version `3.46.1.0`** — matching VonixGuardian
  1.0.1's pinned coords exactly. Forge's nested-jar resolver dedupes by Maven
  coordinates, so when VSU and VG are loaded together only one sqlite module
  enters the layer. This is the same pattern Mekanism, JEI, and LuckPerms use
  for shared library distribution.
- Each Forge/NeoForge build now:
  - Adds a `nestedJar` configuration that resolves `org.xerial:sqlite-jdbc:3.46.1.0`
    from `mavenCentral()` (non-transitive).
  - In `remapJar.doLast`, appends `META-INF/jarjar/sqlite-jdbc-3.46.1.0.jar` plus
    a `META-INF/jarjar/metadata.json` descriptor onto the user-facing jar via
    raw `ZipOutputStream`. (Architectury+loom does not pipe ForgeGradle's `jarJar`
    task through to the final remapped jar, so the merge is performed manually —
    same approach VG uses.)
  - Removes `shadowBundle 'org.xerial:sqlite-jdbc:...'` from the `dependencies`
    block — sqlite is no longer fat-jarred at the top level.
- The 1.19.2 Forge template additionally drops its stale
  `relocate 'org.sqlite' → '...shadow.sqlite'` rule. With JarInJar there is
  nothing at the top level of the outer jar to relocate.
- Fabric subprojects are **unchanged** — Fabric uses a flat classloader, has no
  module layer, and was never affected by this collision.

### Fabric — unchanged
- All four Fabric subprojects continue to ship sqlite-jdbc via `shadowBundle`
  exactly as in 1.5.0. The JarInJar switch only applies to Forge/NeoForge,
  where JPMS is the underlying mechanism.

### Verification
For each of the 4 Forge/NeoForge jars built at 1.5.1:
- `META-INF/jarjar/sqlite-jdbc-3.46.1.0.jar` present, 14,123,618 bytes
  (byte-identical to the nested jar in VonixGuardian 1.0.1).
- `META-INF/jarjar/metadata.json` declares `org.xerial:sqlite-jdbc` at
  `[3.46.1.0,)`, pinned to artifactVersion `3.46.1.0`.
- Zero `org/sqlite/*` entries at the jar root.
- Zero `network/vonix/serverutilities/shadow/sqlite/*` entries.
- `org.slf4j` is still relocated to `network.vonix.serverutilities.shadow.slf4j`
  (unchanged — pure-Java, no JNI; needed to avoid a separate slf4j JPMS
  collision with mods like `via_romana` on 1.20.1 and `comforts` on 1.21.1).
- **Note:** `org.slf4j:slf4j-api:1.7.36` is now declared explicitly in
  `shadowBundle` on every Forge/NeoForge subproject. Previously slf4j-api
  arrived transitively via `shadowBundle 'org.xerial:sqlite-jdbc:…'`. After
  moving sqlite-jdbc out of `shadowBundle` and into the `nestedJar`
  configuration, slf4j stopped being shaded, leaving `relocate 'org.slf4j' →
  '…shadow.slf4j'` to rewrite VSU's own `LoggerFactory` references to a class
  that didn't exist in the final jar (`NoClassDefFoundError:
  network/vonix/serverutilities/shadow/slf4j/LoggerFactory` during mod-init).
  Verified boot-clean on a prod-parity Connector+Forge 1.20.1 staging server
  with VonixGuardian 1.0.1 + VSU 1.5.1: `Done (3.347s)!`, `VonixGuardian
  online.`, 69 KiB `vonixguardian.db` written through the JarInJar sqlite
  native (so JNI works and Forge's nested-jar resolver successfully dedup'd
  both mods' sqlite-jdbc-3.46.1.0 down to a single `org.sqlite` module).

## [1.5.0] - 2026-06-27

`InventoryProvider` SPI under `network.vonix.serverutilities.api` —
the hardcoded `/backsee` passes (Curios → DataComponents → capability
walk → legacy NBT) are now pluggable. 3rd-party mods register custom
resolvers via explicit `InventoryProviderRegistry.register(...)` calls
or `META-INF/services/network.vonix.serverutilities.api.InventoryProvider`,
no VSU release required. Backwards-compatible: no behaviour change.

### Added
- **Public SPI** under `network.vonix.serverutilities.api` (marked
  SemVer-stable in `package-info.java`; internal packages remain
  unstable):
  - `InventoryProvider` — stable `id()`, `priority()` (lower runs
    first), `Optional<InventoryView> resolve(ServerPlayer, int slotHint)`.
  - `InventoryView` — adapter the provider returns; `getSlots()`,
    `getStackInSlot(int)`, `setStackInSlot(int, ItemStack)`,
    `persist()`, `getTitle()`. Each provider owns its own write-back
    semantics through `persist()` — `/backsee` doesn't need to know the
    item's storage layout.
  - `InventoryProviderRegistry` — explicit `register(...)` plus lazy
    `ServiceLoader.load(InventoryProvider.class, ...)` scan on first
    `providers()` access. Last-write-wins by id so external mods can
    override built-ins.
- **Built-in providers** (registered at VSU init, wrap existing 1.4.0
  passes — no functional change):
  - `vonix:curios`     — priority 100, wraps `CuriosInventoryBridge`.
  - `vonix:data_components` — priority 150, **1.21.1 only**, wraps the
    `DataComponents.CONTAINER` pass.
  - `vonix:capability` — priority 200, wraps `CapabilityInventoryBridge`.
  - `vonix:legacy_nbt` — priority 300, the legacy `Items` /
    `inventory` / `BlockEntityTag.Items` walk on
    1.18.2 / 1.19.2 / 1.20.1. On 1.21.1 this provider is registered as a
    no-op stub so a single 3rd-party `META-INF/services` registration
    list works across every target.

### Changed
- `UtilityCommands.openBackpack` dispatches through the registry instead
  of a hardcoded pass sequence. Providers are sorted by `priority()`,
  iterated in order, first non-empty `resolve(...)` wins. The
  `SimpleContainer` / `ChestMenu.sixRows` GUI boilerplate is built once
  in the command and parameterised on `InventoryView` — net code
  reduction.

### Safety
- ServiceLoader scan is wrapped in a catch-all; broken external provider
  jars log a warning and do not break `/backsee`.
- All built-in providers fail-closed on reflection (inherited from the
  1.3.0 / 1.4.0 bridges they wrap).

### Compile matrix (all clean)

| Template | Task                    | JDK | Exit |
|----------|-------------------------|-----|------|
| 1.18.2   | `:forge:compileJava`    | 17  | 0    |
| 1.19.2   | `:forge:compileJava`    | 17  | 0    |
| 1.20.1   | `:forge:compileJava`    | 17  | 0    |
| 1.21.1   | `:neoforge:compileJava` | 21  | 0    |

## [1.4.0] - 2026-06-27

LuckPerms NCDFE crash fix + Curios soft-dep layer on `/backsee`. Ships
on all 4 templates. Backwards-compatible: no config changes, no public
API removals. The crash class affects any modpack that ships VSU
without LuckPerms (Sunlit Cobblemon was the canary).

### Fixed
- **Player-join crash (`NoClassDefFoundError: net/luckperms/api/node/Node`)**
  on any modpack that does not bundle LuckPerms. Root cause: the prior
  `LuckPermsBridge` had `net.luckperms.api.*` types on its public surface
  (return types, field types). When `RankSyncTask.onJoin` first referenced
  `LuckPermsBridge`, the JVM linked the class and resolved every type in
  its constant pool — NCDFE fired during *linking*, OUTSIDE the runtime
  try/catch inside `LuckPermsBridge.get()`. Fix: holder-class isolation
  pattern (JDK canonical, see `java.lang.invoke.MethodHandleStatics`).
  `LuckPermsBridge` is now a probe-only public class with **zero**
  `net.luckperms.*` imports anywhere. Its static initialiser does a
  `Class.forName("net.luckperms.api.LuckPermsProvider", initialize=false,
  classloader)` probe and caches the result in a final boolean
  `LP_PRESENT`. The LP-typed code moved to package-private
  `LuckPermsBridgeImpl` — referenced only inside method bodies (never in
  field types), so the JVM defers linking the Impl class until the first
  call site is hit. When LP is absent, no call site is ever reached, the
  Impl class is never linked, and NCDFE cannot fire. Defensive
  `try { ... } catch (LinkageError | RuntimeException t)` belts wrap
  `RankSyncTask.onJoin`, `RankGroupSyncer.syncAll`, and every public
  `LuckPermsBridge` method as belt-and-braces.

### Added
- **Curios soft-dep layer for `/backsee`**
  (`inventory/CuriosInventoryBridge.java` + new Pass 0 in
  `UtilityCommands.openBackpack`). When Curios is present, `/backsee`
  now scans every curio slot on the target player (cosmetic, charm,
  ring, belt, etc.) before falling through to the existing
  capability-walk / NBT-walk / DataComponents passes. If any curio stack
  itself exposes `IItemHandler`, it is opened with the same
  read-and-write-back GUI used for main-inventory backpacks. Reflection
  only — Curios absent = silently skipped. Probe path:
  `CuriosApi.getCuriosInventory(LivingEntity)` →
  `ICuriosItemHandler.getCurios()` →
  `ICurioStacksHandler.getStacks()` → `IItemHandler.getStackInSlot(i)`.
  The Curios API FQN is the same on 1.18.2 / 1.19.2 / 1.20.1 Forge and
  1.21.1 NeoForge.

### Changed
- `/backsee` pass order is now:
  - 1.18.2 / 1.19.2 / 1.20.1: **(0)** Curios slot scan (new) →
    **(1)** capability walk → **(2)** legacy NBT walk.
  - 1.21.1: **(0)** Curios slot scan (new) →
    **(1)** `DataComponents.CONTAINER` → **(2)** capability walk.
  - Pass 0 is skipped when an explicit slot index is supplied
    (`/backsee <target> <0..40>`); curio slots are not part of the
    main-inventory index space.
- `LuckPermsBridge` public surface refactored — internal-only breaking
  change. `get()` returning `Optional<LuckPerms>` removed; replaced by
  semantic methods (`isPresent()`, `ensureGroupExists(...)`,
  `setUserGroups(...)`, `getUserPrefixInfo(uuid)`) that return only
  plain Java types or VSU-local POJOs. No public consumer outside the
  mod's own `common/` source touched LP-typed return values.

### Safety
- Reflection fail-closed across both new bridges: any `LinkageError` /
  `ReflectiveOperationException` logs once and disables the affected
  code path. NCDFE cannot escape `LuckPermsBridge`; ReflectiveOpEx
  cannot escape `CuriosInventoryBridge`.

### Compile matrix (all clean)

| Template | Task                    | JDK | Exit |
|----------|-------------------------|-----|------|
| 1.18.2   | `:forge:compileJava`    | 17  | 0    |
| 1.19.2   | `:forge:compileJava`    | 17  | 0    |
| 1.20.1   | `:forge:compileJava`    | 17  | 0    |
| 1.21.1   | `:neoforge:compileJava` | 21  | 0    |

## [1.3.0] - 2026-06-27

Backpack-viewer rewrite. Backwards-compatible: `/backsee <player>` keeps
its prior behaviour on any modpack that does not expose item inventories
through the Forge / NeoForge `IItemHandler` capability. No new soft-deps.

### Added
- **Universal `IItemHandler` capability walk in `/backsee`**
  (`inventory/CapabilityInventoryBridge.java`,
  `command/UtilityCommands.openBackpack`): `/backsee <target>` now opens
  any backpack-style item that exposes the Forge / NeoForge
  `IItemHandler` capability — Sophisticated Backpacks & Storage shulkers,
  vanilla shulker boxes (via the Forge default cap provider), Iron Chests
  shulkers, Traveler's Backpack, Iron Backpacks, FunctionalStorage
  drawers-as-item, and any other well-behaved capability-exposing item.
  Edits made through the GUI are written back through
  `IItemHandlerModifiable#setStackInSlot`, so each item's own capability
  provider handles persistence — VSU does not need to know the item's
  storage layout. The capability system is detected and called by
  reflection only; `common/` remains platform-agnostic and the same
  source compiles on all 4 templates.
- **Optional `slot` argument on `/backsee`** (`/backsee <target> [0..40]`):
  scan only the named inventory slot instead of the player's full
  inventory. Backwards-compatible — calling `/backsee <target>` without a
  slot keeps the old "first match wins" behaviour.

### Changed
- `/backsee` pass order: (1) `IItemHandler` capability walk, (2) legacy
  raw-NBT walk (vanilla `Items` / `inventory` / `BlockEntityTag.Items`
  lists). On 1.21.1 NeoForge the order is (1) vanilla
  `DataComponents.CONTAINER`, (2) `IItemHandler` capability walk.

### Removed
- `inventory/SbpBackpackBridge.java` (the 1.3.0 release candidate's
  Sophisticated-Backpacks-specific reflection bridge into
  `BackpackStorage` saved-data). Subsumed by the universal capability
  walk — SBP exposes its inventory through `IItemHandler` like every
  other capability-aware item, so a single bridge handles it correctly
  without hard-coding SBP's class names or NBT layout.

### Pitfalls handled
- Fabric (no Forge cap system on the classpath): probe returns
  `isAvailable() == false` once at startup, the capability pass is a
  no-op, and `/backsee` falls through to the legacy NBT walk.
- NeoForge `Capabilities$ItemHandler.ITEM` vs Forge
  `ForgeCapabilities.ITEM_HANDLER` vs older Forge
  `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`: probed in that order,
  first hit wins, the rest are silently absent.
- `IItemHandler` package location (`net.neoforged.neoforge.items` vs
  `net.minecraftforge.items`): both are probed; whichever loads is used.
- Read-only `IItemHandler`s (no `IItemHandlerModifiable`): capability
  pass skips them and falls through to the legacy NBT walk so the GUI
  doesn't silently swallow edits.
- Every reflective call is wrapped — any `LinkageError` /
  `ReflectiveOperationException` logs a single WARN and returns
  `Optional.empty()`. No `NoClassDefFoundError` can escape the bridge.

## [1.2.0] - 2026-06-23

Donation-rank automation and Venary site integration. All changes are
backward compatible — every new subsystem is a no-op when its dependency
(LuckPerms, Venary endpoint) is absent, so existing servers upgrade cleanly.

### Added
- **Donation rank sync** (`donation_ranks/`): `LuckPermsBridge`, `RankGroupSyncer`,
  `RankSyncTask`. Applies and removes LuckPerms groups in response to donation
  state — auto-apply on join, live removal while online, and expiry removal of
  timed grants. LuckPerms is declared as an **optional** dependency
  (`mandatory = false`, `versionRange = "[5.0,)"`); rank sync is skipped entirely
  if LuckPerms is not installed.
- **Venary site integration** (`venary/`): `VenaryClient` (HTTP layer brought up
  from config on server start, shut down on stop), `VenaryConfig`, `PlayerSyncTask`
  (periodic player sync), and `/link` / `/unlink` commands to bind a Minecraft
  identity to a Venary site account.
- **Feature gating** (`features/`): `FeatureGate`, `FeatureRegistry`,
  `ServerConfigClient`, plus a `/feature <enable|disable|list|reload|status>`
  command to toggle subsystems against server-side config at runtime.
- **Config**: `ModConfig` gained `reload()` and `getVenaryConfig()` for live
  reconfiguration of the Venary/feature layers without a restart.

### Fixed
- **SLF4J classpath collision**: relocated bundled `org.slf4j` →
  `network.vonix.serverutilities.shadow.slf4j` in every platform's shadowJar
  block. VSU previously exported `org.slf4j.event`, which broke the JVM module
  layer (`ResolutionException`) on modpacks where another mod bundles its own
  SLF4J. (Caught on the HTTYD/Isle-of-Berk 1.18.2 boot test.)
- **SQLite relocation hardening**: bundled `org.sqlite` relocated to the
  vonix-private namespace alongside SLF4J. Every bundled third-party package is
  now relocated, not just the one that last caused a collision.
- **UTF-8 BOM in Forge `mods.toml`**: stripped the leading BOM that crashed
  Forge 1.18.2 mod-scan with `Invalid bare key: modLoader` before any mod loaded.
- **modId consistency**: renamed the mod id `vonix_server_utils` →
  `vonix_server_utilities` across `mods.toml`, `fabric.mod.json`, the mixins
  config (`vonix_server_utilities.mixins.json`), and all dependency blocks.

## [1.1.0] - 2026-05-24

Initial public version of the multi-version monorepo. (`mod_version` was born
at 1.1.0 in this repository; see the note under 1.0.1 below.)

### Added
- **Multi-version monorepo**: shared Architectury source built for
  1.18.2 (Fabric + Forge), 1.19.2 (Fabric + Forge), 1.20.1 (Fabric + Forge),
  and 1.21.1 (Fabric + NeoForge).
- **Backpack inventory viewing** — `/backsee <player>`: recursively scans a
  player's inventory, ender chest, curio/trinket slots, and armor for items
  carrying backpack NBT or container-component data. Works across all four
  target versions by abstracting NBT vs. Data Component lookup.
- **Accessory inventory viewing** — `/accsee <player>`: cross-platform accessory
  view integrating Curios API (Forge/NeoForge) and Trinkets API (Fabric) via
  Architectury's `@ExpectPlatform`.
- **Unified build menu** (`build_menu.py`): native GUI to mass-compile every
  version/loader target, with JDK auto-detection.
- **SQLite JDBC driver injection** for Forge/NeoForge via explicit
  `Class.forName` (works around `DriverManager`/bootstrap-classloader
  invisibility on FML).

### Fixed
- **Invsee desync**: replaced ad-hoc container wrappers with the `InvseeContainer`
  proxy, fixing `/invsee` throwing `UnsupportedOperationException` and mapping
  Main/Hotbar/Armor/Offhand correctly onto a 6-row chest UI.

## [1.0.1] — 2026-04-19 (pre-monorepo)

> **Lineage note.** 1.0.0 and 1.0.1 predate the monorepo and were released from
> the single-version 1.21.1 line (see that template's own `CHANGELOG.md`). When
> the monorepo was imported, `mod_version` was set to `1.1.0` and has read 1.1.0
> in every commit since — there is no commit in *this* repo where the version was
> literally `1.0.x`. These entries are preserved to document the real release
> lineage that 1.1.0 built on.

### Fixed
- `/nick` now actually applies the nickname: calls `setCustomName()` so
  `getDisplayName()` returns the nickname in chat and elsewhere.
- Tab list updates immediately on nickname set/clear via
  `ClientboundPlayerInfoUpdatePacket`.

## [1.0.0] — 2026-04-04 (pre-monorepo)

Initial release. Minecraft 1.21.1 on Fabric and NeoForge via Architectury.

### Added
- **Home system** — `/home`, `/sethome`, `/delhome`, `/homes`; configurable
  per-player limit (`max_homes`, default 5); SQLite-persisted, cross-dimension aware.
- **TPA system** — `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`; requests expire
  after `tpa_timeout_seconds` (default 120).
- **Back / BackDeath** — `/back` (pre-teleport position, never clobbered by death)
  and `/backdeath` (last death position, separate history, optional post-death delay).
- **Configuration** — auto-generated `config/vonix_server_utilities.properties`.
- **Storage** — SQLite at `config/vonix_server_utilities/data.db`, WAL mode,
  opened on `SERVER_STARTING` / closed on `SERVER_STOPPED`.

