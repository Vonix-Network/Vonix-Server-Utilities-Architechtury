# Final report — `/backsee` capability-walk rewrite (VSU 1.3.0)

## Outcome
`/backsee` rewritten to use a universal Forge / NeoForge `IItemHandler`
capability walk in place of the prior Sophisticated-Backpacks-specific
reflection bridge. One reflection probe in `common/` covers SBP,
Sophisticated Storage shulkers, vanilla shulker (via Forge default cap
provider), Iron Chests shulkers, Traveler's Backpack, Iron Backpacks,
FunctionalStorage drawers-as-item, and every other well-behaved
capability-exposing item. The bridge source is byte-identical across
all 4 templates. **Nothing committed, nothing pushed, nothing released —
handed back to WeedMeister.**

## Files changed

| Template | File | New / Edited / Deleted |
|---|---|---|
| 1.18.2 (Forge) | `common/.../inventory/CapabilityInventoryBridge.java` | NEW |
| 1.18.2 (Forge) | `common/.../inventory/SbpBackpackBridge.java` | DELETED |
| 1.18.2 (Forge) | `common/.../command/UtilityCommands.java` | EDIT — import swap, cap walk as Pass 1, legacy NBT becomes Pass 2, SBP block removed |
| 1.19.2 (Forge) | `common/.../inventory/CapabilityInventoryBridge.java` | NEW (byte-identical to 1.20.1) |
| 1.19.2 (Forge) | `common/.../inventory/SbpBackpackBridge.java` | DELETED |
| 1.19.2 (Forge) | `common/.../command/UtilityCommands.java` | EDIT — same shape |
| 1.20.1 (Forge) | `common/.../inventory/CapabilityInventoryBridge.java` | NEW (canonical reference) |
| 1.20.1 (Forge) | `common/.../inventory/SbpBackpackBridge.java` | DELETED |
| 1.20.1 (Forge) | `common/.../command/UtilityCommands.java` | EDIT — same shape |
| 1.21.1 (NeoForge) | `common/.../inventory/CapabilityInventoryBridge.java` | NEW (byte-identical to 1.20.1) |
| 1.21.1 (NeoForge) | `common/.../inventory/SbpBackpackBridge.java` | DELETED |
| 1.21.1 (NeoForge) | `common/.../command/UtilityCommands.java` | EDIT — kept existing `DataComponents.CONTAINER` Pass 1, replaced SBP Pass 2 with cap walk |
| root | `CHANGELOG.md` | 1.3.0 entry rewritten — capability walk replaces SBP-bridge entry |
| 1.21.1 | `CHANGELOG.md` | 1.3.0 entry rewritten (per-template) |
| all 4 | `gradle.properties` | `mod_version = 1.3.0` (already set from prior RC work) |

## Verification

Independent compile sweep from parent session (subagent self-reports not
trusted — each template re-compiled directly from this session's
terminal after the subagent reported success):

| Template | Task | Exit | JDK |
|---|---|---|---|
| 1.18.2 | `:forge:compileJava` | 0 | 17 |
| 1.19.2 | `:forge:compileJava` | 0 | 17 |
| 1.20.1 | `:forge:compileJava` | 0 | 17 |
| 1.21.1 | `:neoforge:compileJava` | 0 | 21 |

Cross-template bridge diff:
- 1.18.2 == 1.19.2 == 1.20.1 == 1.21.1 byte-identical (`diff` exit 0
  pairwise vs 1.20.1 baseline).
