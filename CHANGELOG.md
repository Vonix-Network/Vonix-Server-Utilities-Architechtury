# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

