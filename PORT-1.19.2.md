# Port 1.21.1 → 1.19.2 — Summary

## Strategy
The 1.20.1 sibling template already contains a clean port of the 1.21.1 feature
set, with every Architectury/Mojang API drift already collapsed to the form
that works on 1.20.1. The 1.19.2 surface for everything we touch
(ChatFormatting, Component, MutableComponent, Style, Commands.literal,
CommandSourceStack, sendSuccess(Supplier, boolean), LevelResource.ROOT,
SimpleContainer, SimpleMenuProvider, ChestMenu.sixRows, ServerPlayer.openMenu,
Architectury TickEvent.Server.SERVER_POST, ItemStack legacy NBT layout) is
identical to 1.20.1 — so this port mirrors 1.20.1 byte-for-byte except for the
version strings, fabric.mod.json minecraft dep, and the Forge mods.toml ranges
which were already correct in the existing 1.19.2 template scaffolding.

## 1. New packages copied (from 1.20.1, identical to 1.21.1 content modulo
  drift)
- `venary/` (4 files): LinkCommands, PlayerSyncTask, VenaryClient,
  VenaryConfig
- `features/` (3 files): FeatureGate, FeatureRegistry, ServerConfigClient
- `donation_ranks/` (3 files): LuckPermsBridge, RankGroupSyncer, RankSyncTask
- `command/FeatureCommand.java`

## 2. Modified files overwritten (8 of the modified set, plus 4 more the
  1.20.1 builder also touched):
VonixServerUtilities, admin/AdminManager, command/ModCommands,
command/UtilityCommands, command/WorldCommands, config/ModConfig,
database/Database, homes/HomeManager, inventory/InvseeContainer,
kits/KitManager, listener/EventHandler, teleport/TeleportManager,
warps/WarpManager.

Final common/ file count: **25** (matches 1.21.1 and 1.20.1).

## 3. API drift patches applied
- `getServerDirectory().resolve(...)` — already in `.toPath().resolve(...)`
  form in 1.20.1 sources, so cleanly applies to 1.19.2 (server.getServerDirectory()
  returns File on both 1.19.2 and 1.20.1; identical wrap).
- UtilityCommands invsee/backpack: 1.20.1 path uses legacy NBT
  (Items / inventory / BlockEntityTag.Items) — this is the correct shape for
  1.19.2 (DataComponents was added in 1.20.5). No further changes needed.
- Verified no usages of newer 1.20+-only API leaked through:
  DataComponents/ItemContainerContents/HolderLookup/BlockPos.containing — none
  found outside of doc-only PORT-NOTE comments.

## 4. Version-string fixes (1.21.1 → 1.19.2)
- `command/ModCommands.java`: "Platform: Architectury 1.21.1" →
  "Platform: Architectury 1.19.2"
- `venary/VenaryClient.java`: user-agent `MC/1.21.1` → `MC/1.19.2`

## 5. Build-file changes
- `common/build.gradle`: replaced with 1.20.1 version which adds
  `compileOnly 'net.luckperms:api:5.4'` (plus the existing
  fabric-loader/architectury/sqlite-jdbc deps).
- `forge/src/main/resources/META-INF/mods.toml`: appended LuckPerms optional
  dep block:
  ```
  [[dependencies.vonix_server_utilities]]
  modId = "luckperms"
  mandatory = false
  versionRange = "[5.4,)"
  ordering = "AFTER"
  side = "BOTH"
  ```
  Existing 1.19.2 ranges (`loaderVersion = "[43,)"`, `forge [43,)`,
  `minecraft [1.19.2,1.20)`, `architectury [6.6.92,)`) left untouched per task
  instructions.
- `fabric/src/main/resources/fabric.mod.json`: appended `"suggests": { "luckperms": "*" }`.
  Existing `"minecraft": "~1.19.2"` and `"java": ">=17"` left untouched.

## 6. Verification
- File counts equal 1.21.1 / 1.20.1: ✅
- `diff -rq` vs 1.20.1 common/ shows only the 3 intentional version-string
  diffs (ModCommands, VenaryClient — already discussed).
- No stale `1.21` runtime references remain. Three doc-only mentions of "1.20.1"
  and one of "1.21" survive inside PORT-NOTE comments (they are intentional
  cross-version compatibility notes shipped with the source — informational only).
- No `PORT-NOTE` markers required code action; all are documentation of why a
  given API is portable.
- No `PORT-BLOCKED-1.19` markers were necessary — every touched call site
  resolves on 1.19.2 identically to 1.20.1.

## Files Modified / Created
Created (new files copied from 1.20.1):
- common/src/main/java/network/vonix/serverutilities/venary/{LinkCommands,PlayerSyncTask,VenaryClient,VenaryConfig}.java
- common/src/main/java/network/vonix/serverutilities/features/{FeatureGate,FeatureRegistry,ServerConfigClient}.java
- common/src/main/java/network/vonix/serverutilities/donation_ranks/{LuckPermsBridge,RankGroupSyncer,RankSyncTask}.java
- common/src/main/java/network/vonix/serverutilities/command/FeatureCommand.java

Modified (overwritten with 1.20.1 versions + version-string fixes):
- common/src/main/java/network/vonix/serverutilities/VonixServerUtilities.java
- common/src/main/java/network/vonix/serverutilities/admin/AdminManager.java
- common/src/main/java/network/vonix/serverutilities/command/{ModCommands,UtilityCommands,WorldCommands}.java
- common/src/main/java/network/vonix/serverutilities/config/ModConfig.java
- common/src/main/java/network/vonix/serverutilities/database/Database.java
- common/src/main/java/network/vonix/serverutilities/homes/HomeManager.java
- common/src/main/java/network/vonix/serverutilities/inventory/InvseeContainer.java
- common/src/main/java/network/vonix/serverutilities/kits/KitManager.java
- common/src/main/java/network/vonix/serverutilities/listener/EventHandler.java
- common/src/main/java/network/vonix/serverutilities/teleport/TeleportManager.java
- common/src/main/java/network/vonix/serverutilities/warps/WarpManager.java
- common/build.gradle
- forge/src/main/resources/META-INF/mods.toml
- fabric/src/main/resources/fabric.mod.json

## Issues / Notes
- No compile attempted per task constraints.
- No issues blocking the port. Every drift point translates cleanly because
  1.19.2 and 1.20.1 share the same server-command / NBT / Component surface.
- Forge/Fabric loader ranges and minecraft range left at 1.19.2 values as
  instructed — only the LuckPerms optional dep block was appended.
