# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-05-24

### Added
- **Backpack Inventory Viewing**: Added `/backsee <player>` command.
  - Recursively scans a player's inventory, ender chest, curio/trinket slots, and armor for any items containing backpack NBT data or container component data.
  - Supports Backpack mods across all target versions (1.18.2, 1.19.2, 1.20.1, 1.21.1) by abstracting NBT and Data Component lookup.
- **Accessory Inventory Viewing**: Added `/accsee <player>` command.
  - Cross-platform support for viewing a player's accessories.
  - Integrates with Curios API (Forge/NeoForge) and Trinkets API (Fabric) via Architectury's `@ExpectPlatform` system.
- **Build System**: Unified `build_menu.py` native GUI across all supported mod loaders to make mass compiling multi-version projects faster.

### Fixed
- **Invsee UI Bug**: Replaced arbitrary container wrappers with `InvseeContainer` proxy wrapper. This fixes `/invsee` throwing `UnsupportedOperationException` and desyncs, mapping the player's Main, Hotbar, Armor, and Offhand perfectly to a 6-row Chest menu UI.

## [1.0.1] - Previous Release

### Added
- Initial framework for multi-version monorepo:
  - 1.18.2 (Fabric + Forge)
  - 1.19.2 (Fabric + Forge)
  - 1.20.1 (Fabric + Forge)
  - 1.21.1 (Fabric + NeoForge)
- SQLite JDBC driver injection for Forge/NeoForge.
