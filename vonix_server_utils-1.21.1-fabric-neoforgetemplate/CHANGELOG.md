# Changelog

All notable changes to Vonix Server Utilities are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.4.0] — 2026-06-27

### Fixed
- **NCDFE crash on player join** when LuckPerms is absent (Sunlit
  Cobblemon canary). `LuckPermsBridge` rewritten as a probe-only public
  class with zero `net.luckperms.*` types on its surface; LP-typed code
  moved to package-private `LuckPermsBridgeImpl`, deferred-linked only
  when `Class.forName` probe confirms LP presence. Holder-class
  isolation pattern.

### Added
- **Curios soft-dep on `/backsee`** (`CuriosInventoryBridge.java`).
  Pass 0 in `openBackpack`: walk every Curios slot on the target
  player, and if any curio stack exposes `IItemHandler` open it via
  the existing capability bridge. Curios absent = silently skipped
  (reflection-only). Pass order is now Curios → DataComponents.CONTAINER
  → capability walk.

### Changed
- `LuckPermsBridge` public API: `get()` removed, replaced by
  `isPresent()` / `getUserPrefixInfo(uuid)` / semantic group methods.
  No LP types on the public surface anywhere.

---

## [1.3.0] — 2026-06-27

### Added
- `/backsee <target> [slot]` now opens **any backpack-style item that
  exposes the NeoForge `IItemHandler` capability** — Sophisticated
  Backpacks/Storage, vanilla shulker boxes, Iron Chests shulkers,
  Traveler's Backpack, Iron Backpacks, FunctionalStorage drawers-as-item,
  etc. Reflection-only — VSU still loads on Fabric where the capability
  system is absent.
- 1.21.1 specifically: the bridge probes
  `net.neoforged.neoforge.capabilities.Capabilities$ItemHandler#ITEM`
  (NeoForge's `ItemCapability<IItemHandler, Void>`); the existing
  vanilla `DataComponents.CONTAINER` pass for shulker boxes / bundles
  stays intact and runs first.
- Optional `slot` argument on `/backsee` for explicit per-slot targeting.

### Removed
- `inventory/SbpBackpackBridge.java` (the 1.3.0 RC's
  Sophisticated-Backpacks-specific reflection bridge into
  `BackpackStorage` saved-data). Subsumed by the universal capability
  walk.

---

## [1.0.1] — 2026-04-19

### Fixed
- `/nick` command now actually applies the nickname: calls `setCustomName()` on the player entity so `getDisplayName()` returns the nickname in chat and other contexts
- Tab list updated immediately on nickname set/clear via `ClientboundPlayerInfoUpdatePacket`

---

## [1.0.0] — 2026-04-04

Initial release. Supports Minecraft 1.21.1 on Fabric and NeoForge via Architectury API.

### Added

#### Home system
- `/home [name]` — Teleport to a saved home (defaults to `home` if no name given)
- `/sethome [name]` — Save current position as a named home (defaults to `home`)
- `/delhome <name>` — Delete a named home
- `/homes` — List all saved homes with a used/limit counter
- Configurable per-player home limit (`max_homes`, default `5`)
- Homes persisted in SQLite; survive server restarts and cross-dimension aware

#### TPA (teleport request) system
- `/tpa <player>` — Request to teleport to another player
- `/tpahere <player>` — Request another player to teleport to you
- `/tpaccept` — Accept the pending teleport request
- `/tpdeny` — Deny the pending teleport request
- Requests expire automatically after a configurable timeout (`tpa_timeout_seconds`, default `120`)
- Both requester and target receive feedback messages on accept/deny

#### Back / BackDeath
- `/back` — Return to the position you were at before your last teleport
  - Updated on every `/home`, `/tpa` teleport, etc.
  - **Never** overwritten by death — death cannot clobber teleport history
- `/backdeath` — Return to the position where you last died
  - Stored in a fully separate history from `/back`
  - Configurable mandatory wait after death before the command becomes usable
    (`death_back_delay_seconds`, default `0` = instant)
  - Shows a countdown message when the delay has not yet elapsed

#### Configuration
- Config file auto-generated at `config/vonix_server_utilities.properties` on first run
- Options: `max_homes`, `tpa_timeout_seconds`, `death_back_delay_seconds`

#### Storage
- SQLite database at `config/vonix_server_utilities/data.db`
- WAL journal mode enabled for reduced lock contention
- Connection opened on `SERVER_STARTING` and closed cleanly on `SERVER_STOPPED`

### Platform support
| Platform  | Version     |
|-----------|-------------|
| Minecraft | 1.21.1      |
| Fabric    | Loader ≥ 0.18.6, API 0.116.10+1.21.1 |
| NeoForge  | 21.1.215    |
| Architectury API | 13.0.8 |
| Java      | 21          |
