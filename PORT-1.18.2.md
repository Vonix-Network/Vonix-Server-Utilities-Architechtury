# Port to 1.18.2 — Summary

Reference: `vonix_server_utils-1.21.1-fabric-neoforgetemplate/` (via 1.20.1 sibling as intermediate).

## Files copied (11 new)
- `command/FeatureCommand.java`
- `donation_ranks/{LuckPermsBridge, RankGroupSyncer, RankSyncTask}.java`
- `features/{FeatureGate, FeatureRegistry, ServerConfigClient}.java`
- `venary/{LinkCommands, PlayerSyncTask, VenaryClient, VenaryConfig}.java`

## Files modified (14 + 3 metadata)
- Wave-4 fixed common files copied over: `Database.java`, `ModConfig.java`, `TeleportManager.java`, `UtilityCommands.java`, `ModCommands.java`, `WorldCommands.java`, `KitManager.java`, `EventHandler.java`, plus `HomeManager/WarpManager/AdminManager/InvseeContainer/AccessoryHelper/VonixServerUtilities` left in place (no logic change there).
- `common/build.gradle` — added `compileOnly 'net.luckperms:api:5.4'`
- `forge/src/main/resources/META-INF/mods.toml` — appended LuckPerms optional dep (`mandatory = false`)
- `fabric/src/main/resources/fabric.mod.json` — added `"luckperms": "*"` under `suggests`; corrected mixin reference to `vonix_server_utilities.mixins.json`

## API drift patches (Java)
| Issue | Sites | Fix |
|---|---|---|
| `Component.literal(...)` doesn't exist in 1.18.2 | 164 | `new TextComponent(...)` + import |
| `Component.empty()` doesn't exist | a few | `new TextComponent("")` |
| `entity.sendSystemMessage(comp)` doesn't exist | 50 | `entity.sendMessage(comp, Util.NIL_UUID)` |
| `sendSuccess(Supplier<Component>, bool)` doesn't exist | several | `sendSuccess(Component, bool)` (pre-1.20.2 form) |
| `getServerDirectory()` returns File | 2 | `.toPath().resolve(...)` (came in via 1.20.1 base) |
| User-Agent string `MC/1.21.1` | 1 | `MC/1.18.2` |

## Mixin config decision — option (b) applied
1.18.2 had ONLY the stale `vonix_server_utils.mixins.json` (pointing at non-existent `network.vonix.utils.mixin`). The audit flagged this; metadata-fix wave couldn't auto-fix without a reference. Resolution:
- **Deleted:** `common/src/main/resources/vonix_server_utils.mixins.json`
- **Created:** `common/src/main/resources/vonix_server_utilities.mixins.json` with:
  ```json
  {
    "required": false,
    "package": "network.vonix.serverutilities.mixin",
    "compatibilityLevel": "JAVA_17",
    "mixins": [],
    "client": []
  }
  ```
  Empty arrays — project doesn't actually use mixins at runtime per audit. Filename now matches both `fabric.mod.json` and `forge mods.toml` references.

## Verification
- File count in `common/src/main/java/.../`: 25 ✅ (matches 1.20.1 and 1.21.1)
- Zero remaining `Component.literal(` calls
- Zero remaining `sendSystemMessage(` calls
- Zero remaining `sendSuccess(() ->` lambda forms
- Zero `PORT-BLOCKED-1.18` markers — everything translated mechanically
- 12 remaining `PORT-NOTE` comments are doc-only (cross-version-stable APIs)

## Status
**Ready to compile** with JDK 17 + Forge 1.18.2 / Fabric 1.18.2 toolchain.