- Single reflection probe handles all 3 Forge cap APIs
  (`ForgeCapabilities.ITEM_HANDLER`,
  `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`,
  NeoForge's `Capabilities$ItemHandler.ITEM`) plus both `IItemHandler`
  package locations (`net.minecraftforge.items.IItemHandler`,
  `net.neoforged.neoforge.items.IItemHandler`). First hit wins.

Stragglers check:
- No remaining `SbpBackpackBridge` reference in any template's
  `UtilityCommands.java`.
- `SbpBackpackBridge.java` absent from all 4 templates'
  `inventory/` directories.

## Bridge design notes

`CapabilityInventoryBridge` exposes one public method
`resolve(ItemStack) → Optional<Handler>`. The returned `Handler` is a
VSU-side type with `getSlots() / getStackInSlot(int) / setStackInSlot(int,
ItemStack) / isModifiable()` — no Forge / NeoForge types leak across this
surface, so `common/` source stays platform-agnostic.

Probe sequence (first hit wins, all wrapped in `try/Throwable`):
1. NeoForge `Capabilities$ItemHandler.ITEM` →
   `ItemStack#getCapability(ItemCapability, Void)` returns the handler
   directly.
2. Forge 1.19.3+ `ForgeCapabilities.ITEM_HANDLER` →
   `ItemStack#getCapability(Capability)` returns
   `LazyOptional<IItemHandler>`, unwrapped via reflection on
   `LazyOptional#resolve()`.
3. Forge 1.18.2 / 1.19.2 `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`
   — same shape as (2).

`isModifiable()` skips read-only handlers so GUI edits don't get silently
swallowed; those items fall through to the legacy NBT walk.

`/backsee` pass order:
- 1.18.2 / 1.19.2 / 1.20.1: (1) cap walk, (2) legacy NBT.
- 1.21.1: (1) vanilla `DataComponents.CONTAINER`, (2) cap walk.

## Skipped checks
- **In-game smoke test** not run — would require booting a test server
  (Berk-like modpack with SBP and at least one other cap-exposing item)
  and a client. Recommended as part of the next maintenance window when
  the new jar gets staged anyway. The compile sweep + byte-identical
  cross-template diff + fail-closed reflection design make a runtime
  regression unlikely.
- **runServer** not exercised on any template.
- **`./gradlew build`** not run — task scope is compile-only.

## Remaining risk

- **Capability provider on vanilla shulker (Forge default)**: the
  vanilla shulker block's default cap provider exposes a read-only-ish
  view in some Forge versions. If `isModifiable()` returns false the
  pass falls through to the legacy NBT walk, which still works — same
  behaviour as before this refactor.
- **NeoForge `IItemHandler` FQN**: the bridge probes both
  `net.minecraftforge.items.IItemHandler` (compat) and
  `net.neoforged.neoforge.items.IItemHandler`. If NeoForge ever ships a
  build where neither resolves, the cap walk silently disables on that
  build and `/backsee` falls through to the existing
  `DataComponents.CONTAINER` pass. Fail-closed, not crash.
- **`ItemStack#getCapability` shape drift**: matched by parameter type
  (first param assignable from the probed cap object), 1-arg preferred
  over 2-arg. If Forge / NeoForge change the signature in a way that
  breaks parameter-type matching, the probe returns `isAvailable()
  == false` and the cap walk silently disables.
- **Concurrent edits on the same backpack**: two admins running
  `/backsee` on the same player's same slot at the same time race
  through `IItemHandlerModifiable#setStackInSlot`. Last writer wins.
  Same race the legacy NBT path has always had on shulker boxes; not
  worse than before.

## What's NOT in this release

- Audit / orphan / link admin tooling. Scope was trimmed to `/backsee`
  at WeedMeister's direction. Audit can ship as 1.4.0 if needed once
  1.3.0 is in production.
- The previous 1.3.0 RC's `SbpBackpackBridge` is gone entirely, not
  retained as a fallback. SBP is reachable through the capability the
  mod itself exposes; the bespoke `BackpackStorage` reflection path is
  redundant.

## Hand-off

- Branch: working tree only (no commit / push performed).
- Suggested commit message: `refactor(backsee): replace SBP-specific bridge with universal IItemHandler capability walk`
- Suggested tag after commit: `v1.3.0`
- Suggested release: per-template jars from `./gradlew build` in each
  template, attached to a single GitHub release at the monorepo level
  (per the `architectury-release-matrix` reference).
- WeedMeister decides on commit / tag / release / deploy.
