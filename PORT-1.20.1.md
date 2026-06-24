# Port 1.21.1 → 1.20.1 Feature Set

**Source:** `vonix_server_utils-1.21.1-fabric-neoforgetemplate/`
**Target:** `vonix_server_utils-1.20.1-fabric-forge-template/`
**Date:** 2026-06-21
**Mode:** Mechanical port, no compile available.

---

## 1. New packages / files copied (11 files)

From 1.21.1 common/src/main/java/network/vonix/serverutilities/ → 1.20.1 same path:

| Package | File |
|--|--|
| venary/ | VenaryClient.java |
| venary/ | VenaryConfig.java |
| venary/ | LinkCommands.java |
| venary/ | PlayerSyncTask.java |
| features/ | FeatureRegistry.java |
| features/ | ServerConfigClient.java |
| features/ | FeatureGate.java |
| donation_ranks/ | LuckPermsBridge.java |
| donation_ranks/ | RankSyncTask.java |
| donation_ranks/ | RankGroupSyncer.java |
| command/ | FeatureCommand.java |

## 2. Existing files overwritten with Wave-4 fixed versions (8 files)

| Path | Why |
|--|--|
| database/Database.java | migration safety + persistence tables + helpers |
| config/ModConfig.java | Venary keys + `reload()` |
| teleport/TeleportManager.java | DB-backed persistence + `hydrateFromDb` |
| command/UtilityCommands.java | `.requires()` gating + DB persistence |
| command/ModCommands.java | `.requires()` + `/vonixsu feature` + working `/vonixsu reload` |
| command/WorldCommands.java | `.requires()` gating |
| kits/KitManager.java | JSON-loaded with `/kit reload` |
| listener/EventHandler.java | full wiring (FeatureRegistry hydrate, RankSync, PlayerSync) |

File-count check: 14 → 25 (+11). ✅

## 3. API-drift swaps

### 3a. `getServerDirectory()` returns `File` on 1.20 (not `Path` as in 1.21)

| File | Line | Before | After |
|--|--|--|--|
| listener/EventHandler.java | 43 | `server.getServerDirectory().resolve("config")` | `server.getServerDirectory().toPath().resolve("config")` |
| database/Database.java | 239 | `server.getServerDirectory().resolve("vonixcore/vonixcore.db").toFile()` | `server.getServerDirectory().toPath().resolve("vonixcore/vonixcore.db").toFile()` |

(Database.java line 234 `server.getWorldPath(LevelResource.ROOT)` is unchanged — that API returns `Path` on both 1.20 and 1.21.)

### 3b. `DataComponents.CONTAINER` / `ItemContainerContents` (1.20.5+ only)

**Problem:** 1.21 `openBackpack` in UtilityCommands.java uses `net.minecraft.core.component.DataComponents.CONTAINER` and `net.minecraft.world.item.component.ItemContainerContents` — both introduced in MC 1.20.5. Not available on 1.20.1.

**Resolution:** Replaced the body of `openBackpack(...)` (UtilityCommands.java lines ~569–636) with the legacy NBT-based implementation that already existed in the 1.20.1 template at HEAD (reading `Items` / `inventory` / `BlockEntityTag.Items` list tags via `CompoundTag.getList`). Behavior is equivalent. Marked with a PORT-NOTE comment, not PORT-BLOCKED, because it is fully resolved.

No other 1.21-only API surfaces were detected. Surveyed for:
- `ResourceLocation.parse` / `fromNamespaceAndPath` → none used (KitManager uses 1.20-stable `ResourceLocation.tryParse` + `BuiltInRegistries.ITEM.get`)
- `HolderLookup` / `registryAccess().lookupOrThrow` → none used
- NeoForge-only packages (`net.neoforged.*`) → none used
- `Component.literal` / `getName().getString()` / `sendCommands(player)` → identical on 1.20.1
- `Commands.literal` + `CommandSourceStack` → identical
- `LevelResource.ROOT` → identical
- `java.net.http` (VenaryClient) → JDK-level, fine
- `BuiltInRegistries.ITEM` → exists on 1.20.1

## 4. Build / metadata changes

| File | Change |
|--|--|
| `common/build.gradle` | Added `compileOnly 'net.luckperms:api:5.4'` to dependencies block |
| `forge/src/main/resources/META-INF/mods.toml` | Added `[[dependencies.vonix_server_utilities]]` block for `luckperms` with `mandatory = false`, `versionRange = "[5.4,)"`, `ordering = "AFTER"`, `side = "BOTH"` (Forge syntax) |
| `fabric/src/main/resources/fabric.mod.json` | Added `"suggests": { "luckperms": ">=5.4" }` block |

## 5. PORT-BLOCKED markers

**None.** All API drift was resolvable mechanically.

## 6. Remaining `PORT-NOTE:` comments

All 12 existing PORT-NOTEs (carried over from the 1.21 source) are documentation-only — they describe APIs that are *stable across 1.18.2–1.21.1* and required no action:

- VenaryClient.java:34 — `java.net.http` is JDK 11+, identical everywhere
- LinkCommands.java:24,99,105,110,132 — Chat component APIs identical
- PlayerSyncTask.java:25 — Architectury `TickEvent.Server.SERVER_POST` exists on all targets
- ServerConfigClient.java:22,105 — Same `TickEvent` + `sendCommands(player)` available
- FeatureGate.java:21 — `CommandSourceStack` identical on 1.20.1 / 1.21.1
- RankSyncTask.java:35 — `ServerPlayer.getUUID()` / `.getName().getString()` stable
- LuckPermsBridge.java:123 — LuckPerms 5.4 API stable across all targets

Plus one **added** PORT-NOTE in UtilityCommands.java explaining the backpack-API restoration.

## 7. Stale 1.21-only class references

None found.

## 8. Ready-to-compile assessment

**Status: READY TO COMPILE** (pending an actual Gradle build, which is not available in this environment).

- All Java sources reference only APIs present on Minecraft 1.20.1 / Forge 47 / Fabric Loader 0.19.2 / Architectury 9.2.14.
- LuckPerms 5.4 is declared `compileOnly` and guarded at runtime by `LuckPermsBridge.get()` (catches `NoClassDefFoundError`).
- Forge mods.toml and fabric.mod.json correctly declare luckperms as optional/suggested.
- Backpack feature uses the 1.20.1 NBT layout, matching what the template originally shipped.

Risks the operator should still smoke-test in a real build:
1. Verify `KitManager.reloadFromJson(MinecraftServer)` signature matches what `EventHandler` and `/kit reload` call (carried over verbatim from 1.21, no signature drift expected).
2. Confirm `ModConfig.INSTANCE.reload()` works without a prior `load()` if `/vonixsu reload` is run before `SERVER_STARTING` (should be impossible in practice).
3. Architectury `LifecycleEvent.SERVER_STARTED` / `PlayerEvent.PLAYER_JOIN` registration on Architectury 9.2.14 — names match the 1.21 (Architectury 13) ones used here; no shim needed but worth a quick build.
